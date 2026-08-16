# Living Cities

A Minecraft **1.21.1 / NeoForge** mod that turns cities you build by hand into cities that actually
function — population, jobs, an economy, territory and people walking around in them.

Living Cities ships **no prefab buildings**. You build a skyscraper out of whatever blocks you like —
vanilla, Create, furniture mods, road mods — then select it and tell the mod *this is an office
building*. The mod measures what you built and simulates it. The architecture stays entirely yours.

---

## Status: Stage 1 — City Foundation

The mod is being rebuilt stage by stage against [`docs/ROADMAP.md`](docs/ROADMAP.md).

**Stage 0 — Engineering Foundation: complete.** Project, toolchain, registries, logging, config,
network registration, persistence with a data version, client/server separation and CI.

**Stage 1 — City Foundation: in progress.** City hall core, city creation, chunk territory, the
planner tool, building registration, floor detection, capacity, virtual population and basic tax
revenue.

An earlier build reached cities, territory, building scanning, an economy and power/water grids, and
was reset in August 2026 to start again on a proper foundation. That code is still in git history
(`git show 402af0f:livingcities/src/...`) and the reasoning behind it is preserved in
[`docs/PRIOR-BUILD.md`](docs/PRIOR-BUILD.md).

What the mod is being rebuilt towards: [`docs/DESIGN-BRIEF.md`](docs/DESIGN-BRIEF.md).
The stage-by-stage plan, which is the authority on scope and order:
[`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Running it

Requires **JDK 21** and an internet connection that can reach `maven.neoforged.net`.

```bash
cd livingcities
./gradlew runClient      # launches a dev client with the mod loaded
```

The first run downloads and decompiles Minecraft and takes several minutes. After that it is quick.

Other useful commands:

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compile and produce the jar in `build/libs/` |
| `./gradlew runClient` | Launch a client with the mod loaded |
| `./gradlew runServer` | Launch a dedicated server — catches client/server mistakes |

Full setup instructions, including installing a JDK from nothing, are in
[`docs/DEV_SETUP.md`](docs/DEV_SETUP.md).

### Installing the jar into a Modrinth instance

Not worth doing yet — there is nothing to play. Once there is, the jar from `build/libs/` (or from a
successful [Build Living Cities](../../actions/workflows/livingcities-build.yml) run) drops into the
Modrinth App via **Content → Upload files**, on an instance running **NeoForge for Minecraft 1.21.1**.

---

## Compatibility

- **Minecraft** 1.21.1, **NeoForge** 21.1.x, **Java** 21
- Designed to sit alongside large modpacks. It adds its own blocks and items and never replaces or
  overrides vanilla systems.
- **Block-agnostic by design.** The building scanner asks a block *what it does* (does it have full
  collision, is its top face sturdy, does it occlude) rather than checking it against a list of known
  blocks. Modded blocks, furniture and machinery are measured the same way vanilla ones are.
- **Multiplayer from the ground up.** All simulation, money and territory state is server-side. The
  client renders and requests; it never decides.

---

## Project layout

```
livingcities/
  src/main/java/com/branciho/livingcities/
    LivingCities.java      mod entry point
    registry/              blocks, items, creative tab
  docs/
    DESIGN-BRIEF.md        what the mod is meant to become  <- start here
    ROADMAP.md             the stage plan, Stage 0 -> 11+   <- the authority
    DEV_SETUP.md           how to build and run it
    PRIOR-BUILD.md         how the deleted implementation worked
```

Constraints that must not be traded away — version traps, threading rules, the no-prefabs rule — are
in [`AGENTS.md`](../AGENTS.md) at the repository root.
