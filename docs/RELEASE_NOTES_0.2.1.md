# Ember 0.2.1 research build

The app display name, launcher heading, project overview and API application draft now
use Ember, the name selected by the project owner. The Android version code is 3.
The application ID, native library names, credential aliases and OAuth redirect retain
their existing identifiers so an update preserves the existing app identity and data.

The source also includes a CI bootstrap fix that avoids the retired SDK `tools` package;
the corrected GitHub Actions build passed. App behavior changes are limited to branding.
The physical-device and service mock evidence in
docs/evidence comes from the previous implementation validation; it is not a new run
of those tests for this rename. No live account login, entitlement approval or real
Minecraft execution is demonstrated by this release.
