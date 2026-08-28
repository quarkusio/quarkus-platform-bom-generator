# `cpe-artifacts` platform property serialization

This document describes the format of the `cpe-artifacts` platform property produced by the platform BOM generator, so that the Quarkus SBOM generator can decode it.

## What the property records

When a platform member declares both a product CPE and an offering, the
generator records, for each supported **runtime** extension artifact of that
member, the set of artifacts a consumer should attribute to the member's CPE.

The data is a map:

```
runtime extension artifact  →  its deployment artifact + the resolved
                               deployment dependency closure (aligned to the
                               versions the platform ships)
```

Keying by the runtime artifact lets a consumer look up the runtime artifacts it
already has in its `ApplicationModel` directly, without mapping them to their
deployment counterparts itself.

## Property key

The value is written into the member's generated `platform-properties.properties`
keyed by the *generated* member BOM coordinates:

```
platform.<member-bom-groupId>.<member-bom-artifactId>.cpe-artifacts=<base64>
```

The property is present only when the member declares both a CPE and an
offering. Base64's alphabet contains no `${`, so Maven resource filtering leaves
the value untouched.

## Compression pipeline

The value is an exact encoding of the map. It is produced with JDK APIs only — no third-party dependencies:

**Encode:** build the text format below → UTF-8 bytes →
`java.util.zip.Deflater` with `BEST_COMPRESSION` → `Base64` (standard alphabet,
no line breaks).

**Decode:** `Base64.getDecoder().decode(value)` → `java.util.zip.Inflater` →
UTF-8 string → parse the text format below.

The compressed stream is a standard zlib/DEFLATE stream, so any language's
zlib binding can inflate it; the compression level is not needed for
decompression.

## Text format

Coordinates recur heavily across entries (deployment closures overlap between
extensions), and even distinct coordinates share groupIds, versions and
artifactId prefixes. To remove this repetition — most of which lies beyond
DEFLATE's 32&nbsp;KB window — the text has two sections separated by a line
containing exactly `--`:

1. a **dictionary** of every distinct coordinate, grouped so shared parts are
   written once;
2. the **entries**, which reference the dictionary by index.

```
@org.apache.camel.quarkus     ← groupId; the NEXT line is this group's common artifactId prefix
camel-quarkus-                ← positional prefix line (empty when there is no shared prefix)
=3.33.0                       ← version sub-block
core                          ← artifactId minus prefix → camel-quarkus-core     (classifier "", type jar)
support:linux-x86_64          ← camel-quarkus-support, classifier linux-x86_64, type jar
=3.20.0                       ← further versions reuse the same groupId + prefix
legacy                        ← camel-quarkus-legacy
@io.quarkus
quarkus-core                  ← prefix equals the only artifactId (singleton group)
=3.15.0
                              ← empty artifact line ⇒ artifactId equals the prefix exactly
--
1a[0,3,5]                     ← entry: runtime-key index, then delta-encoded dependency indices
```

### Dictionary section

Each line is self-identifying by its first character:

| Line              | Meaning |
|-------------------|---------|
| `@groupId`        | Starts a group. The **very next line** is this group's common artifactId prefix. |
| *(line after `@`)*| The group's common artifactId prefix — **positional, always present**, and empty when the group has no shared prefix. |
| `=version`        | Starts a version sub-block within the current group. |
| *(anything else)* | An **artifact line** (see below). |

An artifact line is the artifactId with the group prefix stripped, optionally
followed by `:classifier` and/or `:type`. Trailing default parts are omitted:

| Artifact line              | Reconstructed coordinate (with prefix `P`, group `G`, version `V`) |
|----------------------------|-------------------------------------------------------------------|
| `rem`                      | `G:` `P+rem` `::jar:V`  (empty classifier, `jar` type) |
| `rem:cls`                  | classifier `cls`, type `jar` |
| `rem::type`                | empty classifier, type `type` |
| `rem:cls:type`             | classifier `cls`, type `type` |
| *(empty line)*             | artifactId equals the prefix exactly (`P`), empty classifier, `jar` type |
| `:cls`                     | artifactId equals the prefix, classifier `cls` |

Because the prefix line is positional (always the line immediately after
`@groupId`), no sentinel character is needed for it, and empty lines are
unambiguously artifact lines. Maven groupIds, artifactIds and versions never
start with `@` or `=`, so the first-character dispatch is unambiguous.

**Indexing:** artifact lines take dictionary indices sequentially in the order
they appear, starting at 0. The `@`, prefix and `=` lines consume no index.

### Entries section

One line per runtime extension:

```
<key-index>[<Δ0>,<Δ1>,...,<Δn>]
```

- `<key-index>` is the **base-36** dictionary index of the runtime extension
  artifact (absolute).
- Inside the brackets are the extension's deployment-rooted dependency closure,
  as base-36 dictionary indices sorted ascending and **delta-encoded**: the
  first value is the absolute index, each subsequent value is the gap from the
  previous one. Reconstruct by a running sum.
- An empty dependency list is `<key-index>[]`.

For example, `1a[0,3,5]` means: key = dictionary entry `Integer.parseInt("1a", 36)`;
dependencies = dictionary entries at absolute indices `0`, `0+3=3`, `3+5=8`.

## Determinism

The output is reproducible: the dictionary is sorted by
`(groupId, version, artifactId, classifier, type)`, so index assignment depends
only on content (not on map iteration order); entries are ordered by their
runtime key's dictionary index; and dependency indices are ascending.
Byte-identical output across builds additionally assumes a consistent JDK/zlib
(the release toolchain), as with any DEFLATE-based artifact.

## Reference implementation

The Java encoder/decoder is
`io.quarkus.bom.decomposer.maven.platformgen.CpeArtifactsEncoder` in the
`quarkus-platform-bom-maven-plugin` module:

```java
Map<ArtifactCoords, List<ArtifactCoords>> map = CpeArtifactsEncoder.decode(value);
```

Its class Javadoc is the authoritative description of the format.
