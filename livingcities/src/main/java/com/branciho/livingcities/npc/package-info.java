/**
 * The physical NPC layer: the handful of real entities that stand in for a city's virtual population.
 *
 * <h2>What this package is</h2>
 *
 * <p>Everything here is <b>decoration derived from simulation state</b>. A city of fifty thousand is
 * still an integer in {@link com.branciho.livingcities.city.CityStats}; this package decides how many
 * bodies a player standing on a particular street corner should be able to see, puts them there, and
 * takes them away again. Nothing in the simulation reads back from it, and nothing here is persisted.
 * Delete every citizen entity in the world and the city is completely unaffected.
 *
 * <p>That one-way dependency is the whole design. It is what lets the crowd be capped, throttled or
 * switched off entirely by config without the economy noticing, and it is why
 * {@link com.branciho.livingcities.entity.CitizenEntity} is registered {@code noSave()}: a citizen
 * that survived a restart would be a citizen the director did not know about.
 *
 * <h2>Layering</h2>
 *
 * <ul>
 *   <li>{@link com.branciho.livingcities.npc.CitizenActivity} and
 *       {@link com.branciho.livingcities.npc.CitizenRole} are pure data - a day/night curve and the
 *       role mix it produces. They import nothing from Minecraft at all, which makes the curve
 *       directly unit-testable and lets the same table drive routing later without moving it.</li>
 *   <li>{@link com.branciho.livingcities.npc.CrowdBudget} is the arithmetic: local city population,
 *       nearby registered buildings and their zone mix, the time-of-day curve and the server config
 *       in, one integer out. It touches no world.</li>
 *   <li>{@link com.branciho.livingcities.npc.StreetSpawnFinder} is the only part that reads blocks.</li>
 *   <li>{@link com.branciho.livingcities.npc.CitizenSpawnDirector} owns the tick, the entity
 *       bookkeeping and the hysteresis, and is the only thing the integration layer calls.</li>
 * </ul>
 *
 * <h2>Threading contract</h2>
 *
 * <p><b>Server thread only.</b> Every method in this package that takes a {@code MinecraftServer},
 * {@code ServerLevel} or {@code Entity} must be called from the server thread. There is no background
 * work here and none should be added: spawning an entity, discarding one, and reading a
 * {@code BlockState} are all main-thread-only operations, and the director's cost is bounded by the
 * NPC cap rather than by city size, so there is nothing worth moving off-thread.
 *
 * <h2>Cost</h2>
 *
 * <p>The director runs one pass every {@code PASS_INTERVAL_TICKS} and does nothing on every other
 * tick. A pass is {@code O(spawned citizens + online players x nearby buildings)} - both small,
 * bounded numbers - and never iterates cities, buildings or population globally.
 */
package com.branciho.livingcities.npc;
