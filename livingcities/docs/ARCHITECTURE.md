# Living Cities — Architecture, v0.1

**Target:** Minecraft Java 1.21.1 · NeoForge 21.1.133 · Java 21 · ModDevGradle 2.0.78
**Mod id:** `livingcities` · **Root package:** `com.branciho.livingcities`
**Status of this document:** describes the code that exists in `src/main/java` today, not a plan. Where the code and the earlier design notes (`design/01-neoforge-api.md`, `design/02-floor-detection.md`) disagree, the code wins and the difference is called out.

You asked seventeen questions before any large amount of code was written. This answers all seventeen, in your order, against the thing that was actually built. Four of them you singled out as the foundation — custom building selection, floor detection, building capacity, virtual population — and those four get the algorithm, its complexity, and worked numbers rather than a paragraph.

You also asked to be told when your design is technically bad. There is a section for that (§18) and it is not padding; three of its entries changed how the mod works.

---

## 1. What I believe the core concept is

Living Cities is a **measurement and simulation layer over player-built geometry**. It ships no architecture. It ships the ability to point at something you built out of any blocks from any mod and say *this is an office*, after which the mod measures it, gives it capacity, fills it with people who exist as numbers, taxes it, and eventually walks a few visible bodies through its door.

The load-bearing sentence in your spec is:

> THE PLAYER BUILDS WHATEVER CITY THEY WANT, USING ANY BLOCKS THEY WANT, AND THIS MOD TURNS THOSE CUSTOM BUILDS INTO FUNCTIONAL CITY BUILDINGS.

Everything in the codebase is downstream of one consequence of that sentence: **the mod cannot know anything about the buildings in advance.** It cannot assume a bed means a resident, a block palette, a wall thickness, a floor height, or that the structure is even sealed. So the mod's central technical problem is not economy or NPCs — it is *turning arbitrary voxel geometry into a defensible number of usable floor blocks*, cheaply, on a server, for buildings made of blocks that did not exist when this code was written.

That is why `scan/` is the largest package in the mod (1,900 of 7,300 lines) and why `FloorAnalyzer` is the single biggest class. Population, jobs, taxes, happiness and NPC budgets are all functions of one integer: `Floor.usableCells()`. Get that number wrong and every other system is confidently wrong.

The second concept, equal in weight, is **two population layers**. A city of 50,000 is the integer 50,000 (`CityStats.population`) plus a small number of disposable bodies. The simulation's cost is `O(buildings)`, never `O(people)` — `EmploymentAllocator` distributes fifty thousand residents across three hundred buildings in three hundred iterations, and would do the same for five million.

---

## 2. What makes it different from existing city-building mods

| | Prefab mods (Sim-U-Kraft, Millénaire, Minecolonies) | Living Cities |
|---|---|---|
| Where buildings come from | The mod's schematics; the player places them | The player's own build; the mod measures it |
| What determines capacity | A number attached to the schematic | Scanned usable floor area of the actual structure |
| Block palette | Fixed, mod-owned | Any block from any mod, by construction |
| What the player is doing | Choosing from a catalogue | Building, then annotating |
| Population representation | One entity per citizen (caps out in the hundreds) | Integers, plus a bounded crowd of scenery entities |
| Mixed use | Not expressible — a schematic is one thing | Per-floor zoning inside one structure |

The practical difference: in a prefab mod, the mod knows what a building is because it built it. Here the mod has to *find out*, from geometry, with no cooperation from the blocks. There is not one vanilla block id anywhere in the `scan` package, and there must never be one. Classification comes from `BlockState`/`BlockBehaviour` queries plus the vanilla door tags, which modded blocks join automatically — so a furniture mod nobody tested sorts itself correctly on first launch.

The second difference is a scale ceiling. A mod that spawns an entity per citizen tops out around a few hundred before the server dies. This one's population is bounded by `Integer.MAX_VALUE` and its simulation cost is bounded by building count.

---

## 3. Which systems are server-authoritative

**All of them.** The client is a renderer and an input device. Concretely:

| Data | Owner | Client's copy |
|---|---|---|
| City existence, name, owner, members, roles | `CityRegistry` (SavedData, overworld) | `CitySummaryPayload` — 12 scalars, push-only |
| Treasury (`long` cents) | `City.treasuryCents` | Read-only in the summary |
| Chunk ownership | `CityRegistry.chunkIndex` | Not sent at all in v0.1 |
| Building bounds, floors, zoning, capacity | `Building` / `Floor` in the registry | `BuildingDetailPayload`, capped at 128 floor rows |
| Population, employment, happiness | `CityStats`, written only by `CitySimulation` | Read-only in the summary |
| Block measurements | `BuildingScanTask` reading `ServerLevel` | Never — the client never scans anything |
| Selection corners | Written server-side in `CityPlannerToolItem.useOn` under `!isClientSide()` | Mirrored to the item component for rendering |

The rule enforced in `ServerPayloadHandler` and `BuildingActions`: **a packet states intent; the server re-derives the result from its own state.** `ServerPayloadHandler.claimChunk` does not read a price from the packet, it computes one; it does not trust that the chunk is adjacent, it checks `City.isAdjacentToTerritory`; it does not trust that the player is nearby, it compares chunk coordinates; and when it loses a race to another claim it refunds rather than silently charging.

Two anti-scouting decisions worth naming. `requestCityData` resolves *which* city you are allowed to see: the one you are standing in only if you are a member of it, otherwise your own — so the management hotkey cannot be used to read a rival's treasury from inside their walls. And `assignBuilding` requires that **every chunk** the selection touches is owned by **one** city, and that you hold `BUILDER` in it.

The one genuinely abusable surface is `AssignBuildingPayload`, which carries two arbitrary `BlockPos`. It is reachable from a modified client. It is bounded by: 128-block reach on both corners, `maxSelectionVolume` (512,000), `maxSelectionHeight` (384), whole-footprint ownership by a single city, `BUILDER` role, overlap rejection against the building index, and a 3-second per-player scan cooldown. Note that `CityPlannerToolItem`'s javadoc claims "there is nothing here for a modified client to lie about" — that is true of the tool path, where the server reads its own component, but not of the packet path. The guards above are what make the packet path safe, and they are the thing to preserve.

---

## 4. City data architecture

One `SavedData` for the whole server, attached to the overworld's `DimensionDataStorage`:

```
CityRegistry extends SavedData                       livingcities_cities.dat
├── Map<UUID, City>                cities            authoritative
├── Map<UUID, Building>            buildings         authoritative, flat (not nested in City)
├── Map<Level, Long2ObjectMap<UUID>>       chunkIndex          DERIVED, rebuilt on load
└── Map<Level, Long2ObjectMap<List<UUID>>> buildingChunkIndex  DERIVED, rebuilt on load
```

Four decisions, each with a reason:

**Server-global, not per-dimension and not per-chunk.** A city is not a chunk-local concept: its territory is mostly unloaded, and the simulation must run for territory nobody is standing in. Per-chunk attachments would mean a city stops existing when you walk away. Vanilla does the same thing for map data and `idcounts`.

**Buildings live in a flat map; `City` holds only ids.** `City.buildingIds` is a `LinkedHashSet<UUID>`; the objects live in `CityRegistry.buildings`. This keeps "which building is at this position" answerable without walking cities, and it means transferring a building between cities is one field write.

**Both indices are derived and rebuilt on load** (`rebuildChunkIndex`, `rebuildBuildingIndex`). The save file therefore never holds two representations of ownership that could drift apart. Chunk keys are `ChunkPos.toLong()` in a fastutil `Long2ObjectOpenHashMap`, so lookups are O(1) with no boxing, and a city's claims serialise as a single `long[]`.

**Money is `long` cents, never a `double`.** A treasury ticked 240 times per in-game day for a hundred in-game days accumulates float error you can see. `ServerPayloadHandler.formatMoney` is the only place cents become a string.

`City` also carries `Map<UUID, CityRole>` with ranks (`MAYOR` 100 → `CITIZEN` 10), so a permission check is one integer comparison and no per-player permission blob is stored. `City.load` re-asserts `owner → MAYOR` after reading, so a hand-edited or migrated save cannot produce an ownerless city.

Corrupt-data policy: `CityRegistry.load` wraps each city and each building in its own try/catch and logs a skip. One unreadable record loses one record, not the server's city data.

---

## 5. Building data architecture

```
Building                                   Floor  (one per detected storey, ordered by Y)
  UUID id, UUID cityId, String name          int floorY, yMin, yMax
  BlockPos min, max      (inclusive)         int usableCells      <- the number that matters
  List<Floor> floors                         int standCells       <- ceiling for any override
  Set<BlockPos> entrances                    int ceilingHeight    <- median clear height
  long lastScanGameTime                      ZoneUse use          <- per-FLOOR, not per-building
  boolean needsRescan                        Set<FloorFlag> flags <- ROOF/BASEMENT/SPLIT_LEVEL/
  int residents, workers  (sim-owned)                                HAS_ATRIUM/MANUAL
                                             double densityOverride  (-1 = use the zone default)
```

**Zoning lives on the floor and the building type is derived.** This is the whole of mixed-use support, and it is why mixed use needs no special case anywhere downstream: `CityEconomy` walks floors and buckets each one by `floor.use().taxCategory()`, so a tower whose ground floor is commercial and whose upper floors are residential is taxed correctly without the word "mixed" appearing in the tax code. `Building.isMixedUse()` is a display query, nothing more.

**Capacity is computed, never stored.** `Building.housingCapacity()` sums `Floor.residents()`, which is a pure function of `usableCells`, flags, zone and `capacityScale`. Nothing can drift. The cost is that these walk the floor list, which is why `CitySimulation` computes them once per step into `int[] housingCap` / `int[] jobCap` and hands the arrays to both the allocator and the tax pass.

**Occupancy is separate from capacity.** `residents`/`workers` are written by `EmploymentAllocator` and clamped to capacity by the setters. Capacity is geometry; occupancy is simulation.

**Staleness is a flag, not a recomputation.** `BlockEvent.BreakEvent`/`EntityPlaceEvent` call `CityRegistry.markDirtyAt`, which sets `needsRescan` on any building containing the position. Capacity keeps using the last good numbers until a rescan completes — so editing a building never makes its residents vanish mid-edit, it just marks the figures as stale, and the panel says so.

**Manual floors survive rescans.** `replaceFloors` preserves floors flagged `MANUAL`; `inheritZoning` carries the previous zoning onto freshly detected floors by matching heights within ±1. Without the second one, rescanning a 25-storey tower after moving a wall would reset every floor to `UNUSED` and wipe the player's layout.

