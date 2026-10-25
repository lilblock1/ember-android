#include <jni.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

namespace {
std::mutex mutex;
struct State {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    ANativeWindow* androidWindow = nullptr;
    std::thread::id owner;
    std::chrono::steady_clock::time_point started;
    jlong handle = 0;
    jlong nextHandle = 1;
    bool current = false;
    bool close = false;
    bool windowSurface = false;
} state;

void error(JNIEnv* env, const std::string& message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}
void eglError(JNIEnv* env, const char* operation) {
    error(env, std::string(operation) + " failed; EGL error=" + std::to_string(eglGetError()));
}
bool owner(JNIEnv* env) {
    if (state.display == EGL_NO_DISPLAY || state.owner != std::this_thread::get_id()) {
        error(env, "GLFW/EGL operation must run on its initialized owner thread");
        return false;
    }
    return true;
}
bool window(JNIEnv* env, jlong handle) {
    if (!owner(env)) return false;
    if (!handle || state.handle != handle) { error(env, "Unknown or stale GLFW window handle"); return false; }
    return true;
}
bool current(JNIEnv* env) {
    if (!owner(env)) return false;
    if (!state.current || !state.handle) { error(env, "No current EGL context"); return false; }
    return true;
}
void destroyWindow() {
    eglMakeCurrent(state.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (state.context != EGL_NO_CONTEXT) eglDestroyContext(state.display, state.context);
    if (state.surface != EGL_NO_SURFACE) eglDestroySurface(state.display, state.surface);
    state.context = EGL_NO_CONTEXT;
    state.surface = EGL_NO_SURFACE;
    state.handle = 0;
    state.current = false;
    state.close = false;
    state.windowSurface = false;
}
void releaseSurface() {
    if (state.androidWindow) ANativeWindow_release(state.androidWindow);
    state.androidWindow = nullptr;
}
}

extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_attachSurface(JNIEnv* env, jclass, jobject surface) {
    std::lock_guard lock(mutex);
    if (state.handle || state.display != EGL_NO_DISPLAY) { error(env, "Attach Surface before initializing the bridge"); return; }
    if (!surface) { error(env, "A valid Android Surface is required"); return; }
    auto* acquired = ANativeWindow_fromSurface(env, surface);
    if (!acquired) { error(env, "Cannot acquire Android Surface"); return; }
    releaseSurface();
    state.androidWindow = acquired;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_releaseSurface(JNIEnv* env, jclass) {
    std::lock_guard lock(mutex);
    if (state.display != EGL_NO_DISPLAY) { error(env, "Terminate EGL before releasing Surface"); return; }
    releaseSurface();
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_andes_lwjgl_NativeBridge_init(JNIEnv* env, jclass) {
    std::lock_guard lock(mutex);
    if (state.display != EGL_NO_DISPLAY) return owner(env);
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) { eglError(env, "eglInitialize"); return false; }
    if (!eglBindAPI(EGL_OPENGL_ES_API)) { eglTerminate(display); eglError(env, "eglBindAPI"); return false; }
    state.display = display;
    state.owner = std::this_thread::get_id();
    state.started = std::chrono::steady_clock::now();
    return true;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_terminate(JNIEnv* env, jclass) {
    std::lock_guard lock(mutex);
    if (state.display != EGL_NO_DISPLAY) {
        if (!owner(env)) return;
        destroyWindow();
        eglTerminate(state.display);
        state.display = EGL_NO_DISPLAY;
        state.owner = {};
    }
    releaseSurface();
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeBridge_createWindow(JNIEnv* env, jclass, jint width, jint height, jboolean visible) {
    std::lock_guard lock(mutex);
    if (!owner(env)) return 0;
    if (state.handle) { error(env, "Only one Android GLFW window is implemented"); return 0; }
    if (width <= 0 || height <= 0 || width > 4096 || height > 4096) { error(env, "Invalid surface extent"); return 0; }
    if (visible && !state.androidWindow) { error(env, "Visible window requires an attached Android Surface"); return 0; }
    const EGLint attributes[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, visible ? EGL_WINDOW_BIT : EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_NONE};
    EGLConfig config{};
    EGLint count = 0;
    if (!eglChooseConfig(state.display, attributes, &config, 1, &count) || count == 0) { eglError(env, "eglChooseConfig"); return 0; }
    EGLSurface surface;
    if (visible) {
        EGLint format = 0;
        eglGetConfigAttrib(state.display, config, EGL_NATIVE_VISUAL_ID, &format);
        if (ANativeWindow_setBuffersGeometry(state.androidWindow, 0, 0, format) != 0) { error(env, "Cannot configure Android window format"); return 0; }
        surface = eglCreateWindowSurface(state.display, config, state.androidWindow, nullptr);
    } else {
        const EGLint dimensions[] = {EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE};
        surface = eglCreatePbufferSurface(state.display, config, dimensions);
    }
    if (surface == EGL_NO_SURFACE) { eglError(env, "eglCreateSurface"); return 0; }
    const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(state.display, config, EGL_NO_CONTEXT, contextAttributes);
    if (context == EGL_NO_CONTEXT) {
        eglError(env, "eglCreateContext");
        eglDestroySurface(state.display, surface);
        return 0;
    }
    state.surface = surface;
    state.context = context;
    state.handle = state.nextHandle++;
    state.windowSurface = visible;
    return state.handle;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_destroyWindow(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard lock(mutex);
    if (window(env, handle)) destroyWindow();
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_makeCurrent(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard lock(mutex);
    if (!owner(env)) return;
    if (handle == 0) {
        if (!eglMakeCurrent(state.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)) { eglError(env, "eglMakeCurrent(release)"); return; }
        state.current = false;
        return;
    }
    if (!window(env, handle)) return;
    if (!eglMakeCurrent(state.display, state.surface, state.surface, state.context)) { eglError(env, "eglMakeCurrent"); return; }
    state.current = true;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeBridge_currentContext(JNIEnv*, jclass) {
    std::lock_guard lock(mutex);
    return state.current && state.owner == std::this_thread::get_id() ? state.handle : 0;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_swapBuffers(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard lock(mutex);
    if (!window(env, handle) || !current(env)) return;
    // Pbuffers have no display buffer swap; flush work without pretending to present.
    if (!state.windowSurface) { glFlush(); return; }
    if (!eglSwapBuffers(state.display, state.surface)) eglError(env, "eglSwapBuffers");
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_swapInterval(JNIEnv* env, jclass, jint interval) {
    std::lock_guard lock(mutex);
    if (!current(env)) return;
    if (interval < 0 || interval > 1) { error(env, "Only swap intervals 0 and 1 are supported"); return; }
    // A pbuffer has no display refresh interval. Some Android drivers reject this call.
    if (!state.windowSurface) return;
    if (!eglSwapInterval(state.display, interval)) eglError(env, "eglSwapInterval");
}
extern "C" JNIEXPORT jintArray JNICALL Java_dev_andes_lwjgl_NativeBridge_framebufferSize(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard lock(mutex);
    if (!window(env, handle)) return nullptr;
    EGLint width = 0, height = 0;
    if (!eglQuerySurface(state.display, state.surface, EGL_WIDTH, &width) || !eglQuerySurface(state.display, state.surface, EGL_HEIGHT, &height)) { eglError(env, "eglQuerySurface"); return nullptr; }
    jint values[] = {width, height};
    jintArray result = env->NewIntArray(2);
    if (result) env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_andes_lwjgl_NativeBridge_shouldClose(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard lock(mutex);
    return window(env, handle) ? state.close : true;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_setShouldClose(JNIEnv* env, jclass, jlong handle, jboolean close) {
    std::lock_guard lock(mutex);
    if (window(env, handle)) state.close = close;
}
extern "C" JNIEXPORT jdouble JNICALL Java_dev_andes_lwjgl_NativeBridge_time(JNIEnv* env, jclass) {
    std::lock_guard lock(mutex);
    if (!owner(env)) return 0;
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - state.started).count();
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_clearColor(JNIEnv* env, jclass, jfloat r, jfloat g, jfloat b, jfloat a) {
    std::lock_guard lock(mutex);
    if (current(env)) glClearColor(r,g,b,a);
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_clear(JNIEnv* env, jclass, jint mask) {
    std::lock_guard lock(mutex);
    if (current(env)) glClear(mask);
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_viewport(JNIEnv* env, jclass, jint x, jint y, jint width, jint height) {
    std::lock_guard lock(mutex);
    if (current(env)) glViewport(x,y,width,height);
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_andes_lwjgl_NativeBridge_getString(JNIEnv* env, jclass, jint name) {
    std::lock_guard lock(mutex);
    if (!current(env)) return nullptr;
    const auto* value = glGetString(name);
    return value ? env->NewStringUTF(reinterpret_cast<const char*>(value)) : nullptr;
}
extern "C" JNIEXPORT jint JNICALL Java_dev_andes_lwjgl_NativeBridge_getInteger(JNIEnv* env, jclass, jint name) {
    std::lock_guard lock(mutex);
    if (!current(env)) return 0;
    GLint value = 0;
    glGetIntegerv(name, &value);
    return value;
}
extern "C" JNIEXPORT jint JNICALL Java_dev_andes_lwjgl_NativeBridge_getError(JNIEnv* env, jclass) {
    std::lock_guard lock(mutex);
    return current(env) ? glGetError() : GL_INVALID_OPERATION;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeBridge_readPixels(JNIEnv* env, jclass, jint x, jint y, jint width, jint height, jobject buffer) {
    std::lock_guard lock(mutex);
    if (!current(env)) return;
    void* data = buffer ? env->GetDirectBufferAddress(buffer) : nullptr;
    jlong capacity = buffer ? env->GetDirectBufferCapacity(buffer) : 0;
    const std::int64_t required = static_cast<std::int64_t>(width) * height * 4;
    if (!data || width <= 0 || height <= 0 || width > 4096 || height > 4096 || capacity < required) { error(env, "Invalid direct pixel buffer/extent"); return; }
    glReadPixels(x,y,width,height,GL_RGBA,GL_UNSIGNED_BYTE,data);
}
