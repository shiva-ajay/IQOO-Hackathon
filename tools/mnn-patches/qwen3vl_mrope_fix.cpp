// Fixes the rotary embedding in taobao-mnn's pre-converted Qwen3-VL llm.mnn (FixLens, 2026-09-26).
//
// The graph was exported with Qwen2-VL-style *chunked* M-RoPE: angle channels 0-23 rotate by the t position,
// 24-43 by h and 44-63 by w. Qwen3-VL is trained with *interleaved* M-RoPE (mrope_interleaved=true): channel i
// uses h if i%3==1, w if i%3==2 (for i < 60), and t otherwise. Text tokens have t == h == w, so text is
// unaffected, but for image tokens h lands only on tiny frequencies and w on near-zero ones: the LLM can't tell
// where anything is in the picture, and grounding boxes come out wrong (x worst).
//
// The rotary subgraph is Concat(pos_t * theta[0:24], pos_h * theta[24:44], pos_w * theta[44:64]). This tool
// rewrites it losslessly (flatbuffer object API, no JSON round trip) as
//     pos_t * thetaT + pos_h * thetaH + pos_w * thetaW
// where each theta vector is the full 64-channel theta masked to the channels of that axis in interleaved order.
// Only three constants and one op change; the weights (llm.mnn.weight) are untouched.
//
// Build + run: tools/mnn-patches/qwen3vl_mrope_fix.sh <model_dir>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <memory>
#include <string>
#include <vector>
#include "MNN_generated.h"

using namespace MNN;

// text_config.rope_theta of Qwen3-VL 2B/4B/8B-Instruct.
static constexpr double kRopeTheta = 5000000.0;

static OpT* findOp(NetT* net, const std::string& name) {
    for (auto& op : net->oplists) if (op->name == name) return op.get();
    return nullptr;
}

int main(int argc, char** argv) {
    if (argc != 3) {
        printf("Usage: %s llm.mnn out.mnn\n", argv[0]);
        return 1;
    }
    std::ifstream in(argv[1], std::ios::binary);
    std::vector<char> buf((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    std::unique_ptr<NetT> net(UnPackNet(buf.data()));

    auto* cT = findOp(net.get(), "/rotary/Constant_3_output_0");
    auto* cH = findOp(net.get(), "/rotary/Constant_5_output_0");
    auto* cW = findOp(net.get(), "/rotary/Constant_7_output_0");
    auto* concat = findOp(net.get(), "/rotary/Concat_output_0");
    auto* mulT = findOp(net.get(), "/rotary/Mul_output_0");
    if (!cT || !cH || !cW || !concat || !mulT || concat->type != OpType_Concat) {
        printf("Rotary subgraph not found or already patched; nothing to do.\n");
        return 2;
    }
    auto& t = cT->main.AsBlob()->float32s;
    auto& h = cH->main.AsBlob()->float32s;
    auto& w = cW->main.AsBlob()->float32s;
    const std::vector<int> section = {(int)t.size(), (int)h.size(), (int)w.size()};
    std::vector<float> theta(t);
    theta.insert(theta.end(), h.begin(), h.end());
    theta.insert(theta.end(), w.begin(), w.end());
    const int half = (int)theta.size();
    printf("mrope_section [%d, %d, %d], %d channels, theta[0]=%g theta[1]=%g theta[last]=%g\n", section[0], section[1],
           section[2], half, theta[0], theta[1], theta[half - 1]);
    // The shipped constants are rounded to 6 decimals (the last ones read 0). Regenerate them exactly from
    // Qwen3-VL's rope_theta (5e6, rotary dim 2*half), after checking that is what they were made from.
    double maxDiff = 0;
    std::vector<float> exact(half);
    for (int i = 0; i < half; ++i) {
        exact[i] = (float)std::pow(kRopeTheta, -2.0 * i / (2.0 * half));
        maxDiff = std::max(maxDiff, (double)std::fabs(exact[i] - theta[i]));
    }
    if (maxDiff > 1e-5) {
        printf("Stored theta doesn't match rope_theta=%g (max diff %g); refusing to patch.\n", kRopeTheta, maxDiff);
        return 4;
    }
    printf("theta matches rope_theta=%g (max rounding %g); using exact values\n", kRopeTheta, maxDiff);
    theta = exact;

    // Interleaved assignment (HF Qwen3-VL apply_interleaved_mrope): h on 1,4,..,3*sec_h-2; w on 2,5,..,3*sec_w-1.
    std::vector<int> axis(half, 0);
    for (int i = 1; i < section[1] * 3; i += 3) axis[i] = 1;
    for (int i = 2; i < section[2] * 3; i += 3) axis[i] = 2;
    int counts[3] = {0, 0, 0};
    for (int a : axis) counts[a]++;
    if (counts[0] != section[0] || counts[1] != section[1] || counts[2] != section[2]) {
        printf("Unexpected section layout (%d/%d/%d); refusing to patch.\n", counts[0], counts[1], counts[2]);
        return 3;
    }
    OpT* consts[3] = {cT, cH, cW};
    for (int a = 0; a < 3; ++a) {
        auto* blob = consts[a]->main.AsBlob();
        blob->float32s.assign(half, 0.f);
        for (int i = 0; i < half; ++i) if (axis[i] == a) blob->float32s[i] = theta[i];
        blob->dims = {1, half};
    }

    // Concat(a, b, c) -> Add(Add(a, b), c), writing to the Concat's output tensor so consumers are unchanged.
    const int a = concat->inputIndexes[0], b = concat->inputIndexes[1], c = concat->inputIndexes[2];
    const int out = concat->outputIndexes[0];
    const int mid = (int)net->tensorName.size();
    net->tensorName.push_back("/rotary/fixlens_interleaved_sum");
    auto makeAdd = [&](const std::string& name, int x, int y, int o) {
        std::unique_ptr<OpT> op(new OpT);
        op->name = name;
        op->type = OpType_BinaryOp;
        op->main.type = OpParameter_BinaryOp;
        auto* p = new BinaryOpT;
        p->opType = BinaryOpOperation_ADD;
        p->T = mulT->main.AsBinaryOp()->T;
        op->main.value = p;
        op->inputIndexes = {x, y};
        op->outputIndexes = {o};
        return op;
    };
    for (size_t i = 0; i < net->oplists.size(); ++i) {
        if (net->oplists[i].get() != concat) continue;
        net->oplists[i] = makeAdd("/rotary/fixlens_add_th", a, b, mid);
        net->oplists.insert(net->oplists.begin() + i + 1, makeAdd("/rotary/fixlens_add_w", mid, c, out));
        break;
    }

    flatbuffers::FlatBufferBuilder builder(1024);
    builder.ForceDefaults(true);  // like the original file, so a dump diff shows only the rotary change
    builder.Finish(Net::Pack(builder, net.get()));
    std::ofstream(argv[2], std::ios::binary).write((const char*)builder.GetBufferPointer(), builder.GetSize());
    printf("Patched: interleaved M-RoPE (t=%d h=%d w=%d channels) -> %s\n", counts[0], counts[1], counts[2], argv[2]);
    return 0;
}
