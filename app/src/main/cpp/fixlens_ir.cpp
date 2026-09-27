// JNI bridge to the vendored IRext decoder (irext/, MIT): turns an AC remote's binary plus a wanted state into
// the IR timing list for that state. IRext keeps one open remote in global state, so every call comes from
// the single "fixlens-ir" thread (com.fixlens.ir.IrBlaster's dispatcher); nothing here is thread-safe.
#include <jni.h>
#include <cstdlib>
#include <cstring>

#include "ir_decode.h"

namespace {
// IRext reads the binary in place, so the copy lives until the next open or close.
UINT8* g_binary = nullptr;
bool g_open = false;
UINT16 g_out[USER_DATA_SIZE];

void closeCurrent() {
    if (g_open) ir_close();
    g_open = false;
    std::free(g_binary);
    g_binary = nullptr;
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fixlens_ir_IrextNative_open(JNIEnv* env, jobject, jbyteArray data) {
    closeCurrent();
    const jsize n = env->GetArrayLength(data);
    if (n <= 0 || n > 0xFFFF) return JNI_FALSE;
    g_binary = static_cast<UINT8*>(std::malloc(static_cast<size_t>(n)));
    if (g_binary == nullptr) return JNI_FALSE;
    env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(g_binary));
    if (ir_binary_open(REMOTE_CATEGORY_AC, 1, g_binary, static_cast<UINT16>(n)) != IR_DECODE_SUCCEEDED) {
        std::free(g_binary);
        g_binary = nullptr;
        return JNI_FALSE;
    }
    g_open = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fixlens_ir_IrextNative_close(JNIEnv*, jobject) {
    closeCurrent();
}

// [modeMask, then per mode 0..4 (cool, heat, auto, fan, dry): tempMin, tempMax, windMask, swingMask].
// Temperatures are IRext indexes (0 = 16 °C); -1 means the mode has no temperature setting.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_fixlens_ir_IrextNative_caps(JNIEnv* env, jobject) {
    jint out[1 + 5 * 4] = {0};
    if (g_open) {
        UINT8 modes = 0;
        get_supported_mode(&modes);
        out[0] = modes;
        for (int m = 0; m < 5; m++) {
            INT8 tmin = -1, tmax = -1;
            UINT8 wind = 0, swing = 0;
            get_temperature_range(static_cast<UINT8>(m), &tmin, &tmax);
            get_supported_wind_speed(static_cast<UINT8>(m), &wind);
            get_supported_swing(static_cast<UINT8>(m), &swing);
            out[1 + m * 4] = tmin;
            out[2 + m * 4] = tmax;
            out[3 + m * 4] = wind;
            out[4 + m * 4] = swing;
        }
    }
    jintArray result = env->NewIntArray(1 + 5 * 4);
    env->SetIntArrayRegion(result, 0, 1 + 5 * 4, out);
    return result;
}

// Timing list (µs, mark first) for the state, sent with [key] (IRext KEY_AC_*); empty when it can't be built.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_fixlens_ir_IrextNative_encode(JNIEnv* env, jobject, jint power, jint mode, jint temp, jint wind, jint swing,
                                        jint key) {
    UINT16 n = 0;
    if (g_open) {
        t_remote_ac_status s;
        std::memset(&s, 0, sizeof s);
        s.ac_power = static_cast<t_ac_power>(power);
        s.ac_mode = static_cast<t_ac_mode>(mode);
        s.ac_temp = static_cast<t_ac_temperature>(temp);
        s.ac_wind_speed = static_cast<t_ac_wind_speed>(wind);
        s.ac_wind_dir = static_cast<t_ac_swing>(swing);
        std::memset(g_out, 0, sizeof g_out);
        n = ir_decode(static_cast<UINT8>(key), g_out, &s);
        if (n > USER_DATA_SIZE) n = 0;
    }
    jintArray result = env->NewIntArray(n);
    if (n > 0) {
        jint* values = env->GetIntArrayElements(result, nullptr);
        for (UINT16 i = 0; i < n; i++) values[i] = g_out[i];
        env->ReleaseIntArrayElements(result, values, 0);
    }
    return result;
}
