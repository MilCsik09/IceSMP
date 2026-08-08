package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class power resource plus the small transient combat loops that directly belong to that
 * resource. The class rework deliberately keeps these mechanics concrete: Warrior owns Frenzy/Guard,
 * Evoker owns its Essence sequencing. There is no generic meter/stack framework.
 *
 * <p>All UUID-only combat methods touch only concurrent maps plus the already-loaded PlayerProfile
 * cache. Player/entity access remains on callers' region threads. Durable class/spec state is never
 * mirrored here.</p>
 */
public final class ResourceManager implements PlayerStateCleanup {

    private static final long COMBAT_GRACE_MS = 5000L;
    private static final String DOCTRINE_BRANCH = "core";

    private static final Set<String> DEVASTATION_RED = Set.of(
            "pyre", "firestorm", "dragonrage", "eternity_breath");
    private static final Set<String> DEVASTATION_BLUE = Set.of(
            "disintegrate", "eternity_surge", "shattering_star");
    private static final Set<String> PRESERVATION_BUILDERS = Set.of(
            "dream_breath", "spiritbloom", "reversion");
    private static final Set<String> PRESERVATION_CONSUMERS = Set.of(
            "echo", "temporal_anomaly", "rewind");

    /** E25/E32 — setter-injected pool bonus lookup (pact + class relic Class Power). */
    private volatile java.util.function.ToDoubleFunction<UUID> maxMultiplier;

    private final ConfigManager configManager;
    private final JobManager jobManager;

    /** Current resource value at {@link #lastRegen} time (lazily regenerated on access). */
    private final Map<UUID, Double> resource = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegen = new ConcurrentHashMap<>();
    private final Map<UUID, String> jobIdCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    /** Warrior-only transient specialization state. */
    private final Map<UUID, StackState> frenzy = new ConcurrentHashMap<>();
    private final Map<UUID, StackState> guard = new ConcurrentHashMap<>();

    /** Evoker-only transient cast sequencing. */
    private final Map<UUID, EvokerCycle> evokerCycle = new ConcurrentHashMap<>();
    private final Map<UUID, SpendSnapshot> pendingSpend = new ConcurrentHashMap<>();

    public ResourceManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    public void setMaxMultiplier(final java.util.function.ToDoubleFunction<UUID> maxMultiplier) {
        this.maxMultiplier = maxMultiplier;
    }

    /** Whether the resource-cost system is active. */
    public boolean isEnabled() {
        return configManager.getBoolean("spells.resource.enabled", true);
    }

    /**
     * Hybrid cost model: blood sacrifice stays HEALTH, large rituals stay XP and heavy physical
     * effort stays HUNGER; bread-and-butter abilities use the class resource.
     */
    public boolean usesResource(final Spell spell) {
        if (!isEnabled()) return false;
        final String overrideKey = "spell-balance." + spell.getId() + ".use-resource";
        if (configManager.getConfiguration() != null
                && configManager.getConfiguration().isSet(overrideKey)) {
            return configManager.getBoolean(overrideKey, true);
        }
        return switch (spell.getCostType()) {
            case HEALTH -> false;
            case XP -> spell.getCostAmount() < configManager.getInt(
                    "spells.resource.xp-ritual-threshold", 80);
            case HUNGER -> spell.getCostAmount() < configManager.getInt(
                    "spells.resource.hunger-heavy-threshold", 8);
        };
    }

