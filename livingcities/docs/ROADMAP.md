# Living Cities — Master Development Roadmap (V3)

**This document is the authority.** Where it conflicts with any other document in this repository —
`DESIGN-BRIEF.md`, `PRIOR-BUILD.md`, older notes, anything — on stage order, architecture,
development process, performance philosophy, packaging, or what belongs in which stage, **this
document wins.**

Older documents remain useful as reference for the vision and for lessons learned. They do not
override this one.

---

## Amendments to V3

V3 was written before the codebase was reset, and one section describes a workflow that has since
changed. These are the only deviations from the source text; everything else is preserved.

**1. Airports have no aircraft.** *(explicit design decision by the project owner)*

Stage 9 airports are **player-built terminals that function as travel hubs**, not flight simulators.
There are no flyable or AI-piloted aeroplanes. A passenger enters the terminal, and the journey to
another airport resolves as a transfer — the mod may present it as boarding, a short transition, and
arrival, but no aircraft entity flies anywhere.

The reason is scope, and it is a good one: functional aircraft mean flight physics, runway
pathfinding, airspace scheduling, takeoff and landing state machines, and cross-dimensional entity
persistence. That is a mod in its own right, and it would consume more effort than Stages 1–8
combined while adding nothing to the city simulation.

**Cars, buses, trains and metros are unaffected** — those remain physical representative vehicles in
Stage 7 as written. The restriction is aircraft only.

**2. Stage 0 is finished.** The August 2026 reset delivered the project, toolchain, registries and CI;
a follow-up pass delivered logging, config, network registration, `SavedData` with a data version,
client/server verification and the version command. **The current stage is Stage 1.**

**3. The "never make the user compile" rule is relaxed.** V3 says the user should never install Java
or run Gradle. That was true when the mod was written by a cloud assistant that could not run the
game — but the project owner has since installed JDK 21 and Git deliberately, and now runs
`./gradlew runClient` locally with an assistant that reads the output.

That local loop is the single largest quality improvement this project has ever made, and this
roadmap should not discourage it. **GitHub Actions still builds every push and still produces the
downloadable JAR** — that requirement stands. What changed is that local testing is now the primary
way features get verified, and CI is the safety net rather than the only route.

**4. Nuclear "foundation hooks" removed from Stage 2.** V3 lists speculative nuclear hooks in Stage 2
while also instructing "do NOT randomly implement major future-stage features". The instruction is
right and the hook contradicts it. Nuclear belongs entirely to Stage 8.

**5. Overlaps clarified** — see the notes marked *Scope* in Stages 3, 5, 7 and 9.

---

## 1. Target platform

| | |
| --- | --- |
| Game | Minecraft Java Edition **1.21.1** |
| Loader | **NeoForge** (21.1.x) |
| Java | **21** |
| Targets | Singleplayer **and** multiplayer |
| Architecture | **Server-authoritative** |
| Build | Gradle + wrapper |
| CI | GitHub Actions — every stage must end with a successful build and a downloadable `.jar` artifact |

No Fabric APIs. No legacy Forge APIs that are not valid NeoForge APIs.

---

## 2. The core vision

Living Cities is a city simulation for **player-built** Minecraft cities. It is **not** a prefab
city-building mod.

The player builds with whatever they like — vanilla blocks, decorative blocks, furniture mods, road
mods, tech mods, Create — and then tells Living Cities what the structure *is*.

> The player manually builds a 40-floor skyscraper.
> Living Cities does **not** generate that skyscraper.
> Player selects building → mod analyses it → player assigns its purpose → building joins the simulation.

Possible functions: Residential · Commercial · Office · Industrial · Mixed Use · Hospital · Police ·
Fire Station · School · University · Hotel · Warehouse · Government · Transport · Utility ·
Recreation · future custom functions.

**Player architecture must always remain the centre of the mod.** No stage may turn Living Cities
into a game where the player places large mod-generated prefabs.

---

## 3. Simulation philosophy

Cities must eventually support 10,000 → 100,000 → 500,000 → 1,000,000+ virtual residents. Minecraft
must never contain one entity per citizen. The simulation therefore has **two layers**.

