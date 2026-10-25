package dev.andes.lwjgl;

import android.view.Surface;
import java.nio.ByteBuffer;

public final class NativeBridge {
    private NativeBridge() {}
    static { System.loadLibrary("andes_glfw"); }
    public static native void attachSurface(Surface surface);
    public static native void releaseSurface();
    public static native boolean init();
    public static native void terminate();
    public static native long createWindow(int width, int height, boolean visible);
    public static native void destroyWindow(long window);
    public static native void makeCurrent(long window);
    public static native long currentContext();
    public static native void swapBuffers(long window);
    public static native void swapInterval(int interval);
    public static native int[] framebufferSize(long window);
    public static native boolean shouldClose(long window);
    public static native void setShouldClose(long window, boolean close);
    public static native double time();
    public static native void clearColor(float r, float g, float b, float a);
    public static native void clear(int mask);
    public static native void viewport(int x, int y, int width, int height);
    public static native String getString(int name);
    public static native int getInteger(int name);
    public static native int getError();
    public static native void readPixels(int x, int y, int width, int height, ByteBuffer buffer);
}
