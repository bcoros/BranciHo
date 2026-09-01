# Cities In Life

A Minecraft **1.21.1 / NeoForge** mod that turns the buildings you build by hand into a working city.

No prefabs. You build a tower out of whatever blocks you like — vanilla, Create, furniture mods, road
mods — draw a box around it with the **Planner Wand**, and tell the mod what it is. It measures the
floors you actually built and turns them into residents, jobs and tax revenue.

---

## Alpha 2.1 — Utilities & Factories

### The Planner Wand

One item, in the **Cities In Life** creative tab. Everything happens through it.

| Input | What it does |
| --- | --- |
| **Right click** | Place corner A. The box then follows your crosshair. |
| **Right click** again | Freeze corner B. |
| **Up / Down arrow** | Choose the building type — works before you start a selection too. |
| **Right arrow** | Switch measuring mode. |
| **Left click** | Confirm and register it. |
| **Sneak + right click** | Clear the selection. |

The panel down the left of the screen shows both corners, the bounds, and — measured live from the
blocks you actually placed — how many floors and how much usable area are in there.

### Building types

| Type | What it does |
| --- | --- |
| **City Core** | Founds your city. Select one first; everything else needs a city. |
| **Residential** | Houses people. 16 usable cells make a dwelling, 10 virtual residents each. |
| **Commercial** | Shop jobs, 6 cells each. |
| **Business** | Office jobs, 3 cells each — denser, because a desk needs less room than a shop floor. |
| **Factory** | Industrial jobs, 4 cells each. |

### Measuring modes — right arrow

| Mode | How it counts |
| --- | --- |
| **Floors** (default) | Finds real storeys: somewhere to stand, headroom above, a roof over it. Accurate. |
| **Block Volume** | Measures enclosed interior space instead. For domes, open-plan warehouses and anything without recognisable floors. |

Both produce the same unit, so capacity works out the same way either way. If a build comes back with
nothing in Floors mode, that is what Block Volume is for.

The roof check in Floors mode is why an open field cannot be declared residential.

### Population is virtual

The numbers are **virtual citizens** — a population figure, not entities. A registered building
stands for a piece of a city rather than one address, so a modest apartment block reads in the
hundreds and a tower in the thousands.

Physical NPCs come later, and will be a small visible sample of that figure — a city of 10,000 might
show a dozen people walking around. There will never be one entity per citizen.

### The city panel — `G`

Treasury, population, jobs, employed, unemployed, territory and what the next chunk costs.

### The land map

From the city panel → **Land & Territory**. A real map of the terrain from above, one tile per
chunk, sampled from the world so you can see the lake or ridge you are claiming around.

**It follows you.** Walk a thousand blocks and the map opens centred on you, so a city can grow in
any direction — claims just have to stay joined up.

- **Left click** an unclaimed chunk to buy it — claims must touch land you already own
- **Right click** one of yours to release it
- Claims get more expensive as the city grows

### Electricity

Build a chain and a city lights up:

```
Solar Panel  →  Transit Station  →  Power Mast  →  …  →  Power Mast  →  Transit Station (in your city)
```

Every arrow is a line you draw with the **Power Line Tool**: right click one block, right click the
other. Sneak + right click the second one to cut a line instead.

| Block | Does | Reach |
| --- | --- | --- |
| **Solar Panel** | Produces 8 power in daylight with a clear view of the sky, less in rain, none at night | 24 |
| **Power Mast** | Carries power and nothing else. Three blocks tall, wooden | **64** |
| **Transit Station** | Hands power to the city — but only if it stands on ground the city owns | 32 |

The masts' long reach is why a solar farm can sit out in the desert and still feed a town. The
station's territory requirement is what keeps power tied to land you actually claimed.

A city short of power **stops growing** rather than emptying out. The city panel shows produced vs
needed.

### Factories that make things

Register a building as **Factory**, then put a **Factory Output Crate** inside the box you selected.

Right click the crate: one slot at the top, a chest's worth of storage below. **Put one item in the
top slot** to choose what the factory makes — a log, a brick, whatever. That item is never consumed;
it is a choice, not an ingredient.

Every five seconds the factory produces into the crate: **one item, plus one more per 64 jobs**. A
factory with no workers makes nothing. When the crate is full production stops rather than voiding
anything, so leaving one running costs you nothing.

**Hoppers can pull from it** — hopper minecarts, droppers, anything that empties a chest works, and a
comparator reads how full it is. Nothing can be put *in*; it is an output.

### Structure mode — `Shift + L`

Registered structures are server-side boxes with no blocks of their own, so without this they are
invisible. Structure mode outlines every one near you, coloured by type, with the one you are
looking at picked out in white.

**To delete, draw a box.** In structure mode the Planner Wand's box turns red and removes every
registration of yours that it touches. Same gesture that created them, and it works on something you
cannot point a crosshair at. The blocks are never touched — only the claim on them goes away.

---

## Installing it

1. Download the jar from the latest successful
   [Build Cities In Life](../../actions/workflows/citiesinlife-build.yml) run — open the run and grab
   the `citiesinlife-jar` artifact.
2. Open the **Modrinth App**, pick your instance, go to **Content → Upload files**, choose the jar.
3. The instance must be on **NeoForge for Minecraft 1.21.1**.

### Building it yourself

Needs **JDK 21**.

```bash
cd citiesinlife
./gradlew build        # -> build/libs/citiesinlife-0.2.1-alpha.1.jar
./gradlew runClient    # launch a dev client with the mod loaded
```

---

## How it works

- **Server-authoritative.** The client sends two corners and a type. The server checks ownership,
  overlap and size, measures the blocks itself, and decides what happens.
- **Block-agnostic.** The scanner asks a block whether you can stand on it and whether it blocks
  motion — never what block it is. Modded blocks measure exactly like vanilla ones.
- **Population is a number.** Cities are simulated in aggregate, at a cost that scales with how many
  buildings you have registered rather than how many people live in them.

Constraints that must not be broken are in [`AGENTS.md`](../AGENTS.md) at the repo root.
