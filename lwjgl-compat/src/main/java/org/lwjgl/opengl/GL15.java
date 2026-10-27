package org.lwjgl.opengl;

import android.opengl.GLES30;
import java.nio.ByteBuffer;

/** Buffer subset with upstream LWJGL descriptors and Android-owned GLES storage. */
public class GL15 {
    protected GL15() {}
    public static final int GL_ARRAY_BUFFER = 0x8892, GL_STATIC_DRAW = 0x88E4;
    public static int glGenBuffers() { AndroidGlesGuard.current(); int[] value = new int[1]; GLES30.glGenBuffers(1,value,0); return value[0]; }
    public static void glBindBuffer(int target, int buffer) { AndroidGlesGuard.current(); GLES30.glBindBuffer(target,buffer); }
    public static void glDeleteBuffers(int buffer) { AndroidGlesGuard.current(); GLES30.glDeleteBuffers(1,new int[]{buffer},0); }
    public static void glBufferData(int target, ByteBuffer data, int usage) {
        if (data == null || !data.isDirect()) throw new IllegalArgumentException("Direct data buffer required");
        AndroidGlesGuard.current(); GLES30.glBufferData(target,data.remaining(),data,usage);
    }
    public static void glBufferData(int target, long size, int usage) {
        int length = AndroidGlesGuard.offset(size);
        AndroidGlesGuard.current(); GLES30.glBufferData(target,length,null,usage);
    }
}