**Virtual layer** — aggregate data only. Population, demographics, employment, unemployment, housing
demand, jobs, economy, taxes, electricity and water demand, sewage, commercial demand, industrial
production, education, healthcare, crime, happiness, pollution, traffic demand, imports, exports,
logistics, land value, tourism, statistics.

**Physical layer** — only what the player can see or touch. Visible citizens, cars, buses, trains,
workers near factories, utility infrastructure, industrial inventories, disasters, emergency vehicles.

> Virtual population: 175,000 · Visible NPCs near active players: 110 — **this is correct.**

---

## 4. Performance rules

Performance is a core feature, not a later optimisation.

**Never design a system that requires:** scanning every building or claimed chunk every tick ·
recalculating the road network every tick · full pathfinding per virtual citizen · thousands of
persistent NPCs · one car per commuter · one item entity per industrial product · packet spam ·
constantly loaded distant cities.

**Prefer:** caches · indexes · dirty flags · staggered simulation · async-safe calculation where
appropriate · aggregate statistics · spatial partitions · chunk indexes · graph networks · scheduled
updates · simulation intervals · hard entity caps · loaded-area simulation · distant abstraction.

Every stage gets a **performance review** before it is called complete.

---

## 5. Multiplayer and security

The server is authoritative. The client is **never** trusted for important state.

The server validates: city and building ownership · selections · territory · money · production ·
employment · utility state · diplomacy · war · resources · NPC and vehicle spawning.

The client handles: rendering · screens · overlays · input · cosmetic effects.

Never create a client-trusted packet such as *"I captured this chunk."* Instead:

```
client requests action → server validates → server changes state → server synchronises result
```

---

## 6. Persistence

Designed properly from Stage 0, not retrofitted.

- Stable UUIDs for every major record: City, Building, District, Network, Route, Alliance.
- **Never** use temporary entity IDs as permanent identifiers.
- Saved data carries a schema version (`LivingCitiesDataVersion = 1`). On format change: increment,
  migrate what can be migrated, **never silently corrupt a world.**
- Do not store derived data that can be rebuilt. Store building IDs and geometry; rebuild chunk
  indexes on load.

---

## 7. Development process — required for every stage

**A — Plan.** Before large implementation, explain goals, systems, architecture, persistent data
changes, networking changes, performance strategy. Do not write hundreds of files before considering
architecture.

**B — Implement.** Only the current stage. Small hooks for future systems are allowed; major
future-stage features are not.

**C — Build.** Compile for real. 1.21.1 / NeoForge / Java 21.

**D — Fix.** Never knowingly leave compilation errors. Build → inspect → fix → rebuild until green.

**E — Test.** Run the client. Test world creation, save, reload, commands, screens, the core new
feature, and dedicated-server safety (`./gradlew runServer`).

**F — Deliver.** Version number · summary · changed systems · known limitations · testing
instructions · source · commit/branch · successful CI build · downloadable JAR.

---

## 8. GitHub Actions

Already set up (`.github/workflows/livingcities-build.yml`). Checkout → Temurin JDK 21 → cache Gradle
→ `./gradlew build` → upload `build/libs/*.jar` as an artifact.

Do not upload a fake JAR. Do not offer `sources.jar` as the playable mod.

---

# Stage 0 — Engineering foundation / clean rebuild

Almost no city gameplay. Its purpose is to make sure everything after it stands on a reliable base.

**Package structure** (`com.branciho.livingcities`) — create only what has a purpose, never hundreds
of empty placeholder classes:

```
livingcities
├── city        ├── network     ├── client      ├── transport    └── integration
├── building    ├── registry    ├── command     ├── industry
├── simulation  ├── config      ├── utility     ├── service
                                └── npc
```

### Status: COMPLETE

Delivered by the August 2026 reset:

- [x] NeoForge 1.21.1 project, Java 21 toolchain, Gradle wrapper
- [x] Correct mod metadata
- [x] Clean registry structure
- [x] GitHub Actions build producing an installable JAR
- [x] Mod launches without crashing

Delivered by the Stage 0 implementation pass:

- [x] Logging conventions
- [x] Config framework
- [x] Network registration architecture (payload registry, no payloads yet)
- [x] Persistent `SavedData` architecture with a data version field
- [x] Verified client/server separation — `runServer` loads with no client class reachable
- [x] `/livingcities version`, printing name, version and current stage

