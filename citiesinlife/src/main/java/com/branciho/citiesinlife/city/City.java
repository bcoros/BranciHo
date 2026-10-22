package com.branciho.citiesinlife.city;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A city: an owner, some money, some claimed ground and the structures standing on it.
 *
 * <p>Money is held in whole units rather than fractions of one. There is no need for cents here and
 * a long counts far past any treasury a player will ever accumulate.
 */
public final class City {

    /** What a city starts with, enough to claim a few chunks before it has to earn anything. */
    public static final long STARTING_TREASURY = 5_000L;

    /**
     * What a city holds while its owner is building in creative.
     *
     * <p>A number rather than a special case in every price check. Creative money is meant to mean
     * "stop asking me what things cost", and the cheapest way to make every cost in the mod stop
     * mattering — including ones written after this — is to make the treasury bigger than any of
     * them. It is topped back up every second, so spending never eats into it.
     */
    public static final long CREATIVE_TREASURY = 1_000_000_000L;

    private final UUID id;
    private String name;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private long treasury = STARTING_TREASURY;

    /**
     * Whether the treasury on show is the creative one, and what the city really has underneath it.
     *
     * <p>Kept apart on purpose. Switching creative money off has to hand the player back the city
     * they actually built, not whatever was left of a billion after an afternoon of buying land —
     * and the city keeps earning and paying its upkeep the whole time, into the banked figure, so
     * leaving creative does not rewind the economy either.
     */
    private boolean creativeFunded;
    private long bankedTreasury;

    private final LongSet claimedChunks = new LongOpenHashSet();
    private final List<UUID> structures = new ArrayList<>();

    /**
     * Cities this one has given the run of its land to, and cities it is at war with.
     *
     * <p>Held here rather than in a table of their own because a relationship is a fact about a city
     * in the same way its treasury is, and every question ever asked of these — may this person
     * build here, may they hit that citizen — starts from a city and not from a pair.
     *
     * <p>A grant is one way: it is the grantor's to give and only they can take it back. A war is
     * not — two cities count as at war while either of them holds an entry, and ending one clears
     * both sides at once.
     *
     * <p>Peace used to require both sides to stand down independently, which was tidier on paper and
     * confusing to actually play: a player who was attacked pressed the only button they had, nothing
     * happened, and the war looked stuck. The declaration already costs the aggressor real money;
     * that is the thing that stops war being spammed, not making peace hard to reach.
     */
    private final Set<UUID> granted = new HashSet<>();
    private final Set<UUID> wars = new HashSet<>();

    /**
     * When each war this city declared began, in game time.
     *
     * <p>Kept because a war has a shape over time and not just an on-off state: the side that
     * declared attacks first, and the two of them swap every few minutes. Both cities have to agree
     * about whose turn it is, and the only thing they both have access to is the declaration - so
     * the clock lives with the declaration, on the city that made it.
     */
    private final Map<UUID, Long> warStarted = new HashMap<>();

    /**
     * Cities this one has offered peace to.
     *
     * <p>An offer, not an act. Held rather than sent and forgotten, so a treaty proposed to
     * somebody who is offline is still waiting for them when they log in - which is the whole point
     * of it being a treaty rather than a button that ends the war on its own.
     */
    private final Set<UUID> peaceOffers = new HashSet<>();

    /**
     * What this city has agreed to with each neighbour, and what it charges them.
     *
     * <p>One row per neighbour this city has ever had dealings with, holding a bitmask of {@link
     * Pact}s it is offering and the two prices it wants for its surplus. Deliberately only this
     * city's half of each arrangement: a pact is live when both cities hold the bit, so consent,
     * cancellation and "waiting for them to answer" all fall out of comparing two masks and there
     * is no third place where a half-torn-down agreement can hide.
     */
    private final Map<UUID, Dealings> dealings = new HashMap<>();

    /** Forty squares of dye. See {@link CityFlag}. */
    private byte[] flag = CityFlag.blank();

    /**
     * Capacity the city's buildings offer, and how much of it is taken up.
     *
     * <p>Housing and jobs are what the buildings <em>could</em> hold; population and employed are what
     * they currently do. Showing both is what makes a half-empty city legible instead of looking like
     * the numbers are simply wrong.
     */
    private int housing;
    private int jobs;
    private int population;
    private int employed;

