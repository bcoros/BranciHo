# Cities In Life

A Minecraft **1.21.1 / NeoForge** mod that turns the buildings you build by hand into a working city.

No prefabs. You build a tower out of whatever blocks you like — vanilla, Create, furniture mods, road
mods — draw a box around it with the **Planner Wand**, and tell the mod what it is. It measures the
floors you actually built and turns them into residents, jobs and tax revenue.

---

## Alpha 1

### The Planner Wand

One item, in the **Cities In Life** creative tab. Everything happens through it.

| Input | What it does |
| --- | --- |
| **Right click** | Place corner A. The box then follows your crosshair. |
| **Right click** again | Freeze corner B. |
| **Scroll** | Choose the building type. |
| **Left click** | Confirm and register it. |
| **Sneak + right click** | Clear the selection. |

The panel down the left of the screen shows both corners, the bounds, and — measured live from the
blocks you actually placed — how many floors and how much usable area are in there.

### Building types

| Type | What it does |
| --- | --- |
| **City Core** | Founds your city. Select one first; everything else needs a city. |
| **Residential** | Houses people. 16 usable floor cells make one dwelling, 3 people per dwelling. |
| **Commercial** | Shop jobs, 28 cells each. |
| **Business** | Office jobs, 14 cells each — denser, because a desk needs less room than a shop floor. |
| **Factory** | Industrial jobs, 18 cells each. |

A cell counts as usable if you can stand on it, there is headroom above it, and something covers it.
That last check is why an open field cannot be declared residential.

### The city panel — `;`

Treasury, population, jobs, employed, unemployed, territory and what the next chunk costs.

### The land map

From the city panel → **Land & Territory**. Your territory as a grid of chunks.

- **Left click** an unclaimed chunk to buy it — claims must touch land you already own
- **Right click** one of yours to release it
- Claims get more expensive as the city grows

### Structure mode — `Shift + L`

Registered structures are server-side boxes with no blocks of their own, so without this they are
invisible. Structure mode outlines every one near you, coloured by type, with the one you are
looking at picked out in white.

**Sneak + left click** it to delete its registration. You get a confirmation first, and the blocks
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
./gradlew build        # -> build/libs/citiesinlife-0.1.0-alpha.1.jar
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