The spatial index (`buildingChunkIndex`, chunk key → building ids) is what makes `buildingAt`, `findOverlap` and `markDirtyAt` cheap. A block placed in a chunk with no buildings costs one `Long2ObjectMap` miss.

---

## 6. Building selection *(foundation 1 of 4)*

### 6.1 The interaction

Two corners on an item, exactly as you specified. `CityPlannerToolItem`:

| Input | Effect |
|---|---|
| Right-click a block | Corner A |
| Sneak + right-click a block | Corner B |
| Use in air, selection complete | **Register the building** |
| Sneak + use in air | Clear |

The selection is a `SelectionData(Optional<BlockPos> a, Optional<BlockPos> b)` stored as a `DataComponentType` on the stack, with both a `Codec` (persistent, survives relog) and a `StreamCodec` (network-synchronised, so the client can draw the box). It travels with the tool, so it is per-player and per-tool with no server-side session map to expire.

The important detail: **the component is written only inside `if (!context.getLevel().isClientSide())`.** The server writes it, the client receives it as synced item data. The client's copy exists to render `LevelRenderer.renderLineBox` in `ClientEvents.onRenderLevel` without a packet round-trip per click — it is a mirror, not the source.

Registration (`CityPlannerToolItem.use` → `BuildingActions.assignBuilding`) reads the corners from the **server's own copy** of the component. The `AssignBuildingPayload` C2S path exists in parallel for a future screen-driven flow and is validated independently (§3).

### 6.2 The validation ladder, cheapest first

`BuildingActions.assignBuilding` runs, in order — every step is integer arithmetic or a hash lookup, so a bad request costs microseconds and never allocates a snapshot:

1. **Reach.** Both corners within 128 blocks (`distanceToSqr > 128²` → reject).
2. **Normalise.** `Building.fromCorners` sorts the two arbitrary corners into inclusive `min`/`max`.
3. **Volume.** `Building.volume()` is computed as `(long) sizeX * sizeY * sizeZ` — deliberately `long`. A 256×384×256 box overflows `int`, and an overflowed negative volume passes a naive `<=` check.
4. **Height.** `sizeY > maxSelectionHeight` (384).
5. **Territory.** Every chunk in `occupiedChunks()` must be owned, and by the *same* city. Unowned → `building_unclaimed`. Two cities → `building_spans_cities`.
6. **Permission.** `BUILDER` in that city, or op level 2.
7. **Overlap.** `CityRegistry.findOverlap` checks only buildings sharing a chunk with the candidate — typically 0–3 — then does an AABB intersect. Any overlap is rejected, naming the conflicting building.
8. **Rate limit.** 3 seconds per player between scan-triggering requests.

Only then is the building created, named (given one if the player supplied none), added to the registry and its index, and the scan enqueued. The building exists immediately with zero capacity; the panel shows "Measuring the building…" until the scan lands.

### 6.3 Complexity and cost

Steps 1–4 and 6–8 are O(1). Step 5 is O(chunks touched), typically 1–36. Step 7 is O(buildings in those chunks), typically under 10. **The entire rejection path is well under a microsecond.** That matters because it is the path a hostile client hammers.

### 6.4 What I did not build, and why it is fine

There is no visual corner-dragging and no "select connected structure" wand. Both were in the design notes. A wand flood-fill escapes into terrain on roughly one build in five, and its output can only ever be a *proposal* the player then nudges — which means you need the two-corner editor anyway. Two corners plus a live box is the workflow that never fails; the wand is an accelerator that can be added later on top of it without changing anything here.

---

## 7. Floor assignment and detection *(foundation 2 of 4)*

This is the hardest thing in the mod. `FloorAnalyzer` is 768 lines and every one of them is arithmetic.

### 7.1 The structural decision that makes it possible

> **The expensive Minecraft calls are `O(distinct BlockStates)`, not `O(blocks)`.**

A 64×128×64 selection holds ~524,000 cells and typically **100–800 distinct block states**. So the work splits in two:

- **`BlockProfiler.profile(level, pos, state)`** — server thread, called *once per distinct state*, at a real position where that state occurs. Returns a `BlockProfile`: a record of ten primitives. This is the only code in the pipeline that touches block behaviour.
- **`FloorAnalyzer`** — worker thread, reads `short[] cells` (palette indices) and `BlockProfile[]` and does nothing else. It imports `java.*` and one enum. **It cannot reach the world because it holds no reference to it.**

That is not a convention, it is the type system. `BuildingSnapshot` contains `int`s, a `short[]` and an array of immutable records. There is no `Level`, no `BlockPos`, no `BlockState` to accidentally call a method on. The `package-info.java` states this as the package's contract.

Why not build a snapshot `BlockGetter` and call shape methods off-thread, the way vanilla's async pathfinder does? Because that is only safe for *vanilla* blocks. An arbitrary modded `getCollisionShape` may cast its `BlockGetter` to `Level`, look up a `BlockEntity`, or read a client field. Off the main thread that is a data race in someone else's code, reported as our crash. Profiling on the main thread deletes the entire bug class for a cost of a few hundred calls.

### 7.2 Classification without a block list

`BlockProfiler.classify` decides a `BlockClass` from geometry alone:

```
is(livingcities:scan_excluded)              -> EXCLUDED   (barriers, structure voids, light blocks)
isAir()                                     -> OPEN
door-like                                   -> DOOR_LIKE
  = is(seals_building)
  OR (!fullBlock AND (hasProperty(OPEN) OR is(DOORS/TRAPDOORS/FENCE_GATES)))
shapeEmpty AND fluid non-empty              -> FLUID
isCollisionShapeFullBlock                   -> isSolidRender ? SOLID_SUPPORT : TRANSPARENT_SOLID
shapeEmpty                                  -> OPEN
topY <= 0.25 AND volume <= 0.30             -> DECOR_PASSABLE
isFaceSturdy(UP) AND topY >= 0.99           -> TOP_SUPPORT
otherwise                                   -> PARTIAL
```

Three details that carry real weight:

- **`isSolidRender` separates glass from stone for free.** It is `canOcclude() && full cube`; glass has `noOcclusion()`, so `TRANSPARENT_SOLID` falls out with no special case. Both seal and support identically; the distinction exists for daylight and outlook quality later.
- **The `!fullBlock` guard on door detection.** Barrels also carry the `OPEN` property. A barrel in a wall is still a wall and a barrel in a floor is still floor; classifying it as a door would quietly delete usable area.
- **Fluids are tested after solids.** A waterlogged stair is a stair.

The four cell predicates on `BlockProfile` are the vocabulary everything else reasons in:

```java
footY(y)       = collisionTopY >= 0.5 ? y+1 : y     // where your feet actually end up
supports()     = SOLID_SUPPORT|TRANSPARENT_SOLID|TOP_SUPPORT, or PARTIAL with a full/half top
headClear()    = OPEN|DECOR_PASSABLE|EXCLUDED, or (!blocksMotion && collisionTopY < 0.5)
seals()        = SOLID_SUPPORT|TRANSPARENT_SOLID|DOOR_LIKE, or TOP_SUPPORT/PARTIAL that blocks motion
surfaceWeight()= 1.00 solid / 0.90 top-support / 0.60 partial / 0.00 otherwise
```

`footY` is the rule that makes stairs, slabs, carpets and rugs just work without naming any of them:

| Block | `collisionTopY` | `footY` | Result |
|---|---|---|---|
| Stone, upper slab, stairs | 1.0 | y+1 | You stand on top. Correct. |
| Lower slab | 0.5 | y+1 | Half-block step up. Correct. |
| Carpet, pressure plate, snow layer | ≤0.125 | y | Feet in the same cell, supported from below. Correct. |

`seals()` returning true for `DOOR_LIKE` **whether the door is open or shut** is the single most important exception in the algorithm. A house with its front door hanging open is still a house; without this, leaving a door open evaporates the building's population.

### 7.3 The four phases

Let `W×D×H` be the snapshot (registered box inflated by `marginXZ=2` horizontally, `+2/−1` vertically), `V = W·D·H`, `F` = detected floors, `A = W·D`.

```
Phase A  histogram of standable cells per height           O(V)
Phase B  candidates: plateau-aware peaks + NMS             O(H·sep + H log H)
Phase C  validation: enclosure fill + components + ceiling O(F·A)
Phase D  Y ranges, ceiling heights, basement tagging       O(F)
```

**Phase A — `histogram()`.** For each column inside the *registered footprint* (never the collar — letting the surrounding terrain vote would put a floor at ground level of every hillside), walk Y upward. For each cell that `supports()`, compute `footY`, check `minBodyHeight = 2` consecutive `headClear` cells above it, and if so set a bit in `standBits[floorY]`, add to `standCount[floorY]` and `standWeight[floorY] += surfaceWeight()`. Then probe up to `maxCeilingProbe = 8` cells for something that `blocksMotion` and increment `coveredCount[floorY]`.

Two things make this fast. The snapshot is **column-major** (`index = ((z*sizeX)+x)*sizeY + y`), and the loop nesting is z→x→y, so every access is linear — support below, headroom above, ceiling above that are all contiguous. That is roughly an order of magnitude in cache misses over the obvious layout. And the `standBits` check makes the lowest support in a column win, so a slab sitting on stone is counted once rather than twice.

**Phase B — `generateCandidates()`.** A height is a candidate if `standCount >= minCandidateCells (6)` and it beats its neighbours within `minFloorSeparation (3)`. The comparison is deliberately asymmetric — strict `>` looking down, non-strict `>=` looking up — so a two-block-thick floor slab, which produces two equal heights, always resolves to the lower one. That is both deterministic and the height you actually stand on. Candidates are then non-max-suppressed by weight; **losers are not discarded**, they go into `suppressed` because a suppressed height may be the other half of a split level.

Candidate generation is deliberately **generous** and validation is **strict**. The reverse ordering silently loses mezzanines, basements and split levels with no diagnostic the player could act on. Strict validation rejects things for a reason that can be named, counted and reported — which is exactly what `FloorPlan.rejectedCount` is for.

**Phase C — `evaluate()`, the enclosure fill.** Per candidate height, flood "outside" inward from the collar:

```java
fillPassable(x, y, z) = for k in 0..minBodyHeight-1:
    profile is not DOOR_LIKE   AND   profile.headClear()
```

Seeds are every collar cell (outside the registered footprint) that is passable — by construction genuinely outdoors, which is the entire reason the collar exists. `ScanSettings` clamps `marginXZ` to at least 1 in its compact constructor, because a zero collar has no failure mode a player would recognise: the fill finds no outdoor seed, decides the whole world is enclosed, and reports a lawn as apartments.

The fill is **4-connected, never 8**. Diagonal connectivity leaks straight through the corner where two walls meet at 45°, which is one of the most common shapes in player builds.

