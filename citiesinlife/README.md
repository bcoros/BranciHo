# Cities In Life

A Minecraft **1.21.1 / NeoForge** mod that turns the buildings you build by hand into a working city.

No prefabs. You build a tower out of whatever blocks you like — vanilla, Create, furniture mods, road
mods — draw a box around it with the **Planner Wand**, and tell the mod what it is. It measures the
floors you actually built and turns them into residents, jobs and tax revenue.

---

## Alpha 1.5

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

From the city panel → **Land & Territory**. Your territory as a grid of chunks, over a
see-through panel so you can still see where you are standing.

- **Left click** an unclaimed chunk to buy it — claims must touch land you already own
- **Right click** one of yours to release it
- Claims get more expensive as the city grows

### Structure mode — `Shift + L`

Registered structures are server-side boxes with no blocks of their own, so without this they are
invisible. Structure mode outlines every one near you, coloured by type, with the one you are
looking at picked out in white.

**Sneak + right click** it to delete its registration. You get a confirmation first, and the blocks
are never touched — only the claim on them goes away.

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
./gradlew build        # -> build/libs/citiesinlife-0.1.5-alpha.1.jar
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
