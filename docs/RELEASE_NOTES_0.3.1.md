# Ember 0.3.1 research

Guava 32.1.2-jre and failureaccess 1.0.1 are now included in the ownership-gated, verified
Java dependency plan. All 36 rule-free non-LWJGL Java artifacts download and verify.
Desktop-only native classifiers remain excluded. This fixes the missing Guava class on S25+.

The next real-client failure is ART verification of an erased Enum[] argument passed to
an interface-array parameter. An original non-game fixture reproduced it with SDK D8 8.6.2
and Google Maven D8 9.5.28, in debug/release. New original host-only bytecode tooling inserts
an explicit type check on private derivatives; three host regression tests pass. Physical
execution of the corrected conversion awaits stable USB access. Original JARs are preserved.

The startup probe now loads original verified JAR resources, checks a private DEX hash
manifest and offers a separate authenticated experiment using the phone's encrypted session.
Launch values never leave the phone. Console redaction handles split writes and bounded
lines. No dummy/demo identity is introduced. The corrected authenticated experiment has
not run while USB is disconnected.

27 core, 3 bytecode-tool, 11 auth, 5 LWJGL host tests and 6 Python tests pass. APK assembly
and lint pass. Native adapters, desktop graphics translation, full asset installation and
actual main-menu/world gameplay remain incomplete. This is managed ART research, not
native AOT. No original or translated game content is in distributed artifacts.
