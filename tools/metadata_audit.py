"""Audit public Mojang metadata only. Never fetches client/libraries/assets or signs in."""
import argparse
import hashlib
import json
import urllib.request
import urllib.parse

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MAX_JSON = 8 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("Metadata redirects are not allowed")


def public_metadata(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname != "piston-meta.mojang.com"
            or parsed.username or parsed.password or parsed.port not in (None, 443)
            or not parsed.path.endswith(".json") or parsed.query or parsed.fragment):
        raise ValueError("Only HTTPS public Mojang JSON metadata is allowed")
    request = urllib.request.Request(url, headers={"User-Agent": "Ember-Compatibility-Research/0.2.1"})
    with urllib.request.build_opener(NoRedirect).open(request, timeout=30) as response:
        data = response.read(MAX_JSON + 1)
    if len(data) > MAX_JSON:
        raise ValueError("Metadata exceeds size limit")
    return data


def summarize(metadata):
    libraries = [item["name"] for item in metadata.get("libraries", [])]
    lwjgl = [name for name in libraries if name.startswith("org.lwjgl:")]
    return {
        "version": metadata["id"],
        "java": metadata.get("javaVersion"),
        "main_class": metadata.get("mainClass"),
        "lwjgl_libraries": lwjgl,
        "android_native_candidates": [name for name in libraries if "android" in name.lower()],
        "conclusion": "Desktop dependencies need replacement/adaptation. Metadata is not proof of execution.",
        "game_files_downloaded": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default="1.20.4")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    manifest = json.loads(public_metadata(MANIFEST))
    entry = next((item for item in manifest["versions"] if item["id"] == args.version), None)
    if entry is None:
        raise ValueError("Unknown version")
    data = public_metadata(entry["url"])
    if hashlib.sha1(data).hexdigest() != entry["sha1"]:
        raise ValueError("Version metadata integrity check failed")
    summary = summarize(json.loads(data))
    summary["metadata_sha1"] = entry["sha1"]
    summary["metadata_url"] = entry["url"]
    with open(args.output, "w", encoding="utf-8") as output:
        json.dump(summary, output, indent=2)
        output.write("\n")


if __name__ == "__main__":
    main()