### Success condition

CI green · Minecraft launches · command works · world loads, saves and reloads · no advanced city
simulation yet.

**Met. Stage 0 is closed — do not reopen or rebuild it.**

---

# Stage 1 — City foundation

### City Hall Core

The player builds their own city hall. The mod provides only the functional **City Hall Core**,
placed somewhere inside it. Right-click opens a management screen offering **Create City**.

Once created: name · owner · UUID · treasury · population · territory · statistics.

Avoid requiring commands for normal gameplay wherever a good UI is possible. Commands remain for
admin and debug.

### City creation establishes

City UUID · owner · city hall position · dimension · treasury · starting territory · starting virtual
population.

### Territory

Chunk-based initially. The player claims neighbouring chunks. Requirements: cannot claim another
city's territory · server validates · claim costs can increase · costs configurable · adjacency
required · territory persists. Needs a management UI **and** an in-world overlay.

> A claim the player cannot see is a claim they cannot reason about. This is not cosmetic.

### City roles

Owner · Administrator · Builder · Member. No complex diplomacy at this stage.

### City Planner Tool

Selects arbitrary builds: Corner A, Corner B, with a visible selection outline. Selection must
**not** place permanent marker blocks, and may render only while the planner is held.

### Building registration

Assign a selected structure as Residential · Commercial · Office · Industrial · Mixed Use, and give
it a name (*"Aegis Tower"*).

Store: UUID · city · name · bounds · dimension · function · detected floors · usable area · capacity ·
metadata.

> **Reserve an entrances field in the building record now**, even though entrances are a Stage 3
> feature. Adding it later means a save migration for every existing world. *(Scope clarification)*

### Floor detection — very important

**Do not calculate capacity from bounding volume.** A hollow skyscraper must not count the air inside
its bounding box as usable space.

Identify real usable floors: find substantial horizontal walkable surfaces, require air and headroom
above them, group nearby surfaces into floor levels, calculate usable floor cells.

The player must be able to override incorrect detection.

### Manual floor management

The building screen lists floors and allows per-floor type override — Floor 1 Commercial, Floor 2
Office, Floors 3–20 Residential. **This is what makes real mixed use possible.**

### Capacity

Depends primarily on usable area. Residential → housing. Office → jobs. Commercial → jobs and visitor
capacity. Industrial → jobs and future production. Formulas must be configurable and easy to
rebalance.

> **Calibration targets:** a small house should hold roughly **6** residents, an apartment block
> ~**180**, a residential tower ~**1800**. Check any capacity model against all three — a linear
> model fits the small case and is badly wrong at the top. Housing should quantise into whole
> dwellings, because half an apartment houses nobody. *(Carried forward from the original brief)*

### Virtual population · employment · basic economy

First aggregate population simulation, driven by available housing, jobs and basic attractiveness.
**Do not spawn citizens yet.** Aggregate workers / jobs / employed / unemployed, no individual
records. City treasury with basic tax revenue per zone type. No complex global economics yet.

### Success condition

Build city hall → place core → create city → claim territory → select an arbitrary building →
register it → detect floors → assign uses → generate population and jobs → earn tax revenue.

---

# Stage 2 — Utilities

Cities come to require infrastructure.

### Electricity

Buildings generate demand; producers generate supply. **Do not require a cable to every apartment.**
Use realistic abstraction:

```
power plant → transmission network → substation → distribution coverage → buildings
```

**Components:** cable · power pole · transmission pylon · transformer · substation.

Networks must be cached. **Never flood-fill the entire power system every tick** — rebuild only after
a topology change, and rate-limit that.

**Solar** produces power under appropriate conditions.

**Wind** should ideally be a **player-built multiblock** — turbine controller, tower, rotor hub,
blades — rather than one block that becomes a magical turbine. This fits the no-prefab rule, but it
is a substantial amount of work; if Stage 2 is running long, a single-block turbine is an acceptable
interim with the multiblock tracked as follow-up.

**Coal** plants consume real Minecraft fuel. Coal causes pollution in Stage 5.