    /** What the city's buildings draw, and what its network actually delivers. */
    private int powerNeeded;
    private int powerProduced;

    /** The same for water: what the people need, and what actually came out of the tanks. */
    private int waterNeeded;

    /**
     * Sewage: how much the city makes, and how much of that is actually taken away.
     *
     * <p>Production is not something you build. Everything else on the city panel is a thing the
     * player chose to make; this is the bill for having chosen to have a city at all, and the only
     * decision is whether to deal with it.
     */
    private int sewageProduced;
    private int sewageHandled;
    private int waterSupplied;

    /**
     * How much of the city's drinking water is coming out of its own sewers, as a percentage.
     *
     * <p>Not a boolean, because a city with four pumping stations and one crossed connection is a
     * different problem from one drinking nothing but sewage, and the death rate should say so.
     */
    private int waterTainted;

    /**
     * How much of this step's power and water arrived from somebody else's city.
     *
     * <p>Not persisted, and not part of the totals it is reported alongside: it is recomputed from
     * scratch every step by {@link com.branciho.citiesinlife.sim.UtilityTrade}, and a figure saved
     * to disk would come back describing a deal that may no longer exist.
     */
    private int powerImported;
    private int waterImported;

    /**
     * How much rubbish is piled up, and how much ground the city has given over to parks.
     *
     * <p>Refuse is the one utility that works backwards: everything else is a supply that has to
     * keep up with a demand, and this is a demand that has to be kept down. Left alone it climbs
     * with the population until it starts putting people off living here.
     *
     * <p>Park area is measured in square metres of ground the player drew a box around, not in floor
     * space, because a park has no floors. It is the only registered thing in the mod that is meant
     * to be outdoors.
     */
    private int refuse;
    private int parkArea;

    /**
     * What the city hall has declared, and what the city remembers happening to it.
     *
     * <p>The alert level is the only state behind "raise every alarm": see {@link AlertLevel} for
     * why there is no second flag beside it. The ledger is a bounded, oldest-first history — bounded
     * inside this class rather than at the screen, because the whole list goes into the save file
     * and every future caller has to be held to the same limit.
     */
    private AlertLevel alertLevel = AlertLevel.PEACE;
    private final List<LedgerEntry> ledger = new ArrayList<>();

    /**
     * The master mute: every siren and every alarm in the city, off.
     *
     * <p>Deliberately overrides genuine emergencies as well as declared ones, which is the whole
     * reason it exists. A reactor that will be critical for the next twenty minutes while you fix
     * it, or a crater that will give off fallout for ten, is a thing you already know about — and
     * the alternative to a mute is a player turning the mod's volume down and then not hearing the
     * next one either.
     *
     * <p>Saved, because it is a decision rather than a state of the world, and a decision that
     * unmade itself over a reload would be worse than useless. Every panel that can set it also
     * shows it, so it cannot be forgotten quietly.
     */
    private boolean hushed;

    /**
     * How far back the city remembers.
     *
     * <p>A hundred and twenty lines is months of anything worth writing down. It was forty, chosen
     * to fit a panel that turned out not to scroll at all — so the panel only ever showed the last
     * six and the other thirty-four were unreachable. Now that it scrolls, the limit can be what a
     * city's history ought to be rather than what fits on a screen, and trimming from the front of
     * a list this size still costs nothing next to the tick that writes to it.
     */
    public static final int MAX_LEDGER = 120;

    public City(UUID id, String name, UUID owner, ResourceKey<Level> dimension) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    /** Territory is per dimension, so chunk (0,0) in the Nether is not chunk (0,0) here. */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long treasury() {
        return treasury;
    }

    public void deposit(long amount) {
        if (creativeFunded) {
            // The real economy carries on underneath the creative pile.
            bankedTreasury = Math.max(0L, bankedTreasury + amount);
            return;
        }
        treasury += amount;
    }

    public boolean canAfford(long amount) {
        return treasury >= amount;
    }

    public boolean withdraw(long amount) {
        if (!canAfford(amount)) {
            return false;
        }
        treasury -= amount;
        return true;
    }

    /** Whether this city is currently spending its owner's creative-mode money. */
    public boolean creativeFunded() {
        return creativeFunded;
    }

