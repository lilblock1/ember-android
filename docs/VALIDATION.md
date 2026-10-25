# Validation evidence

Validated locally on Windows on 2026-10-24. This evidence applies to the research foundation,
not to Minecraft game compatibility.

| Check | Actual result |
|---|---|
| Android ARM64 C++ compile / APK assembly | Passed |
| Core JVM security tests | 8 passed, 0 failed |
| Python audit tests | 5 passed, 0 failed; Java 17 fixture and descriptor inventory |
| Authentication service-chain mocks | 9 passed, 0 failed; no live API requests |
| LWJGL ABI/policy host tests | 4 passed, 0 failed; compares against upstream 3.3.2 |
| C++ input queue host executable | Passed: bounded queue, invalid/finite coordinates, lifecycle reset, concurrent producer/consumer |
| Android lint | 0 errors, 7 warnings: six pinned dependency update notices and ARM64-only ChromeOS ABI warning |
| Physical Keystore test | 1 passed on S25+: encryption, tamper rejection and local removal |
| Original DEX/reflection/registered-JNI device tests | 2 passed on S25+ |
| LWJGL/EGL device tests | 2 passed on S25+ after pbuffer fix; pixel readback/thread/handle checks |
| Visible Android EGL window | Passed: RGB 64,128,191; GLES 3.2; Adreno 830; own app UI observed |
| Foundation APK signature / alignment | APK Signature Scheme v2 verified; 16 KiB ZIP page alignment check passed |
| Gradle wrapper bootstrap | Download/checksum validation and Gradle 8.11.1 launch passed |
| Original Java fixture → DEX, min API 29 | Conversion and selected execution passed on S25+; desktop AWT remains absent |
| Official 1.20.4 public metadata | Downloaded JSON only, version metadata SHA-1 matched manifest |
| Physical device | S25+ / SM-S936U / Android 15 API 35 / ARM64 |
| Real Microsoft browser login / refresh/logout | Not run: own application registration absent |
| Xbox/XSTS/Minecraft entitlement/profile flow | Staged implementation and mock tests pass; live approval/contract unverified |
| Real Minecraft client launch / gameplay / save / multiplayer | Not run; no execution engine exists |
| GitHub Actions | Workflow supplied; not run remotely, no repository selected |

Evidence summaries live in `docs/evidence/`. No proprietary game files were fetched or
executed. The public metadata and original synthetic fixture are not gameplay evidence.
The C++ test was compiled and executed directly on the Windows host with Zig 0.14.1's
C++ toolchain. The portable CMake/CTest workflow is supplied for CI, not claimed as locally run.

## Build environment findings

Toolchains were installed under this task's `work/toolchain`: Temurin JDK 17.0.20.1,
Gradle 8.11.1, SDK 35/build tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1. Downloaded
JDK/Gradle/Zig distributions were verified against publisher checksums. SDK Manager handled
Android package installation.

This host's Windows AF_UNIX connection failed inside Java's selector/pipe implementation.
For local build processes only, an OpenJDK PipeImpl patch routed internal selector wake-up
pipes through TCP loopback. It lives in `work/`, is not in the project, APK or source archive,
and is not needed by the standard Linux CI configuration. Java game runtime code was not
created by this workaround. Ordinary JDK installations should use the normal build instructions.

A temporary R: drive mapping shortened task paths for NDK/Ninja's Windows 260-character
limit. `-PnativeBuildDir` permits a fresh, short CMake staging directory. C++ linkage explicitly
retains clang's g++ driver mode so Windows short filename aliases do not drop libc++ linkage.
An automatic safety review rejected a generated-folder cleanup command; the build instead
used fresh staging and retained the earlier generated folders.

No benchmark or UI/rendering validation on Android is implied by a successful build.
