# Cities In Life — handoff and rework brief

**Read this whole file before touching anything.** It was written by the cloud Claude Code session that
built Cities In Life from August to October 2026, for the Claude session that takes the project
over on the owner's own PC. That session can do three things this one never could: run the game,
see it, and copy the jar straight into the owner's Modrinth instance. Everything the owner asked
for, every decision they made, and everything that is known to be broken is in here.

Read alongside it:

- [`../AGENTS.md`](../AGENTS.md) — the hard rules for working on this codebase.
- [`HISTORY.md`](HISTORY.md) — every commit, in order. Run `git log` on any of them for the reasons;
  the commit messages explain *why*, and that was deliberate.
- The **Tutorial Book** text in `src/main/resources/assets/citiesinlife/lang/en_us.json` (every
  `guide.citiesinlife.*` key). It is the most accurate player-facing description of how each system
  works today, and it was kept up to date with every change.

### If you already talked to the owner about "Living Cities"

The owner first asked about this rework in a PC chat that was pointed at the **old, deleted Living
Cities project**. If that is you, these things you said were about the wrong codebase:

- **The folder is `citiesinlife`**, on branch `claude/modrinth-custom-mod-install-lws8dm`. Not
  `livingcities`.
- **The cars are ours.** Cities In Life has its own `CarEntity`, plus a police car, fire truck and
  ambulance. They are not from Immersive Vehicles or MTS. When the owner says the cars look bad and
  do not go to work, they mean these.
- **It is not 3,050 lines.** Cities In Life is about 47,000 lines in 298 files. A rework is many
  milestones, not one.
- **The shooting star picture has been sent.** It is described in §6.

`README.md` in this folder is **stale**. It still describes Alpha 2.1 (floor counting, 16 cells per
dwelling, uploading the jar through Modrinth). Do not trust it; rewrite it as part of the rework.

---

## 1. Getting started

```bash
git clone https://github.com/bcoros/BranciHo
cd BranciHo
git checkout claude/modrinth-custom-mod-install-lws8dm
cd citiesinlife
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # a dev Minecraft with only this mod
```

- **Work only in `citiesinlife/`.** The `livingcities` folder you may find in old clones is the
  *deleted predecessor*. It is not a reference and nothing should be ported from it.
- **The branch** is `claude/modrinth-custom-mod-install-lws8dm`. It is PR #7 against `master`. Do not
  push to another branch, and do not open new pull requests unless the owner asks.
- **Version** is `mod_version` in `gradle.properties` — currently **0.11.8**. Bump it for every build
  the owner will install, so they can tell builds apart.
- **Minecraft 1.21.1, NeoForge 21.1.133, Java 21, ModDevGradle.**
- **CI:** `.github/workflows/citiesinlife-build.yml` compiles every push and uploads the jar as the
  `citiesinlife-jar` artifact. The repo is public, so Actions minutes are unlimited. Superseded runs
  are cancelled and artifacts expire after 14 days.
- **The cloud session could not build locally.** Its network policy blocked `maven.neoforged.net`,
  so GitHub Actions was its only compiler, and it never once saw the game running. Every visual
  thing in this mod was built blind. **Expect visual bugs nobody has seen yet**, and look at
  everything once you can.
- `tools/syntax-check.sh` is a fast structural check only (brackets, obvious syntax). It is not a
  type check. Gradle is.

### Getting builds to the owner

The owner plays through the **Modrinth app**, in a NeoForge 1.21.1 instance. Before this handoff,
every build meant: open the GitHub Actions run, download the artifact zip, unzip it, add the jar to
the instance. They found that slow, and it is the main reason for moving to a PC session.

From a session on their PC: build, then copy the jar into the instance's `mods` folder (replace the
old Cities In Life jar, never leave two). Confirm the instance path with them first. Minecraft must
be restarted to load a new jar; nothing can swap a mod into a running game.

### GeckoLib

The rework will very likely use **GeckoLib** for animated NPCs and cars. It is a library mod: once
Cities In Life depends on it, every instance and every server that runs the mod must have the
matching GeckoLib (NeoForge, 1.21.1) in its `mods` folder too, or Minecraft refuses to start and
names the missing mod. The owner was told their pack already contains it. **Check the actual
version in their instance** before choosing which GeckoLib version to compile against.

