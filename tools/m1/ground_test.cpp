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
    // A case may hold several steps separated by "\n-----\n": they run in order on the same KV cache (like a
    // chat turn followed by a side request), each reported on its own line with "step".
    std::vector<std::pair<size_t, std::string>> steps;  // (case, prompt)
    for (size_t c = 1; c < parts.size(); ++c) {
        std::string body = parts[c], ssep = "\n-----\n";
        size_t p0 = 0;
        while (true) {
            size_t n = body.find(ssep, p0);
            steps.emplace_back(c, body.substr(p0, n == std::string::npos ? std::string::npos : n - p0));
            if (n == std::string::npos) break;
            p0 = n + ssep.size();
        }
    }
    size_t lastCase = 0;
    int stepNo = 0;
    for (auto& [c, stepPrompt] : steps) {
        bool firstStep = c != lastCase;
        stepNo = firstStep ? 0 : stepNo + 1;
        lastCase = c;
        std::ostringstream sink;
        if (firstStep) {
            llm->reset();
            if (!joined && !system.empty()) llm->response(system, &sink, nullptr, 0);  // prefill only, like the app's prewarm
        }
        // Later steps close the previous answer first (generation stops on <|im_end|> without feeding it back).
        std::string turn = firstStep ? (joined ? system + stepPrompt : stepPrompt) : "<|im_end|>\n" + stepPrompt;
        std::ostringstream os;
        auto t0 = Clock::now();
        auto ms = [&] { return std::chrono::duration<double, std::milli>(Clock::now() - t0).count(); };
        int64_t visionBefore = llm->getContext()->vision_us;
        llm->response(turn, &os, nullptr, 0);
        double prefillDone = ms(), tFirst = -1, tBox = -1, tEl1 = -1;
        auto* ctx = llm->getContext();
        while (!llm->stoped() && ctx->gen_seq_len < maxTokens) {
            llm->generate(1);
            if (ctx->status == MNN::Transformer::LlmStatus::INTERNAL_ERROR) { fprintf(stderr, "internal error\n"); break; }
            std::string out = os.str();
            if (tFirst < 0 && out.find_first_not_of(" \n\t") != std::string::npos) tFirst = ms();
            if (tBox < 0 && boxLineEnd(out) >= 0) tBox = ms();
            if (tEl1 < 0 && out.find('}') != std::string::npos) tEl1 = ms();  // first list element complete
        }
        printf("{\"case\":%zu,\"step\":%d,\"out\":\"%s\",\"prefill_ms\":%.0f,\"first_ms\":%.0f,\"box_ms\":%.0f,\"el1_ms\":%.0f,"
               "\"total_ms\":%.0f,\"prompt_tok\":%d,\"gen_tok\":%d,\"vision_s\":%.2f,\"decode_s\":%.2f}\n",
               c, stepNo, jsonEscape(os.str()).c_str(), prefillDone, tFirst, tBox, tEl1, ms(), ctx->prompt_len, ctx->gen_seq_len,
               (ctx->vision_us - visionBefore) / 1e6, ctx->decode_us / 1e6);
    }
    Llm::destroy(llm.release());
    return 0;
}
