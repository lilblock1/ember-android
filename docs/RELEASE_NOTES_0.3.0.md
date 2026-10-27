# Ember 0.3.0 research

Purchased Java ownership now verifies a fresh signed Services license and matching profile,
with pinned publisher key, account/nonce/time checks and memory-only grants. The production
flow passed on S25+. Original Minecraft 1.20.4 client plus six initialization dependencies
were downloaded from Mojang hosts and hash/size checked after rechecking ownership.

Added explicit GLES shader/VBO/VAO adapters. Upstream-compiled original fixture draws a
verified pixel on S25+. Twenty core tests, eleven auth mocks and five LWJGL host tests pass.
Three physical EGL tests pass. Unknown subscriptions/keys/contracts still fail closed.

The actual Minecraft Main entry was reached in a private ART/DEX experiment and stopped
at missing Guava Ticker. This is managed runtime execution, not a native AOT port. Main menu,
worlds, gameplay, assets/full dependency installation, audio and desktop GL translation are
not working. Play remains disabled. No game/translated binaries are distributed.

Debug APK retains the package/signing identity and installed session. API approval is owner
attested; the written provider reference remains unprovided. See OWNERSHIP.md, FEASIBILITY.md
and docs/evidence for precise results and limitations.
