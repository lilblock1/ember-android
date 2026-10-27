package org.lwjgl.opengl;

import android.opengl.GLES30;

/** Explicit GLES shader subset. Desktop GLSL is rejected, never silently rewritten. */
public class GL20 {
    protected GL20() {}
    public static final int GL_VERTEX_SHADER=0x8B31, GL_FRAGMENT_SHADER=0x8B30;
    public static final int GL_COMPILE_STATUS=0x8B81, GL_LINK_STATUS=0x8B82;
    public static int glCreateShader(int type) { AndroidGlesGuard.current(); return GLES30.glCreateShader(type); }
    public static void glShaderSource(int shader, CharSequence source) {
        if (source == null || !source.toString().trim().startsWith("#version 300 es\n"))
            throw new UnsupportedOperationException("Only explicit GLSL ES 3.00 is supported; desktop shader translation is missing");
        AndroidGlesGuard.current(); GLES30.glShaderSource(shader,source.toString());
    }
    public static void glCompileShader(int shader) { AndroidGlesGuard.current(); GLES30.glCompileShader(shader); }
    public static int glGetShaderi(int shader, int name) { AndroidGlesGuard.current(); int[] value=new int[1]; GLES30.glGetShaderiv(shader,name,value,0); return value[0]; }
    public static String glGetShaderInfoLog(int shader, int maxLength) {
        if (maxLength < 0) throw new IllegalArgumentException("Negative log length");
        AndroidGlesGuard.current(); String value=GLES30.glGetShaderInfoLog(shader); return value.substring(0,Math.min(value.length(),maxLength));
    }
    public static int glCreateProgram() { AndroidGlesGuard.current(); return GLES30.glCreateProgram(); }
    public static void glAttachShader(int program, int shader) { AndroidGlesGuard.current(); GLES30.glAttachShader(program,shader); }
    public static void glLinkProgram(int program) { AndroidGlesGuard.current(); GLES30.glLinkProgram(program); }
    public static int glGetProgrami(int program, int name) { AndroidGlesGuard.current(); int[] value=new int[1]; GLES30.glGetProgramiv(program,name,value,0); return value[0]; }
    public static String glGetProgramInfoLog(int program, int maxLength) {
        if (maxLength < 0) throw new IllegalArgumentException("Negative log length");
        AndroidGlesGuard.current(); String value=GLES30.glGetProgramInfoLog(program); return value.substring(0,Math.min(value.length(),maxLength));
    }
    public static void glUseProgram(int program) { AndroidGlesGuard.current(); GLES30.glUseProgram(program); }
    public static void glDeleteProgram(int program) { AndroidGlesGuard.current(); GLES30.glDeleteProgram(program); }
    public static void glDeleteShader(int shader) { AndroidGlesGuard.current(); GLES30.glDeleteShader(shader); }
    public static void glEnableVertexAttribArray(int index) { AndroidGlesGuard.current(); GLES30.glEnableVertexAttribArray(index); }
    public static void glDisableVertexAttribArray(int index) { AndroidGlesGuard.current(); GLES30.glDisableVertexAttribArray(index); }
    public static void glVertexAttribPointer(int index,int size,int type,boolean normalized,int stride,long pointer) {
        int offset=AndroidGlesGuard.offset(pointer); AndroidGlesGuard.current();
        int[] binding=new int[1]; GLES30.glGetIntegerv(0x8894,binding,0);
        if (binding[0]==0) throw new UnsupportedOperationException("Raw host pointers are unsupported; bind a vertex buffer");
        GLES30.glVertexAttribPointer(index,size,type,normalized,stride,offset);
    }
}