    /**
     * Switch the creative treasury on or off.
     *
     * @return whether anything changed, so the caller knows if the save needs writing
     */
    public boolean setCreativeFunded(boolean funded) {
        if (creativeFunded == funded) {
            return false;
        }
        if (funded) {
            bankedTreasury = treasury;
            treasury = CREATIVE_TREASURY;
        } else {
            treasury = bankedTreasury;
            bankedTreasury = 0L;
        }
        creativeFunded = funded;
        return true;
    }

    /** Put back whatever creative spending took out. Does nothing to a city paying its own way. */
    public boolean refillCreative() {
        if (!creativeFunded || treasury == CREATIVE_TREASURY) {
            return false;
        }
        treasury = CREATIVE_TREASURY;
        return true;
    }

    public LongSet claimedChunks() {
        return claimedChunks;
    }

    public boolean owns(long chunkKey) {
        return claimedChunks.contains(chunkKey);
    }

    public void claim(long chunkKey) {
        claimedChunks.add(chunkKey);
    }

    public void unclaim(long chunkKey) {
        claimedChunks.remove(chunkKey);
    }

    /**
     * What the next chunk costs.
     *
     * <p>Rising with the size of the claim, so sprawling outward to grab land is a decision with a
     * price rather than something you do idly on the first afternoon.
     */
    public long nextClaimCost() {
        return 250L + (long) claimedChunks.size() * 100L;
    }

    public List<UUID> structures() {
        return structures;
    }

    public void addStructure(UUID structureId) {
        if (!structures.contains(structureId)) {
            structures.add(structureId);
        }
    }

    public void removeStructure(UUID structureId) {
        structures.remove(structureId);
    }

    public int housing() {
        return housing;
    }

    public int jobs() {
        return jobs;
    }

    public int population() {
        return population;
    }

    public int employed() {
        return employed;
    }

