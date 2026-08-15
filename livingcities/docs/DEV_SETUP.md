# Running the mod from source (and letting an AI test it)

The single biggest quality problem with this project so far is that nobody runs the game between
the code being written and it being installed. Compiling proves the code is *valid*; it proves
nothing about whether a button works. Every bug found so far — ghost buildings, a hotkey that drew
nothing, a panel that opened once — would have died in about thirty seconds of actually playing.

This document is how to close that gap.

---

## Why the cloud sandbox cannot do it

The environment this mod was written in cannot reach `maven.neoforged.net`, `libraries.minecraft.net`
or `piston-meta.mojang.com` — they are blocked by an egress policy. It also has no display. So it
cannot download Minecraft, cannot build locally, and cannot open a game window. GitHub Actions does
the compiling instead, which is why every change goes out as a CI artifact.

That is a property of *where* it runs, not of the tooling. On a normal PC none of it applies.

---

## Option A — run the assistant on your own PC (the real fix)

This gives an AI assistant the same loop a developer has: change code, launch the game, read the
crash, fix it. Bugs die before they ever reach a jar you install.

**1. Install prerequisites**

| Thing | Why | Where |
| --- | --- | --- |
| **JDK 21** | Minecraft 1.21.1 needs it | [adoptium.net](https://adoptium.net) — pick Temurin 21 |
| **Git** | to clone this repo | [git-scm.com](https://git-scm.com) |
| **Claude Code** | the assistant | [claude.com/claude-code](https://claude.com/claude-code) |

Verify Java afterwards:

```bash
java -version    # must say 21
```

**2. Get the project**

```bash
git clone https://github.com/bcoros/BranciHo.git
cd BranciHo/livingcities
```

**3. Check it builds on your machine**

```bash
./gradlew build          # Windows: gradlew.bat build
```

The first run downloads Minecraft and NeoForge and takes several minutes. After that it is seconds.

**4. Launch the game**

```bash
./gradlew runClient
```

This opens a real Minecraft client with the mod already loaded. No jar to install, no Modrinth
instance to update — it builds and launches in one step.

**5. Start the assistant in that folder**

```bash
claude
```

It can now run `./gradlew runClient` itself and read everything the game prints: crashes, stack
traces, registry errors, exceptions thrown when you click a button. That is the loop.

---

## Option B — you run it, and paste the log (cheap, most of the benefit)

If installing the assistant locally is more than you want to do, this still fixes most of the
problem:

```bash
cd BranciHo/livingcities
./gradlew runClient
```

When something breaks, copy the console output — particularly anything with `Exception`,
`Caused by:`, or a stack trace — and paste it back. A stack trace names the exact file and line.
"It crashed when I right-clicked the substation" plus twenty lines of log is worth more than an
hour of guessing.

Logs also live in `livingcities/run/logs/latest.log`.

---

## What an AI can and cannot test

| | |
| --- | --- |
| Read crashes and stack traces | **Yes**, from the launched game's output |
| Catch a dedicated-server-only crash | **Yes**, via `./gradlew runServer` |
| Notice a missing texture or model | **Yes**, the log warns about them |
| See the game window | Only through screenshots |
| Judge whether a substation radius *feels* right | **No.** That is you. |
| Judge whether a city looks alive | **No.** That is you. |

The split matters: an assistant can eliminate everything that is *broken*, which is most of what has
gone wrong so far. Whether the mod is any *good* is a separate question that needs a person playing it.

---

## Useful commands

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compile and produce the jar in `build/libs/` |
| `./gradlew runClient` | Launch a client with the mod loaded |
| `./gradlew runServer` | Launch a dedicated server — catches client/server mistakes |
| `./tools/syntax-check.sh` | Fast structural check with no Minecraft needed |

`runServer` is worth knowing about. This mod keeps all client code under `client/` behind a
`Dist.CLIENT` guard precisely so a dedicated server never loads it, and `runServer` is the only way
to prove that actually holds.