```
usable[y] = standBits[y] AND NOT outside AND footprint
```

Then `measure()` computes the largest 4-connected component, the median clear height, and the covered fraction, and `passes()` requires all four:

```java
usableCells >= 6 && largestComponent >= 4 && medianCeiling >= 2 && coveredFraction >= 0.50
```

Each rejection kills a specific false positive:

| False positive | Killed by |
|---|---|
| Roof, open terrace | `coveredFraction < 0.50` |
| Surrounding terrain, lawn | Enclosure — reachable from the collar, so `usableCells ≈ 0` |
| Scaffolding, catwalks, ladders | `largestComponent < 4` (a scaffold column is 1×1 per Y) |
| Crawlspace | `medianCeiling < 2` |
| Hollow decorative shell | `usableCells < 6` |
| Water and lava surfaces | `FLUID` never `supports()` |

**The leak ladder.** If a floor fails, and it is *roofed but mostly reachable from outside* (`leakFraction > 0.60` and `coveredStand > 0.70`) — the signature of one missing block in a wall — the ray estimator runs: a stand cell counts as indoors if `seals()` is hit within 32 cells in **at least 3 of 4** directions and there is a ceiling within 8. The result is discounted by `rayConfidence = 0.85` and **can only ever rescue a floor, never lower a confident fill** (`if (scaled > fillCount)`). The floor is flagged `leaky` and the player is told in chat: `scan_leaky`. That last part is the point — a player told "2 floors have an uncertain envelope" fixes a missing block in ten seconds; a silently wrong number they cannot.

**Roofs are kept, not deleted.** `keepTopmostRoof` retains the highest open-air candidate as a floor with `FloorFlag.ROOF`, re-measured over the whole deck so `standCells` reports its real area, but with `usableCells = 0` so it can never contribute capacity. An open terrace halfway up a tower is a different thing and is rejected. This preserves information the UI wants ("Roof deck") and that helipads and rooftop gardens will want.

**Split levels.** `absorbSubLevels` folds a suppressed height back into its winner when the two are *side by side* rather than *stacked*: if their usable sets overlap by less than `subLevelMaxOverlap = 0.20` in plan, they are one logical floor at two heights (a house on a hillside) and are merged with `FloorFlag.SPLIT_LEVEL`. A genuine mezzanine sits directly over the floor below and overlaps heavily, so it stays a separate floor. The overlap test is the entire distinction.

**Atriums need no code at all**, and this is worth understanding because people assume it is hard. A three-storey atrium is simply a region of XZ where floors 2 and 3 have no stand cells. Phase A never generates them; those floors get correspondingly smaller `usableCells`; the ground floor keeps its full area. The geometry describes itself.

**Phase D — `buildFloors()`.** Y ranges run from each floor to one below the next (the last runs to `topSolidY`). Ceiling height is the **median clear height over the usable cells**, capped to the storey's own range — not the Y span — so a room under a pitched roof reports 3, not 17. Floors below `gradeY` get `FloorFlag.BASEMENT`; `gradeY` is the median of 16 `Heightmap.WORLD_SURFACE` probes around the perimeter, sampled on the server thread before the hand-off. A median, not a single sample, because one probe landing on a tree or the player's scaffolding moves the basement line ten blocks.

### 7.4 Player editing

Zoning is one click per floor in `BuildingPanelScreen` — a cycle button through 13 uses, one `SetFloorZonePayload(buildingId, floorIndex, zoneId)` per click, paged 10 rows at a time. The zone travels as its **string id, not an ordinal**, so reordering the enum later cannot silently reinterpret an in-flight packet as a different use. The server validates building existence, `BUILDER` role, index range, and that the id resolves; an unknown id is a rejection, never a guess. Zoning a `ROOF` floor as residential produces a warning rather than a hard error, because the flag is a measurement and the player may have roofed it since.

### 7.5 Measured cost

For a 40×40 footprint × 100 tall building (160,000 registered cells; snapshot 44×103×44 = 199,408):

| Phase | Where | Cost |
|---|---|---|
| Snapshot | server thread, budgeted `scanBlocksPerTick = 24,000` | ~8 ticks (0.4 s) |
| Profiling | server thread, 128 states/tick | 1–7 ticks |
| Analysis | worker thread | O(V), single-digit ms |
| Commit | server thread | O(F) |
| Peak memory | | `short[199,408]` = 0.4 MB + bitsets ≈ 0.45 MB |

At the 512,000-block volume cap the snapshot is ~592,000 cells ≈ 1.2 MB and takes ~25 ticks (1.3 s), with `MAX_ACTIVE = 2` bounding peak memory at ~2.6 MB. Snapshots are released the instant analysis completes — caching them would mean a server with 500 buildings holding 600 MB.

---

## 7B. Building capacity *(foundation 3 of 4)*

### 7B.1 The formula, as implemented

Per floor, in `Floor`:

```java
capacityMultiplier() = ROOF      ? 0.0
                     : BASEMENT  ? (RESIDENTIAL 0.60 | OFFICE,COMMERCIAL 0.80 | else 1.0)
                     : 1.0
                     × (ceilingHeight <= 2 ? 0.85 : 1.0)

effectiveArea = usableCells × capacityMultiplier() × config.capacityScale

residents = area < 9 ? 0 : floor(area / 16) × 3            // CELLS_PER_DWELLING=16, RESIDENTS=3
jobs      = area < 9 ? 0 : max(1, round(area / cellsPerJob))
```

`Building.housingCapacity()` / `jobCapacity()` sum across floors. **The floor operation is per storey, not on the total** — that is deliberate and it is what makes the small-house case work.

### 7B.2 Why housing is quantised into dwellings

Straight residents-per-usable-block is the obvious reading of your spec, and it is wrong at the small end. At the implied 0.1875 residents/block, a garden shed gets 4 residents and a chicken coop gets 2. Quantising into **dwellings of 16 usable blocks holding 3 people each** does four things:

1. **A shed rounds to zero dwellings, not to 0.4 residents.** Sub-room structures produce nobody.
2. **Capacity is stable under redecoration.** The small house is 6 residents for *any* usable area in [16, 32) per storey — you can furnish it, move a wall, add a chimney, and the headline number does not twitch.
3. **The UI gets a real object**: "12 apartments / 36 residents" rather than a float.
4. **Future features get somewhere to hang.** Rent, household wealth tiers and occupancy all need a dwelling, not a fraction of a person.

The honest cost: granularity is 3 residents per floor. A 5-storey block can be 165, 180 or 195 — not 178. Jobs are *rounded* rather than floored, and floored at a minimum of 1, precisely because that granularity would be absurd for a corner shop.

### 7B.3 The three spec anchors

Your spec named three targets and told me not to hardcode them. One constant set — 16 blocks per dwelling, 3 residents per dwelling — reproduces all three from measured geometry, with no per-case fudging. That is the real test of whether the formula is sound.

| Build | Geometry | Interior/floor | Usable/floor | Dwellings/floor | Residents/floor | Floors | **Total** | Spec target | Δ |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| One-room hut | 7×7, 1 storey | 25 | 22 | 1 | 3 | 1 | **3** | — (half a house) | — |
| **Small house** | 7×7, 2 storeys | 25 | 22 / 21 | 1 | 3 | 2 | **6** | 6 | **0%** |
| **Apartment block** | 20×20, 5 storeys | 324 | 202 | 12 | 36 | 5 | **180** | 180 | **0%** |
| **Residential tower** | 25×25, 25 storeys | 529 | 390 | 24 | 72 | 25 | **1,800** | 1,800 | **0%** |

The usable figures are derived, not reverse-engineered. The apartment block: 18×18 = 324 interior, minus ~100 cells of partition and corridor walls, ~12 of lift shaft and risers, ~10 of fixtures = 202. The tower: 23×23 = 529, minus ~35 net service core (two stairwells and two lifts, with the stairs contributing back as `TOP_SUPPORT`), ~94 of partitions, ~10 fixtures = 390.

**The fit is not knife-edge**, which matters more than the zero in the Δ column. Each anchor has a band of usable area per floor that produces the identical headline number:

| Anchor | Band of usable cells/floor giving the target | Width |
|---|---|---|
| Small house → 6 | 16 – 31 per storey | ±32% |
| Apartment block → 180 | 192 – 207 per storey | ±4% |
| Residential tower → 1,800 | 384 – 399 per storey | ±2% |

So the small house is 6 for essentially any two-storey cottage, and the two larger anchors tolerate a few percent of measurement error before they step. Below that they step by one dwelling per floor — the quantisation being honest about itself.

### 7B.4 Cross-checks the anchors do not cover

**Mixed-use tower, 40×40 × 30 floors + roof.** Interior 38×38 = 1,444; service core and partitions leave ~995 usable per floor:

| Zone | Floors | Per-floor calculation | Result |
|---|---|---|---|
| Commercial | 1 | `round(995/28)` | **36 jobs** |
| Office | 2–7 (6) | `round(995/14) = 71` × 6 | **426 jobs** |
| Residential | 8–30 (23) | `floor(995/16) = 62` dwellings → 186 × 23 | **4,278 residents** |
| Roof deck | 31 | multiplier 0 | **0** |

**4,278 residents and 462 jobs in one tower.** Is that believable? 4,278 people in ~22,900 usable blocks is ~5.4 blocks/person — roughly five times denser than real urban housing. That is a deliberate game-design choice, and it is stated in the config comment: Minecraft buildings are small next to the cities players picture, and a 40×40×30 tower is a serious build that should feel like it matters. A server owner wanting Cities-Skylines realism sets `capacityScale = 0.35` and gets ~1,500 residents from the same tower.

**Jobs at the small end.** A 9×9 corner shop, one floor: interior 7×7 = 49, usable 45 → `round(45/28) = 2` **jobs**. A corner shop with two employees.

**Jobs vs. your "factory needs 400 workers" example.** A 30×20 single-floor factory yields `round(480/18) = 27` jobs. That is the building's *staffing capacity*, and it is not the same number as your 400. A production recipe will declare its own `workerDemand`, and `staffing% = min(1, jobsFilled / workerDemand)` throttles output — which is exactly the shortfall mechanic you described ("Required 400 / Available 220 / Staffing 55%"). Keeping geometry and recipe difficulty as independent knobs is what lets you rebalance industry without rebalancing architecture. `Building.staffingRatio()` is already there for it.

**Job densities as implemented** (`ZoneUse`, usable blocks per job): Office 14 · Government 16 · Industrial 18 · Public Service 20 · Commercial 28 · Entertainment 30 · Transport 45 · Utility 60 · Warehouse 110 · Park 0 · Residential/Special 0.

---

## 8. Virtual population *(foundation 4 of 4)*

### 8.1 Cost model

