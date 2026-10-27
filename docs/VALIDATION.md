# Validation evidence

Validated locally on Windows on 2026-10-24. This evidence applies to the research foundation,
not to Minecraft game compatibility.

| Check | Actual result |
|---|---|
| Android ARM64 C++ compile / APK assembly | Passed |
| Core JVM security/ownership tests | 20 passed, 0 failed in 0.3.0 |
| Python audit tests | 5 passed, 0 failed; Java 17 fixture and descriptor inventory |
| Authentication service-chain and consent-policy mocks | 11 passed, 0 failed on 2026-10-26; old Microsoft-only consent cannot authorize Xbox exchange |
| LWJGL ABI/policy host tests | 5 passed, 0 failed; compares against upstream 3.3.2 |
| C++ input queue host executable | Passed: bounded queue, invalid/finite coordinates, lifecycle reset, concurrent producer/consumer |
| Android lint | 0 errors, 7 warnings: six pinned dependency update notices and ARM64-only ChromeOS ABI warning |
| Physical Keystore test | 1 passed on S25+: encryption, tamper rejection and local removal |
| Original DEX/reflection/registered-JNI device tests | 2 passed on S25+ |
| LWJGL/EGL device tests | 3 passed on S25+ in 0.3.0; clear/readback, shader/VBO/VAO draw/readback and thread/handle checks |
| Visible Android EGL window | Passed: RGB 64,128,191; GLES 3.2; Adreno 830; own app UI observed |
| Foundation APK signature / alignment | APK Signature Scheme v2 verified; 16 KiB ZIP page alignment check passed |
| Gradle wrapper bootstrap | Download/checksum validation and Gradle 8.11.1 launch passed |
| Original Java fixture → DEX, min API 29 | Conversion and selected execution passed on S25+; desktop AWT remains absent |
| Official 1.20.4 public metadata | Downloaded JSON only, version metadata SHA-1 matched manifest |
| Physical device | S25+ / SM-S936U / Android 15 API 35 / ARM64 |
| Real Microsoft phone sign-in | Connected state observed on S25+ on 2026-10-26; refresh/logout/revocation not yet tested |
| Xbox/XSTS/Minecraft entitlement/profile flow | Passed connection on S25+ on 2026-10-26: Minecraft profile connected state observed after all five stages; ownership interpretation remains unverified |
| Real Minecraft client entry / gameplay | Actual Main initialized and entered in isolated ART/DEX probe; missing Guava Ticker stopped it. Main menu/world/save/multiplayer not reached |
| GitHub Actions | Passed on Ubuntu: [run 40156947017](https://github.com/lilblock1/ember-android/actions/runs/40156947017), commit d2caafd3e8b4eebbed131e56705121b79fbde472; APK/instrumentation assembly, host tests and lint; physical-device tests remain separate |

Evidence summaries live in `docs/evidence/`. Historical 0.2.x results above predate ownership. In 0.3.0, the original client and six initialization libraries were downloaded privately after signed purchased-license verification; the real entry was probed without credentials. No proprietary files are in source/APKs. Neither that entry probe nor synthetic graphics tests establish gameplay.
The C++ test was compiled and executed directly on the Windows host with Zig 0.14.1's
C++ toolchain. The portable CMake/CTest workflow passed in GitHub Actions; it is not claimed as locally run.

The initial remote workflow failed during SDK setup because the setup action requested
the retired `tools` package. Explicitly selecting `platform-tools` fixed setup, and the
subsequent run completed successfully. CI does not run Microsoft sign-in, ownership
verification or the real Minecraft client.

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

## 0.3.0 physical ownership and client evidence

On October 26 CDT / October 27 UTC, the production purchased-license verifier passed on
S25+: RS256 signature, fresh request nonce, signer/profile and expiry checks. Client size
24,445,539 and SHA-1 fd19469fed4a4b4c15b2d5133985f0e3e7816a8a matched Mojang metadata.
Six initialization dependencies also matched metadata size/hashes. Tests ran through
manual instrumentation after replacement installs; app data/session were preserved.
An earlier Gradle connected-test run uninstalled the app; it was restored and the owner
reauthenticated before these successful runs. Do not use that Gradle lifecycle for an
already signed-in test app.

The entry probe reports a blocker as a successful diagnostic collection, not a successful
game launch. Main initialized and its stack frame was observed; it then threw
NoClassDefFoundError for com/google/common/base/Ticker. No menu, world, save, performance,
audio or actual-client rendering/input result is claimed. Source reports retain no raw
service bodies, signed licenses, tokens, device serials or profile identity.
