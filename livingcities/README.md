# Living Cities

A Minecraft **1.21.1 / NeoForge** mod that turns cities you build by hand into cities that actually
function — population, jobs, an economy, territory and people walking around in them.

Living Cities ships **no prefab buildings**. You build a skyscraper out of whatever blocks you like —
vanilla, Create, furniture mods, road mods — then select it and tell the mod *this is an office
building*. The mod measures what you built and simulates it. The architecture stays entirely yours.

---

## Installing it into your Modrinth instance

The mod is a normal `.jar`. You do not need to publish anything to Modrinth to use it.

1. **Get the jar.** Either download it from the latest successful
   [Build Living Cities](../../actions/workflows/livingcities-build.yml) run — open the run and grab the
   `livingcities-jar` artifact — or build it yourself (see below).
2. Open the **Modrinth App** and select your instance (e.g. `Create-COPY`).
3. Go to the **Content** tab.
4. Click **Upload files** (top right, next to *Browse content*) and pick `livingcities-0.1.0.jar`.
5. Make sure the instance is on **NeoForge for Minecraft 1.21.1**, then hit **Play**.

The artifact zip also contains a `-sources.jar`; you only need the plain `livingcities-0.1.0.jar`.

### Building it yourself

Requires JDK 21 and an internet connection that can reach `maven.neoforged.net`.

```bash
cd livingcities
./gradlew build
# -> build/libs/livingcities-0.1.0.jar
```

To launch a dev client with the mod already loaded:

```bash
./gradlew runClient
```

---

## Compatibility

- **Minecraft** 1.21.1, **NeoForge** 21.1.x
- Designed to sit alongside large modpacks. It registers a handful of blocks, one item and one entity,
  and never replaces or overrides vanilla systems.
- **Block-agnostic by design.** The building scanner asks the block *what it does* (does it have full
  collision, is its top face sturdy, does it occlude) rather than checking it against a list of known
  blocks. Modded blocks, furniture and machinery are measured the same way vanilla ones are.
- **Multiplayer from the ground up.** All simulation, money and territory state is server-side. The
  client renders and requests; it never decides.

---

## Getting started in game

1. Craft or `/give` yourself a **City Hall Core** and a **City Planner Tool**
   (creative tab: *Living Cities*).
2. Build a city hall — anything you like, any blocks you like.
3. Place the **City Hall Core** inside it and right-click it → **Found City**, and name your city.
4. Press **Alt + O** to open city management. (Rebindable: *Options → Controls → Living Cities*.)
5. Build something else — a house, an apartment block, a tower.
6. Hold the **City Planner Tool**. Right-click one corner of the building, then **sneak + right-click**
   the opposite corner. A cyan box shows your selection.
7. Register it as a building and assign what each floor is for. One tower can be shops on the ground
   floor, offices in the middle and apartments up top.

Useful commands:

| Command | What it does |
| --- | --- |
| `/livingcities here` | Which city owns the chunk you are standing in |
| `/livingcities list` | Every city on the server (operators only) |

---

## Configuration

Server-side settings live in `config/livingcities-server.toml` once the world has been loaded — density,
tax rates, territory costs, NPC budget, scan budget and simulation rate. Server owners and modpack
authors can reshape the experience without touching code.

---

## Project layout

```
livingcities/
  src/main/java/com/branciho/livingcities/
    city/        cities, territory, roles, persistence
    building/    registered buildings, floors, zone uses
    scan/        the block-agnostic building scanner
    sim/         aggregate population, employment and economy
    npc/         representative citizens and the spawn director
    net/         payloads and server-side validation
    client/      everything client-only, guarded by Dist.CLIENT
  docs/ARCHITECTURE.md
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design in depth — how selection, floor
detection, capacity and virtual population actually work, and where the implementation deliberately
differs from the original specification.

---

## Status

**v0.1 — foundation.** Cities, territory, building registration, floor detection, capacity, virtual
population, basic economy and a first pass at physical NPCs. Utilities (electricity, water), industry
and diplomacy are later milestones; see the roadmap in the architecture document.
