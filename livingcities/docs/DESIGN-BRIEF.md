# Living Cities — what it is meant to be

The mod was reset to an empty skeleton and is being rebuilt. This document is the target. It is
deliberately about *what* and *why*, not *how* — the how is open again.

---

## The one idea

**You build a structure by hand. You select it. You tell the mod what it is. It becomes a
functioning part of a city.**

That is the whole mod. Everything else is a consequence.

Concretely: a player builds a tower out of whatever blocks they like — vanilla, Create, a furniture
mod, a road mod, blocks from packs nobody has ever tested this against. They select its two corners,
mark it *Office*, and the mod **measures what is actually there** — floors, usable area, entrances —
and turns it into jobs, workers, power draw and tax revenue.

### The rule that cannot be traded away

**The mod ships no prefab buildings. Ever.**

Every city-builder mod that exists hands the player a menu of pre-made structures to plop down. This
one does not. The architecture stays entirely the player's. If a feature would be easier to build by
shipping a prefab, the feature is wrong, not the rule.

This is why the scanner has to be block-agnostic, and why there can be no hardcoded list of vanilla
blocks anywhere: it must ask a block *what it does* — does it have full collision, a sturdy top
face, does it occlude light — never *what it is*.

---

## Why "two population layers" is not an optimisation detail

A city should hold tens of thousands of residents. Minecraft cannot hold tens of thousands of
entities.

So population is **a number**, and NPCs are **a sample of it**. Around a hundred physical citizens
walk the streets and represent a virtual population that may be a hundred times larger. The
simulation's cost scales with the number of *buildings*, never with the number of people.

Get this wrong early and the mod has a hard ceiling it can never be refactored out of.

---

## What a city is made of

| | |
| --- | --- |
| **Territory** | Chunks a city claims. Buildings must sit inside them. |
| **Buildings** | A registered box: bounds, floors, a use per floor, measured capacity. |
| **Population** | A number that grows toward what housing and jobs can support. |
| **Economy** | Tax revenue in, maintenance out. A treasury that can go broke. |
| **Utilities** | Power and water, produced somewhere, distributed by a network, consumed by buildings. |
| **Citizens** | The visible sample. They should look like they are going somewhere. |

---

## Roadmap

The order matters — each stage assumes the one before it exists.

| Stage | What it adds |
| --- | --- |
| **1. Foundation** | Found a city, claim territory, select a structure, measure it, zone it, see it. |
| **2. Utilities** | Power and water: generation, distribution networks, coverage, buildings that go dark. |
| **3. Living streets** | A pedestrian graph, real commuting between registered buildings, time-of-day activity. |
| **4. Industrial economy** | Supply chains, goods, industry that depends on something. |
| **5. Services** | Schools, hospitals, emergency services, coverage radii that matter. |
| **6. Multiplayer civilisation** | Several cities per world, trade, borders, roles and permissions. |
| **7. Transport** | Roads and transit that change where people can live and work. |
| **8. Infrastructure & disasters** | Things that break, and the consequences of not maintaining them. |

Stages 1 and 2 were built once already and then deliberately deleted. `PRIOR-BUILD.md` documents how
they were solved — useful as reference, not as a template.

---

## Non-negotiables

These come from mistakes already made, not from taste. `AGENTS.md` at the repository root has the
full list; the four that shaped the design most:

1. **Server-authoritative.** A packet says what the player *wants*. The server decides what happens.
2. **A registration is invisible.** It is a box in server data with no blocks of its own. Any feature
   that creates one needs a way to *see* it and *delete* it, or demolishing a building leaves a ghost
   that reserves the ground forever.
3. **Never touch the world off the main thread.** Snapshot to plain arrays first, then analyse.
4. **Capacity comes from usable floor area, not bounding volume.** A hollow tower and a solid cube of
   the same size are not the same building.

---

## How to tell if it is working

Not "does it compile" — the previous build compiled perfectly and shipped a hotkey that drew
nothing, a panel that could only be opened once, and registrations that could never be deleted.

The test is: **launch the game and use the feature.** `./gradlew runClient`. If a button cannot be
clicked and the result seen, the feature is not finished.