---

## 2. Rules that must not be broken

From `AGENTS.md`, restated because they matter more than anything below:

1. **No prefab buildings, ever.** The player builds by hand out of any blocks; the mod measures.
2. **Server-authoritative.** A payload says what the player *wants*. The server re-checks ownership,
   cost, overlap, permissions (creative mode for the editor) and capacity from its own state.
3. **Client code only under `client/`**, plus `net/ClientCityCache` which must import nothing from
   `net.minecraft.client`. Common-side code must never name a `Screen` class — opening a screen from
   the server goes payload → `ClientCityCache` flag → client tick opens it.
4. **Block-agnostic.** No lists of vanilla blocks. Ask a block what it *does*.
5. **Population is a number.** Never one entity per citizen.
6. **Every handler must change something visible.** Compiling proves nothing.
7. **Anything that creates an invisible registration needs a way to see it and remove it.**
8. **Clear static client state on disconnect** (`ClientEvents.onLoggingOut`).

### Rules the owner set during development

These are decisions, not suggestions. They came from playtesting, and several were reversed once
before ending up here — so do not "fix" them back.

- **Pavement is a preference, never a rail.** No pavement → NPCs walk anywhere. Broken stretches →
  they hop path to path. A stretch one block higher than the next still counts as connected.
  Off the pavement, an NPC heads for the *nearest available* stretch.
- **Service NPCs (police, fire, medics) take shortcuts** and often ignore pavement.
- **Cars: beyond 100 blocks** (`carTravelDistance`), a citizen walks to the nearest car park and
  drives. **No road or no car park → they walk the whole way.** Same 100 for service vehicles. Do not
  break this.
- **Jobs per building must not be reduced.** Shops 6 floor cells per job, offices 3, factories 4.
  They were raised once to cut population and the owner's city went from ~13k jobs to ~3k; they
  were put straight back. Population is cut through housing instead (see §4).
- **Roads and pavement have no health** and are never protected by a building's box. One hit and the
  tile is gone.
- **Creepers must still destroy blocks.** Only TNT-like blasts are absorbed by a building's box.
- **The owner can always mine their own blocks** without damaging the building's health.
- **Editor mode is creative only**, checked on the server.
- **Sirens:** see §4. Nuclear plants raise them only in the **red** band or after exploding, never
  amber. After an explosion they fade with the *radiation*, not the bang.
- **Silence All silences everything, including real emergencies.**
- **Launch All only works at war**, against a city you pick from a list, and each missile picks a
  random chunk of theirs.
- **Explosion loudness:** ballistic = medium-loud, nuclear = very loud, reactor meltdown = very loud.
- **The Tutorial Book must be updated every time something player-facing changes.** The owner asked
  for this repeatedly.
- **The book must fit the screen** at any GUI scale (it overflowed once and could not be paged).

---

## 3. How the owner works

- They are the **only playtester**. They play, then report bugs in batches, often in capitals and
  with typos. Read for intent; ask when two readings lead to different work.
- **"DONT CODE YET" means answer only** — no edits, no commits.
- They want a **playable build after every change that works**, with a one-line note of what to look
  at in game.
- They like being surprised with extra features when invited ("add 2–5 of your own"), and want the
  explanation *afterwards*.
- Explain **why** in plain language. They follow along closely and notice when an answer dodges.
- When you get something wrong, say so plainly and fix it. They respond well to that.

---

## 4. What exists today — every system

Keybinds: **G** city panel · **Shift+L** structure mode · **Shift+V** editor (creative) · **↑/↓**
building type · **Shift+I** creative unlimited treasury · **R** road tool panel. All rebindable.

### City, buildings and measuring

- **Planner Wand**: right click corner A, right click corner B, left click to register, ↑/↓ picks
  the type, sneak + right click cancels. First box must be a **City Core** (founds and names the city).
- **14 building types** (`structure/StructureType`): City Core, Residential, Commercial, Business,
  Factory, Police Station, Fire Station, Hospital, Park, Garbage Depot, Military Base, Turbine Power
  Plant, Nuclear Plant, Missile Silo. New types go at the **end** of the enum — the scroll order and
  the war wand cycle come from ordinal position.
