# Working on Living Cities

Notes for anyone — human or AI assistant — picking this project up. Read this before changing code.

The mod lives in `livingcities/`. Everything else in this repo is unrelated (the loose `.md` files at
the root are leftovers from a Git workshop in 2020 — leave them alone).

## Current state: Stage 1 — City Foundation

The mod was reset to a clean skeleton in August 2026 and is being rebuilt stage by stage against
`livingcities/docs/ROADMAP.md`.

**Stage 0 — Engineering Foundation is complete.** Project, toolchain, registries, logging, config,
network registration, `SavedData` with a data version, client/server separation and CI are all in
place. Do not rebuild them.

**Stage 1 — City Foundation is the current stage.** City hall core, city creation, chunk territory,
roles, the planner tool, building registration, floor detection, capacity, virtual population,
employment and basic tax revenue. Nothing beyond Stage 1 until it is finished and signed off.

An earlier, pre-reset build reached roughly 100 Java files covering cities, buildings, a scanner, an
economy simulation and power/water grids. It was deleted on purpose. It is still in git history and
can be read — `git show 402af0f:livingcities/src/...` — and `livingcities/docs/PRIOR-BUILD.md`
explains how each problem was solved. Use them as reference, **not** as something to restore.

## What it is

A Minecraft **1.21.1 / NeoForge** mod that turns structures the player built by hand into functional
city buildings — population, jobs, economy, electricity, water. It ships **no prefab buildings**, and
that is the one rule that must never be traded away: the player builds a skyscraper out of whatever
blocks they like, selects it, and says *this is an office*. The mod measures what is there and
simulates it.

## Which document wins

| Document | What it settles |
| --- | --- |
| **`livingcities/docs/ROADMAP.md`** | **The authority.** Stage order, scope, process, what belongs where. Stage 0 → 11+. |
| `livingcities/docs/DESIGN-BRIEF.md` | Why the mod exists and what makes it different. Read first, for orientation. |
| `AGENTS.md` (this file) | Constraints that must hold whatever stage you are on. |
| `livingcities/docs/DEV_SETUP.md` | How to build and run it. |
| `livingcities/docs/PRIOR-BUILD.md` | How the deleted implementation worked. Reference only. |

If two of them disagree about stage order or scope, **`ROADMAP.md` wins.**

Two rules from the roadmap that are easy to violate by accident:

- **Implement only the current stage.** Small hooks for future systems are fine; major future-stage
  features are not. When a stage is done, stop and report rather than rolling into the next one.
- **Airports have no aircraft.** Stage 9 airports are player-built terminals that move passengers as
  a validated transfer between registered airports, and generate virtual air traffic. There is no
  flight simulation. Cars, buses, trains and metros are unaffected — those are real vehicles in
  Stage 7.

## Hard constraints

- **Minecraft 1.21.1, NeoForge 21.1.x, Java 21.** Not Fabric, not legacy Forge, not 1.20.x, not
  1.21.2+. Version-specific traps that have already cost time: `useItemOn` returns
  `ItemInteractionResult` here (merged away in 1.21.4+), `Screen.render` takes a float partial tick
  while `RenderLevelStageEvent.getPartialTick()` returns a `DeltaTracker`, `StreamCodec.composite`
  caps at six components, and `registerSimpleBlockItem` returns `DeferredItem<BlockItem>` rather than
  `DeferredItem<Item>`.
- **Server-authoritative.** A payload says what the player *wants*. The server decides what happens,
  re-deriving everything from its own state. Never trust a position, a name, or the fact that the
  client thought a button should be clickable.
- **Client code lives only under `client/`**, guarded by `Dist.CLIENT`. Common code must never
  reference `net.minecraft.client.*` or a dedicated server crashes on load. `./gradlew runServer` is
  the only way to prove this still holds.
- **Never touch `Level`, `BlockState` or entities off the main thread.** Snapshot to plain arrays
  first. In the previous build the floor analyser imported no Minecraft classes at all — that was
  deliberate, and it is what made "this cannot touch the world" checkable rather than merely asserted.
- **Block-agnostic.** No hardcoded lists of vanilla blocks anywhere. Ask a block what it *does* —
  full collision, sturdy top face, occlusion — so modded blocks work in packs nobody tested.
- **Two population layers.** Tens of thousands of virtual residents, ~100 physical NPCs. Never one
  entity per citizen. Simulation cost is O(buildings), never O(population).

## Things that are easy to get wrong

Every item here is a bug that actually shipped, not a hypothetical.

- **Compiling proves nothing.** The single largest source of bugs in this project's history was valid
  code that did nothing: a hotkey bound to a boolean nobody read, a payload fully implemented and
  never sent, a remove function that was never called. Run the game.
- **Registrations are invisible.** A building is a box in server data with no blocks of its own. Any
  feature touching them needs a way to *see* and *delete* them, or you recreate the ghost-building
  trap: demolish the structure, and its registration silently keeps reserving the ground forever.
- **Capacity comes from usable floor cells, not bounding volume**, and housing should quantise into
  whole dwellings — half an apartment houses nobody. The design targets are a small house holding
  ~6 residents, an apartment block ~180, a residential tower ~1800. Any capacity model should be
  checked against all three, because a linear one fits the small case and is wildly wrong at the top.
- **Static state must reset on server start and stop.** In single player the JVM outlives the
  integrated server, so a player quitting to the menu and opening another world inherits the previous
  world's caches.
- **Expensive work is deferred and rate limited, never per-block.** Laying a cable run places a
  hundred blocks in seconds; rebuilding a network on each placement would be a hundred full walks.

## Verifying changes

```bash
cd livingcities
./gradlew build           # the real compile
./gradlew runClient       # launch and actually look at it
./gradlew runServer       # proves no client class leaked into common code
```

GitHub Actions compiles every push and uploads the jar. That is a safety net, not a test.

## Commit style

Explain **why**, not what the diff already shows. Where a constant was chosen for a reason, say what
the reason was. Several decisions here look arbitrary until you know the number they were fitted to.
