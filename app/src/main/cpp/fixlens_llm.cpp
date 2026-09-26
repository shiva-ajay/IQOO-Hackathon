// Minimal JNI bridge to MNN's LLM engine for FixLens: load a (VL) model, run prompts, stream text back.
// Multi-turn: the config sets reuse_kv=true and use_template=false, so each prompt (already chat-templated by
// Kotlin) is appended to the KV cache. A turn can be rolled back to a mark with eraseHistory.
#include <jni.h>
#include <android/log.h>
#include <llm/llm.hpp>
#include <atomic>
#include <memory>
#include <ostream>
#include <streambuf>
#include <string>

#define TAG "FixLens"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

using MNN::Transformer::Llm;
using MNN::Transformer::LlmStatus;

namespace {
// One engine per app; a new question may cancel the running one between tokens.
struct Session {
    std::unique_ptr<Llm> llm;
    std::atomic<bool> cancel{false};
    // Tokens in the KV cache as we know it. MNN applies reset/erase lazily on the next response(), so
    // getCurrentHistory() is stale right after them; this value is not.
    size_t kvLen = 0;
};
}  // namespace

namespace {

// Forwards generated text to a Kotlin callback, only ever cutting on complete UTF-8 sequences.
class CallbackBuf : public std::streambuf {
public:
    CallbackBuf(JNIEnv* env, jobject cb) : env_(env), cb_(cb) {
        jclass cls = env->GetObjectClass(cb);
        onText_ = env->GetMethodID(cls, "onText", "(Ljava/lang/String;)V");
    }
    ~CallbackBuf() override { flushAll(); }

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        pending_.append(s, n);
        emitComplete();
        return n;
    }
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) { pending_.push_back(static_cast<char>(ch)); emitComplete(); }
        return ch;
    }

private:
    void emitComplete() {
        size_t cut = pending_.size();
        // Step back over an unfinished multi-byte sequence at the end.
        size_t i = pending_.size();
        int back = 0;
        while (i > 0 && back < 4) {
            unsigned char c = pending_[i - 1];
            if ((c & 0xC0) == 0x80) { --i; ++back; continue; }
            int need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : 4;
            if (need > back + 1) cut = i - 1;
            break;
        }
        if (cut == 0) return;
        send(pending_.substr(0, cut));
        pending_.erase(0, cut);
    }
    void flushAll() { if (!pending_.empty()) { send(pending_); pending_.clear(); } }
    void send(const std::string& text) {
        jstring js = env_->NewStringUTF(text.c_str());
        env_->CallVoidMethod(cb_, onText_, js);
        env_->DeleteLocalRef(js);
    }

    JNIEnv* env_;
    jobject cb_;
    jmethodID onText_;
    std::string pending_;
};

std::string toStd(JNIEnv* env, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_fixlens_vision_VlmEngine_nativeLoad(JNIEnv* env, jobject, jstring configPath, jstring extraConfig) {
    auto session = std::make_unique<Session>();
    session->llm.reset(Llm::createLLM(toStd(env, configPath)));
    if (!session->llm) return 0;
    session->llm->set_config(toStd(env, extraConfig));
    if (!session->llm->load()) return 0;
    return reinterpret_cast<jlong>(session.release());
}

// keep: 0 = never keep the turn in the KV (side request), 1 = keep unless cancelled, 2 = always keep.
// resetFirst clears the whole KV before prefilling (a rebuild).
// Returns "<cancelled 0|1> <kv tokens after> <stats...>".
extern "C" JNIEXPORT jstring JNICALL
Java_com_fixlens_vision_VlmEngine_nativeGenerate(JNIEnv* env, jobject, jlong handle, jstring prompt,
                                                 jint maxTokens, jint keep, jboolean resetFirst, jobject callback) {
    auto* session = reinterpret_cast<Session*>(handle);
    auto* llm = session->llm.get();
    session->cancel = false;
    if (resetFirst) { llm->reset(); session->kvLen = 0; }
    size_t mark = session->kvLen;
    bool cancelled = false;
    int64_t visionBefore = llm->getContext()->vision_us;
    {
        CallbackBuf buf(env, callback);
        std::ostream os(&buf);
        // Prefill only (max_new_tokens = 0), then decode token by token so a newer question can cancel us.
        llm->response(toStd(env, prompt), &os, nullptr, 0);
        auto* ctx = llm->getContext();
        while (!llm->stoped() && ctx->gen_seq_len < maxTokens) {
            if (session->cancel) { cancelled = true; break; }
            llm->generate(1);
            if (ctx->status == LlmStatus::INTERNAL_ERROR) break;
        }
        os.flush();
    }
    auto* ctx = llm->getContext();
    size_t after = llm->getCurrentHistory();
    if (keep == 0 || (keep == 1 && cancelled)) {
        if (after > mark) llm->eraseHistory(mark, 0);
        after = mark;
    }
    session->kvLen = after;
    char stats[320];
    snprintf(stats, sizeof(stats), "%d %zu prompt=%d tok, gen=%d tok, kv=%zu, vision=%.2fs, prefill=%.2fs, decode=%.2fs",
             cancelled ? 1 : 0, after, ctx->prompt_len, ctx->gen_seq_len, after,
             (ctx->vision_us - visionBefore) / 1e6, ctx->prefill_us / 1e6, ctx->decode_us / 1e6);
    LOGI("VLM %s", stats);
    return env->NewStringUTF(stats);
}

extern "C" JNIEXPORT void JNICALL
Java_com_fixlens_vision_VlmEngine_nativeReset(JNIEnv*, jobject, jlong handle) {
    auto* session = reinterpret_cast<Session*>(handle);
    session->llm->reset();
    session->kvLen = 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fixlens_vision_VlmEngine_nativeCancel(JNIEnv*, jobject, jlong handle) {
    reinterpret_cast<Session*>(handle)->cancel = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fixlens_vision_VlmEngine_nativeRelease(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Session*>(handle);
}
