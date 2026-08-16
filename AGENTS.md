# Working on Cities In Life

Notes for anyone — human or AI — picking this project up.

The mod lives in `citiesinlife/`. The loose `.md` files at the repo root are leftovers from a Git
workshop in 2020 and have nothing to do with it.

> An earlier attempt at this idea, called Living Cities, was deleted in August 2026 and this project
> started fresh. It is still in git history if you ever want it (`git log -- livingcities`), but it is
> not a reference and nothing here needs to match it.

## What it is

A Minecraft **1.21.1 / NeoForge** mod. You build a structure by hand out of any blocks you like, draw
a box around it with the **Planner Wand**, and say what it is — City Core, Residential, Commercial,
Business or Factory. The mod measures what is actually inside the box and simulates it: population,
jobs, treasury, territory.

**It ships no prefab buildings, and never will.** That is the one rule. If a feature would be easier
by handing the player a pre-made structure to place, the feature is wrong, not the rule.

## Hard constraints

- **Minecraft 1.21.1, NeoForge 21.1.x, Java 21.** Not Fabric, not 1.20.x, not 1.21.2+. Traps that
  have already cost time on this codebase: `StreamCodec.composite` caps at six components,
  `RenderLevelStageEvent.getPartialTick()` returns a `DeltaTracker` while `Screen.render` takes a
  float, and `Screen.render` draws the background *before* the widgets — so a panel painted in
  `render` covers its own buttons. Paint it in `renderBackground` instead.
- **Server-authoritative.** A payload says what the player *wants*. The server re-derives ownership,
  cost, overlap and capacity from its own state. Never trust a position or the fact that the client
  thought a button should be clickable.
- **Client code lives only under `client/`**, guarded by `Dist.CLIENT`. The one exception is
  `net/ClientCityCache`, which the common-side payload registration touches — it deliberately imports
  nothing from `net.minecraft.client` so a dedicated server can load it. Keep it that way.
  `./gradlew runServer` is the only real proof this still holds.
- **Block-agnostic.** No hardcoded lists of vanilla blocks. `scan/StructureScanner` asks a block what
  it *does* — sturdy top face, blocks motion, holds a roof up — so modded blocks measure the same as
  vanilla ones. In a modpack this is the difference between working and not.
- **Population is a number.** Simulation cost scales with the number of structures, never with the
  number of people. There will never be one entity per citizen.

## Things that are easy to get wrong

- **Compiling proves nothing.** The biggest source of bugs in the predecessor was valid code that did
  nothing: a hotkey wired to a boolean nobody read, a payload fully implemented and never sent, a
  delete function that was never called. **Every handler must change something visible.**
- **Registrations are invisible.** A structure is a box in server data with no blocks of its own.
  That is why structure mode (Shift+L) and its delete exist, and why they were built at the same time
  as registration rather than afterwards. Any new feature that creates one of these needs a way to
  *see* it and *remove* it.
- **Static client state must be cleared on disconnect.** In single player the JVM outlives the world,
  so a player who quits to the menu and opens another world inherits the last one's cache. See
  `ClientEvents.onLoggingOut`.
- **Capacity comes from usable floor cells, not bounding volume.** A hollow tower and a solid cube of
  the same size are not the same building. Housing quantises into whole dwellings because half an
  apartment houses nobody.

## Verifying changes

```bash
cd citiesinlife
./tools/syntax-check.sh   # fast, no Minecraft needed; structural errors only
./gradlew build           # the real compile
./gradlew runClient       # launch it and actually use the feature
./gradlew runServer       # proves no client class leaked into common code
```

GitHub Actions compiles every push and uploads the jar. That is a safety net, not a test.

## Commit style

Explain **why**, not what the diff already shows. Where a constant was chosen for a reason, say what
the reason was.
