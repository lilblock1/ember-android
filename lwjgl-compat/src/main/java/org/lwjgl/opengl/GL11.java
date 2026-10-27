package org.lwjgl.opengl;

import java.nio.ByteBuffer;

/** Original subset adapter; LWJGL's GL11 and GL11C are distinct classes. */
public class GL11 {
    protected GL11() {}
    public static final int GL_COLOR_BUFFER_BIT = GL11C.GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT = GL11C.GL_DEPTH_BUFFER_BIT;
    public static final int GL_RGBA = GL11C.GL_RGBA, GL_UNSIGNED_BYTE = GL11C.GL_UNSIGNED_BYTE;
    public static final int GL_VERSION = GL11C.GL_VERSION, GL_RENDERER = GL11C.GL_RENDERER, GL_VENDOR = GL11C.GL_VENDOR, GL_NO_ERROR = 0;
    public static final int GL_TRIANGLES = 4, GL_FLOAT = 0x1406;
    public static void glDrawArrays(int mode, int first, int count) {
        if (first < 0 || count < 0) throw new IllegalArgumentException("Invalid draw range");
        AndroidGlesGuard.current(); android.opengl.GLES30.glDrawArrays(mode, first, count);
    }
    public static void glClearColor(float r, float g, float b, float a) { GL11C.glClearColor(r,g,b,a); }
    public static void glClear(int mask) { GL11C.glClear(mask); }
    public static void glViewport(int x, int y, int w, int h) { GL11C.glViewport(x,y,w,h); }
    public static String glGetString(int name) { return GL11C.glGetString(name); }
    public static int glGetInteger(int name) { return GL11C.glGetInteger(name); }
    public static int glGetError() { return GL11C.glGetError(); }
    public static void glReadPixels(int x, int y, int w, int h, int format, int type, ByteBuffer pixels) { GL11C.glReadPixels(x,y,w,h,format,type,pixels); }
}
