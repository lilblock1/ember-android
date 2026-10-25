#include <jni.h>

namespace {
jlong marker(JNIEnv*, jclass) { return 0x4a415641; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_andes_compatibility_NativeFixtureBridge_attach(JNIEnv* env, jobject, jclass fixture) {
    // Original synthetic fixture only. No Minecraft symbols or binary patching.
    JNINativeMethod methods[] = {{const_cast<char*>("desktopNativeHandle"),
        const_cast<char*>("()J"), reinterpret_cast<void*>(marker)}};
    return env->RegisterNatives(fixture, methods, 1) == JNI_OK;
}