**Demand** is influenced by population, building function, usable area and industry. Shortage hurts
city performance.

### Water

Water intake · pump · treatment · storage · water tower · mains · distribution station.

Same rule: **no pipe to every toilet.** Network plus district coverage abstraction. Buildings consume
virtual water; industry may use substantially more.

### Utility overlay

Planner and management UI can display power and water coverage. Do not permanently clutter world
rendering — overlays are opt-in.

### Success condition

A city needs working electricity and water. Shortages affect the simulation. Networks stay
performant.

---

# Stage 3 — Living streets

The city becomes visibly alive.

**Representative citizens.** Physical NPCs that *represent* the virtual population. They are not
one-to-one persistent citizens.

**NPC spawn director.** One centralised director evaluates active player areas, decides target
density, and gradually spawns and despawns. **Never let each building spawn its own people.** Count
is influenced by virtual population, local building density, time of day, district, player proximity,
configurable density and hard performance caps.

**Building entrances.** The player assigns Main / Residential / Commercial / Office / Service
entrances. Buildings may have several. This exists so NPCs stop pathing through walls.

**Pedestrian network.** Logical routes with node types: path · sidewalk · crosswalk · plaza ·
entrance · gathering point. Visible in planner mode, hidden in normal play.

**Activities.** HOME · COMMUTING · WORKING · SHOPPING · LEISURE · WALKING · RETURNING_HOME. Keep it
simple enough to scale.

**Time of day.** Morning commuters · midday commercial activity · evening shopping and return home ·
low night-time street population.

**Long distance abstraction.** Never physically walk NPCs across thousands of blocks. Nearby is
physical; far away is virtual travel.

**NPC variety.** Several visual variants — but do not spend the whole stage on art.

**Parks and plazas.** Basic recreation and gathering areas, *as physical destinations NPCs walk to*.
Parks as a **service** affecting land value and happiness belong to Stage 5. *(Scope clarification)*

### Success condition

Walking through a populated city visibly feels alive, without thousands of entities.

---

# Stage 4 — Industrial economy

Factories become genuinely useful in Minecraft terms.

The player builds a factory and registers it as Industrial. It has workers, electricity, water and
capacity.

**Input terminals** accept real Minecraft resources. **Output terminals** produce real Minecraft
items.

> Clay + workers + electricity → Bricks

**Production recipes** are data-driven, supporting vanilla, Living Cities recipes, datapacks and
future mod integrations. Prevent absurd exploits.

**Staffing** scales output: 1000 workers needed, 700 available → 70% efficiency.

**Warehouses** provide inventory and logistics capacity. **Commercial buildings** consume simulated
goods — a mall consumes more than a small shop. **Supply and demand** should be understandable;
**do not turn normal gameplay into an accounting spreadsheet.** Missing goods can be imported for
money; excess can be exported.

### Success condition

A player-built factory consumes resources and produces real Minecraft goods, with output affected by
workers and utilities.

---

# Stage 5 — Services & society

City quality starts to matter. Player-designated: hospital · clinic · police station · fire station ·
school · university · park · garbage facility · recycling facility.

**Healthcare** — aggregate coverage. **Safety and crime** — aggregate model, never individual crimes.
**Fire coverage** — service coverage now; full disaster simulation in Stage 8. **Education** —
influences workforce quality across Uneducated / Basic / Educated / Highly Educated, stored as
population percentages. **Garbage** — aggregate generation and capacity; physical garbage trucks wait
for Stage 7. **Pollution** — air, noise, ground; from coal, industry, and later traffic.

**Land value** rises with parks, services, commercial access, transport access and low pollution;
falls with crime, pollution, utility shortages and heavy industry. **Happiness** is aggregate.

### Success condition

A well-designed city performs noticeably better than a badly serviced, polluted one.

---

# Stage 6 — Multiplayer civilisation

**Advanced roles.** Owner · Mayor · Administrator · Builder · Citizen, or similarly logical
permissions.

**Trade** between cities: money · goods · resources · electricity · water.

**Diplomacy states:** Neutral · Friendly · Trade Partner · Non-Aggression Pact · Alliance · War ·
Ceasefire.

**Alliances** give benefits without automatically letting allies build or grief.

