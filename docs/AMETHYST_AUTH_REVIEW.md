# Amethyst authentication reference review

Reviewed `v3_openjdk` at commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, 2026-10-24.

[MicrosoftLoginFragment.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MicrosoftLoginFragment.java)
opens authorization in a WebView using a fixed legacy client ID and desktop redirect.
PKCE/state binding was not present in the inspected authorization request.

[MicrosoftBackgroundLogin.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/authenticator/microsoft/MicrosoftBackgroundLogin.java)
uses Microsoft → Xbox user token → XSTS → Minecraft token/profile. It logs authentication
values, treats a returned profile as ownership and skips entitlement retrieval in that
case. It also contains a demo fallback and incomplete subscription handling. These are
observations of this commit, not claims about every Amethyst version.

The standard service sequence is independently implemented here, retaining browser PKCE,
own approved registration, encrypted credentials and strict installation gating. No legacy
app ID, WebView password flow, secret-bearing logs or demo fallback was added.

[MinecraftAccount.java](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/value/MinecraftAccount.java)
serializes account fields to JSON files. Here serialization is passed to an encrypted
vault, and token-bearing result/response types redact their string representations.

Its [license](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/LICENSE)
is LGPLv3. No source was copied or linked. The same protocol sequence does not require
importing the Pojav-derived launcher/runtime. Its repository does not establish permission
for this project's client ID. [Own setup and pending tests](AUTHENTICATION.md).