    public void setCapacity(int housing, int jobs) {
        this.housing = housing;
        this.jobs = jobs;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    public void setEmployed(int employed) {
        this.employed = Math.max(0, employed);
    }

    public int powerNeeded() {
        return powerNeeded;
    }

    public int powerProduced() {
        return powerProduced;
    }

    public void setPower(int produced, int needed) {
        this.powerProduced = Math.max(0, produced);
        this.powerNeeded = Math.max(0, needed);
    }

    public int waterNeeded() {
        return waterNeeded;
    }

    public int waterSupplied() {
        return waterSupplied;
    }

    public int waterTainted() {
        return waterTainted;
    }

    public byte[] flag() {
        return flag;
    }

    public void setFlag(byte[] cells) {
        this.flag = CityFlag.sanitise(cells);
    }

    public AlertLevel alertLevel() {
        return alertLevel;
    }

    /** Whether the city has muted every siren and alarm it owns. */
    public boolean hushed() {
        return hushed;
    }

    /** @return whether this changed anything */
    public boolean setHushed(boolean hushed) {
        if (this.hushed == hushed) {
            return false;
        }
        this.hushed = hushed;
        return true;
    }

    /**
     * Declare an alert level.
     *
     * @return whether this actually changed anything, so the caller can skip repainting every siren
     *         in the city when somebody presses the button they were already on
     */
    public boolean setAlertLevel(AlertLevel level) {
        if (alertLevel == level) {
            return false;
        }
        alertLevel = level;
        return true;
    }

    /**
     * The live ledger, oldest first — the same convention as {@link #structures()} and
     * {@link #army()}. Copy it before iterating if the loop might write to it.
     */
    public List<LedgerEntry> ledger() {
        return ledger;
    }

    /**
     * Write one line into the city's history, trimming the oldest away if it is full.
     *
     * <p>Bounded here rather than at the caller: there are already half a dozen places that will
     * want to write a line, and one of them forgetting the cap would grow the save file without
     * limit.
     */
    public void note(long at, String key, String detail) {
        ledger.add(new LedgerEntry(at, key, detail));
        while (ledger.size() > MAX_LEDGER) {
            ledger.remove(0);
        }
    }

    public int powerImported() {
        return powerImported;
    }

    public int waterImported() {
        return waterImported;
    }

    public void addImports(int power, int water) {
        powerImported += Math.max(0, power);
        waterImported += Math.max(0, water);
    }

    /** Cleared at the top of every step, before the trade pass runs again. */
    public void clearImports() {
        powerImported = 0;
        waterImported = 0;
    }

    public void setWaterTainted(int percent) {
        this.waterTainted = Mth.clamp(percent, 0, 100);
    }

    public void setWater(int supplied, int needed) {
        this.waterSupplied = Math.max(0, supplied);
        this.waterNeeded = Math.max(0, needed);
    }

    public int sewageProduced() {
        return sewageProduced;
    }

    public int sewageHandled() {
        return sewageHandled;
    }

    public void setSewage(int handled, int produced) {
        this.sewageHandled = Math.max(0, handled);
        this.sewageProduced = Math.max(0, produced);
    }

    /** What is going untreated. The number that turns into rubbish and unhappiness. */
    public int sewageUntreated() {
        return Math.max(0, sewageProduced - sewageHandled);
    }

    // ------------------------------------------------------------------ army

    /**
     * One soldier on the city's books.
     *
     * <p>The record is the soldier, not the body walking about. Bodies are spawned from the barracks
     * when the chunk is loaded and discarded when it is not; the roll survives either way, which is
     * why firing somebody is a decision and unloading a chunk is not.
     *
     * @param weapon    the registry id of what they carry, or empty for bare hands. Kept as a
     *                  string rather than as an item so a save still opens if the mod that supplied
     *                  the gun is uninstalled.
     * @param trainingDoneAt game time the current course finishes, or 0 if they are not on one
     */
    public record Soldier(UUID id, String name, int training, String weapon, long trainingDoneAt) {

        public Soldier withTraining(int level) {
            return new Soldier(id, name, level, weapon, 0L);
        }

        public Soldier withWeapon(String item) {
            return new Soldier(id, name, training, item, trainingDoneAt);
        }

        public Soldier startingCourse(long finishesAt) {
            return new Soldier(id, name, training, weapon, finishesAt);
        }

        public boolean inTraining() {
            return trainingDoneAt > 0L;
        }
    }

    /** What hiring, and then improving, one soldier costs. */
    public static final long HIRE_COST = 1_500L;
    public static final long TRAIN_COST = 1_200L;

    /** How long a course takes. Two minutes, so training is a decision made before a war. */
    public static final int TRAIN_TICKS = 2_400;

    /** How many soldiers one city may keep. A barracks, not a nation. */
    public static final int MAX_ARMY = 8;

    private final List<Soldier> army = new ArrayList<>();

    public List<Soldier> army() {
        return army;
    }

    public @Nullable Soldier soldier(UUID id) {
        for (Soldier soldier : army) {
            if (soldier.id().equals(id)) {
                return soldier;
            }
        }
        return null;
    }

    public boolean enlist(Soldier soldier) {
        if (army.size() >= MAX_ARMY) {
            return false;
        }
        return army.add(soldier);
    }

    public boolean discharge(UUID id) {
        return army.removeIf(soldier -> soldier.id().equals(id));
    }

    // ------------------------------------------------------------- bodyguards

    /** What one bodyguard costs to take on. Dearer than a soldier: this one follows you home. */
    public static final long HIRE_GUARD_COST = 900L;

    /** How many bodyguards one city may keep. A detail, not a retinue. */
    public static final int MAX_GUARDS = 4;

    /**
     * The bodyguard roll.
     *
     * <p>The same {@link Soldier} record as the army, held in a separate list rather than flagged
     * inside one. They are hired somewhere else, paid at a different rate, capped separately, and
     * cannot be sent to take ground — so every question either list is ever asked has a different
     * answer, and one list with a discriminator would mean writing that condition at every site.
     */
    private final List<Soldier> guards = new ArrayList<>();

    public List<Soldier> guards() {
        return guards;
    }

    public @Nullable Soldier guard(UUID id) {
        for (Soldier guard : guards) {
            if (guard.id().equals(id)) {
                return guard;
            }
        }
        return null;
    }

    public boolean engage(Soldier guard) {
        if (guards.size() >= MAX_GUARDS) {
            return false;
        }
        return guards.add(guard);
    }

    public boolean release(UUID id) {
        return guards.removeIf(guard -> guard.id().equals(id));
    }

    /** Put an updated record back in the same place, so the list does not shuffle under the screen. */
    public boolean replace(Soldier updated) {
        for (int i = 0; i < army.size(); i++) {
            if (army.get(i).id().equals(updated.id())) {
                army.set(i, updated);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------- services

    public int refuse() {
        return refuse;
    }

    public void addRefuse(int amount) {
        refuse = Math.max(0, refuse + amount);
    }

    /**
     * How much rubbish this city can have lying about before anybody minds.
     *
     * <p>Scaled to the population, so a hamlet is not judged by a capital's standards. A city with
     * no collection at all creeps past this and then keeps going.
     */
    public int refuseTolerance() {
        return 200 + population * 2;
    }

    public int parkArea() {
        return parkArea;
    }

    public void setParkArea(int parkArea) {
        this.parkArea = Math.max(0, parkArea);
    }

    // ------------------------------------------------------------- diplomacy

    /** Whether this city has given another the run of its land. */
    public boolean grantedTo(UUID otherCityId) {
        return granted.contains(otherCityId);
    }

    /** Let another city's people build here. Returns false if nothing changed. */
    public boolean grant(UUID otherCityId) {
        return granted.add(otherCityId);
    }

    public boolean revoke(UUID otherCityId) {
        return granted.remove(otherCityId);
    }

    /**
     * Declare yourself hostile to another city.
     *
     * <p>Stored one way round; two cities are at war while either of them is hostile. Call
     * {@link #makePeace} on <em>both</em> to end it, which is what the server does.
     */
    public boolean declareWar(UUID otherCityId, long gameTime) {
        warStarted.put(otherCityId, gameTime);
        return wars.add(otherCityId);
    }

    /** When this city's war with that one started, or -1 if it never declared one. */
    public long warStarted(UUID otherCityId) {
        Long when = warStarted.get(otherCityId);
        return when == null ? -1L : when;
    }

    public boolean offeringPeaceTo(UUID otherCityId) {
        return peaceOffers.contains(otherCityId);
    }

    public boolean offerPeace(UUID otherCityId) {
        return peaceOffers.add(otherCityId);
    }

    public boolean withdrawPeaceOffer(UUID otherCityId) {
        return peaceOffers.remove(otherCityId);
    }

    /** Drop this city's own hostility. Ending a war means doing this on both sides. */
    public boolean makePeace(UUID otherCityId) {
        warStarted.remove(otherCityId);
        peaceOffers.remove(otherCityId);
        return wars.remove(otherCityId);
    }

    /** Whether <em>this</em> city is the hostile one. Ask {@code Diplomacy} for the real answer. */
    public boolean hostileTo(UUID otherCityId) {
        return wars.contains(otherCityId);
    }

    public Set<UUID> granted() {
        return granted;
    }

    public Set<UUID> wars() {
        return wars;
    }

    /**
     * Forget a city entirely.
     *
     * <p>Called when one is deleted. Without it a razed city leaves its wars behind in everybody
     * else's save file forever, and a new city that happened to be handed the same UUID would
     * inherit somebody else's grudge.
     */
    public boolean forget(UUID otherCityId) {
        warStarted.remove(otherCityId);
        peaceOffers.remove(otherCityId);
        return granted.remove(otherCityId) | wars.remove(otherCityId)
                | dealings.remove(otherCityId) != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("treasury", treasury);
        tag.putBoolean("creativeFunded", creativeFunded);
        tag.putLong("banked", bankedTreasury);
        tag.putLongArray("chunks", claimedChunks.toLongArray());

        ListTag structureList = new ListTag();
        for (UUID structureId : structures) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", structureId);
            structureList.add(entry);
        }
        tag.put("structures", structureList);

        tag.putInt("housing", housing);
        tag.putInt("jobs", jobs);
        tag.putInt("population", population);
        tag.putInt("employed", employed);
        tag.putInt("powerNeeded", powerNeeded);
        tag.putInt("powerProduced", powerProduced);
        tag.putInt("waterNeeded", waterNeeded);
        tag.putInt("sewageProduced", sewageProduced);
        tag.putInt("sewageHandled", sewageHandled);
        tag.putInt("waterSupplied", waterSupplied);
        tag.putInt("waterTainted", waterTainted);
        tag.putInt("refuse", refuse);
        tag.putInt("parkArea", parkArea);
        ListTag armyList = new ListTag();
        for (Soldier soldier : army) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", soldier.id());
            entry.putString("name", soldier.name());
            entry.putInt("training", soldier.training());
            entry.putString("weapon", soldier.weapon());
            entry.putLong("trainingDoneAt", soldier.trainingDoneAt());
            armyList.add(entry);
        }
        tag.put("army", armyList);

        ListTag guardList = new ListTag();
        for (Soldier guard : guards) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", guard.id());
            entry.putString("name", guard.name());
            entry.putInt("training", guard.training());
            entry.putString("weapon", guard.weapon());
            entry.putLong("trainingDoneAt", guard.trainingDoneAt());
            guardList.add(entry);
        }
        tag.put("guards", guardList);

        tag.put("granted", writeIds(granted));
        tag.put("wars", writeIds(wars));
        tag.put("peaceOffers", writeIds(peaceOffers));
        ListTag started = new ListTag();
        for (Map.Entry<UUID, Long> entry : warStarted.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", entry.getKey());
            row.putLong("at", entry.getValue());
            started.add(row);
        }
        tag.put("warStarted", started);
        ListTag deals = new ListTag();
        for (Map.Entry<UUID, Dealings> entry : dealings.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", entry.getKey());
            row.putInt("pacts", entry.getValue().pacts);
            row.putInt("powerPrice", entry.getValue().powerPrice);
            row.putInt("waterPrice", entry.getValue().waterPrice);
            deals.add(row);
        }
        tag.put("dealings", deals);
        tag.putByteArray("flag", flag);

        tag.putString("alertLevel", alertLevel.id());
        tag.putBoolean("hushed", hushed);
        ListTag ledgerList = new ListTag();
        for (LedgerEntry entry : ledger) {
            CompoundTag row = new CompoundTag();
            row.putLong("at", entry.at());
            row.putString("key", entry.key());
            row.putString("detail", entry.detail());
            ledgerList.add(row);
        }
        tag.put("ledger", ledgerList);
        return tag;
    }

    /** This city's half of its arrangement with one neighbour. */
    public static final class Dealings {
        private int pacts;
        private int powerPrice = 1;
        private int waterPrice = 1;

        private boolean empty() {
            return pacts == 0 && powerPrice == 1 && waterPrice == 1;
        }
    }

    // ------------------------------------------------------------------ pacts

    /** Whether this city is holding its half of a pact with that one. */
    public boolean offers(UUID otherCityId, Pact pact) {
        Dealings row = dealings.get(otherCityId);
        return row != null && (row.pacts & pact.bit()) != 0;
    }

    /**
     * Set or clear this city's half of a pact.
     *
     * @return whether anything changed
     */
    public boolean setOffer(UUID otherCityId, Pact pact, boolean on) {
        Dealings row = on ? dealings.computeIfAbsent(otherCityId, id -> new Dealings())
                : dealings.get(otherCityId);
        if (row == null) {
            return false;
        }
        int was = row.pacts;
        row.pacts = on ? was | pact.bit() : was & ~pact.bit();
        if (!on && row.empty()) {
            dealings.remove(otherCityId);
        }
        return row.pacts != was;
    }

    public int powerPrice(UUID otherCityId) {
        Dealings row = dealings.get(otherCityId);
        return row == null ? 1 : row.powerPrice;
    }

    public int waterPrice(UUID otherCityId) {
        Dealings row = dealings.get(otherCityId);
        return row == null ? 1 : row.waterPrice;
    }

    /** What this city charges that one per unit per step. Zero is a gift, and allowed. */
    public void setPrices(UUID otherCityId, int power, int water) {
        Dealings row = dealings.computeIfAbsent(otherCityId, id -> new Dealings());
        row.powerPrice = Mth.clamp(power, 0, MAX_PRICE);
        row.waterPrice = Mth.clamp(water, 0, MAX_PRICE);
    }

    /** Every neighbour this city has any arrangement with. */
    public Set<UUID> dealtWith() {
        return dealings.keySet();
    }

    /** The most anybody may charge per unit per step, so a typo cannot bankrupt a neighbour. */
    public static final int MAX_PRICE = 999;

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        return list;
    }

