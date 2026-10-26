# Ember 0.2.3 research build

The local build records the owner's 2026-10-26 confirmation of Ember's API approval and
requests the documented minimum Xbox sign-in scope. No approval ID is invented. Server
authorization remains enforced, and entitlement/profile responses cannot grant installation
rights until the ownership adapter is validated.

The app now distinguishes unconfigured services, Microsoft-only consent, and an account
connection ready to check. A restored Microsoft-only session shows Connect Minecraft account;
it is never silently reused as an Xbox-authorized token. The new consent happens in the
Microsoft browser. No client secret or passwords are handled by Ember.

Hard-coded pending-approval messages no longer appear in a configured build. Installation
and Play remain disabled because the installer, validated license adapter, and real client
execution are not yet implemented. These controls are not unlocked by Microsoft sign-in.

Public/CI builds do not include the local approval configuration. This change does not add
a graphics translator, runtime, game downloader, or Minecraft binaries.

Physical result: the S25+ displayed Minecraft profile connected after the fresh Xbox
consent flow. All five service stages completed; the screenshot contains no token.
Public evidence omits the profile name and ID. Ownership remains unverified.