    /** Must only be called while the caller owns the player's region thread. */
    private void cacheJob(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) jobIdCache.remove(player.getUniqueId());
        else jobIdCache.put(player.getUniqueId(), job.name().toLowerCase(java.util.Locale.ROOT));
    }

    private double profile(final UUID id, final String key, final double globalDefault) {
        final String jobId = jobIdCache.get(id);
        if (jobId == null) return globalDefault;
        return configManager.getDouble("spells.resource.class." + jobId + "." + key,
                globalDefault);
    }

    private double max(final UUID id) {
        final java.util.function.ToDoubleFunction<UUID> bonusRef = maxMultiplier;
        final double multiplier = bonusRef == null ? 1.0D
                : Math.max(0.1D, bonusRef.applyAsDouble(id));
        return Math.max(10.0D, profile(id, "max",
                configManager.getDouble("spells.resource.max", 100.0D)) * multiplier);
    }

    private double regenPerSecond(final UUID id) {
        return Math.max(0.0D, profile(id, "regen-per-second",
                configManager.getDouble("spells.resource.regen-per-second", 8.0D)));
    }

    private double current(final UUID id) {
        final long now = System.currentTimeMillis();
        final double maxValue = max(id);
        double value = Math.min(maxValue, resource.getOrDefault(id, maxValue));
        final Long last = lastRegen.get(id);
        if (last != null) {
            final double elapsedSeconds = Math.max(0.0D, (now - last) / 1000.0D);
            final double idleDecay = Math.max(0.0D,
                    profile(id, "idle-decay-per-second", 0.0D));
            final Long combat = lastCombat.get(id);
            final boolean inCombat = combat != null && now - combat <= COMBAT_GRACE_MS;
            if (idleDecay > 0.0D && !inCombat) {
                value = Math.max(0.0D, value - elapsedSeconds * idleDecay);
            } else if (value < maxValue) {
                value = Math.min(maxValue, value + elapsedSeconds * regenPerSecond(id));
            }
        }
        resource.put(id, value);
        lastRegen.put(id, now);
        return value;
    }

    // ---------------------------------------------------------------------
    // Warrior vertical slice — concrete transient Frenzy / Guard loops
    // ---------------------------------------------------------------------

    /**
     * Applies Berserker Frenzy or Guardian Counterguard to outgoing damage. The caller owns the
     * damaged entity's region and remains responsible for writing the returned value to the event.
     */
    public double modifyOutgoingDamage(final UUID damagerId, final double baseDamage,
                                       final boolean pvp) {
        if (damagerId == null || baseDamage <= 0.0D) return baseDamage;
        final ClassLoadout loadout = activeLoadout(damagerId);
        if (loadout == null) return baseDamage;

        if ("berserker".equals(loadout.specializationId())) {
            final StackState state = liveFrenzy(damagerId, loadout);
            if (state == null || state.stacks() <= 0) return baseDamage;
            final int maxStacks = frenzyMaxStacks();
            if (loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                    && state.stacks() >= maxStacks) {
                frenzy.remove(damagerId);
                final double burst = configManager.getDouble(
                        "class-gameplay.warrior.berserker.capstone."
                                + (pvp ? "pvp" : "pve") + "-burst-percent",
                        pvp ? 18.0D : 35.0D);
                return baseDamage * (1.0D + percent(burst));
            }

            final double perStack = configManager.getDouble(
                    "class-gameplay.warrior.berserker.frenzy."
                            + (pvp ? "pvp" : "pve") + "-damage-per-stack-percent",
                    pvp ? 1.5D : 3.0D);
            double multiplier = 1.0D + percent(perStack * state.stacks());
            if ("tempered_fury".equals(doctrine(loadout)) && isEnabled()) {
                final double threshold = clamp(configManager.getDouble(
                        "class-gameplay.warrior.berserker.frenzy."
                                + "tempered-fury-resource-threshold-percent", 70.0D),
                        0.0D, 100.0D);
                final double resourcePercent = max(damagerId) <= 0.0D ? 0.0D
                        : current(damagerId) / max(damagerId) * 100.0D;
                if (resourcePercent >= threshold) {
                    multiplier += percent(configManager.getDouble(
                            "class-gameplay.warrior.berserker.frenzy.tempered-fury-"
                                    + (pvp ? "pvp" : "pve") + "-bonus-percent",
                            pvp ? 4.0D : 8.0D));
                }
            }
            return baseDamage * Math.max(1.0D, multiplier);
        }

        if ("guardian".equals(loadout.specializationId())
                && "counterguard".equals(doctrine(loadout))) {
            final StackState state = liveGuard(damagerId, loadout);
            if (state == null || state.stacks() <= 0) return baseDamage;
            guard.remove(damagerId);
            final double perStack = configManager.getDouble(
                    "class-gameplay.warrior.guardian.guard.counterguard-"
                            + (pvp ? "pvp" : "pve") + "-damage-per-stack-percent",
                    pvp ? 2.5D : 5.0D);
            return baseDamage * (1.0D + percent(perStack * state.stacks()));
        }
        return baseDamage;
    }

    /** Guardian mitigation is based on Guard already built before this hit. */
    public double modifyIncomingDamage(final UUID victimId, final double baseDamage,
                                       final boolean pvp) {
        if (victimId == null || baseDamage <= 0.0D) return baseDamage;
        final ClassLoadout loadout = activeLoadout(victimId);
        if (loadout == null || !"guardian".equals(loadout.specializationId())) return baseDamage;
        final StackState state = liveGuard(victimId, loadout);
        if (state == null || state.stacks() <= 0) return baseDamage;

        final int maxStacks = guardMaxStacks(loadout);
        if (loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                && state.stacks() >= maxStacks) {
            guard.remove(victimId);
            final double hitCap = Math.max(0.5D, configManager.getDouble(
                    "class-gameplay.warrior.guardian.capstone."
                            + (pvp ? "pvp" : "pve") + "-hit-cap",
                    pvp ? 10.0D : 8.0D));
            return Math.min(baseDamage, hitCap);
        }

        final double perStack = Math.max(0.0D, configManager.getDouble(
                "class-gameplay.warrior.guardian.guard."
                        + (pvp ? "pvp" : "pve") + "-mitigation-per-stack-percent",
                pvp ? 2.0D : 4.0D));
        final double masteryCapBonus = Math.max(0.0D, configManager.getDouble(
                "class-gameplay.warrior.guardian.mastery.mitigation-cap-per-rank-percent",
                0.5D)) * loadout.mastery().rank();
        final double mitigation = Math.min(75.0D,
                perStack * state.stacks() + masteryCapBonus);
        return baseDamage * (1.0D - percent(mitigation));
    }

    /** Hit dealt: updates combat time, Rage gain and Berserker Frenzy. */
    public void onDamageDealt(final UUID damagerId) {
        if (damagerId == null) return;
        lastCombat.put(damagerId, System.currentTimeMillis());
        if (isEnabled()) {
            final double gain = Math.max(0.0D,
                    profile(damagerId, "combat-gain-per-hit", 0.0D));
            if (gain > 0.0D) {
                resource.put(damagerId,
                        Math.min(max(damagerId), current(damagerId) + gain));
            }
        }
        final ClassLoadout loadout = activeLoadout(damagerId);
        if (loadout != null && "berserker".equals(loadout.specializationId())) {
            final StackState previous = liveFrenzy(damagerId, loadout);
            final int gain = "blood_rush".equals(doctrine(loadout))
                    ? Math.max(1, configManager.getInt(
                    "class-gameplay.warrior.berserker.frenzy.blood-rush-stack-gain", 2))
                    : 1;
            final int next = Math.min(frenzyMaxStacks(),
                    (previous == null ? 0 : previous.stacks()) + gain);
            frenzy.put(damagerId, new StackState(next, System.currentTimeMillis()));
        }
    }

    /** Hit taken: updates combat time and builds Guardian Guard after the hit resolves. */
    public void onDamageTaken(final UUID victimId) {
        if (victimId == null) return;
        lastCombat.put(victimId, System.currentTimeMillis());
        final ClassLoadout loadout = activeLoadout(victimId);
        if (loadout != null && "guardian".equals(loadout.specializationId())) {
            final StackState previous = liveGuard(victimId, loadout);
            final int next = Math.min(guardMaxStacks(loadout),
                    (previous == null ? 0 : previous.stacks()) + 1);
            guard.put(victimId, new StackState(next, System.currentTimeMillis()));
        }
    }

    public boolean isInCombat(final UUID playerId, final long graceMillis) {
        final Long last = lastCombat.get(playerId);
        return last != null && System.currentTimeMillis() - last <= graceMillis;
    }

    private int frenzyMaxStacks() {
        return Math.max(1, configManager.getInt(
                "class-gameplay.warrior.berserker.frenzy.max-stacks", 5));
    }

    private int guardMaxStacks(final ClassLoadout loadout) {
        int maximum = Math.max(1, configManager.getInt(
                "class-gameplay.warrior.guardian.guard.max-stacks", 5));
        if ("hold_the_line".equals(doctrine(loadout))) {
            maximum += Math.max(0, configManager.getInt(
                    "class-gameplay.warrior.guardian.guard.hold-the-line-extra-stacks", 2));
        }
        return maximum;
    }

    private StackState liveFrenzy(final UUID id, final ClassLoadout loadout) {
        double duration = Math.max(0.1D, configManager.getDouble(
                "class-gameplay.warrior.berserker.frenzy.duration-seconds", 5.0D));
        if ("blood_rush".equals(doctrine(loadout))) {
            duration = Math.max(0.1D, configManager.getDouble(
                    "class-gameplay.warrior.berserker.frenzy.blood-rush-duration-seconds",
                    3.5D));
        }
        duration += Math.max(0.0D, configManager.getDouble(
                "class-gameplay.warrior.berserker.mastery.duration-per-rank-seconds",
                0.15D)) * loadout.mastery().rank();
        return liveStack(frenzy, id, duration);
    }

    private StackState liveGuard(final UUID id, final ClassLoadout loadout) {
        final double duration = Math.max(0.1D, configManager.getDouble(
                "class-gameplay.warrior.guardian.guard."
                        + ("hold_the_line".equals(doctrine(loadout))
                        ? "hold-the-line-duration-seconds" : "duration-seconds"),
                "hold_the_line".equals(doctrine(loadout)) ? 9.0D : 6.0D));
        return liveStack(guard, id, duration);
    }

    private static StackState liveStack(final Map<UUID, StackState> states, final UUID id,
                                        final double durationSeconds) {
        final StackState state = states.get(id);
        if (state == null) return null;
        final long maxAge = Math.max(100L, (long) (durationSeconds * 1000.0D));
        if (System.currentTimeMillis() - state.updatedAt() <= maxAge) return state;
        states.remove(id, state);
        return null;
    }

    // ---------------------------------------------------------------------
    // Evoker vertical slice — Essence sequencing through the existing cast cost boundary
    // ---------------------------------------------------------------------

    /** Base resource cost from live spell balance, before specialization sequencing. */
    public int costOf(final Spell spell) {
        return Math.max(0, configManager.getInt(
                "spell-balance." + spell.getId() + ".resource-cost",
                spell.getResourceCost()));
    }

    public int effectiveCost(final Player player, final Spell spell) {
        cacheJob(player);
        final int base = costOf(spell);
        final ClassLoadout loadout = activeLoadout(player.getUniqueId());
        if (loadout == null) return base;
        return Math.max(0, switch (loadout.specializationId()) {
            case "devastation" -> devastationCost(player.getUniqueId(), spell.getId(), base, loadout);
            case "preservation" -> preservationCost(player.getUniqueId(), spell.getId(), base, loadout);
            default -> base;
        });
    }

    public boolean canAfford(final Player player, final Spell spell) {
        if (!isEnabled()) return true;
        cacheJob(player);
        return current(player.getUniqueId()) >= effectiveCost(player, spell);
    }

    /**
     * The existing resource spend boundary also advances Evoker's specialization-local sequence.
     * A no-op cast is rolled back by {@link #refund(Player, Spell)}; successful casts require no
     * extra persistence or scheduler.
     */
    public void consume(final Player player, final Spell spell) {
        if (!isEnabled()) return;
        cacheJob(player);
        final UUID id = player.getUniqueId();
        final EvokerCycle previous = evokerCycle.get(id);
        final boolean hadPrevious = previous != null;
        final int cost = effectiveCost(player, spell);
        pendingSpend.put(id, new SpendSnapshot(cost, previous, hadPrevious));
        resource.put(id, Math.max(0.0D, current(id) - cost));
        advanceEvokerCycle(id, spell.getId(), activeLoadout(id));
    }

    public void refund(final Player player, final Spell spell) {
        if (!isEnabled()) return;
        cacheJob(player);
        final UUID id = player.getUniqueId();
        final SpendSnapshot snapshot = pendingSpend.remove(id);
        final int refund = snapshot == null ? effectiveCost(player, spell) : snapshot.cost();
        resource.put(id, Math.min(max(id), current(id) + refund));
        if (snapshot != null) {
            if (snapshot.hadCycle()) evokerCycle.put(id, snapshot.previousCycle());
            else evokerCycle.remove(id);
        }
    }

    private int devastationCost(final UUID id, final String spellId, final int base,
                                final ClassLoadout loadout) {
        final EvokerCycle state = liveEvokerCycle(id, loadout);
        if (state == null) return Math.max(0, base - masteryCostReduction(loadout));
        final String aspect = devastationAspect(spellId);
        if (aspect.isEmpty()) return Math.max(0, base - masteryCostReduction(loadout));

        final int maxStacks = Math.max(1, configManager.getInt(
                "class-gameplay.evoker.devastation.harmony.max-stacks", 3));
        if (loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                && state.stacks() >= maxStacks
                && configManager.getStringList(
                "class-gameplay.evoker.devastation.capstone.finishers").contains(spellId)) {
            return 0;
        }

        int reduction = masteryCostReduction(loadout);
        if (!state.lastType().isEmpty() && !state.lastType().equals(aspect)) {
            reduction += Math.max(0, configManager.getInt(
                    "class-gameplay.evoker.devastation.harmony."
                            + "alternating-cost-reduction-per-stack", 2)) * state.stacks();
            if ("chromatic_flow".equals(doctrine(loadout))) {
                reduction += Math.max(0, configManager.getInt(
                        "class-gameplay.evoker.devastation.harmony."
                                + "chromatic-flow-extra-reduction", 2));
            }
        }
        if ("focused_flame".equals(doctrine(loadout)) && "red".equals(aspect)) {
            reduction += Math.max(0, configManager.getInt(
                    "class-gameplay.evoker.devastation.harmony."
                            + "focused-flame-red-reduction", 3));
        }
        return Math.max(0, base - reduction);
    }

    private int preservationCost(final UUID id, final String spellId, final int base,
                                 final ClassLoadout loadout) {
        final EvokerCycle state = liveEvokerCycle(id, loadout);
        int reduction = masteryCostReduction(loadout);
        if (state == null || !PRESERVATION_CONSUMERS.contains(spellId)) {
            return Math.max(0, base - reduction);
        }
        final int maxStacks = Math.max(1, configManager.getInt(
                "class-gameplay.evoker.preservation.echo.max-stacks", 3));
        if (loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                && state.stacks() >= maxStacks
                && spellId.equals(configManager.getString(
                "class-gameplay.evoker.preservation.capstone.finisher", "rewind"))) {
            return 0;
        }
        reduction += Math.max(0, configManager.getInt(
                "class-gameplay.evoker.preservation.echo.cost-reduction-per-stack", 2))
                * state.stacks();
        if ("echo_weaver".equals(doctrine(loadout)) && "echo".equals(spellId)) {
            reduction += Math.max(0, configManager.getInt(
                    "class-gameplay.evoker.preservation.echo.echo-weaver-extra-reduction", 2));
        }
        if ("time_keeper".equals(doctrine(loadout)) && "rewind".equals(spellId)) {
            reduction += Math.max(0, configManager.getInt(
                    "class-gameplay.evoker.preservation.echo.time-keeper-rewind-reduction", 4));
        }
        return Math.max(0, base - reduction);
    }

    private int masteryCostReduction(final ClassLoadout loadout) {
        if (loadout.mastery().rank() < 5) return 0;
        return Math.max(0, configManager.getInt(
                "class-gameplay.evoker." + loadout.specializationId()
                        + ".mastery.cost-reduction-at-rank-5", 1));
    }

    private void advanceEvokerCycle(final UUID id, final String spellId,
                                    final ClassLoadout loadout) {
        if (loadout == null) return;
        final long now = System.currentTimeMillis();
        if ("devastation".equals(loadout.specializationId())) {
            final String aspect = devastationAspect(spellId);
            if (aspect.isEmpty()) return;
            final EvokerCycle previous = liveEvokerCycle(id, loadout);
            final int maxStacks = Math.max(1, configManager.getInt(
                    "class-gameplay.evoker.devastation.harmony.max-stacks", 3));
            if (previous != null && loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                    && previous.stacks() >= maxStacks
                    && configManager.getStringList(
                    "class-gameplay.evoker.devastation.capstone.finishers").contains(spellId)) {
                evokerCycle.remove(id);
                return;
            }
            final int next = previous == null ? 1
                    : previous.lastType().equals(aspect) ? 1
                    : Math.min(maxStacks, previous.stacks() + 1);
            evokerCycle.put(id, new EvokerCycle(aspect, next, now));
            return;
        }

        if ("preservation".equals(loadout.specializationId())) {
            final EvokerCycle previous = liveEvokerCycle(id, loadout);
            final int maxStacks = Math.max(1, configManager.getInt(
                    "class-gameplay.evoker.preservation.echo.max-stacks", 3));
            if (PRESERVATION_BUILDERS.contains(spellId)) {
                final int next = Math.min(maxStacks,
                        previous == null ? 1 : previous.stacks() + 1);
                evokerCycle.put(id, new EvokerCycle("echo", next, now));
            } else if (PRESERVATION_CONSUMERS.contains(spellId) && previous != null) {
                if ("echo_weaver".equals(doctrine(loadout)) && "echo".equals(spellId)
                        && previous.stacks() > 1) {
                    evokerCycle.put(id, new EvokerCycle("echo", previous.stacks() - 1, now));
                } else {
                    evokerCycle.remove(id);
                }
            }
        }
    }

    private EvokerCycle liveEvokerCycle(final UUID id, final ClassLoadout loadout) {
        final EvokerCycle state = evokerCycle.get(id);
        if (state == null) return null;
        final String key = "devastation".equals(loadout.specializationId())
                ? "class-gameplay.evoker.devastation.harmony.duration-seconds"
                : "class-gameplay.evoker.preservation.echo.duration-seconds";
        final double fallback = "devastation".equals(loadout.specializationId()) ? 8.0D : 10.0D;
        final long maxAge = Math.max(100L,
                (long) (Math.max(0.1D, configManager.getDouble(key, fallback)) * 1000.0D));
        if (System.currentTimeMillis() - state.updatedAt() <= maxAge) return state;
        evokerCycle.remove(id, state);
        return null;
    }

    private static String devastationAspect(final String spellId) {
        if (DEVASTATION_RED.contains(spellId)) return "red";
        if (DEVASTATION_BLUE.contains(spellId)) return "blue";
        return "";
    }

    // ---------------------------------------------------------------------
    // Resource UI / common helpers
    // ---------------------------------------------------------------------

    public Component hudLine(final Player player) {
        if (!isEnabled()) return null;
        cacheJob(player);
        final UUID id = player.getUniqueId();
        final int maxValue = (int) Math.round(max(id));
        final int value = Math.max(0,
                Math.min(maxValue, (int) Math.round(current(id))));
        final int filled = Math.round(value / (float) maxValue * 10.0F);
        final NamedTextColor color = colorFor(player);
        Component bar = Component.text(nameFor(player) + " ", NamedTextColor.GRAY);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text(" " + value, NamedTextColor.WHITE));
        final Component mechanic = mechanicHud(id);
        return mechanic == null ? bar : bar.append(mechanic);
    }

    private Component mechanicHud(final UUID id) {
        final ClassLoadout loadout = activeLoadout(id);
        if (loadout == null) return null;
        return switch (loadout.specializationId()) {
            case "berserker" -> {
                final StackState state = liveFrenzy(id, loadout);
                yield state == null ? null : Component.text("  ⚔x" + state.stacks(),
                        NamedTextColor.RED);
            }
            case "guardian" -> {
                final StackState state = liveGuard(id, loadout);
                yield state == null ? null : Component.text("  ◆x" + state.stacks(),
                        NamedTextColor.AQUA);
            }
            case "devastation", "preservation" -> {
                final EvokerCycle state = liveEvokerCycle(id, loadout);
                yield state == null ? null : Component.text("  ✦x" + state.stacks(),
                        NamedTextColor.LIGHT_PURPLE);
            }
            default -> null;
        };
    }

    public String resourceName(final Player player) {
        return nameFor(player);
    }

    public int resourceValue(final Player player) {
        cacheJob(player);
        return (int) Math.round(current(player.getUniqueId()));
    }

    public int resourceMax() {
        return (int) Math.round(Math.max(10.0D,
                configManager.getDouble("spells.resource.max", 100.0D)));
    }

    public int resourceMax(final Player player) {
        cacheJob(player);
        return (int) Math.round(max(player.getUniqueId()));
    }

    public int resourcePercent(final Player player) {
        cacheJob(player);
        final double maxValue = max(player.getUniqueId());
        return maxValue <= 0.0D ? 0
                : (int) Math.round(current(player.getUniqueId()) / maxValue * 100.0D);
    }

    public String resourceBarPlain(final Player player) {
        cacheJob(player);
        final int maxValue = (int) Math.round(max(player.getUniqueId()));
        final int value = Math.max(0,
                Math.min(maxValue, (int) Math.round(current(player.getUniqueId()))));
        final int filled = maxValue <= 0 ? 0
                : Math.round(value / (float) maxValue * 10.0F);
        Component bar = Component.empty();
        final NamedTextColor color = colorFor(player);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text(" " + value, NamedTextColor.YELLOW))
                .append(Component.text("/" + maxValue, NamedTextColor.GRAY));
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(bar);
    }

    private String nameFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) return "Erő";
        return switch (job) {
            case WARRIOR -> "Düh";
            case ARCHER -> "Fókusz";
            case ASSASSIN -> "Energia";
            case DEATH_KNIGHT -> "Runikus Erő";
            case MONK -> "Csi";
            case WARLOCK -> "Lélekerő";
            case DEMON_HUNTER -> "Fúria";
            case PALADIN -> "Szent Erő";
            case DRUID -> "Természeti Erő";
            case EVOKER -> "Eszencia";
            default -> "Mana";
        };
    }

    private NamedTextColor colorFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) return NamedTextColor.LIGHT_PURPLE;
        return switch (job) {
            case WARRIOR, DEATH_KNIGHT -> NamedTextColor.RED;
            case ARCHER, DRUID -> NamedTextColor.GREEN;
            case ASSASSIN, MONK, PALADIN -> NamedTextColor.YELLOW;
            case WARLOCK, DEMON_HUNTER, EVOKER -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA;
        };
    }

    private ClassLoadout activeLoadout(final UUID playerId) {
        try {
            final ClassSpecSection profile = PlayerProfileAuthority.current().requireSection(
                    playerId, ProfileSectionId.CLASS_SPEC, ClassSpecSection.class);
            if (profile.activeSlot() == null) return null;
            final ClassLoadout loadout = profile.loadout(profile.activeSlot());
            return loadout.status() == LoadoutStatus.ACTIVE ? loadout : null;
        } catch (final RuntimeException notReady) {
            return null;
        }
    }

    private static String doctrine(final ClassLoadout loadout) {
        return loadout.doctrineChoices().getOrDefault(DOCTRINE_BRANCH, "");
    }

    private static double percent(final double value) {
        return Math.max(0.0D, value) / 100.0D;
    }

    private static double clamp(final double value, final double minimum, final double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        resource.remove(playerId);
        lastRegen.remove(playerId);
        jobIdCache.remove(playerId);
        lastCombat.remove(playerId);
        frenzy.remove(playerId);
        guard.remove(playerId);
        evokerCycle.remove(playerId);
        pendingSpend.remove(playerId);
    }

    private record StackState(int stacks, long updatedAt) { }
    private record EvokerCycle(String lastType, int stacks, long updatedAt) { }
    private record SpendSnapshot(int cost, EvokerCycle previousCycle, boolean hadCycle) { }
}