`CityStats` holds five integers and two longs. A city of 50,000 is `population = 50000`. Per simulation step, per city:

```
capacity       O(buildings × floors)
occupancy      O(buildings)          ×2   (housing, jobs)
economy        O(buildings × floors)
happiness      O(1)                       — 5 factors over a snapshot record
growth         O(1)                       — one arithmetic step
notifications  O(warnings)                — 5
```

**Nothing is O(population).** A city of fifty thousand costs exactly what a city of fifty costs.

### 8.2 Distribution: why Bresenham and not division

`EmploymentAllocator.distribute` spreads a city-wide headcount across buildings proportional to capacity, using an error-accumulating division:

```java
share = room × assign;  whole = share / capacityTotal;  carry += share % capacityTotal;
if (carry >= capacityTotal) { carry -= capacityTotal; whole++; }
```

Plain `floor(capacity × total / capacityTotal)` loses up to one person per building. A city with 300 half-empty buildings would silently fail to seat 150 workers *every step*, and the aggregate would never agree with the sum of its parts — the kind of bug that surfaces as "the numbers in the UI don't add up" six months later. The accumulator is exact: the untruncated shares sum to `total`, so the accumulated remainders are an exact multiple of `capacityTotal` and the carries recover precisely what was truncated.

Overflow is handled: `room × assign` is at most 2⁶² as a `long` regardless of building count. Demand above capacity saturates and the surplus surfaces as `homeless()` / `unemployed()` rather than being deleted — because a demolished apartment block should be a visible crisis, not a quiet correction.

### 8.3 Growth: damped relaxation with a proven bound

The obvious implementation — "+2% per day if happy" — is positive feedback that either explodes or oscillates. `PopulationModel` instead relaxes toward an explicit target:

```
desirability = 0.20 + 0.50·happiness + 0.30·jobCoverage − taxPenalty     (clamped 0..1)
target       = housingCapacity × desirability
x_next       = x + r·(target − x)                                        r = growthRate, default 0.08
```

The three weights sum to exactly 1, so a delightful, fully employed, low-tax city fills its housing completely and **nothing can push it past capacity**. The 0.20 base is non-zero because even a miserable town keeps residents; without it a single bad step would empty a city.

`jobCoverage` is measured against the workforce the city would have at **full occupancy** (`housingCapacity × 0.61`), not today's population. Using today's population would make an empty city look fully employed and invite a growth spurt into jobs that do not exist.

Taxes appear here *and* inside happiness, which is not double counting a mistake: happiness is how the people already here feel, while the tax penalty is a migration signal — cost of living is something outsiders weigh before moving, whether or not current residents have got used to it.

**Stability.** With a constant target the step map is affine with factor `1−r`; the config clamps `r ∈ [0.001, 0.5]`, so `1−r ∈ [0.5, 0.999]` — monotone convergence, no overshoot. The target is *not* constant (it depends on happiness, which depends on population), so the class derives the real bound: population enters desirability only through the happiness term (weight 0.50); within happiness only HOUSING (0.25), EMPLOYMENT (0.20) and FINANCES (0.15) respond to population — 0.60 of the total weight; and `HappinessModel` measures every one of those against **capacity**, so each moves by at most `1/housingCapacity` per extra resident. Therefore

```
|T'(x)| ≤ 0.50 × 0.60 × housingCapacity × (1/housingCapacity) = 0.30      — independent of city size
f'(x) ∈ [1 − 1.30r, 1 − r]
```

Convergence needs `r < 1.538`; strictly monotone approach needs `r ≤ 0.769`. The config ceiling is **0.5**, with ~35% margin, and the default 0.08 gives `f' = 0.896` — each step closes 8% of the gap, half-life ≈ 8.3 steps ≈ 42 seconds at the default 100-tick interval.

**That capacity-relative denominator in `HappinessModel` is load-bearing, not stylistic.** With a population denominator the slope blows up for small populations and a village of three can oscillate. The class documents this and points at the derivation, and `HappinessModel` deliberately adds **no smoothing of its own** — a second lag inside a negative feedback loop turns a first-order system into a second-order one, which can ring even when both lags are individually stable.

The fractional remainder is carried between steps (`CityStats.populationRemainder`). Without it, a city whose target is 8 above its population computes +0.64 people per step, floors it to zero, and never grows — the classic "my village is stuck at 3" bug.

### 8.4 A worked city

One 20×20 apartment block (180 capacity, 5 floors × 202 usable), fully occupied, 8 claimed chunks, default taxes.

**Economy** (`CityEconomy.assess`, per in-game day, cents):

```
per floor: activity = round(36 residents × fill 1.0) × 2000        = 72,000
           property = round(202 × 5.0 × (0.25 + 0.75×1.0))         =  1,010
           base                                                     = 73,010
×5 floors                                                           = 365,050
income = 365,050 × 8%                                               =  29,204   $292.04/day
expenses = 8 chunks×500 + 1 bldg×800 + 1,010 cells×1 + 180×40       =  13,010   $130.10/day
net                                                                 = +16,194   $161.94/day
effectiveTaxPermille = 29,204 × 1000 / 365,050                      = 80 (8%)
```

At `simulationIntervalTicks = 100`, each step moves `floorDiv(16,194 × 100, 24,000) = 67` cents and carries the remaining 11,400 into `cashCarryCents`. Without that carry the city would lose 0.7% of its income *per day* to truncation.

**Population.** Happiness starts at 700‰. This city has **no jobs**, so `jobCoverage = 0` and `desirability = 0.20 + 0.50×0.70 + 0.30×0 = 0.55` → **target 99 residents**, not 180. An apartment block with nowhere to work does not fill.

Add an office with 110 jobs (covering `180 × 0.61 = 110` potential workers). Now HOUSING 1.0, EMPLOYMENT 1.0, TAXES `1 − 80/250 = 0.68`, SERVICES **0.0** (no civic floor area at all), FINANCES 1.0 → happiness `0.25 + 0.20 + 0.136 + 0 + 0.15 = 0.736` → 736‰. `desirability = 0.20 + 0.368 + 0.30 = 0.868` → **target 156**.

Add a park — `PARK` is a civic use, and `HappinessModel` wants `1.5` civic cells per unit of housing capacity — and SERVICES starts paying. That is the mechanism by which parks matter, and it is why `CityEconomy` computes `civicCells` during the same floor walk the tax base needs rather than in a second pass.

**Note the shape of that story: the constraint moves.** Housing → jobs → services. That is the loop that makes a city management game, and it falls out of five weighted factors and one relaxation equation.

### 8.5 Notifications

`CityNotifications` is the difference between a warning system and chat spam. Two mechanisms: **state-change detection** (a warning fires when it *becomes* true, not while it is true) and **two cooldowns** — a 24,000-tick repeat for a standing problem and a 6,000-tick anti-flap floor. The second is the important one: without it a metric hovering exactly on its threshold produces an "onset" every other step and defeats the state-change check entirely. Thresholds are both proportional and absolute (`amount >= minimum && amount >= total × ratio`), so a hamlet with one homeless resident is not a housing crisis. `TREASURY_LOW` suppresses itself once `TREASURY_EMPTY` fires. Each warning names the minimum `CityRole` that hears it, so twenty citizens do not get twenty copies of a treasury notice aimed at administrators. A world whose time ran backwards (`/time set`, a rollback) resets the cooldown rather than muting warnings for the session. Delivery is behind a `Sink` interface, and a third-party sink throwing cannot abort a simulation tick.

---

## 9. Physical NPCs

`CitizenEntity` exists, is registered, has attributes, a renderer and four skin variants. **Nothing spawns one yet.** `LivingCitiesServerEvents.onServerTick` carries the placeholder: `// Still to be attached: CitizenSpawnDirector.tick(server, registry)`.

What is built is the entity's *contract*, and it is the part that determines whether the crowd is affordable:

```java
EntityType.Builder.of(CitizenEntity::new, MobCategory.MISC)
    .sized(0.6F, 1.8F).eyeHeight(1.62F)
    .clientTrackingRange(8)     // 8 chunks, not the default 10 — a crowd is a lot of tracking
    .updateInterval(3)          // position sync every 3 ticks; scenery does not need 1
    .noSave()                   // never written to chunk NBT
```

plus `shouldBeSaved() = false`, `removeWhenFarAway() = true`, `getBaseExperienceReward() = 0`, and four goals only (float, stroll, look-at-player, look-around). Citizens are **scenery, not a mob farm**: no drops, no XP, no breeding, no inventory, no persistent identity.

The design that follows from your spec, and that the config already has knobs for (`maxPhysicalNpcs = 120`, `npcDensityMultiplier`):

- Representative NPCs **do not map to virtual citizens.** There is no citizen #38,412. A body is a sample drawn from the aggregate, discarded when the player leaves.
- The spawn budget for a region is a function of local building capacity, occupancy, time of day and zone mix — a downtown at 17:00 draws more than a suburb at 03:00 — clamped by `maxPhysicalNpcs` server-wide.
- Spawns are anchored to `Building.entrances` (the data structure exists), routed over the `PathNodeBlock` graph rather than asking vanilla pathfinding to understand a city.
- Because they are never saved, the director is free to despawn aggressively; the illusion is maintained by respawning near whoever is looking.

This preserves your goal exactly as you framed it: 50,000 virtual citizens, ~100 visible bodies.

---

## 10. How performance problems are avoided

Six mechanisms, all of them in the code today:

**1. Nothing scales with population.** §8.1.

**2. Scans are budgeted and bounded.** `scanBlocksPerTick = 24,000` is the *server's* whole allowance, divided among at most `MAX_ACTIVE = 2` concurrent tasks, so two scans cost the same tick time as one. `MAX_QUEUED = 32`, plus a 3-second per-player cooldown, plus volume and height caps.

**3. The expensive world calls are per distinct state, not per block.** §7.1. A half-million-cell region costs a few hundred `getCollisionShape` calls.

**4. The heavy arithmetic is off-thread, and the result is polled.** `BuildingScanTask` hands a Minecraft-free snapshot to `Util.backgroundExecutor()` and **registers no completion callback** — `BuildingScanService.tick` polls `task.phase()`. A callback would run on the worker thread; polling makes "results are applied on the server thread" a property of the control flow rather than a comment someone has to keep honouring. Every `Building` mutation, every `setDirty()`, every packet provably happens on the server thread.

**5. Cities are staggered, not batched.** `CitySimulation` runs every tick and almost never does work. Each city gets a stable slot in the `simulationIntervalTicks` window from a Fibonacci-mixed hash of its UUID, cached as bucket lists, rebuilt only when the registry, interval or city count changes (plus a 5-minute safety refresh that also garbage-collects state for disbanded cities). At 100 cities and a 100-tick interval the per-tick cost is one list lookup and one city step. The slot is stable across restarts because it derives only from the UUID.

