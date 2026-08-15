# Working on Living Cities

Notes for anyone — human or AI assistant — picking this project up. Read this before changing code.

The mod lives in `livingcities/`. Everything else in this repo is unrelated.

## What it is

A Minecraft **1.21.1 / NeoForge** mod that turns structures the player built by hand into functional
city buildings — population, jobs, economy, electricity, water. It ships **no prefab buildings**, and
that is the one rule that must never be traded away: the player builds a skyscraper out of whatever
blocks they like, selects it, and says *this is an office*. The mod measures what is there and
simulates it.

Full design rationale is in `livingcities/docs/ARCHITECTURE.md`.
How to actually run it is in `livingcities/docs/DEV_SETUP.md`.

## Hard constraints

- **Minecraft 1.21.1, NeoForge 21.1.x, Java 21.** Not Fabric, not legacy Forge, not 1.20.x, not
  1.21.2+. Version-specific traps: `useItemOn` returns `ItemInteractionResult` here (merged away in
  1.21.4+), `Screen.render` takes a float partial tick while `RenderLevelStageEvent.getPartialTick()`
  returns a `DeltaTracker`, and `StreamCodec.composite` caps at six components.
- **Server-authoritative.** A payload says what the player *wants*. The server decides what happens,
  re-deriving everything from its own state. Never trust a position, a name, or the fact that the
  client thought a button should be clickable.
- **Client code lives only under `client/`**, guarded by `Dist.CLIENT`. Common code must never
  reference `net.minecraft.client.*` or a dedicated server crashes on load. `./gradlew runServer` is
  the only way to prove this still holds.
- **Never touch `Level`, `BlockState` or entities off the main thread.** Snapshot to plain arrays
  first. `scan/FloorAnalyzer` imports no Minecraft classes at all — that is deliberate, and it is what
  makes "this cannot touch the world" checkable rather than merely asserted.
- **Block-agnostic.** No hardcoded lists of vanilla blocks anywhere. The scanner asks a block what it
  *does* — full collision, sturdy top face, occlusion — so modded blocks work in packs nobody tested.
- **Two population layers.** Tens of thousands of virtual residents, ~100 physical NPCs. Never one
  entity per citizen. Simulation cost is O(buildings), never O(population).

## Things that are easy to get wrong

- **Capacity comes from usable floor cells, not bounding volume.** Housing is quantised into whole
  dwellings (16 cells each, 3 residents) because half an apartment houses nobody. The constants are
  fitted so a small house holds 6, an apartment block ~180 and a residential tower ~1800 — the three
  figures the original brief named. Changing them breaks all three at once.
- **Registrations are invisible.** A building is a box in server data with no blocks of its own. Any
  feature touching them needs a way to *see* and *delete* them, or you recreate the ghost-building
  trap: demolish the structure, and its registration silently keeps reserving the ground forever.
- **Static state must reset on server start and stop.** In single player the JVM outlives the
  integrated server, so a player quitting to the menu and opening another world inherits the previous
  world's caches. See `LivingCitiesServerEvents`.
- **Expensive work is deferred and rate limited, never per-block.** Laying a cable run places a
  hundred blocks in seconds; rebuilding the grid each time would be a hundred full walks.

## Verifying changes

```bash
cd livingcities
./tools/syntax-check.sh   # fast, no Minecraft needed; catches structural errors only
./gradlew build           # the real compile
./gradlew runClient       # launch and actually look at it
./gradlew runServer       # proves no client class leaked into common code
```

GitHub Actions compiles every push and uploads the jar. **Compiling proves nothing about whether a
button works** — most bugs in this project's history were valid code that did nothing. Run the game.

## Roadmap

Alpha 1 Foundation → **Alpha 2 Utilities** → Alpha 3 Living Streets → Alpha 4 Industrial Economy →
Alpha 5 Services → Alpha 6 Multiplayer Civilization → Alpha 7 Transport → Alpha 8 Infrastructure &
Disasters → Alpha 9+ Expansion.

Alpha 1 and 2 are complete. Alpha 3 is next: a pedestrian node graph, real commuting between
registered buildings, and time-of-day activity. Parts of it (`npc/`) were built early and work, but
`npc/PedestrianNetwork` is an intentionally empty seam — NPCs currently wander rather than commute.

## Commit style

Explain **why**, not what the diff already shows. Where a constant was chosen for a reason, say what
the reason was. Several decisions here look arbitrary until you know the number they were fitted to.