- **Measuring** (`scan/StructureScanner`): enclosed space between something solid below and above,
  divided by 3 → "usable cells". Floor/roof may be up to 2 blocks outside the box. Also counts
  **block mass** (every non-air, non-liquid block) for health. Floor-counting mode was removed.
- **Capacity**: housing = `round(cells × 0.25)`, minimum 4 cells, at least 1 resident. Jobs =
  `cells / cellsPerJob`, minimum 9 cells, at least 1 job. cellsPerJob: Commercial 6, Business 3,
  Factory 4, Police 14, Fire 16, Hospital 9, Garbage 18. Housing was 1.25 per cell until it turned
  out a 10×10 house held 125 people.
- **Structure mode (Shift+L)** outlines every registration nearby, coloured by type, with a health
  bar and the building's name over each. Drawing a box in structure mode deletes registrations.
- **Editor mode (Shift+V, creative only)** — list of the city's buildings; per building: rename,
  People / Jobs / Health overrides (−1 = measured; "Auto" clears all), **Boost**, Re-measure, Repair,
  Go to (teleports onto the roof), Spawn N citizens, plus city-wide totals. Server checks creative +
  ownership on every edit. Classes: `client/screen/EditorScreen`, `net/payload/EditorPayload`,
  `EditStructurePayload`, `ServerActions.syncEditor/editStructure`.
- **Boost** (`Structure.boost`, one number meaning whatever that building makes): power plant or
  reactor → extra power; garbage depot → refuse cleared per step with no lorry; **city hall → extra
  water AND the same amount of sewage capacity**, because sewage produced *is* water drunk. Other
  types: greyed out.

### Simulation (`sim/CitySimulation`, every 200 ticks = 10 s)

- Population grows 12% of the gap per step toward `min(housing, jobs × 2 + 20 + parkArea / 20)`.
- Short power or water slows growth; refuse above tolerance (`200 + population × 2`) slows it;
  sewage in the drinking water kills people every step until fixed.
- Tax: 1 per resident + 2 per filled job per step. Upkeep: 1 per claimed chunk per step.
- New registrations seed 25% of new headroom immediately so a change is visible.
- **Territory**: land map from the City panel; buy adjacent chunks, release your own. Cost grows.

### Utilities

- **Power** (`power/`): solar panels, four windmill colours, coal plant (boiler + steam turbine +
  chimney in a Turbine Power Plant box; no chimney → soot → fire → explosion; Repair Tool clears
  soot), nuclear. Lines drawn with the **Power Line Tool**; power masts (wood) and grid pylons
  (steel, 250 blocks mast-to-mast) carry them; a **Transit Station** on the city's own land hands
  power to the city.
- **Water** (`water/`): invisible pumping chain drawn with the **Pipe Connect Tool** (Starter Pump in
  real water → Pumps → End Pump), then real pipe blocks you place that join on contact, valves,
  water storage tanks (the city drinks from tanks on its land), end pipe (a tap). Demand is charged
  against capacity, not population.
- **Sewage**: Sewage Collector + pipes to an outfall outside every city's border. A collector on the
  same run as drinking water poisons the city. There is no separate sewage pipe — a pipe is a pipe.
- **Refuse**: produced by the city; bin lorries from a staffed Garbage Depot collect it.
- **Upgrade Tool** improves starter pumps, end pumps and turbines.
- **The owner says the utilities' connection to buildings is buggy.** See §5.

### Nuclear (`nuclear/`)

Uranium ore → uranium → fuel. Reactor: 4 fuel rod columns per nuclear turbine + 4 control rod
columns, all the same height (≥ 8), waterlogged, capped with Sealing Blocks; a cooling loop through
specific ports with a Pressurised Pipe last on the cooled run; Uranium Storage feeds the rods; Steam
Emitter is the chimney. Four levers (Turbine Power, Cooler, Heat, Pressure), Main Monitor, Plant
Alarm (amber → red). Punching any reactor block explains what is wrong. **Meltdown**: pipes, then
turbines, then core, then an ~80-block crater, fallout 500 blocks for 10 minutes (creative exempt).

### Factories and workplaces

