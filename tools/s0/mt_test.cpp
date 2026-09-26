// S0 spike: multi-turn KV append with Qwen3-VL on MNN (reuse_kv, raw chat template, eraseHistory rollback).
// Build + run: tools/s0/run_mt_test.sh
#include <llm/llm.hpp>
#include <chrono>
#include <cstdio>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>

using MNN::Transformer::Llm;

static std::unique_ptr<Llm> llm;

static std::string turn(const std::string& prompt, int maxTokens = 100) {
    std::ostringstream os;
    auto t0 = std::chrono::steady_clock::now();
    size_t before = llm->getCurrentHistory();
    llm->response(prompt, &os, nullptr, 0);
    auto* ctx = llm->getContext();
    while (!llm->stoped() && ctx->gen_seq_len < maxTokens) llm->generate(1);
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    printf("  [kv %zu -> %zu | prompt=%d tok, gen=%d, vision=%.2fs prefill=%.2fs decode=%.2fs total=%.0fms]\n",
           before, llm->getCurrentHistory(), ctx->prompt_len, ctx->gen_seq_len, ctx->vision_us / 1e6,
           ctx->prefill_us / 1e6, ctx->decode_us / 1e6, ms);
    return os.str();
}

static std::string user(const std::string& content, bool first = false) {
    return std::string(first ? "" : "<|im_end|>\n") + "<|im_start|>user\n" + content +
           "<|im_end|>\n<|im_start|>assistant\n";
}

int main(int argc, char** argv) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    std::string cfg = argv[1], img = argv[2];
    llm.reset(Llm::createLLM(cfg));
    llm->set_config(R"({"backend_type":"cpu","thread_num":4,"precision":"low","sampler_type":"greedy",
        "reuse_kv":true,"use_template":false,"max_all_tokens":4096,"tmp_path":"/data/local/tmp/fixlens-m0"})");
    llm->load();
    std::string sys = "<|im_start|>system\nYou are Fixy, a friendly repair helper. Max 2 short sentences. "
                      "Earlier pictures may show a different view; answer about the latest picture unless asked.<|im_end|>\n";

    printf("T1 (image washer): What appliance is this?\n");
    printf("  -> %s\n", turn(sys + user("<img>" + img + "/washer_ariston_640.jpg</img>What appliance is this?", true)).c_str());

    printf("T2 (text): What did I ask you before?\n");
    printf("  -> %s\n", turn(user("What exactly did I ask you in my previous question?")).c_str());

    printf("T3 (image car): new picture\n");
    printf("  -> %s\n", turn(user("<img>" + img + "/car_volt_640.jpg</img>What is in this new picture? Is it the same thing as the first picture?")).c_str());

    printf("T4 (text, recall across images): what was in the first picture?\n");
    printf("  -> %s\n", turn(user("What was in the very first picture I showed you?")).c_str());

    size_t mark = llm->getCurrentHistory();
    printf("T5 (to be erased): My name is Ravi. mark=%zu\n", mark);
    printf("  -> %s\n", turn(user("Remember this: my name is Ravi and my car is red.")).c_str());
    llm->eraseHistory(mark, 0);
    printf("  erased back to %zu (all_seq_len now %d)\n", mark, llm->getContext()->all_seq_len);

    printf("T6 (after rollback): What is my name?\n");
    printf("  -> %s\n", turn(user("What is my name? If I never told you, answer exactly: I don't know.")).c_str());

    printf("T7 (grounding after history): washer again\n");
    printf("  -> %s\n", turn(user("<img>" + img + "/syn_sq.jpg</img>Describe the shapes and their colors.")).c_str());
    Llm::destroy(llm.release());
    return 0;
}
