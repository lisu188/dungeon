# Dungeon Generator — Kotlin Port

A Kotlin/JVM port and modernization of **drow's Random Dungeon Generator** from [donjon.bin.sh](https://donjon.bin.sh/).

The project explores translating a procedural dungeon-generation algorithm from its original Perl implementation into structured, idiomatic Kotlin while preserving the generator's behavior. The current Kotlin implementation separates configuration, generation, dungeon state, and rendering concerns and builds on Java 17 with Gradle.

## What is here

- Kotlin/JVM implementation of the dungeon generator
- configurable room and corridor layouts
- room placement, corridor carving, stairs, masks, and cleanup passes
- rendering separated from generation logic
- the original/reference Perl implementation (`dungeon.pl`)
- an earlier single-file Kotlin port (`dungeon.kts`) retained for comparison

The generation pipeline is intentionally explicit: initialize cells → place rooms → open/connect rooms → label rooms → carve corridors → place stairs → clean the resulting map.

## Build and run

Requirements:

- JDK 17+

```bash
./gradlew build
./gradlew run
```

## Repository status

This is an experimental/learning project rather than a production library. The current default branch reflects an ongoing migration from the historical scripts into a conventional Gradle/Kotlin project structure.

## Provenance and license

The original `dungeon.pl` is **Random Dungeon Generator by drow** from `donjon.bin.sh` and states that it is provided under the **Creative Commons Attribution-NonCommercial 3.0 Unported License**.

This repository preserves that attribution deliberately. The Kotlin implementation is a derivative port of that algorithm and should not be interpreted as an independently invented dungeon-generation algorithm. See the header in `dungeon.pl` for the original attribution and licensing notice.
