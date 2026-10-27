# JVM intersection arrays and the ART verifier

## Reproduced boundary

After adding Guava/failureaccess and all 36 selected Java artifacts, the real 1.20.4
entry runs on S25+ until ART rejects an erased Enum[] argument to an interface-array
parameter. This is now a type-verification failure, rather than a missing Guava class.
The original client also reaches normal missing-required-argument handling on desktop
Java 17 with the same dependencies; that control is not Android/gameplay evidence.

The original `tests/fixtures/EnumIntersectionFixture.java` reproduces the issue without
Minecraft code. It uses a generic type bounded by Enum and an interface. Javac erases
the local array to Enum[], then passes it to a generic interface-array parameter.
HotSpot accepts the valid typed invocation; ART requires an explicit array cast.
On S25+, conversions using SDK D8 8.6.2-dev and Google Maven D8 9.5.28 both failed
with VerifyError, in both debug and release modes. No verifier disabling is used.

## Original narrow translation pass

`bytecode-tools` is a host-only Java 17 tool using ASM 9.7.1. It tracks operand types
and inserts CHECKCAST only when a statically erased Enum[] argument is passed to a
known one-dimensional interface-array parameter. It saves/reloads arguments in order,
including wide primitives, and keeps the receiver and argument evaluation order intact.
It adds no branches, preserves existing stack-map boundaries and is idempotent.
Host tests cover equivalent results, idempotence/known-interface gating, wide
arguments and single supplier evaluation. The original fixture still returns 2 on JVM.
The emitted DEX retains the explicit interface-array checkcast.

For correctly typed intersection arrays, the cast exposes the type that Java already
requires. Unchecked callers outside that contract may fail earlier. This is a bounded
compatibility experiment, not a claim of universal JVM/ART equivalence or native AOT.

Applied to private, integrity-verified inputs, it inserts two casts in two client classes.
The official original client JAR is preserved. Signature blocks are omitted only from
private transformed derivatives; no original signature or ownership proof is bypassed.
Input/expanded-archive/class-size limits apply, no archive paths are extracted, and
output must be a new private directory. The preparation wrapper refuses source-tree
outputs, verifies official input hashes first and records DEX SHA-256 values. The phone
checks that manifest before loading the private DEX. Modified/translated game files are
excluded from source, APKs and public artifacts.

The corrected fixture passed on S25+: verified/executed with result 2. The real client
passed the corresponding verifier boundary and reached subsequent platform API failures.
See evidence/enum-intersection-s25.json. The main menu has not been reached. Updating
compiler version alone did not fix the reproducer.

## Reproduce privately

```
./gradlew :bytecode-tools:test :bytecode-tools:installDist
python tools/prepare_owned_client_probe.py --client /private/1.20.4-client.jar --libraries /private/initialization-libraries --android-sdk /sdk --java /jdk17/bin/java --array-adapter-classpath 'bytecode-tools/build/install/bytecode-tools/lib/*' --output /private/new-client-dex
```

The app's opt-in startup probe defaults to no arguments. Its separate authenticated
variant obtains the actual session from the phone's Keystore-encrypted vault only after
fresh ownership checks. It uses an isolated game directory and redacts token/profile
values from console output, including fragmented writes; oversize/partial lines are
withheld. Only structural error/stack-frame facts are exported, never credentials or
raw authenticated exception messages. Other native, graphics, asset and Java SE gaps
remain independent of this cast fix.

## Primary references and licensing scope

Google describes [D8 conversion and preferred Maven prebuilts](https://r8.googlesource.com/r8/+/refs/heads/main/README.md).
The separately evaluated 9.5.28 JAR matched Google's published SHA-256; see evidence.
[ART is Android's managed DEX runtime](https://source.android.com/docs/core/runtime),
not a native Minecraft AOT port. ASM uses the [3-Clause BSD license](https://asm.ow2.io/license.html)
and is a host build dependency, not an APK runtime library.

The [Minecraft EULA](https://www.minecraft.net/en-us/eula) permits original Java Edition
modification tools for owners and restricts distributing modified game versions. This
experiment keeps game content/derivatives private and distributes only original tool
source. It is not legal clearance to redistribute translated game content or grant
access to an unowned game. API/ownership checks remain unchanged.
