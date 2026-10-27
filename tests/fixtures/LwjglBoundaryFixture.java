package fixture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
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
    /** Original ES shaders and vertices, never Minecraft graphics or a compatibility claim. */
    public static String renderShader() {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        long window=0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
            window=GLFW.glfwCreateWindow(32,32,"Original shader fixture",0,0);
            if (window==0) throw new AssertionError("Window failed");
            GLFW.glfwMakeContextCurrent(window);
            int vertex=GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            int fragment=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            int program=0,buffer=0,array=0;
            try {
                GL20.glShaderSource(vertex,"#version 300 es\nlayout(location=0) in vec2 position; void main(){gl_Position=vec4(position,0,1);}");
                GL20.glShaderSource(fragment,"#version 300 es\nprecision mediump float; out vec4 color; void main(){color=vec4(1,0,0,1);}");
                GL20.glCompileShader(vertex); GL20.glCompileShader(fragment);
                if (GL20.glGetShaderi(vertex,GL20.GL_COMPILE_STATUS)!=1 || GL20.glGetShaderi(fragment,GL20.GL_COMPILE_STATUS)!=1)
                    throw new AssertionError("Original ES shader compile failed");
                program=GL20.glCreateProgram(); GL20.glAttachShader(program,vertex); GL20.glAttachShader(program,fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=1) throw new AssertionError("Shader link failed");
                GL20.glUseProgram(program);
                array=GL30.glGenVertexArrays(); GL30.glBindVertexArray(array);
                buffer=GL15.glGenBuffers(); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
                ByteBuffer vertices=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder());
                vertices.asFloatBuffer().put(new float[]{-1,-1,3,-1,-1,3});
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
                GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,8,0L);
                GL11.glViewport(0,0,32,32); GL11.glClearColor(0,0,0,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                ByteBuffer pixel=ByteBuffer.allocateDirect(4);
                GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                if ((pixel.get(0)&255)<250 || (pixel.get(1)&255)>5 || (pixel.get(2)&255)>5) throw new AssertionError("Shader/VBO pixel mismatch");
                if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("GL error after draw");
                return "Shader/VBO/VAO pixel verified: 255,0,0";
            } finally {
                GL20.glUseProgram(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0); GL30.glBindVertexArray(0);
                if (buffer!=0) GL15.glDeleteBuffers(buffer); if (array!=0) GL30.glDeleteVertexArrays(array);
                if (program!=0) GL20.glDeleteProgram(program); GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
            }
        } finally { try { if (window!=0) GLFW.glfwDestroyWindow(window); } finally { GLFW.glfwTerminate(); } }
    }
}