**War must be formal.** No *"kill the mayor once and take the city."*

**Territory conflict:** contested border chunks · control zones · district capture · strategic points.

**Server settings** — all configurable, so a friendly server can disable warfare entirely: war
enabled · offline attack · offline capture · block destruction · territory capture · grace period ·
cooldown.

### Success condition

Players operate separate cities, trade, cooperate, ally, and optionally fight for territory.

---

# Stage 7 — Transportation & traffic

Vehicles arrive. Again: **representative traffic**, never one car per simulated citizen.

*Scope: Stage 7 is transport **within** a city. Inter-city connections are Stage 9.*

**Road network** — player-defined logical roads. Node types: lane · intersection · turn · parking
entrance · traffic signal · stop. **Do not force one road block type.**

**Cars** — visible representative traffic, density driven by virtual traffic demand.

**Traffic demand** — affected by population, workplaces, distance, time, road capacity and transit.

**Traffic lights** — logical signal phases. **Parking** — aggregate demand and capacity.

**Buses** — the player builds stops, stations and a depot, then defines routes. Visible
representative buses.

**Rail** — passenger and possibly freight stations and routes. **Metro** — stations and virtual
transit capacity, with physical trains where technically reasonable.

**Create compatibility** — optional integration may begin here. Living Cities must work fully without
Create installed.

### Success condition

Believable traffic and useful public transport, with entity counts under control.

---

# Stage 8 — Advanced infrastructure & disasters

**Advanced nuclear power** — physically constructed, not one magic block. Components: reactor casing ·
controller · fuel assemblies · control rods · coolant · steam generator · turbine · generator ·
cooling system · transformer. Track heat, fuel, coolant and control.

**Meltdowns** are configurable and never forced.

**Advanced electrical grid** — high voltage · transmission capacity · overload · blackouts ·
redundancy.

**Sewage** — generation, sewer mains, pumping, treatment. No pipe to every toilet.
**Contamination** — water and sewage failures affect city health.

**Fires** expand the emergency system. **Disasters** — major fires · flooding · storm damage ·
industrial accidents · grid failures. Configurable, and technically reasonable.

### Success condition

Large cities require real infrastructure planning and can face optional emergencies.

---

# Stage 9 — Tourism, intercity economy & major connections

The first expansion era.

*Scope: Stage 9 connects cities to each other and to the wider world. Transport **inside** a city was
Stage 7.*

**Tourism** — visitors driven by landmarks, hotels, parks, entertainment, shopping, historic districts
and accessibility.

**Hotels** — custom registration, capacity from usable area.

### Airports — travel hubs, not flight simulation

**There are no aeroplanes.** No flyable aircraft, no AI-piloted aircraft, no flight physics, no
runway pathfinding, no airspace scheduling. This is a deliberate and permanent design decision, not a
temporary simplification.

The player builds an airport out of their own blocks and registers functional areas: terminal ·
gates · runway · cargo · parking · transit connection. A runway is scenery the player builds because
an airport looks wrong without one — nothing takes off from it.

An airport does two things:

1. **Generates virtual air traffic** — passenger and cargo volume feeding tourism, imports and
   exports. Capacity derives from the registered terminal and gate area, exactly like every other
   building in this mod.
2. **Moves players and NPCs between connected airports as a transfer.** Presentation may be dressed
   up — check-in, a gate, a short transition, arrival at the destination terminal — but the
   underlying operation is a validated teleport between two registered airports, subject to fare,
   cooldown and whatever conditions the design calls for.

Rationale: functional aircraft would cost more effort than Stages 1–8 combined and add nothing to the
city simulation, which is what this mod is actually about. An airport that reliably moves people and
generates economic traffic is worth far more than a half-working aeroplane.

**Ports** — cargo and passenger shipping. Same principle: ships may be scenery and traffic may be
virtual; do not build a sailing simulator.

**Intercity transport** — rail, airport, port and highway connections. **Global trade** — improved
imports and exports.

### Success condition

Cities become connected to a larger regional and global economy.

---

# Stage 10 — Government, districts & city specialisation

**Districts** defined by the player: Downtown · Old Town · Industrial Park · Financial District ·
University Quarter. Each with its own statistics.