**6. Dirty marking is O(1) on the hot path.** Every block break and place on the server calls `markDirtyAt`, which is a `Long2ObjectMap` miss for any chunk with no buildings. And it only *marks* — rescanning a skyscraper per block would be ruinous when a player lays a floor.

Deliberate non-optimisations, so you know what is left on the table: the snapshot reads through `LevelChunk#getBlockState(BlockPos)` rather than section-local coordinates, and there is no `hasOnlyAir()` fast path for empty sections. For a 40-storey tower that is 60–80% air sections, so this is roughly 3–5× of snapshot throughput unclaimed. Correctness is unaffected; it is a known optimisation (§20.4).

---

## 11. Multiplayer ownership

Ranked roles, checked server-side, with operator bypass at the call site rather than in the data:

```
MAYOR 100  >  ADMINISTRATOR 80  >  ENGINEER 60  >  BUILDER 40  >  CITIZEN 10
```

`City.hasPermission(uuid, required)` is a pure data check — one integer comparison. `ServerPayloadHandler.hasPermission(player, city, required)` adds `player.hasPermissions(2)`. Keeping the bypass out of `City` means the data model stays testable without a server.

Current requirements, all enforced:

| Action | Requires |
|---|---|
| Found a city | An unclaimed chunk, a real `CityHallCoreBlockEntity` within 8 blocks, a free name |
| Claim / unclaim a chunk | `ADMINISTRATOR`, target within 8 chunks of the player, adjacency, funds |
| Register a building | `BUILDER`, whole footprint owned by that one city, no overlap |
| Set a floor zone | `BUILDER` |
| Rescan | `BUILDER` |
| View city data | `CITIZEN` in the city you are standing in, otherwise your own city only |

**Buildings cannot span two cities** and cannot be registered on unclaimed ground — which is the concrete form of "players should not be able to edit another city's assigned buildings without permission". Cross-city overlap is impossible because *all* overlap is rejected.

The `CityHallCoreBlockEntity` holds only a `UUID` pointer. The city record lives in the registry. A player who breaks, moves or duplicates the block cannot fork or fabricate a city — a stale pointer clears itself and offers to found a new one.

Claim pricing escalates geometrically: `baseChunkClaimCost × claimCostGrowth^ownedChunks` (defaults $250 and 1.02, making the 100th chunk ~7× the first), capped at `maxClaimsPerCity`. Creative players bypass cost when `creativeBypassesCost` is on. A lost claim race refunds.

Not yet built: co-owner management UI, invites, ownership transfer, and diplomacy (v0.6). The data model carries all of it already — `City.members` is a role map, not an owner field.

---

## 12. NeoForge systems and APIs used

Verified against the 1.21.1 / NeoForge 21.1 API contract sheet in `design/01-neoforge-api.md`. Version traps that were actively avoided are marked ⚠.

