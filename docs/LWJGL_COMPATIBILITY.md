# LWJGL 3.3.2 / ARM64 execution boundary

`lwjgl-compat` supplies an original small GLFW/GL11/GL11C/GL15/GL20/GL30 API subset and native ARM64
EGL/GLES bridge. Upstream 3.3.2 jars are compile/test references only, not APK contents.
The original Java 17 fixture compiles against real LWJGL, becomes DEX and loads against
the Android subset. It creates a context, clears a surface, reads a pixel and checks RGB.

Implemented: explicit GLES 3.0 hints; one Android window or pbuffer; context binding/release;
window swapping/interval; framebuffer size; close flag; timer; clear/color/viewport/query;
bounds-checked direct RGBA8 readback; owner-thread/stale-handle checks and resource teardown.
Handles are validated IDs, not unchecked Java-supplied pointers.

Desktop OpenGL fails explicitly. Unknown hints, monitors/shared contexts and event pumping
are unsupported. Callbacks, most GLFW/LWJGL classes, native memory APIs, general shader/texture/buffer APIs, audio, Vulkan translation and AOT game compilation remain unimplemented. The new subset supports original GLSL ES 3.00 shader compilation/linking, VBO upload, VAO attributes and draw/readback; desktop GLSL is rejected. This
subset cannot launch Minecraft 1.20.4. ART/DEX is managed execution, not AOT compilation.

## Physical evidence

S25+ / SM-S936U / Android 15 API 35 / ARM64: both EGL instrumentation tests passed after
fixing offscreen behavior. Pbuffers have no display refresh/swap; interval control is
skipped and work is flushed instead. The initial failing test is recorded in validation.
The visible app surface also returned the expected result in its UI:

```
Pixel verified: 64,128,191
OpenGL ES 3.2 V@0800.17.11
Adreno (TM) 830
```

Five host tests compare selected method descriptors with actual upstream 3.3.2, reject
desktop/unsupported contexts and check Java-side readback bounds. These are subset results,
not general LWJGL/Minecraft compatibility evidence.

## Real-client audit and 0.3.0 extension

The entitled account now downloaded its original 1.20.4 client through Ember. The read-only
size/hash-verified audit found 292 direct LWJGL member references. After adding the explicit
GLES shader/VBO/VAO subset, 249 declarations remain absent. Inherited/dynamic calls and
method behavior are outside this static count; a declaration match never proves compatibility.
See evidence/client-1.20.4-lwjgl-linkage.json.

The third physical EGL test compiles an original fixture against upstream LWJGL 3.3.2,
converts it to DEX, and executes it against our adapter. It compiles/links ES vertex and
fragment shaders, uploads a triangle through a direct VBO, binds a VAO, draws and verifies
a red pixel. All three EGL tests passed on S25+. Sources are original test data, not game
shaders. Context ownership is required on every new graphics entry; raw host pointers,
non-direct buffer uploads and offsets exceeding Android int limits are rejected.

The real Main entry was separately observed under ART/DEX after six logging/parser libraries
were verified, then failed at an unresolved Guava Ticker. No graphics calls from the game
were validated. Fixing dependencies does not implement desktop GL, GLFW callbacks or audio.
See evidence/actual-client-initialization-s25.json and FEASIBILITY.md.

```
python tools/client_linkage_audit.py --client /owned/1.20.4.jar --version 1.20.4 --shim-classes lwjgl-compat/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes --output build/client-linkage.json
```

Only public metadata is fetched by that tool. It never executes or alters the client.
