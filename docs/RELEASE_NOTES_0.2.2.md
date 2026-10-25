# Ember 0.2.2 research build

The local APK is configured with Ember's verified Microsoft native public-client
registration. The Android version code is 4. Generic Microsoft browser sign-in is now
available for physical-device testing. The saved native callback matches the app.
Minecraft Services access remains disabled pending Mojang approval and confirmed scopes.

The public source excludes `auth.local.properties`; developers must configure their
build explicitly. The registration's public identifiers and pending review status are
documented in REGISTRATION_STATUS.md. No client secret or user credentials are included.

No live phone login or game compatibility result is claimed by this configuration change.
Earlier physical probe tests in docs/evidence are historical evidence, not a rerun of
those tests for this build. The unconfigured source baseline passed GitHub Actions before
this registration update; see docs/evidence/github-actions.json for the exact commit.
