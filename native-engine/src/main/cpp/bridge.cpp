#include <jni.h>
#include <GLES3/gl3.h>
#include <string>
#include "probe_core.h"

namespace {
struct Probe {
    andes::InputQueue input;
    int width = 1;
    int height = 1;
    float x = 0.5f;
    float y = 0.5f;
    bool touched = false;
};
Probe* probe(jlong handle) { return reinterpret_cast<Probe*>(handle); }
std::string glString(GLenum name) {
    const auto* value = glGetString(name);
    return value ? reinterpret_cast<const char*>(value) : "unavailable";
}
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_engine_NativeProbe_create(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new Probe{});
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_engine_NativeProbe_destroy(JNIEnv*, jobject, jlong handle) {
    delete probe(handle);
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_andes_engine_NativeProbe_surfaceCreated(JNIEnv* env, jobject, jlong handle) {
    auto* state = probe(handle);
    if (!state) return env->NewStringUTF("Probe unavailable");
    state->input.resume();
    state->touched = false;
    const auto description = "C++ ARM64 platform probe\nGPU: " + glString(GL_RENDERER) +
        "\nVendor: " + glString(GL_VENDOR) + "\nAPI: " + glString(GL_VERSION) +
        "\nNo Minecraft bytecode, LWJGL bridge or desktop OpenGL translator is loaded.";
    return env->NewStringUTF(description.c_str());
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_engine_NativeProbe_resize(JNIEnv*, jobject, jlong handle, jint width, jint height) {
    if (auto* state = probe(handle)) {
        state->width = width > 0 ? width : 1;
        state->height = height > 0 ? height : 1;
    }
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_engine_NativeProbe_touch(JNIEnv*, jobject, jlong handle, jfloat x, jfloat y) {
    if (auto* state = probe(handle)) state->input.push(x, y);
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_engine_NativeProbe_pause(JNIEnv*, jobject, jlong handle) {
    if (auto* state = probe(handle)) { state->input.pause(); state->touched = false; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_engine_NativeProbe_draw(JNIEnv*, jobject, jlong handle) {
    auto* state = probe(handle);
    if (!state) return;
    andes::InputEvent event{};
    while (state->input.pop(event)) { state->x = event.x; state->y = event.y; state->touched = true; }
    glViewport(0, 0, state->width, state->height);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(0.035f, 0.065f, 0.10f, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    if (state->touched) {
        glEnable(GL_SCISSOR_TEST);
        glScissor(static_cast<int>(state->x * state->width) - 12,
                  static_cast<int>((1 - state->y) * state->height) - 12, 24, 24);
        glClearColor(0.36f, 0.88f, 0.64f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }
}