    private static void readIds(CompoundTag tag, String key, Set<UUID> into) {
        // Absent on any city saved before Alpha 4, which reads as an empty list rather than as an
        // error - an old world simply starts out at peace with everybody.
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getCompound(i).getUUID("id"));
        }
    }

    public static City load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        City city = new City(tag.getUUID("id"), tag.getString("name"), tag.getUUID("owner"), dimension);
        city.treasury = tag.getLong("treasury");
        city.creativeFunded = tag.getBoolean("creativeFunded");
        city.bankedTreasury = tag.getLong("banked");
        for (long chunk : tag.getLongArray("chunks")) {
            city.claimedChunks.add(chunk);
        }
        ListTag structureList = tag.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structureList.size(); i++) {
            city.structures.add(structureList.getCompound(i).getUUID("id"));
        }
        city.housing = tag.getInt("housing");
        city.jobs = tag.getInt("jobs");
        city.population = tag.getInt("population");
        city.employed = tag.getInt("employed");
        city.powerNeeded = tag.getInt("powerNeeded");
        city.powerProduced = tag.getInt("powerProduced");
        city.waterNeeded = tag.getInt("waterNeeded");
        city.sewageProduced = tag.getInt("sewageProduced");
        city.sewageHandled = tag.getInt("sewageHandled");
        city.waterSupplied = tag.getInt("waterSupplied");
        city.waterTainted = tag.getInt("waterTainted");
        city.refuse = tag.getInt("refuse");
        city.parkArea = tag.getInt("parkArea");
        ListTag armyList = tag.getList("army", Tag.TAG_COMPOUND);
        for (int i = 0; i < armyList.size(); i++) {
            CompoundTag entry = armyList.getCompound(i);
            city.army.add(new Soldier(
                    entry.getUUID("id"),
                    entry.getString("name"),
                    entry.getInt("training"),
                    entry.getString("weapon"),
                    entry.getLong("trainingDoneAt")));
        }

        // Absent in saves from before bodyguards existed. An empty list is the right answer for a
        // city that never hired one.
        ListTag guardList = tag.getList("guards", Tag.TAG_COMPOUND);
        for (int i = 0; i < guardList.size() && city.guards.size() < MAX_GUARDS; i++) {
            CompoundTag entry = guardList.getCompound(i);
            city.guards.add(new Soldier(
                    entry.getUUID("id"),
                    entry.getString("name"),
                    entry.getInt("training"),
                    entry.getString("weapon"),
                    entry.getLong("trainingDoneAt")));
        }

        readIds(tag, "granted", city.granted);
        readIds(tag, "wars", city.wars);
        readIds(tag, "peaceOffers", city.peaceOffers);
        ListTag started = tag.getList("warStarted", Tag.TAG_COMPOUND);
        for (int i = 0; i < started.size(); i++) {
            CompoundTag row = started.getCompound(i);
            if (row.hasUUID("id")) {
                city.warStarted.put(row.getUUID("id"), row.getLong("at"));
            }
        }
        // Sanitised on the way in, so a city saved before flags existed comes back with a blank
        // one rather than an empty array everything downstream would have to guard against.
        city.flag = CityFlag.sanitise(tag.getByteArray("flag"));
        ListTag deals = tag.getList("dealings", Tag.TAG_COMPOUND);
        for (int i = 0; i < deals.size(); i++) {
            CompoundTag row = deals.getCompound(i);
            if (!row.hasUUID("id")) {
                continue;
            }
            Dealings held = new Dealings();
            held.pacts = row.getInt("pacts");
            // Absent on a city saved before pacts existed, and a price of zero read from a missing
            // tag would silently turn every future export into a gift.
            held.powerPrice = row.contains("powerPrice") ? row.getInt("powerPrice") : 1;
            held.waterPrice = row.contains("waterPrice") ? row.getInt("waterPrice") : 1;
            city.dealings.put(row.getUUID("id"), held);
        }

        // Absent on any city saved before alert levels existed. getString returns "" for a missing
        // key, so byId falls straight through to PEACE - which is the right answer for a city
        // nobody has ever put on alert, and is what the sirens read.
        city.alertLevel = AlertLevel.byId(tag.getString("alertLevel"), AlertLevel.PEACE);
        // Absent in saves from before the mute existed, and getBoolean answers false for a missing
        // key, which is exactly the right default: a city that has never been muted is not muted.
        city.hushed = tag.getBoolean("hushed");

        // Capped on the way in as well as on the way out. A save hand-edited to hold ten thousand
        // ledger rows would otherwise be loaded in full and then written back out in full forever.
        ListTag ledgerList = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < ledgerList.size() && city.ledger.size() < MAX_LEDGER; i++) {
            CompoundTag row = ledgerList.getCompound(i);
            city.ledger.add(new LedgerEntry(
                    row.getLong("at"), row.getString("key"), row.getString("detail")));
        }
        return city;
    }
}
