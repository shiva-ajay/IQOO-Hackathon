// M1 grounding harness: runs FixLens's exact prompts through Qwen3-VL on MNN, on the phone, and reports the raw
// reply and timings per case. Mirrors the app: same config, system prompt prefilled first (like the session
// prewarm), then the timed user turn decoded token by token.
// Usage (via run_ground_test.py): ground_test <config.json> <cases.txt>
//   cases.txt: a system prompt, then one user turn per case, separated by lines "=====".
//   Optional: [max_tokens] [extra config JSON] [split|joined]. "split" (default) prefills the system prompt on its
//   own first, as the app does; "joined" sends system + user turn as one prompt.
#include <llm/llm.hpp>
#include <chrono>
#include <cstdio>
#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

using MNN::Transformer::Llm;
using Clock = std::chrono::steady_clock;

static std::string jsonEscape(const std::string& s) {
    std::string o;
    for (char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\t': o += "\\t"; break;
            case '\r': break;
            default: o += c;
        }
    }
    return o;
}

// Index just past the first complete JSON value (after optional ``` fence), or -1.
static long boxLineEnd(const std::string& s) {
    size_t i = s.find_first_not_of(" \n\t");
    if (i == std::string::npos) return -1;
    if (s.compare(i, 3, "```") == 0) {
        size_t close = s.find("```", s.find('\n', i) == std::string::npos ? s.size() : s.find('\n', i));
        return close == std::string::npos ? -1 : (long)(close + 3);
    }
    if (s[i] != '{' && s[i] != '[') return 0;  // not JSON: "box" resolves immediately
    int depth = 0;
    bool str = false, esc = false;
    for (size_t k = i; k < s.size(); ++k) {
        char c = s[k];
        if (str) { if (esc) esc = false; else if (c == '\\') esc = true; else if (c == '"') str = false; continue; }
        if (c == '"') str = true;
        else if (c == '{' || c == '[') depth++;
        else if ((c == '}' || c == ']') && --depth == 0) return (long)k + 1;
    }
    return -1;
}

int main(int argc, char** argv) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    std::ifstream in(argv[2]);
    std::stringstream ss;
    ss << in.rdbuf();
    std::vector<std::string> parts;
    std::string all = ss.str(), sep = "\n=====\n";
    size_t pos = 0;
    while (true) {
        size_t n = all.find(sep, pos);
        parts.push_back(all.substr(pos, n == std::string::npos ? std::string::npos : n - pos));
        if (n == std::string::npos) break;
        pos = n + sep.size();
    }
    int maxTokens = argc > 3 ? atoi(argv[3]) : 150;
    bool joined = argc > 5 && std::string(argv[5]) == "joined";
    std::unique_ptr<Llm> llm(Llm::createLLM(argv[1]));
    llm->set_config(R"({"backend_type":"cpu","thread_num":4,"precision":"low","sampler_type":"greedy",
        "reuse_kv":true,"use_template":false,"max_all_tokens":4096,"max_new_tokens":150,
        "tmp_path":"/data/local/tmp/fixlens-m0"})");
    if (argc > 4 && std::string(argv[4]).size() > 2) llm->set_config(argv[4]);
    llm->load();
    const std::string& system = parts[0];
    for (size_t c = 1; c < parts.size(); ++c) {
        llm->reset();
        std::ostringstream sink;
        if (!joined && !system.empty()) llm->response(system, &sink, nullptr, 0);  // prefill only, like the app's prewarm
        std::string turn = joined ? system + parts[c] : parts[c];
        std::ostringstream os;
        auto t0 = Clock::now();
        auto ms = [&] { return std::chrono::duration<double, std::milli>(Clock::now() - t0).count(); };
        int64_t visionBefore = llm->getContext()->vision_us;
        llm->response(turn, &os, nullptr, 0);
        double prefillDone = ms(), tFirst = -1, tBox = -1;
        auto* ctx = llm->getContext();
        while (!llm->stoped() && ctx->gen_seq_len < maxTokens) {
            llm->generate(1);
            if (ctx->status == MNN::Transformer::LlmStatus::INTERNAL_ERROR) { fprintf(stderr, "internal error\n"); break; }
            std::string out = os.str();
            if (tFirst < 0 && out.find_first_not_of(" \n\t") != std::string::npos) tFirst = ms();
            if (tBox < 0 && boxLineEnd(out) >= 0) tBox = ms();
        }
        printf("{\"case\":%zu,\"out\":\"%s\",\"prefill_ms\":%.0f,\"first_ms\":%.0f,\"box_ms\":%.0f,\"total_ms\":%.0f,"
               "\"prompt_tok\":%d,\"gen_tok\":%d,\"vision_s\":%.2f,\"decode_s\":%.2f}\n",
               c, jsonEscape(os.str()).c_str(), prefillDone, tFirst, tBox, ms(), ctx->prompt_len, ctx->gen_seq_len,
               (ctx->vision_us - visionBefore) / 1e6, ctx->decode_us / 1e6);
    }
    Llm::destroy(llm.release());
    return 0;
}
