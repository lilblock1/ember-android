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

## Private actual-client experiment (0.3.0)

After fresh ownership and client integrity checks, a private copy was converted with D8
alongside six verified logging/parser JARs. Originals were preserved. The new
`tools/prepare_owned_client_probe.py` repeats that conversion from already-owned inputs
and public metadata. It refuses existing outputs or outputs inside the source tree.
Use JDK 17, SDK/build-tools 35 and a new private directory.

```
python tools/prepare_owned_client_probe.py --client /private/1.20.4-client.jar --libraries /private/initialization-libraries --android-sdk /sdk --java /jdk17/bin/java --output /private/new-client-dex
```

The opt-in instrumentation probe loads private DEX only after fresh ownership checks.
The real Main class initialized; its entry stack frame was observed before missing Guava
Ticker stopped it. It receives no credentials/arguments. Passing this diagnostic means
evidence collection succeeded, not game launch. This is managed ART/DEX, not native AOT;
no production engine is selected and no translated game bytes are in source/APKs.

## 0.3.1 dependency and verifier work

Guava/failureaccess now resolve on S25+, and all 36 rule-free Java artifacts download
and verify against official metadata (55,695,981 bytes). Startup then exposed an ART
intersection-array VerifyError. See ART_ARRAY_COMPATIBILITY.md for the original
reproducer, four failing converter variants and the narrowly tested translation pass.
The preparation command now also requires `--array-adapter-classpath` after building
`:bytecode-tools:installDist`. Only private derivatives change; original JARs remain intact.
