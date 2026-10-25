# Original Java 17 runtime boundary experiment

The separate `compatibility-lab` module builds a test APK containing an **original** synthetic
fixture. It is not a game, an AOT Minecraft port or a replacement client. The foundation
application does not depend on this module.

Build: `./gradlew :compatibility-lab:assembleDebugAndroidTest`

On a connected physical ARM64 Android 10+ device:
`./gradlew :compatibility-lab:connectedDebugAndroidTest`

The build compiles `tests/fixtures/CompatibilityFixture.java` with Java 17 and converts that
classfile to DEX using SDK build tools 35.0.0. The instrumentation tests are designed to:

1. Load the DEX through Android's in-memory class loader, separate from the test's classes.
2. Execute the fixture's reflection-based `StringBuilder` constructor.
3. Register its declared native method with a small ARM64 JNI bridge and verify a marker.
4. Assert that calling its desktop `java.awt.Desktop` dependency still fails explicitly.

Both tests passed on the physical S25+ / SM-S936U, Android 15 API 35, ARM64, on
2026-10-24. See VALIDATION.md and sanitized device test evidence.

Passing these tests proves only the selected synthetic ART/DEX/JNI
boundary. ART remains a managed runtime. This does not establish AOT game execution,
desktop Java SE compatibility, LWJGL adaptation, graphics translation or Minecraft playability.
It does not change any first-playable milestone status.

No Microsoft account or Minecraft files are needed to run this isolated test. It makes no
network requests and does not request game authentication permissions. Keep results separate
from the actual-client device compatibility table.
