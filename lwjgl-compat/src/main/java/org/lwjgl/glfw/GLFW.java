package org.lwjgl.glfw;

import java.nio.IntBuffer;
import dev.andes.lwjgl.NativeBridge;

/** Original, intentionally small LWJGL 3.3.2 API adapter. Not a full GLFW implementation. */
public class GLFW {
    protected GLFW() {}
    public static final int GLFW_TRUE = 1, GLFW_FALSE = 0;
    public static final int GLFW_VISIBLE = 0x00020004;
    public static final int GLFW_CLIENT_API = 0x00022001;
    public static final int GLFW_CONTEXT_VERSION_MAJOR = 0x00022002;
    public static final int GLFW_CONTEXT_VERSION_MINOR = 0x00022003;
    public static final int GLFW_OPENGL_PROFILE = 0x00022008;
    public static final int GLFW_CONTEXT_CREATION_API = 0x0002200B;
    public static final int GLFW_OPENGL_API = 0x00030001, GLFW_OPENGL_ES_API = 0x00030002;
    public static final int GLFW_EGL_CONTEXT_API = 0x00036002;
    private static final ThreadLocal<Hints> HINTS = ThreadLocal.withInitial(Hints::new);
    private static final ThreadLocal<String> ERROR = ThreadLocal.withInitial(() -> "");
    private static final class Hints {
        int api = GLFW_OPENGL_API, major = 1, minor = 0;
        boolean visible = true;
        String unsupported = "";
    }
    public static String androidLastError() { return ERROR.get(); }
    public static boolean glfwInit() {
        try { return NativeBridge.init(); }
        catch (RuntimeException failure) { ERROR.set(failure.getMessage()); return false; }
    }
    public static void glfwTerminate() { NativeBridge.terminate(); }
    public static void glfwDefaultWindowHints() { HINTS.set(new Hints()); ERROR.set(""); }
    public static void glfwWindowHint(int hint, int value) {
        Hints h = HINTS.get();
        switch (hint) {
            case GLFW_CLIENT_API: h.api = value; break;
            case GLFW_CONTEXT_VERSION_MAJOR: h.major = value; break;
            case GLFW_CONTEXT_VERSION_MINOR: h.minor = value; break;
            case GLFW_VISIBLE:
                if (value != GLFW_TRUE && value != GLFW_FALSE) h.unsupported = "Invalid visibility hint";
                else h.visible = value == GLFW_TRUE;
                break;
            case GLFW_CONTEXT_CREATION_API:
                if (value != GLFW_EGL_CONTEXT_API) h.unsupported = "Only EGL contexts are implemented";
                break;
            default: h.unsupported = "Unsupported GLFW window hint: " + hint;
        }
    }
    public static long glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        Hints h = HINTS.get();
        String reason = h.unsupported;
        if (h.api != GLFW_OPENGL_ES_API) reason = "Desktop OpenGL requires a translator; only explicit GLES probes are implemented";
        else if (h.major != 3 || h.minor != 0) reason = "Only an explicit GLES 3.0 request is implemented";
        else if (monitor != 0 || share != 0) reason = "Monitor selection and shared contexts are unimplemented";
        else if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || title == null) reason = "Invalid window dimensions/title";
        if (!reason.isEmpty()) { ERROR.set(reason); return 0; }
        try { return NativeBridge.createWindow(width, height, h.visible); }
        catch (RuntimeException failure) { ERROR.set(failure.getMessage()); return 0; }
    }
    public static void glfwDestroyWindow(long window) { NativeBridge.destroyWindow(window); }
    public static void glfwMakeContextCurrent(long window) { NativeBridge.makeCurrent(window); }
    public static long glfwGetCurrentContext() { return NativeBridge.currentContext(); }
    public static void glfwSwapBuffers(long window) { NativeBridge.swapBuffers(window); }
    public static void glfwSwapInterval(int interval) { NativeBridge.swapInterval(interval); }
    public static boolean glfwWindowShouldClose(long window) { return NativeBridge.shouldClose(window); }
    public static void glfwSetWindowShouldClose(long window, boolean close) { NativeBridge.setShouldClose(window, close); }
    public static void glfwGetFramebufferSize(long window, IntBuffer width, IntBuffer height) {
        if ((width != null && !width.hasRemaining()) || (height != null && !height.hasRemaining())) throw new IllegalArgumentException("Empty size buffer");
        int[] size = NativeBridge.framebufferSize(window);
        if (width != null) width.put(width.position(), size[0]);
        if (height != null) height.put(height.position(), size[1]);
    }
    public static double glfwGetTime() { return NativeBridge.time(); }
    public static void glfwPollEvents() { throw new UnsupportedOperationException("Android-to-GLFW event callbacks are not implemented"); }
}
