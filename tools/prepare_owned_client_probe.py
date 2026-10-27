"""Private ART experiment for an already-owned 1.20.4 installation, not native AOT.

Fetches public metadata only. Verifies input client/libraries; runs D8 into a new private
directory. Never downloads game bytes, changes originals, installs or publishes outputs.
Keep translated/proprietary content out of source trees and distributable artifacts.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
from metadata_audit import MANIFEST, public_metadata

COORDINATES = {
    "com.mojang:logging:1.1.1", "org.slf4j:slf4j-api:2.0.7",
    "org.apache.logging.log4j:log4j-api:2.19.0",
    "org.apache.logging.log4j:log4j-core:2.19.0",
    "org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0",
    "net.sf.jopt-simple:jopt-simple:5.0.4",
}


def checked(path, expected):
    path = pathlib.Path(path).resolve(strict=True)
    if not path.is_file() or path.stat().st_size != expected["size"]:
        raise ValueError("Input size mismatch")
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        while block := stream.read(65536):
            digest.update(block)
    if digest.hexdigest() != expected["sha1"]:
        raise ValueError("Input hash mismatch")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True)
    parser.add_argument("--libraries", required=True, help="Private directory containing hash-named verified JARs")
    parser.add_argument("--android-sdk", required=True)
    parser.add_argument("--java", required=True, help="JDK 17 java executable")
    parser.add_argument("--output", required=True, help="New private output directory outside the source tree")
    args = parser.parse_args()
    output = pathlib.Path(args.output).resolve()
    project = pathlib.Path(__file__).resolve().parents[1]
    if output == project or project in output.parents or output.exists():
        raise ValueError("Use a new private directory outside the source tree")
    manifest = json.loads(public_metadata(MANIFEST))
    entry = next(value for value in manifest["versions"] if value["id"] == "1.20.4")
    data = public_metadata(entry["url"])
    if hashlib.sha1(data).hexdigest() != entry["sha1"]:
        raise ValueError("Metadata hash mismatch")
    metadata = json.loads(data)
    inputs = [checked(args.client, metadata["downloads"]["client"])]
    selected = [value for value in metadata["libraries"] if value["name"] in COORDINATES]
    if {value["name"] for value in selected} != COORDINATES:
        raise ValueError("Unexpected dependency contract")
    for value in selected:
        artifact = value["downloads"]["artifact"]
        inputs.append(checked(pathlib.Path(args.libraries) / (artifact["sha1"] + ".jar"), artifact))
    sdk = pathlib.Path(args.android_sdk).resolve(strict=True)
    output.mkdir(parents=True)
    subprocess.run([args.java, "-Xmx3g", "-cp", str(sdk / "build-tools/35.0.0/lib/d8.jar"),
                    "com.android.tools.r8.D8", "--min-api", "29", "--lib",
                    str(sdk / "platforms/android-35/android.jar"), "--output", str(output),
                    *map(str, inputs)], check=True)
    report = {"version": "1.20.4", "inputsIntegrityVerified": True,
              "nativeAot": False, "playable": False,
              "dex": [{"file": path.name, "bytes": path.stat().st_size,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                      for path in sorted(output.glob("*.dex"))]}
    (output / "probe-manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