Factory + **Factory Output Crate** inside it: put one item in the top slot to choose what it makes;
produces 1 + 1 per 64 jobs every 5 s; hoppers can pull from it. **Office Space** (desks) and
**Register Counter** (tills) are what citizens actually work at.

### Roads, cars, airports

- **Road Tool** paints road *markings* on existing blocks. Brushes: Street, Junction, Car Park,
  Motorway, Remove. Street direction letters N/E/S/W mean "a car may *leave* this tile that way" —
  paint both letters on every tile of a two-way street; there are no lanes. Junctions and car parks
  are passable every way. Only motorways cross other players' borders.
- **Path Tool** marks pavement.
- **Cars** (`entity/CarEntity`, `road/Commute`, `road/RoadRouter`): our own 3D-model cars, plus police
  car, fire truck and ambulance with light bars. **The owner says they look bad and work badly.**
- **Airports**: Tourist Airplane (tourists arrive and spend money) and Transport Airplane (citizens
  commute abroad under an International Travel pact). Nobody crosses a border on foot.

### NPCs (`entity/`, `entity/ai/`, `sim/CitizenDirector`)

- **Citizens**: physical sample of the population; cap per city is the `citizensPerCity` setting
  (default 15, max 50, steps of 5; 0 = off). Spawned beside free beds near players. Jobs at desks and
  tills, day and night shifts, sleep in their bed, curfew, crime and murder, poisoning, walking to
  parked cars.
- **Service NPCs** (Service NPC Spawner inside a service building): police, fire, medics, bin men,
  soldiers, clerks, bodyguards (formation behind the owner). Emergency services drive beyond 100
  blocks if a road is within ~30 blocks of the station.
- **Tourists**: arrive by plane, visit, leave after 5 minutes.
- **Pathfinding** (rewritten in 0.11.5–0.11.7): `TownNavigation` (8192-node search vs vanilla 256,
  doors passable), `TownNodeEvaluator` (stepping off pavement costs +0.5 per step, roads +0.15,
  service NPCs pay a fifth), `OpenDoorGoal`, step height 1.0 (kerbs no longer need a jump), reach 128
  blocks (tourists 96), `Routes.walkTo` (a route counts as arriving if it ends within 4 blocks —
  `canReach()` alone left every citizen standing next to their own bed), and failed routes back off
  5 s → 10 s → 20 s.
- **The owner says NPCs are the most bugged part of the mod.** See §5.

### Diplomacy and war

- **Neighbours tab**: one-way grants (let them build on your land), two-sided pacts (International
  Travel, Help Utilities = sell surplus power/water at your price, Alliance = asked to join wars),
  city flags (City Flag block flies it).
- **War**: costs money, announced server-wide, allies asked to join. Phases swap every 3 minutes;
  attackers plant charges, defenders dig a trench and hold it. Peace is an offer the other side must
  accept (waits for them offline). **Military Tool** + Military Base train soldiers (2 minutes).
  **War Planner Wand** seizes a captured building. At war, neither side may register new buildings
  or buy land.
- **Building health** (`structure/Structure`, `city/Demolition`): max health = block mass (min 40).
  Blasts inside a box do not remove blocks — they cost 1 health per block they would have removed,
  × blast size (TNT ×1 … warhead ×8). At 0 the registration is deleted and the blocks stay. Creepers,
  ghasts, withers (anything whose *direct* source is a living mob or projectile), warheads and
  meltdowns still destroy blocks. Health is saved and heals 1/40 of max per step in peace.

### Missiles (`missile/`)

Missile Silo box + Ballistic, Nuclear or Interceptor missile blocks (10 tall, consumed on launch).
Optional sealing-block roof that slides open, alarm while doors move. **Missile Command** map from
the City panel: pick silo, warhead, chunk. Allowed on open ground or on a city you are at war with.
~40 s flight, visible arc, target is warned. **Interceptors fire automatically**, one per incoming
warhead, 50% hit. Ballistic crater ~26 blocks + fires; nuclear ~80 blocks, mushroom cloud, thunder
for everyone in that dimension, 10 minutes of fallout. **Launch All** (City Hall, or the amber button)
at war only, pick an enemy city, each silo hits a random chunk of theirs.

### City Hall