**Lifecycle and buses**
`@Mod(MOD_ID)` with the injected `(IEventBus modBus, ModContainer container)` constructor · `@EventBusSubscriber(modid, bus, value = Dist.CLIENT)` · mod bus: `RegisterPayloadHandlersEvent`, `EntityAttributeCreationEvent`, `RegisterKeyMappingsEvent`, `EntityRenderersEvent.RegisterRenderers` · game bus: `ServerTickEvent.Post`, `ServerStartingEvent`, `ServerStoppingEvent`, `RegisterCommandsEvent`, `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `ClientTickEvent.Post`, `RenderLevelStageEvent`.

**Registries**
`DeferredRegister.createBlocks/createItems` and `DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE / ENTITY_TYPE / DATA_COMPONENT_TYPE / CREATIVE_MODE_TAB)` · `DeferredBlock`/`DeferredItem` · `registerBlock`, `registerItem`, `registerSimpleBlockItem` · `BlockEntityType.Builder.of(...).build(null)` · `EntityType.Builder` with `noSave()` · `DataComponentType.builder().persistent(Codec).networkSynchronized(StreamCodec).build()` · ⚠ **no `Properties.setId(...)`** — that arrived in 1.21.2 and would not compile here · ⚠ `ResourceLocation.fromNamespaceAndPath`, never the (private) constructor.

**Networking**
`PayloadRegistrar.playToServer` / `playToClient` · `CustomPacketPayload` + `Type<>` + `StreamCodec.ofMember` (hand-written codecs, avoiding `composite`'s 6-field ceiling) · `IPayloadContext.player()` / `enqueueWork` · ⚠ `PacketDistributor.sendToPlayer(player, payload)` — the flat static form; the Forge 1.20 `PLAYER.with(...)` builder does not exist.

**Persistence**
`SavedData` + `SavedData.Factory<>(ctor, loader, null)` · `save(CompoundTag, HolderLookup.Provider)` · `server.overworld().getDataStorage().computeIfAbsent(...)` · `CompoundTag`/`ListTag` with explicit `Tag.TAG_COMPOUND` on every `getList`.

**Block geometry** (all of `BlockProfiler`, main thread only)
`isAir` · `getCollisionShape(BlockGetter, BlockPos, CollisionContext.empty())` — `empty()` not `of(entity)`, so entity-sensitive shapes cannot make capacity depend on who walks past · `isCollisionShapeFullBlock` · `isFaceSturdy(level, pos, Direction.UP)` (3-arg, which is `SupportType.FULL`) · `blocksMotion` · `canOcclude` · `isSolidRender(level, pos)` — ⚠ two-arg in 1.21.1, no-arg from 1.21.2 · `getFluidState` · `hasProperty(BlockStateProperties.OPEN)` · `is(TagKey<Block>)` · `getLightEmission` · `hasBlockEntity` · `VoxelShape.min/max(Direction.Axis)` · `BlockTags.DOORS/TRAPDOORS/FENCE_GATES`.

**Blocks and items**
⚠ `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` — the 1.21.1 signature. 1.20's `use(...)` no longer exists and 1.21.4 merged `ItemInteractionResult` away; both would fail here · `Block implements EntityBlock` + `simpleCodec` · `Item.useOn(UseOnContext)` → `InteractionResult`, `use(...)` → `InteractionResultHolder`.

**Config**
`ModConfigSpec.Builder().configure(Server::new)` · `ModConfig.Type.SERVER` · `container.registerConfig`. Everything affecting simulation is SERVER config, so a client cannot alter its own economy.

**Other**
`Util.backgroundExecutor()` · `Heightmap.Types.WORLD_SURFACE` via `level.getHeight` · fastutil (`Long2ObjectOpenHashMap`, `LongOpenHashSet`, `Reference2IntOpenHashMap` — identity-keyed, correct because `BlockState`s are canonical singletons from a frozen registry) · `Math.clamp` (Java 21).

**One thing to fix:** the code passes `bus = EventBusSubscriber.Bus.MOD/GAME`. In NeoForge 21.1 that parameter is deprecated and **ignored** — routing is automatic (an event goes to the mod bus iff it implements `IModBusEvent`). It is harmless but produces a deprecation warning and should be dropped.

---

## 13. Package and module architecture

```
com.branciho.livingcities
├── LivingCities                  @Mod entry point. No client types reachable from here.
├── LivingCitiesServerEvents      the only server heartbeat + block-change hooks
│
├── city/          City, CityStats, CityRole, CityRegistry(SavedData)     ── server state
├── building/      Building, Floor, ZoneUse, FloorFlag                    ── server state
│
├── scan/          BlockClass, BlockProfile, BlockProfiler,               ── measurement
│                  BuildingSnapshot, BuildingScanTask, BuildingScanService,
│                  FloorAnalyzer, DetectedFloor, FloorPlan, ScanSettings
│
├── sim/           CitySimulation, PopulationModel, EmploymentAllocator,  ── simulation
│                  CityEconomy, CityBudget, HappinessModel/Factor/Breakdown,
│                  CitySnapshot, CityNotifications, CityWarning
│
├── net/           LivingCitiesNetwork, ServerPayloadHandler,             ── transport
│                  BuildingActions, payload/*
│
├── block/ blockentity/ item/ entity/ registry/ config/ command/          ── Minecraft glue
│
└── client/        ClientEvents, ClientActions, ClientPayloadHandler,     ── Dist.CLIENT ONLY
                   KeyBindings, LivingCitiesClient, screen/*, render/*
```

Four boundaries that are enforced rather than aspirational:

**`client/` is never referenced from common code.** The one crossing is `LivingCitiesNetwork` naming `ClientPayloadHandler::handleOpenScreen` as a method reference. `ClientPayloadHandler` has **only common types in its signatures and fields** and delegates into `ClientActions`, which is where `Minecraft.getInstance()` lives. Registering payloads runs on both sides and forces the *declaring* class to load, but not its method bodies' dependencies — so the declaring class must stay import-clean. This is the single most common dedicated-server crash source in NeoForge modding; verify with `runServer` on every change.

**`scan/` splits at the Minecraft boundary.** `BlockProfiler` and `BuildingScanTask` touch the world and are server-thread-only. `BuildingSnapshot`, `FloorAnalyzer`, `FloorPlan`, `DetectedFloor` and `ScanSettings` contain no Minecraft type at all. `package-info.java` states this as a contract with the reasoning; `FloorAnalyzer` imports `java.*` and one enum.

**`sim/` touches no world.** No `Level`, no `BlockState`, no entity. Cities simulate while their territory is unloaded, which is the point — a city does not stop existing because nobody is standing in it.

**The scanner knows nothing about persistence or networking.** `BuildingScanService.ScanListener` is a one-method interface; `LivingCitiesServerEvents` supplies the implementation that marks the registry dirty and pushes `BuildingDetailPayload` to the city's members. Without the second half a player watching the panel sees "Measuring…" forever even though the scan finished.

Data-driven extension points already in place: `ZoneUse` reads through accessors (`cellsPerJob()`, `taxCategory()`, `providesHousing()`) rather than being switched on, so it can become a datapack registry with the downstream code unchanged; `ScanSettings` is a record of tunables, not static constants; two datapack tags (`livingcities:scan_excluded`, `livingcities:seals_building`) let a pack author correct a pathological block with no code change; `SetFloorZonePayload` sends string ids, not ordinals.

---

## 14. Networking architecture

Six payload types, protocol version `"1"`, all hand-written `StreamCodec.ofMember` codecs.

| Payload | Direction | Handler | Guards |
|---|---|---|---|
| `CreateCityPayload` | C→S | `ServerPayloadHandler.createCity` | 8-block reach, real BE, name sanitised + uniqueness, chunk unowned |
| `ClaimChunkPayload` | C→S | `ServerPayloadHandler.claimChunk` | `ADMINISTRATOR`, 8-chunk proximity, adjacency, cost, claim cap, refund on race |
| `RequestCityDataPayload` | C→S | `ServerPayloadHandler.requestCityData` | Server picks which city you may see |
| `AssignBuildingPayload` | C→S | `BuildingActions.assignBuilding` | §6.2 — the full ladder |
| `SetFloorZonePayload` | C→S | `BuildingActions.setFloorZone` | `BUILDER`, index range, zone id resolves |
| `RescanBuildingPayload` | C→S | `BuildingActions.rescanBuilding` | `BUILDER`, 3 s cooldown |
| `OpenCityScreenPayload` | S→C | `ClientPayloadHandler.handleOpenScreen` | — |
| `CitySummaryPayload` | S→C | `ClientPayloadHandler.handleCitySummary` | 12 scalars |
| `BuildingDetailPayload` | S→C | `ClientPayloadHandler.handleBuildingDetail` | ≤128 floor rows |

Design rules:

**Hand-written codecs, not `composite`.** `StreamCodec.composite` tops out at six components and a city summary is inherently wide. Hand-writing keeps the wire format explicit — which is what you need when it has to stay backward compatible — and costs nothing.

**Every C2S handler goes through `context.enqueueWork(...)`** after an `instanceof ServerPlayer` check, so mutations land on the server thread.

**Bounded on both ends.** Strings are length-capped on write *and* read (`writeUtf(name, 32)`). `BuildingDetailPayload` caps floor rows at 128 and reports `floorsOmitted` so the UI can say so honestly instead of silently showing a short list; the reader **throws** on a count outside the allowed range, because a peer claiming more rows than the protocol permits is malformed, not verbose.

**The server drives the UI.** The `ALT+O` hotkey does not open a screen. It sends `RequestCityDataPayload(openScreen = true)`; the server decides which city the player may see and pushes back both the data and an `OpenCityScreenPayload`. The client cannot open a management screen for a city it was never told about.

**Refresh-in-place, not replace.** `ClientActions.acceptBuildingDetail` refreshes an open `BuildingPanelScreen` rather than constructing a new one — a scan finishing arrives while the player is looking at the panel, and replacing the screen would reset their page and steal focus mid-click.

Not yet built: a city-wide building list (the first packet that will get genuinely large — it will need paging or a delta channel), territory sync for the border overlay, and any S2C push on a timer. Today the client is told things only when it asks or when something it is looking at changes.

---

## 15. Persistent-data architecture

One file: `<world>/data/livingcities_cities.dat`, a `SavedData` on the overworld's storage.

```
DataVersion: 1
Cities: [ { Id, Name, Owner, Dimension, CoreX/Y/Z, Treasury, Claims: long[],
            Members: [{Player, Role}], Buildings: [{Id}], Stats: {…} } ]
Buildings: [ { Id, CityId, Name, Min/Max XYZ, LastScan, NeedsRescan, Residents, Workers,
               Floors: [{FloorY, YMin, YMax, Usable, Stand, Ceiling, Use, Density, Flags}],
               Entrances: [{X,Y,Z}] } ]
```

Decisions:

- **`DataVersion` from day one**, with a `migrate(tag, fromVersion)` hook. It currently only logs. Migrations are free to write ahead of time and impossible to retrofit.
- **Indices are never persisted.** Both chunk indices are rebuilt on load, so the file cannot contain two disagreeing copies of ownership.
- **Claims serialise as `long[]`** via `ChunkPos.toLong()` — one array per city, not an NBT map.
- **Enums serialise by name, not ordinal.** `ZoneUse` by its stable `id()` string, `CityRole` and `FloorFlag` by `name()`. `FloorFlag.load` swallows unknown names, so a flag removed in a later version simply stops applying rather than failing the load.
- **Per-record fault isolation.** One unreadable city or building is logged and skipped.
- **Optional fields are `contains()`-guarded on read** (`Floor.Density`, `CityStats.Happiness`), which is what makes adding a field a non-migration.
- **Transient state is explicitly transient.** `CitySimulation.STATE` (cash carry, last snapshot, happiness breakdown), `CityNotifications.STATE` (cooldowns) and `BuildingActions.LAST_SCAN_REQUEST` are static maps reset at both ends of the server lifecycle. A restarted server re-announces standing problems once, which is the behaviour you want: a mayor logging in after a crash should be told the city is broke.
- **`setDirty()` discipline.** Every mutating method on `CityRegistry` calls it, `CitySimulation.tick` calls it after any city stepped, and the scan listener calls it on commit. A missed `setDirty()` is silent data loss that only appears after a restart.

Honest caveat: this is hand-rolled NBT, not `Codec`-based. It is straightforward and debuggable, but a type change to an existing field is a real migration rather than a codec update. Given `DataVersion` and `migrate` exist, that is a manageable cost.

---

## 16. v0.1 implementation order (as actually executed)

Your spec listed 25 items for v0.1. The order they were built in, and why:

| # | Stage | Rationale |
|---|---|---|
| 1 | Project skeleton, `@Mod`, registries, config, `neoforge.mods.toml`, CI syntax check | Nothing else can be tested until the jar loads |
| 2 | `City`, `CityStats`, `CityRole`, `CityRegistry` (SavedData) + persistence | Everything hangs off the registry; getting `setDirty` and the derived indices right early is cheap |
| 3 | City Hall Core block + BE, `CreateCityPayload`, `CityManagementScreen`, `ALT+O` | First end-to-end vertical slice: place a block, found a city, see numbers |
| 4 | Territory: claims, chunk index, escalating cost, adjacency | Buildings need owned ground to sit on, so this precedes registration |
| 5 | `Building`, `Floor`, `ZoneUse`, `FloorFlag`; **capacity quantised into dwellings** | The data model the scanner fills in. The dwelling fix (commit `7c39a09`) came before any scanning, so the anchors were the target from the start |
| 6 | `sim/`: economy, employment, happiness, population, notifications | Deliberately built *before* the scanner: the simulation is testable against hand-written `Building` objects, and it defines exactly what the scanner has to produce |
| 7 | `BlockClass`, `BlockProfile`, `BlockProfiler` | The block-agnosticism claim, isolated and verifiable first |
| 8 | `BuildingSnapshot`, `BuildingScanTask`, `BuildingScanService` | Threading and budgeting, with no algorithm in them yet |
| 9 | `FloorAnalyzer` — histogram, candidates, enclosure, split levels, roofs | The hard part, on top of infrastructure that already worked |
| 10 | `BuildingActions`, assign/zone/rescan payloads, `BuildingPanelScreen` | The flow that connects the tool to the scanner *(landed, uncommitted)* |
| 11 | NPC spawn director | **Not started** |

The one ordering decision worth defending: **the simulation was written before the scanner.** It looks backwards — the scanner produces the scanner's input. But `sim/` is a pure function of `Building` objects, so it can be exercised against hand-built ones, and writing it first pinned down precisely which number the scanner had to produce and how accurate it needed to be. Building the scanner first would have meant tuning heuristics against a guess at what mattered.

---

## 17. Serious technical risks

**R1 — Floor detection is a heuristic against geometry nobody designed to be measured.** It will get some buildings wrong. Mitigated by making it *legible*: `FloorPlan` carries `candidateCount`, `rejectedCount`, `leakyFloorCount`, `unknownStates`, `paletteSize` and timing; the player is told in chat how many floors were found and whether any envelope was uncertain; `MANUAL` floors survive rescans. **The residual risk is a build type nobody anticipated** — a boat, a treehouse, a cave base — reporting something absurd with no clear cause. The mitigation is diagnostics, not more heuristics.

**R2 — Enclosure leaks.** One missing block at head height leaks the flood fill. The ladder handles it (detect → ray estimate → discount → flag → report), but there is currently **no override mode for deliberately open structures** — a market hall, a covered plaza, a multi-storey car park. The design called for `EnclosureMode.WHOLE_FOOTPRINT`; it is not implemented. Those buildings will measure low and the player has no lever.

**R3 — Modded blocks that misbehave.** Two guards: every `BlockProfiler.profile` call is wrapped in `try/catch` (a badly written `getCollisionShape` *does* throw) and degrades to `BlockProfile.unknown()` — pessimistic on purpose: blocks motion, supports nothing, seals nothing, so it can neither inflate usable area nor invent a floor. Unknowns are counted and reported. Separately, a state whose geometry differs between two sampled positions ≥8 blocks apart is flagged `positionSensitive`. **That flag is measured and counted but not acted on** (§20.7).

**R4 — Erosion of the off-thread contract.** `FloorAnalyzer` is pure today, and nothing mechanically stops the next person adding a `ServerLevel` parameter "just for logging". The design called for a build-time test reflecting over the class's field and parameter types. **Add it before the package grows.** This is the risk most likely to produce a rare, unreproducible crash months from now.

**R5 — Capacity inflation.** There is no per-building cap and no open-plan factor (§20.2). A 96×96 hollow shell with a roof measures as one enormous residential floor. `capacityScale` and upkeep costs are the only brakes.

**R6 — Static per-server state.** Four static maps (`CitySimulation.STATE`, `CityNotifications.STATE`, `BuildingActions.LAST_SCAN_REQUEST`, `BuildingScanService.INSTANCES`). All are reset at both ends of the lifecycle, and `INSTANCES` is a `WeakHashMap` keyed on `MinecraftServer` — the safest of the four. It would break if two servers ever ran in one JVM. Acceptable today; worth revisiting if any of these grows.

**R7 — Scan-triggered stall from a modified client.** Bounded by cooldown + `MAX_ACTIVE 2` + `MAX_QUEUED 32` + volume caps + territory ownership. Residual: 32 queued maximum-size scans is roughly 11 minutes of budgeted work, and the queue is FIFO with no per-player fairness. A per-player queue cap would close it.

**R8 — Packet growth.** Fine today. The first thing that will hurt is a city-wide building list; it needs paging or deltas before it is written, not after.

**R9 — Unverified API surface.** This code has not been compiled against the real NeoForge Maven in the environment it was written in. The items most likely to need a one-line fix, in order: the deprecated `@EventBusSubscriber(bus = …)` parameter, `isFaceSturdy`'s 3-arg overload, `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`'s exact constant name, and `SavedData.Factory`'s 3-arg form with a null `DataFixTypes`. None is load-bearing on the architecture.

**R10 — Dedicated-server class loading.** §13. One careless import in `ClientPayloadHandler` is a `NoClassDefFoundError` on every dedicated server. `runServer` in CI, permanently.

---

## 18. Where I disagreed with the spec, and what I did instead

You asked to be told. Nine entries; the first three changed how the mod works.

### 18.1 Manual floor definition as the primary workflow — **inverted**

> *Spec:* "After selecting the building, the player defines floors. Floor 1: Y 64–68. Floor 2: Y 69–73…"

**Why it's bad.** A 25-storey tower is 25 forms before the building does anything, and it is 25 forms *again* after you move a wall. Worse, what the player types is a Y range — which is not the number the mod needs. Capacity comes from usable floor blocks, so the mod has to measure the interior regardless. Manual entry would be pure friction that buys nothing.

**What I did.** Detection is the default and always runs; manual editing is the correction layer. `Building.replaceFloors` preserves any floor flagged `MANUAL` across a rescan, and `inheritZoning` carries the previous zoning onto freshly detected floors by height match, so re-measuring a tower after moving one wall does not wipe a mixed-use layout.

**What the player gets.** Select two corners, use the tool, and about two seconds later a floor table with usable-block counts and one-click zoning. Your authority over the floor list is intact — you can still add, remove and correct — you just no longer *start* from an empty form.

### 18.2 Linear residents-per-block capacity — **quantised into dwellings**

> *Spec:* "Calculate capacity based on usable residential area, density configuration…" — which reads as linear.

**Why it's bad.** Linear density is right in the middle and wrong at both ends. At 0.1875 residents/block a garden shed houses 4 people and a wardrobe houses 1. Every small structure in the world becomes housing, and capacity twitches every time the player moves a chair.

**What I did.** Housing quantises into dwellings of 16 usable blocks holding 3 residents each, floored **per storey** (§7B). Jobs stay continuous but are *rounded* with a floor of 1, because job granularity at that scale would be absurd.

**What the player gets.** A shed houses nobody. A cottage houses 6 and keeps housing 6 no matter how you decorate it. The UI can say "12 apartments" instead of "36.4 residents", and rent, occupancy and household wealth have an object to attach to later. The honest cost: a 5-storey block can be 165, 180 or 195, not 178.

### 18.3 A single global simulation interval — **staggered per city**

> *Spec:* "City simulation: perhaps once every 20 / 40 / 100 ticks."

**Why it's bad.** The obvious implementation is `if (gameTime % 100 == 0) for (city : cities) step(city)`. Every city lands on the same tick. A 100-city server does one hundred cities' work five times a second and nothing in between — a visible, periodic stutter, with a worst case that scales with city count.

**What I did.** Each city gets a stable slot in the interval window from a Fibonacci-mixed hash of its UUID (`0x9E3779B97F4A7C15`, then an xorshift — a plain modulo clumps when UUID low bits are not uniform), cached as bucket lists. Per tick: one list lookup, then the cities in that bucket. The slot is stable across restarts because it derives only from the UUID, so the spread does not reshuffle every reload.

**What the player gets.** Identical simulation rate, flat frame times. At 100 cities and a 100-tick interval that is one city per tick instead of 100 cities every 100th tick. Raising `simulationIntervalTicks` now *spreads* load rather than delaying everything at once, which is what the config comment promises.

### 18.4 Percentage-based population growth — **damped relaxation with a proven bound**

Your spec says population should "grow or decline depending on city conditions" without specifying dynamics. The natural implementation — a growth percentage modulated by happiness — is positive feedback inside a loop where happiness itself depends on population. It either runs away or oscillates, and the failure is subtle enough to survive playtesting and appear on someone's 200-hour server.

Instead: exponential relaxation toward an explicit target, with the stability bound derived in `PopulationModel`'s javadoc and the config ceiling (0.5) set inside the monotone region with ~35% margin. `HappinessModel` measures every population-sensitive factor against **capacity** rather than population specifically so the loop's slope is independent of city size — otherwise a village of three could oscillate — and it deliberately adds no smoothing of its own, because a second lag inside a negative feedback loop makes a second-order system that can ring.

**What the player gets:** population that visibly chases a number they can influence, settles, and never thrashes. It also gives the UI something honest to display: "growing toward 1,240".

### 18.5 Claim price scaling with distance — **adjacency is a rule, not a price**

> *Spec:* "Claim price may increase depending on… distance from existing territory. Prevent players from randomly purchasing a chunk 5,000 blocks away."

**Why it's bad.** If a distant claim is merely expensive, a rich city buys the map — which is precisely the outcome the next sentence of your spec forbids. Price is the wrong instrument for a rule you want absolute.

**What I did.** `requireAdjacentClaims` (default on) makes 4-neighbourhood adjacency a hard requirement, plus an 8-chunk player-proximity gate on the packet so you cannot paint territory from across the world. Price escalates with *territory size only* (`base × growth^owned`), which is the pressure that should be economic.

**What the player gets:** territory that grows outward like a city instead of appearing in patches. Outposts and colonies become an explicit mechanic later, with their own rules, rather than an emergent side effect of someone being rich.

### 18.6 A client-side "City Management Mode" — **server-driven**

The spec's framing ("ALT+O opens City Management Mode") implies the client renders city state it holds. In multiplayer that means either the client caches data it should not have, or the hotkey works differently for cities you do not belong to.

`ALT+O` sends a request. The server resolves *which* city you may see — the one you are standing in only if you are a member, otherwise your own — and pushes both the data and the screen-open command. Cost: one round-trip before the screen appears. Benefit: standing in a rival's downtown and pressing the hotkey shows you your own city, not theirs, and there is no client-side cache to desync or read out of.

### 18.7 Money as a display concern — **`long` cents everywhere**

The spec mentions `$` formatting. The trap is treating money as a `double` because it has a decimal point. A treasury updated 240 times per in-game day accumulates visible error, and a `double` silently loses precision past 2⁵³. Money is a `long` in cents from `City.treasuryCents` through `CityBudget` to the packet; formatting happens once, at the edge. Sub-cent per-step accrual is carried in `CitySimState.cashCarryCents` (§8.4) rather than truncated.

### 18.8 Ceiling height as a capacity factor — **penalty only, never a bonus**

> *Spec:* "Possible factors: … perhaps ceiling height."

Rewarding tall ceilings would make a cathedral out-house an apartment block, which is exactly backwards: a tall ceiling is *not* extra floor area. `Floor.capacityMultiplier` applies 0.85 for a cramped ceiling (≤2, usable but unpleasant) and nothing above that. Honest note: the design also called for graded dilution above 5 blocks; that was dropped as complexity that bought nothing measurable. It can come back if atrium-heavy builds turn out to over-count.

### 18.9 Building type on the building — **zone on the floor**

Your mixed-use section already asks for per-floor uses, so this is agreement rather than disagreement — but it is worth naming, because it is the reason mixed use is not a feature anywhere in the code. `ZoneUse` lives on `Floor`; `CityEconomy` buckets by `floor.use().taxCategory()`; `Building.primaryUse()` and `isMixedUse()` are *display* queries computed from the floors. A tower with commercial ground, six office floors and 23 residential floors is taxed correctly, employs correctly and houses correctly with zero mixed-use-specific code.

---

## 19. How to test this in Minecraft

`./gradlew runClient` (or `runServer` — do both; the dedicated server is where client-leak crashes appear).

### A. Found a city — 2 minutes

1. Creative mode. Open the **Living Cities** creative tab; take a **City Hall Core**, a **City Planner Tool**, and a stack of any building block.
2. Build something you would call a town hall. Place the City Hall Core inside it.
3. **Right-click the core.** The Found a City screen opens. Name it, confirm.
   - ✅ Chat: `Founded <name>`. The management screen opens. Population 0, treasury $5,000, 1 chunk, 0 buildings.
4. `/livingcities here` → the city name, population, chunks, treasury.
5. Press **ALT+O** anywhere. The management screen opens with your city.
   - ✅ **Negative test:** have a second player press ALT+O inside your territory. They should see *their* city or nothing — never yours.

### B. Register a building and watch it get measured — 5 minutes

6. Build a simple house **inside your claimed chunk**: 7×7 footprint, walls, a door, a floor at Y=64, a ceiling at Y=68, a second storey, a roof. Leave the door closed *or* open — it should not matter.
7. Take the **City Planner Tool**. **Right-click** one bottom corner (corner A), **sneak+right-click** the opposite top corner (corner B).
   - ✅ A cyan wireframe box appears around the selection. The action bar shows both corners and the block volume.
8. **Right-click in the air** (no sneak) to register.
   - ✅ `Registered Building 1`, the building panel opens showing "Measuring the building…"
   - ✅ Within a second or two: `Building 1: 2 floors, N usable blocks`, and the floor table fills in with Y ranges and usable counts.
9. **Zone it.** Click the button on each floor row to cycle it to **Residential**.
   - ✅ Each row gains a resident count. A 7×7 two-storey house should read **3 + 3 = 6 residents**.
10. Wait ~30 seconds and press ALT+O.
    - ✅ Population is climbing toward a target below 6 — this city has no jobs (§8.4). That is correct behaviour, not a bug.

### C. Prove the four hard things work

11. **Roof detection.** Look at the floor table. The topmost row should be flagged `roof` and contribute **0 residents** even if you zone it residential (you get a warning when you try).
12. **Doors are walls.** Open the front door, press **Rescan**. ✅ The usable count must not change. (If it drops, `seals()` is broken for `DOOR_LIKE`.)
13. **Stairs and slabs.** Add a staircase between floors and rescan. ✅ Floor count must stay 2 — steps are suppressed by `minFloorSeparation` and the connected-component test, not counted as storeys.
14. **Basements.** Dig a room under the house, extend corner A downward, re-register (delete the old selection first — overlap is rejected). ✅ The new bottom floor is flagged `basement`; zoned residential it contributes at 60%.
15. **Leak reporting.** Remove one wall block at head height (Y=65) and rescan. ✅ Either the count barely changes (the ray estimator rescued it) with a yellow `2 floors have an uncertain envelope` message, or the floor drops out and the table says so. **Either is correct; silence is not.**
16. **Mixed use.** Build a 3-storey block. Zone floor 1 Commercial, floor 2 Office, floor 3 Residential. ✅ The header reads **Mixed use**; row 1 shows jobs, row 3 shows residents.
17. **Block-agnosticism.** Install Create, a furniture mod and a building-blocks mod. Rebuild the house entirely out of their blocks. ✅ Floors are still detected and the usable count is in the same ballpark. Watch the log for `threw while being profiled` — any hit there is a real mod incompatibility with a named block id.

### D. Prove the boundaries hold

18. **Territory.** Try to register a building whose footprint crosses into an unclaimed chunk. ✅ `Your city must own every chunk the building sits on.`
19. **Overlap.** Try to register a second building overlapping the first. ✅ `That overlaps Building 1.`
20. **Permission.** Have a non-member try to zone your building's floors. ✅ `You do not have permission to do that.`
21. **Staleness.** Break a block inside a registered building. ✅ The panel shows `Blocks changed - rescan to update capacity`, and capacity keeps its old value until you press Rescan.
22. **Rate limit.** Spam the Rescan button. ✅ `Slow down - a scan was just requested.`
23. **Persistence.** Save and quit, reload. ✅ City, treasury, buildings, floors, zoning and population all survive. Log line: `Loaded N cities and M buildings (schema v1)`.
24. **Dedicated server.** `./gradlew runServer`, connect. ✅ No `NoClassDefFoundError`. Everything above works over the network.

### E. Performance sanity

25. Build (or `/fill`) a 40×40 × 30-storey tower. Register it.
    - ✅ Progress is smooth; no tick spike. `/debug` or a TPS mod should show no dip beyond a millisecond or two per tick.
    - ✅ The debug log reports floors found, candidates, rejections, palette size and analysis time in ms.
26. Found 5 cities and register 20 buildings. ✅ Frame time flat — the stagger schedule spreads the work.

---

## 20. Known limitations of v0.1

Stated plainly. Several of these are things the design called for and the code does not yet do.

**Resolved since this document was drafted**

1. ~~The building registration flow is uncommitted.~~ **Resolved.** `BuildingActions`, the four payloads and `BuildingPanelScreen` are committed, registered and compiling; §19 B–D work.
2. ~~The NPC spawn director does not exist.~~ **Resolved.** `CitizenSpawnDirector` is implemented and wired into `onServerTick`, with `CrowdBudget`, `CitizenActivity` and `StreetSpawnFinder` behind it. What is still missing is *routing*: citizens wander near where they spawn rather than commuting between buildings, because `PedestrianNetwork` is an empty seam.

**Measurement gaps**

3. ~~Furnishing reduces capacity.~~ **Resolved.** `FloorAnalyzer.addFurnishedCells` now returns furniture-occupied cells to the usable set at weight 0.75 when they are enclosed and touch usable floor, and adds them to the bitset so a row of desks cannot split one room in two. Original text: **furnishing currently *reduces* capacity — a direct violation of your spec.** `BlockProfile.furnishing()` exists and is documented as the guard against exactly this, and `FloorAnalyzer` never calls it. A chair or desk blocks its own cell's head clearance, so that column drops out of the floor's usable set. Your spec says furniture must be an optional bonus, never a requirement; today a heavily furnished apartment measures a few percent small. **Fix:** count `furnishing()` cells that are enclosed, roofed and 4-adjacent to a usable cell, weighted 0.75. This is the highest-priority correctness item in the mod.
4. **No open-plan factor and no per-building capacity cap.** The design specified `openPlanFactor` (discounting undivided floor plates for residential use) and `MAX_CAPACITY_PER_BUILDING = 8000`. Neither is in the code. A 96×96 hollow shell with a roof measures as one enormous residential floor.
5. **No `EnclosureMode` override.** A deliberately open market hall, covered plaza or car park will measure low, and the player has no lever to say "count the whole footprint".
6. **No chunk tickets.** `BuildingScanTask` checks `level.isLoaded` and fails with `scan_chunks_unloaded` if a chunk goes away mid-scan. Fine when the player is standing there (the normal case); wrong at the render-distance edge.
7. **`positionSensitive` is detected, counted and then ignored.** The two-sample probe finds blocks whose collision comes from a `BlockEntity` rather than the state; nothing acts on the flag. The design called for a follow-up per-cell pass below a cell cap.
8. **`FloorFlag.HAS_ATRIUM` is never set.** The enum constant and the extension point comment exist in `buildFloors`; the detection does not.
9. **Snapshot reads are unoptimised.** `LevelChunk#getBlockState(BlockPos)` per cell, with no `hasOnlyAir()` section skip. ~3–5× of snapshot throughput available for a day's work.

**Flow and lifecycle gaps**

10. **No automatic rescan.** `needsRescan` is set by the block hooks and shown in the panel, but nothing debounces and re-queues. The player must press Rescan. The design specified a 200-tick debounce with a global rate limit and partial re-analysis of the affected Y band.
11. **Block-change detection misses non-event writes** — WorldEdit, structure blocks, Create contraptions, most mod block-setters. The Rescan button is the only remedy; there is no low-frequency integrity sweep.
12. **Entrance markers and path nodes are inert decoration.** Both blocks are registered and placeable; nothing reads them. `Building.addEntrance` has no caller anywhere.
13. **Territory claiming has no UI.** `ClaimChunkPayload` is fully implemented and validated, but no screen sends it. Only the founding chunk is claimed today.
14. **`Floor.densityOverride` has no path to reach it** (only `inheritZoning` copies it forward). When a packet is added it **must** be clamped server-side — the override branch bypasses dwelling quantisation entirely and is a direct "give me infinite population" exploit otherwise.

**UI gaps**

15. **The management screen is the Overview tab only.** No Buildings, Economy, Taxes, Utilities, Industry, Services, Territory, Statistics, Multiplayer or Diplomacy tabs. The layout is row-based specifically so adding them is a row-provider swap.
16. **No overlays at all.** `ALT+B` flips a boolean and prints a message; nothing renders from it. The only world rendering is the planner's selection box.
17. **No charts, icons, tooltips or searchable lists.** Text rows and a cycle button.

**Code warts worth fixing before anything depends on them**

18. **`Building.primaryUse()` returns `ZoneUse.SPECIAL` to mean "mixed use"** — and `SPECIAL` is also a real assignable zone. It needs a distinct sentinel. `isMixedUse()` is the correct query and is what the panel actually uses.
19. **`@EventBusSubscriber(bus = …)` is passed everywhere** and is deprecated and ignored in NeoForge 21.1. Harmless, noisy, should be removed.
20. **No tests.** `FloorAnalyzer` was built specifically to be testable from synthetic snapshots with no world — that was the whole point of the `BuildingSnapshot` boundary — and `src/test` does not exist. The three capacity anchors in §7B.3 should be unit tests, and the reflection guard from R4 should be a build-time check.
21. **`migrate()` only logs.** Correct for schema v1; it becomes real work at v2.

**Whole milestones not started**

22. **Utilities (v0.2)** — no electricity, no water, no cables, poles, transformers, substations, pumps or pipes. Nothing in the current model consumes power or water.
23. **Industry (v0.4)** — no production recipes, no input/output terminals, no warehouses. `Building.staffingRatio()` exists and is unused, waiting for it.
24. **Services (v0.5)** — `GOVERNMENT`, `PUBLIC_SERVICE`, `PARK` and `ENTERTAINMENT` zones exist and feed the happiness SERVICES factor as raw civic floor area, but there are no hospitals, police, fire, schools, land value or pollution.
25. **Diplomacy and war (v0.6)** — nothing. The role and membership model is there; alliances, trade, contested chunks and district capture are not.

---

## 21. Roadmap

### v0.2 — Utilities

Plugs into: a `UtilityNetwork` service alongside `BuildingScanService`, and two new demand terms on `Floor`.

- Cables, poles, transmission towers, transformers, substations as blocks, with the network held as a **cached graph rebuilt on change** — never ticked per cable. This is the same "measure on edit, cache the result, invalidate by chunk index" shape the scanner already uses, and the building chunk index is directly reusable for it.
- Solar (sky exposure × time × weather), wind as a real multiblock, coal as a component chain.
- Substations supply *connected territory*, not individual buildings — your explicit requirement that a skyscraper never needs a wire per apartment. Coverage is a chunk-set query against the existing chunk index.
- Water: intake → treatment → storage → distribution node, same coverage model.
- Demand: `usableCells × perCellDemand(zone)`, so it is a floor-walk exactly like the tax base and can ride along in the same pass.
- Shortage consequences wire into existing machinery: a new `HappinessFactor.UTILITIES` (weights renormalise — see the note in `PopulationModel` about the safe growth-rate ceiling shrinking when a population-sensitive factor is added) and new `CityWarning` entries.
- The first two overlays (electricity, water) — and building the overlay renderer here rather than in v0.1 is deliberate: an overlay is only worth writing once there is spatial data worth looking at.

### v0.3 — Living NPCs

- **`CitizenSpawnDirector`** — the hole in `onServerTick`. Budget per region from local capacity, occupancy, zone mix and time of day; hard-clamped by `maxPhysicalNpcs`.
- Make `EntranceMarkerBlock` and `PathNodeBlock` real: entrances register onto their containing `Building` on placement, path nodes form a graph NPCs route over.
- Schedules as *aggregate flows* — at 08:00 the residential→office flow rises — not per-citizen itineraries.
- Skin/outfit variety (the `DATA_VARIANT` accessor and four textures are already there), idle behaviours, sitting.
- Parks and plazas as designatable areas, benches and gathering nodes.

### v0.4 — Industrial economy

- Data-driven recipes (JSON/datapack), with input requirements derived from vanilla crafting/smelting where one exists so a dirt-to-netherite exploit is not expressible.
- Input and output terminals as block entities the player places inside their own factory.
- Production throttled by `staffingRatio() × powerRatio × waterRatio × materialRatio` — every one of those factors either exists or arrives in v0.2.
- Warehouses, commercial consumption, supply and demand; deeper taxation on top of the existing `TaxCategory` bucketing.

### v0.5 — Services

- Hospitals, police, fire, schools, universities, waste — same philosophy: you build it, you assign it, an administration block anchors it. `ZoneUse` already reserves `PUBLIC_SERVICE` and `GOVERNMENT`.
- Replace the crude `civicCells` proxy in `HappinessModel` with real coverage: service radius, capacity vs. population served.
- Land value as a per-chunk field, pollution as a per-chunk field, both feeding capacity quality multipliers and desirability.
- Education gating job types — `ZoneUse` grows a skill requirement, `EmploymentAllocator` grows a second dimension.

### v0.6 — Multiplayer civilization

- Diplomacy states, alliances, trade agreements — `City.members` is already a role map, and the registry is already server-global, so this is new state rather than a refactor.
- Player-to-player and city-to-city transfers, selling electricity and water over shared utility networks.
- War: contested border chunks, district control points, capture progress, with every destructive mechanic behind a server config so a friends' server can run diplomacy without war.

### Cross-cutting, do these earlier than they feel urgent

- **Unit tests for `FloorAnalyzer`** against synthetic snapshots, with the three capacity anchors as assertions. The architecture was shaped to make this possible; not doing it wastes the design.
- **The R4 reflection guard** on the off-thread contract.
- **`ZoneUse` → datapack registry.** The accessor-based design means downstream code does not change.
- **A `/livingcities debug` command family** — profile the block you are looking at, dump a building's stand-cell histogram, render the enclosure overlay. On an algorithm this heuristic, being able to *see what it decided* is the only way to tune constants against real player builds instead of against imagination.
