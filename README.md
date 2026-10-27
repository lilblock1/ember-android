# Ember — Android Java Edition compatibility research

Independent, unofficial project. Not approved by or associated with Mojang or Microsoft.

**Research build, not a playable Minecraft port.** Microsoft/Minecraft account connection,
signed purchased Java ownership verification and the official 1.20.4 client download passed
on the physical S25+. Guava and the selected Java dependency set are now verified. Its real Java entry point
passes that dependency failure and stops at an ART enum-array verifier boundary. An original
translation fix passes host regression tests; its corrected physical test awaits USB access. The main menu, worlds and gameplay do not work.
No game content, JVM or PojavLauncher code is bundled in APKs/source.

## Implemented

- Android 10+, ARM64 Kotlin / Compose app with persistent appearance settings and device diagnostics.
- C++ JNI / GLES 3 surface probe, touch events, bounded native input queue and lifecycle suspension.
- Browser PKCE plus an approval-gated Xbox/XSTS/Minecraft Services chain. The project's
  own registration is configured locally, the owner reports approval, and generic Microsoft
  and Xbox/XSTS/Minecraft profile sign-in have worked on S25+. Ownership validation remains separate.
- AES-256-GCM credential storage using non-exportable Android Keystore keys, refresh support,
  local logout and protection against stale asynchronous session results.
- Signed purchased-license verification, fresh nonce/account/profile binding and short-lived
  memory-only installation grants. [Contract and key provenance](docs/OWNERSHIP.md).
- Ownership-gated official original client and 36 selected Java-library downloads, metadata
  chaining, size/SHA-1 validation, private staging and atomic commit. This is not a full installer.
- Original host-only Enum/interface array translation adapter with regression tests.
  [Exact scope and current device boundary](docs/ART_ARRAY_COMPATIBILITY.md).
- Read-only classfile/JNI audit and public version-metadata audit; tests and GitHub Actions.
- Separate original Java 17 DEX/reflection/JNI instrumentation experiment for ARM64 devices.
  See [runtime lab](docs/RUNTIME_LAB.md); both tests passed on S25+ / Android 15.
- LWJGL 3.3.2 API subset and ARM64 EGL/GLES bridge, with upstream-compiled DEX and real
  pixel readback tested on S25+. [Exact scope](docs/LWJGL_COMPATIBILITY.md).

See [FEASIBILITY](docs/FEASIBILITY.md), [architecture](docs/ARCHITECTURE.md),
[authentication setup](docs/AUTHENTICATION.md), [status](docs/STATUS.md) and
[validation evidence](docs/VALIDATION.md).

## Build

Install JDK 17 and Android SDK. Pinned tools: Gradle 8.11.1, AGP 8.9.3, Kotlin 2.1.20,
SDK/build tools 35, NDK 27.2.12479018, CMake 3.22.1. These are intentional reproducible
baseline versions, not a claim that they are the newest available.

1. Open this directory in Android Studio, or set `ANDROID_HOME` to your SDK directory.
2. Install `platforms;android-35`, `build-tools;35.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`.
3. Run `./gradlew :core:test :app:assembleDebug :app:lintDebug` (Windows: `gradlew.bat`).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`. Proprietary content is never packaged.
5. `adb install -r <apk>` on an ARM64 device. Review [device testing](docs/DEVICE_TESTING.md).

On Windows, use a short workspace/SDK path if Ninja encounters the 260-character limit.
An optional `-PnativeBuildDir=<short-path>` selects a separate CMake staging directory.

No Microsoft configuration is needed to build or use the platform probe. Live OAuth is
an optional developer setup; Microsoft authentication alone cannot unlock downloads.

## Tests and research tools

```
./gradlew :bytecode-tools:test :core:test :authentication:testDebugUnitTest :lwjgl-compat:testDebugUnitTest
python -m unittest discover -s tests -v
cmake -S native-engine/src/main/cpp -B build/native-host
cmake --build build/native-host
ctest --test-dir build/native-host --output-on-failure
python tools/metadata_audit.py --version 1.20.4 --output build/metadata-audit.json
python tools/classfile_audit.py /path/to/already-owned-client.jar --output build/classfile-audit.json
./gradlew :authentication:assembleDebugAndroidTest
```

`JAVA_HOME/bin` must be on PATH for the Python fixture test. The metadata tool fetches
only public Mojang JSON and checks the version metadata hash. The classfile tool never
executes a JAR. Do not commit local game files or account data. Build instrumentation APKs and run them manually when preserving a signed-in session: Gradle connected tests may uninstall target apps.

## Next execution gate

Select and prove a legally permissible Java bytecode execution architecture, then implement
the version-specific Java/desktop API and LWJGL/native ABI bridges. The C++ surface test
does not satisfy this gate. No runtime has been selected as production-ready.

Ember's public-client registration is complete and the owner reports Mojang approval;
see [registration status](docs/REGISTRATION_STATUS.md) for the evidence limits. The purchased-license
adapter is implemented and physically tested; subscriptions remain unsupported. See the concrete
approval record template in `docs/API_APPROVAL_RECORD.example.md`.

Release signing must use an externally supplied keystore and CI secrets; debug keys are
never production keys. Application updates must retain the application ID and signing
identity. Future installations/worlds must live separately from update staging directories.
Do not distribute proprietary JARs, translated game binaries or game assets in releases.