Panel only works standing inside your own City Core box. **Call a meeting** (invites everyone online,
teleports acceptors to seats, End sends them back), **alert level** Peace / Alert / War, **Silence
All**, **Announce** to everyone on your ground, **Launch All**, the scrolling **city ledger**,
**bodyguards** (hire up to 4), **clerks**. Three button blocks: red = meeting, blue = silence, amber =
launch all. **Hologram Map** block: a top-down map of your claimed chunks with a dot per person
standing on them (only works inside your city hall).

### Sirens (`city/Sirens`, `blockentity/SirenBlockEntity`)

Lattice mast with rotating horns, anywhere on your claimed land. All sirens in a city sound
together. Up when any of: a warhead aimed at your land; Alert or War at city hall; one of your
reactors in the **red** band or melting; fallout on your land (fades with the radiation). Silence
All overrides everything. Existing sirens get their block entity on chunk load.

### Everything else

- **Settings screen** (City panel, top right): world owner only — citizens per city, cars on/off,
  car distance (32–512, default 100), nuclear blast scale, steam plume, operators ignore borders.
  Machine volume is per player.
- **Sounds**: every sound is a **reused vanilla sound**, layered and pitched. There are **no custom
  sound files** in the mod.
- **Tutorial Book**: 15 chapters, sized to the window, chapter list scrolls.

---

## 5. Known problems

### What the owner reported (October 2026, before handing over)

- **NPCs are the most bugged part.** They "don't go to work", get stuck on walls and one-block
  obstacles, and stood next to their beds. The last three builds (0.11.5–0.11.7) rewrote pathfinding,
  **but none of it was ever seen running.** Test it first.
- **Cars look bad and work badly.**
- **Utilities that connect to buildings are buggy** — power, water and sewage reaching registered
  buildings.
- Plenty of "broken scripts" in general. Expect handlers that compile but do nothing.

### Found by the previous session and not fixed