**Policies** — tax rates · transit funding · education spending · environmental rules · industry
incentives · tourism promotion.

**City budget** — improved income and expenses.

**Specialisation** — Financial Centre · Industrial City · Tourism Hub · Technology Centre · University
City · Port City · Manufacturing Hub. **Never a class the player selects** — it must emerge from what
they actually built.

### Success condition

Players shape not only the physical city but its economic and governmental identity.

---

# Stage 11+ — Expansion era

**Do not attempt all of this at once.** Each category can become its own stage.

**Advanced industry** — Ore → Steel → Components → Machinery · Oil → Fuel → Plastics · Agriculture →
Food → Commercial distribution.

**Advanced economics** — loans · bonds · interest · market prices · business demand · financial sector.

**Companies** — optional aggregate business simulation. Never thousands of individual corporate NPCs.

**Special NPCs** — a *small* number of persistent named characters: mayor, business owner, specialist.
Virtual population 500,000 · persistent special NPCs 40 · visible representative citizens 150 — correct.

**Demographics** — statistical age, income, education and employment sectors. Never a biography per
person. **Families** — aggregate. Never family trees for 500,000 citizens. **Wealth classes** — lower,
middle, upper income, influencing housing, shopping, taxes and land value.

**Create integration** — optional and deeper: mechanical production, item logistics, fluids,
factories, trains. Living Cities must always remain standalone.

**Other integrations** — vehicle, road, furniture, rail and technology mods. All optional.

**Megacity optimisation** — continue scaling toward hundreds of thousands, potentially millions.

---

## Systems that must never be fully individual

- one physical NPC per citizen
- one physical car per commute
- one inventory per citizen
- one detailed family history per citizen
- one full pathfinding calculation per virtual commuter
- one physical item entity per industrial product

These would destroy performance. Use abstraction.

---

## User experience principles

Normal players should not have to rely on commands. Commands are for debugging, administration and
testing.

Normal gameplay increasingly uses: city hall screen · building management screen · planner tool ·
map/territory screen · utility overlays · transport editor · diplomacy screen · statistics dashboard.

---

## Compatibility principles

Do not assume only vanilla blocks exist. The building scanner must tolerate modded blocks — ask a
block *what it does*, never *what it is*. Functional systems should use tags, interfaces or
configurable definitions. Optional integrations must never make another mod mandatory.

---

## Quality rule

**A stage is not finished because files were written.** A stage is complete only when:

1. the intended core functionality exists
2. the project compiles
3. GitHub Actions succeeds
4. a playable JAR exists
5. save and reload do not obviously break
6. the major new feature can actually be tested in game
7. known limitations are documented

> The previous build compiled perfectly and shipped a hotkey bound to a boolean nobody read, a panel
> that could only be opened once, and registrations that could never be deleted. **Compiling proves
> nothing.** Run the game.

---

## Official stage order

| Stage | Name | Status |
| --- | --- | --- |
| 0 | Engineering Foundation / Clean Rebuild | **complete** |
| **1** | City Foundation | **in progress** |
| 2 | Utilities | |
| 3 | Living Streets | |
| 4 | Industrial Economy | |
| 5 | Services & Society | |
| 6 | Multiplayer Civilization | |
| 7 | Transportation & Traffic *(within a city)* | |
| 8 | Advanced Infrastructure & Disasters | |
| 9 | Tourism / Intercity Economy / Airports / Ports | |
| 10 | Government / Districts / City Specialization | |
| 11+ | Expansion Era | |

---

## Standing instruction

**Stage 0 is complete. The current stage is Stage 1 — City Foundation.**

Do not continue the pre-reset implementation as the primary codebase — use it only for lessons and
vision. The architecture must be designed to survive every later stage.

Stage 0's exit conditions have been met: the project compiles, targets NeoForge 1.21.1 on Java 21,
has a correct Gradle wrapper, clean client/server separation, persistence architecture, GitHub
Actions and a downloadable JAR, and launches successfully. **Do not rebuild any of it.** Build on it.

When a stage is complete: **stop.** Report exactly what was implemented, provide the successful build,
and wait for explicit instruction to continue.

**Never skip stages. Never move a major feature between stages without explicit approval.**
