package org.lwjgl.opengl;

import android.opengl.GLES30;

/** Vertex-array subset for explicit GLES 3.0 contexts. */
public class GL30 {
    protected GL30() {}
    public static int glGenVertexArrays() { AndroidGlesGuard.current(); int[] value=new int[1]; GLES30.glGenVertexArrays(1,value,0); return value[0]; }
    public static void glBindVertexArray(int array) { AndroidGlesGuard.current(); GLES30.glBindVertexArray(array); }
    public static void glDeleteVertexArrays(int array) { AndroidGlesGuard.current(); GLES30.glDeleteVertexArrays(1,new int[]{array},0); }
}
