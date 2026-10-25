"""Read-only inventory of an already-owned local JAR; never downloads or executes it.

Finds constant-pool class references, classfile major versions and JNI native methods.
This conservative scan is incomplete: strings/reflection/generated classes can hide dependencies.
"""
import argparse
import io
import json
import struct
import zipfile
from collections import Counter

PREFIXES = ("java/awt/", "javax/swing/", "org/lwjgl/", "sun/misc/", "jdk/internal/")


def scan_class(data):
    stream = io.BytesIO(data)

    def read(size):
        value = stream.read(size)
        if len(value) != size:
            raise ValueError("Truncated class file")
        return value

    def u2(): return struct.unpack(">H", read(2))[0]
    def u4(): return struct.unpack(">I", read(4))[0]
    if read(4) != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid class magic")
    u2()
    major = u2()
    count = u2()
    pool, classes, name_types, members = {}, {}, {}, []
    index = 1
    while index < count:
        tag = read(1)[0]
        if tag == 1:
            pool[index] = read(u2()).decode("utf-8", errors="replace")
        elif tag == 7:
            classes[index] = u2()
        elif tag in (9, 10, 11): members.append((tag, u2(), u2()))
        elif tag == 12: name_types[index] = (u2(), u2())
        elif tag in (3, 4, 17, 18): read(4)
        elif tag in (5, 6):
            read(8)
            index += 1
        elif tag in (8, 16, 19, 20): read(2)
        elif tag == 15: read(3)
        else: raise ValueError(f"Unsupported constant-pool tag {tag}")
        index += 1
    u2()
    this_class = u2()
    u2()
    read(2 * u2())

    def skip_attributes():
        for _ in range(u2()):
            u2()
            read(u4())

    declared = []
    for _ in range(u2()):
        flags, name, descriptor = u2(), u2(), u2()
        declared.append({"kind": "field", "name": pool[name], "descriptor": pool[descriptor], "flags": flags})
        skip_attributes()
    natives = []
    for _ in range(u2()):
        flags, name, descriptor = u2(), u2(), u2()
        declared.append({"kind": "method", "name": pool[name], "descriptor": pool[descriptor], "flags": flags})
        if flags & 0x0100:
            natives.append(pool[name] + pool[descriptor])
        skip_attributes()
    refs = sorted({pool[name] for name in classes.values() if pool.get(name, "").startswith(PREFIXES)})
    references = []
    for tag, owner, name_type in members:
        name, descriptor = name_types[name_type]
        references.append({"kind": "field" if tag == 9 else "method", "owner": pool[classes[owner]],
                           "name": pool[name], "descriptor": pool[descriptor]})
    return {"major": major, "platform_references": refs, "native_methods": natives,
            "class": pool[classes[this_class]], "declared_members": declared, "member_references": references}


def scan_jar(path):
    versions, findings = Counter(), {}
    with zipfile.ZipFile(path) as jar:
        entries = jar.infolist()
        if len(entries) > 200000 or sum(item.file_size for item in entries) > 512 * 1024 * 1024:
            raise ValueError("JAR exceeds audit limits")
        for entry in entries:
            if entry.filename.endswith(".class"):
                if entry.file_size > 16 * 1024 * 1024:
                    raise ValueError("Class exceeds audit limit")
                result = scan_class(jar.read(entry))
                versions[result["major"]] += 1
                if result["platform_references"] or result["native_methods"]:
                    findings[entry.filename] = result
    return {"classfile_major_versions": dict(versions), "platform_findings": findings,
            "limitation": "Static inventory only. No linking, translation, execution or compatibility validation performed."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    with open(args.output, "w", encoding="utf-8") as output:
        json.dump(scan_jar(args.jar), output, indent=2)