- **The book says a Residential building needs beds** ("one bed is one home, no beds houses
  nobody"). **The simulation does not check beds** — housing comes from enclosed space only. Beds
  only matter for where physical citizens spawn. Make the book and the code agree.
- Plants, reactors and silos are protected by the building-health shield like everything else. The
  owner was asked whether they should stay breakable and never answered.
- Damage queued against a building is discarded if its chunk unloads before it is applied.
- `README.md` is stale (see the top of this file).
- Releases: the previous session wanted CI to publish each jar to a permanent GitHub Release instead
  of a login-only artifact. That needs `permissions: contents: write` on the workflow and was not
  done.

---

## 6. The rework

In October 2026 the owner asked for a **full rework of everything**, in their words:

> WE GONA DO FULL REWORK OF THE MOD effects cinematic stuff the models and stuff for the items rework
> blocks Rework systems rework NPCs rework cars function rework tool scripts rework UI reworks effect
> rework EVERYTHING REWORKED to way better i want COOL effects and cinematic looks like The shooting
> star mode

> even broken scripts will be fixed CUZ there are LWKY alot and more scripts that ARE 100% needed
> mostly the utilities are bugged that connects to city models ofcours rework LIKE FR EVERYTHING
> rework even sounds reworked NPCs ARE THE MOST BUGGED the cars look bad they work VERY BAd and buggy
> they dont go to work etc.

> You can do anything that works ... you can use anything and ask me anything

What they want, gathered from those messages:

- **Cool effects and a cinematic look.** Their reference is **"The Shooting Star"**. They sent a
  picture of it; ask them to send it to you too, because it is not in the repo (it is probably
  somebody else's image and the repo is public). It has three panels:
  1. From above: a huge glowing yellow sphere sitting in a giant crater ringed with debris, with
     long dark fissures running hundreds of blocks out across the land.
  2. The impact: a black pillar banded with glowing orange rings driving down into a molten crater,
     a white-hot core, glowing cracks, and a **glowing orange grid** of hexagons and circuit-like
     lines spreading across the ground, with sparks in the air.
  3. Space: a glowing **wireframe cube** floating over a black hole, a **warped grid** bending down
     into it, a purple ring of light, rainbow light rays, stars, and a small sci-fi **HUD label in
     target brackets** ("SOL-3").

  The look: enormous scale, **emissive glow**, **glowing grid and line overlays drawn over the
  world**, **holograms and wireframes**, particles, and sci-fi HUD labels. The previous session
  suggested, without agreement yet: the Planner Wand selection as a glowing wireframe hologram with
  a grid sweeping across the building as it scans; missile impacts and meltdowns as a molten crater
  with glowing cracks spreading out; the city view and hologram map as glowing grid overlays with
  target-bracket labels on buildings. Confirm the direction with the owner before building it.
- New **models and textures** for items and blocks.
- **NPCs and cars reworked** — looks *and* behaviour. GeckoLib animation is approved.
- **Every system** reworked and fixed, with the utilities' connection to buildings first among them.
- **Tools** (wands, road tool, line tools) reworked.
- **UI** reworked.
- **Sounds** reworked. The previous session could only reuse vanilla sounds. Generated sounds or
  free sound files the owner picks are the options; say which you are doing.
- The owner wants to **see** the work, and approved letting Claude watch the game and take
  screenshots.
- They approved new **dependency mods** (GeckoLib, and possibly a rendering library for bloom and
  glow — check it supports 1.21.1 with Sodium and Iris before relying on it; shaders are the biggest
  risk for anything "cinematic").

**The order the owner agreed to:**

1. Visual identity — textures, block and item models, GUI style, sounds, effect style.
2. Planner Wand and building selection made cinematic — animated selection box, a scan sweep over
   the building, floors lighting up.
3. City management mode — a camera that pulls up into a top-down view, overlays that fade in, a
   polished UI.
4. Simulation — numbers that are real and visibly change.
5. NPCs (GeckoLib-animated) — living streets.
6. Utilities, cars, everything else — each its own milestone.

Since then the owner has said NPCs, utilities and cars are the most broken, so **bring those
forward**, or at least make them work before making them pretty. Agree that with them.

**Size warning.** The plan above was first written for the deleted predecessor, which was ~3,000
lines. **Cities In Life is about 47,000 lines in 298 files**, with 133 textures and 56 blocks. A
rework of everything is many milestones. Ship each one as a build the owner can play, and never
leave the mod unplayable between them. "Rework everything at once" is what ended the predecessor.

---

## 7. The last requests, in the owner's words

Earlier requests (August to early September) survive as commits — see `HISTORY.md`. These are the
ones from September and October, verbatim, oldest first.

**0.11.1 — sirens, building health, launch all, missile sounds, the book**

- "Okay do the uptdate ALSO when i was testing i can confirm the air raid sirens didn't made a sound"
- "AIR SIRENS SHOULD WORK AND CAN EXIST EVEN OUTSIDE OF SILO SURVEY it should work like this when a
  mislle is detected on your territori ALL air sirens should work"
- Sirens for the nuclear plant only "WHEN nuclear power plant is in critical or it exploded (NOT IN
  STATE OF THE YELLOW MIDLLE CRITICAL ONLY IF ITS RED IN HIGH CRITICAL LEVEL AND WHEN IT EXPLODES)";
  after an explosion they "will fade away after the radiaction"; with two causes "it shouldnt stop
  until all problems are reserved"; rename "air raid sirens" to "siren".
- "radiation from nuclear mislle counts still ... add another button to city hall called stop all
  sirens/alarms this stops all alarms and sirens EVEN if an actuall emergency is happening ... add
  another button like that [the meeting button] ... (YOU WILL HAVE 2 WAY TO ACTIVATE IT from the UI
  or from the model button)"
- "add healt to those buildings If the bbuilding is bigger (have more of the block mass ...) now to
  show the HP it will be ablways ABOVE the box AND YOU CAN ONL SEE THE HP in Structure mode"
- "bigger explosion bigger damage (and after the healt hits 0 the building will be destryoed) also
  ... you cannot select any box in war (just like with territori but with the planner wand)"
- The shield: "if the blocks are selected in the residentional box ... and then it gets hit with TnT
  those blocks dont disapear ... when the box hits 0 it shouldnt destroy all blocks it will just
  delete the box but your blocks stay there until you TnT it another time"
- "JUST NOT THE CREEPER PROOF ... make sure the owner of city can destroy its own block without
  damaging the planner wand box ... ROADS AND PATHS ... they are single hit if they get destroyed they
  get destroyed and they never shield the road or path"
- "the HP for big buildings and small buildings inst diffrent ... TnT destroyes whole building when
  in center ... the structures dont get healt back ... i want it green bar and above the actuall HP"
- Launch all: "add another button for it ... lunch all ONLY works when you are in war with somebody
  ... you can select his city (idk make UI for it like select this guy) and when you click lunch all
  mislles will randomly choose one of the enemies chunks"
- "after the mislle lands and explodes the sounds from the mislle still have the slow fading sounds
  ... ballistic mislle have mid loud sound for explosions nuclear HIGH explosion sound (alos nuclear
  for power plant finall explosion) ... dont forget to still uptdate the tutorial book"
- "THE TUTORIAL BOOK IS BUGGED its so big that i cant even flip pages and the whole UI is going out
  of the screen fix it"

**0.11.2–0.11.4 — editor mode, overpopulation, hologram map**

- "add editor mode (when you click Shif+V) now here you will have all your buildings that you
  planned in box now here you can rename those buildings ... when you rename it above the HP ... it
  will have another name above too or adjust how many (virtual) people you want to have in them or
  how many jobs the building can have (keep in mind editor mode can be only activated in creative
  mode)"
- "Overpopulation fix ... ITS TOO GENEROUSE abaut citiziens and it adds WAY WAY MORE then it should
  ... still keep the residentional building to have the biggest cappacity for people"
- "the hologram map when i open it it just says the person dosnt actually make the minimap and show
  even the person"
- "also add something of yours to the Editor mode (i wana be suprised a bit and you can add there max
  5, min 2 features to it DONT SAY WHAT IT WILL BE ...)" — became Re-measure, Repair, Go to, and the
  city totals line.
