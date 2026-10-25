package fixture;

import java.nio.ByteBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;

/** Compiled against REAL upstream LWJGL 3.3.2, executed against the original Android subset. */
public final class LwjglBoundaryFixture {
    public static String render(boolean visible) {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW/EGL initialization failed");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 0);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, visible ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            window = GLFW.glfwCreateWindow(64, 64, "Original LWJGL boundary fixture", 0, 0);
            if (window == 0) throw new IllegalStateException("EGL window creation failed");
            GLFW.glfwMakeContextCurrent(window);
            if (GLFW.glfwGetCurrentContext() != window) throw new AssertionError("Current context mismatch");
            GLFW.glfwSwapInterval(1);
            GL11C.glViewport(0, 0, 64, 64);
            GL11C.glClearColor(0.25f, 0.50f, 0.75f, 1f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
            ByteBuffer pixel = ByteBuffer.allocateDirect(4);
            GL11C.glReadPixels(0, 0, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
            int r = pixel.get(0) & 255, g = pixel.get(1) & 255, b = pixel.get(2) & 255;
            if (Math.abs(r - 64) > 5 || Math.abs(g - 128) > 5 || Math.abs(b - 191) > 5)
                throw new AssertionError("Unexpected pixel: " + r + "," + g + "," + b);
            if (GL11C.glGetError() != GL11C.GL_NO_ERROR) throw new AssertionError("GL error after readback");
            String result = "Pixel verified: " + r + "," + g + "," + b + "\n" +
                GL11C.glGetString(GL11C.GL_VERSION) + "\n" + GL11C.glGetString(GL11C.GL_RENDERER);
            GLFW.glfwSwapBuffers(window);
            return result;
        } finally {
            try { if (window != 0) GLFW.glfwDestroyWindow(window); }
            finally { GLFW.glfwTerminate(); }
        }
    }
}
