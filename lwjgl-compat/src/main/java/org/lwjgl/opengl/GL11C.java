package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import dev.andes.lwjgl.NativeBridge;

/** Only functions shared with GLES are mapped. No desktop GL capability is advertised. */
public class GL11C {
    protected GL11C() {}
    public static final int GL_COLOR_BUFFER_BIT = 0x4000, GL_DEPTH_BUFFER_BIT = 0x0100;
    public static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_VERSION = 0x1F02, GL_RENDERER = 0x1F01, GL_VENDOR = 0x1F00, GL_NO_ERROR = 0;
    public static void glClearColor(float r, float g, float b, float a) { NativeBridge.clearColor(r, g, b, a); }
    public static void glClear(int mask) { NativeBridge.clear(mask); }
    public static void glViewport(int x, int y, int width, int height) { NativeBridge.viewport(x, y, width, height); }
    public static String glGetString(int name) { return NativeBridge.getString(name); }
    public static int glGetInteger(int name) { return NativeBridge.getInteger(name); }
    public static int glGetError() { return NativeBridge.getError(); }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        if (pixels == null || !pixels.isDirect() || pixels.isReadOnly()) throw new IllegalArgumentException("Writable direct pixel buffer required");
        if (format != GL_RGBA || type != GL_UNSIGNED_BYTE) throw new UnsupportedOperationException("Only RGBA/UNSIGNED_BYTE readback is implemented");
        long bytes = (long)width * height * 4;
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || bytes > pixels.remaining()) throw new IllegalArgumentException("Invalid readback extent/buffer");
        NativeBridge.readPixels(x, y, width, height, pixels.slice());
    }
}