- "Spawn NPCs ... if you spawn 10 NPCs to that excat building you will select it will spawn 10
  citiziens (And thats it)"
- "the civilian NPCs are dumb in big cities becouse they cant really navigate ... if they have a huge
  wall infront of them they just go into it and wont try to find the nearest way"
- "yk how there is limit for citiziens increase the number (even in settings so then you can lower
  it) BUT INCREASE IT TO stagering 50 citiziens"
- "add to the editor mode: add healt-this works like this it adds how much healt you want for the
  building you selected"

**0.11.5–0.11.8 — pathfinding, jobs, boost**

- "the NPCs pathfinding needs still an ubgrade ALSO they avoid path tools rules they literally wont
  walk on it ik i said prefer walk on it but NOT ALWAYS prefer and they still get stuck into a wall
  or 1 block tall object"
- "I SEE YOU CHANGE SOMETHING WITH THE EMPLOYEES THINK becouse i went from 13k employees to 3k
  employees can you make the old employee system back i just asked for the population of citiziens
  reduce not the jobs avaible"
- "REDO THE OLD JOB ASSIGNMENT WHEN YOU PLANNER WAND SOMETHING WITH COMMERCIONAL OR OFFICE OR ETC. ...
  THE OLD ONE WAS BETTER"
- "the NPCs are just standing right next to their beds ... if NPCs arent on path they can walk and
  they will try to walk to the nearest AND AVAIBLE path, (also service NPCs usually can take
  shortcuts and sometimes ignore paths) ALSO DONT FORGET THE RULE 100 blocks aways and NPCs are going
  to the nearest parking lot and going there with their car even service vehlices ... if there is no
  road assign or parking lot or anything for them to drive and spawn cars they will walk there"
- "ALSO IF NPCs dosnt have path to the place they can still walk freely to the place (path as i say
  is where they prefer to go if its all connected or if its connected a bit of not connected they
  will try to go path to path, Also connected paths still count if its 1 block taller then the other
  path down)"
- "Something new in editor called 'booster' ... if you have selected powerplants you can also select
  how much energy it can produce or if you have garbage depot selected you can select how much refuse
  it can collect AND NOW mainly how will water and suage be boosted? (becouse they arent in the box
  think and i dont want them in the box think)"
- Questions the owner asked and had answered: how the road tool's N/E/S/W letters work (now explained
  in the book's Roads chapter), and whether the interceptor missile was outdated (it is not — it
  fires automatically).
