"""Read-only audit of an already-owned client. Fetches only public JSON, never game files.

An official client hash match confirms identity/integrity, not account ownership or launch permission.
"""
import argparse
import hashlib
import json
import pathlib
import zipfile
from classfile_audit import scan_class
from metadata_audit import MANIFEST, public_metadata


def key(member):
    return (member["owner"], member["kind"], member["name"], member["descriptor"])


def declarations(classes_directory):
    available = set()
    for path in pathlib.Path(classes_directory).rglob("*.class"):
        data = scan_class(path.read_bytes())
        for member in data["declared_members"]:
            if member["flags"] & 1:
                available.add((data["class"], member["kind"], member["name"], member["descriptor"]))
    if not available:
        raise ValueError("No compiled shim classes found; build :lwjgl-compat:assembleDebug first")
    return available


def linkage(jar_path, available):
    references = {}
    with zipfile.ZipFile(jar_path) as jar:
        entries = jar.infolist()
        if len(entries) > 200000 or sum(item.file_size for item in entries) > 512 * 1024 * 1024:
            raise ValueError("JAR exceeds audit limits")
        for entry in entries:
            if not entry.filename.endswith(".class"): continue
            if entry.file_size > 16 * 1024 * 1024: raise ValueError("Class exceeds audit limit")
            data = scan_class(jar.read(entry))
            for member in data["member_references"]:
                if member["owner"].startswith("org/lwjgl/"):
                    identity = key(member)
                    references[identity] = {**member, "declaration_present": identity in available}
    values = [references[identity] for identity in sorted(references)]
    return {"lwjgl_references": values, "total": len(values),
            "missing_declarations": sum(not item["declaration_present"] for item in values),
            "limitation": "Declaration inventory only; bodies, inherited members, reflection and dynamic loading are not validated. A declaration match is not behavioral compatibility."}


def verified_client_metadata(version):
    manifest = json.loads(public_metadata(MANIFEST))
    entry = next((item for item in manifest["versions"] if item["id"] == version), None)
    if entry is None: raise ValueError("Unknown version")
    data = public_metadata(entry["url"])
    if hashlib.sha1(data).hexdigest() != entry["sha1"]: raise ValueError("Untrusted version metadata")
    return json.loads(data)["downloads"]["client"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True)
    parser.add_argument("--version", default="1.20.4")
    parser.add_argument("--shim-classes", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    client = pathlib.Path(args.client)
    metadata = verified_client_metadata(args.version)
    if client.stat().st_size != metadata["size"]: raise ValueError("Client size does not match official selected version")
    digest = hashlib.sha1()
    with client.open("rb") as source:
        while block := source.read(1024 * 1024): digest.update(block)
    if digest.hexdigest() != metadata["sha1"]: raise ValueError("Client hash does not match official selected version")
    report = linkage(client, declarations(args.shim_classes))
    report.update(version=args.version, official_client_sha1=metadata["sha1"],
                  ownership_verified=False, client_executed=False, proprietary_downloads_performed=False)
    pathlib.Path(args.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__": main()
