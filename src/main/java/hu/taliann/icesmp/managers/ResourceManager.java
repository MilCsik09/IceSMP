package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class "power" resource — the WoW-style cost pool shown as a coloured bar <b>in the HUD
 * sidebar</b> (a scoreboard line, deliberately NOT a boss bar so it never stacks with the world-boss
 * bar). It is a 0–max meter, named and coloured per class (Mana / Düh / Energia / Runikus Erő / Csi …):
 * <b>casting a spell SPENDS it</b>, and it <b>regenerates over time</b>. If a spell costs more than the
 * player currently has, the cast is blocked (the legacy hunger/XP/HP costs only apply when this system
 * is disabled in config).
 *
 * <p>Regeneration is computed lazily on access (no scheduler needed): every read first credits the
 * time elapsed since the last update, so the pool stays accurate whether it's polled by the HUD tick
 * or touched by a cast. All state is UUID-keyed in concurrent maps, so reads/writes are thread-safe and
 * Folia-correct without touching any entity off its region thread.
 */
public final class ResourceManager implements PlayerStateCleanup {

    /** E25/E32 — setter-injektált pool-bónusz lookup (pakt + sárkánytojás-relikvia). */
    private volatile java.util.function.ToDoubleFunction<UUID> maxMultiplier;
    /** Optional compact class-gameplay suffix rendered on the same HUD resource line. */
    private volatile java.util.function.Function<Player, Component> hudSuffix = ignored -> Component.empty();
    private volatile java.util.function.Function<Player, hu.taliann.icesmp.classspec.integration.ClassHudState>
            classHudState = ignored -> hu.taliann.icesmp.classspec.integration.ClassHudState.empty();

    public void setMaxMultiplier(final java.util.function.ToDoubleFunction<UUID> maxMultiplier) {
        this.maxMultiplier = maxMultiplier;
    }

    public void setHudSuffix(final java.util.function.Function<Player, Component> hudSuffix) {
        this.hudSuffix = hudSuffix == null ? ignored -> Component.empty() : hudSuffix;
    }

    public void setClassHudState(final java.util.function.Function<Player,
            hu.taliann.icesmp.classspec.integration.ClassHudState> provider) {
        classHudState = provider == null
                ? ignored -> hu.taliann.icesmp.classspec.integration.ClassHudState.empty() : provider;
    }

    /** Owner-thread capture only; async integrations consume the copy embedded in HudSnapshot. */
    public hu.taliann.icesmp.classspec.integration.ClassHudState classHudState(final Player player) {
        try {
            final var snapshot = classHudState.apply(player);
            return snapshot == null ? hu.taliann.icesmp.classspec.integration.ClassHudState.empty() : snapshot;
        } catch (final RuntimeException invalidRuntimeState) {
            return hu.taliann.icesmp.classspec.integration.ClassHudState.empty();
        }
    }

    /** Harc utáni türelmi idő: eddig számít "harcban" a játékos (düh-típusú tárnál nincs decay). */
    private static final long COMBAT_GRACE_MS = 5000L;

    private final ConfigManager configManager;
    private final JobManager jobManager;
    /** Current resource value at {@link #lastRegen} time (lazily regenerated on access). */
    private final Map<UUID, Double> resource = new ConcurrentHashMap<>();
    /** Epoch millis of the last time a player's resource was regenerated. */
    private final Map<UUID, Long> lastRegen = new ConcurrentHashMap<>();
    /**
     * UUID → kaszt-id (kisbetűs JobType-név) cache a kasztonkénti profilokhoz. A
     * {@code current(UUID)} lusta regen-útvonalán nincs Player-példány (a PDC-t nem olvashatnánk
     * biztonságosan idegen régió-szálról), ezért minden Player-t kapó publikus metódus frissíti a
     * cache-t a játékos SAJÁT régió-szálán (HUD-tick, cast) — a UUID-alapú olvasók ebből dolgoznak.
     */
    private final Map<UUID, String> jobIdCache = new ConcurrentHashMap<>();
    /** Epoch millis of the player's last dealt hit (combat-gain / idle-decay gate). */
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    public ResourceManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    /** Whether the resource-cost system is active. When false, spells fall back to hunger/XP/HP costs. */
    public boolean isEnabled() {
        return configManager.getBoolean("spells.resource.enabled", true);
    }

    /**
     * Whether this spell is paid for with the class resource (hybrid model). Each spell keeps its
     * thematic cost where one fits, and uses the class pool otherwise:
     * <ul>
     *   <li><b>HEALTH</b> (blood magic / self-sacrifice) — always kept on HP.</li>
     *   <li><b>XP</b> ≥ {@code xp-ritual-threshold} (great arcane rituals, summons, weather, signature
     *       ultis) — kept on XP.</li>
     *   <li><b>HUNGER</b> ≥ {@code hunger-heavy-threshold} (heavy physical effort: stances, second-wind,
     *       big totem/beast summons) — kept on hunger.</li>
     *   <li>everything else (light arcane / light stamina) — the class resource, so every class's bar is
     *       used by its bread-and-butter spells.</li>
     * </ul>
     * Always false when the system is disabled (then every spell uses its declared hunger/XP/HP cost).
     *
     * @param spell the spell being cast
     * @return true if it should be paid from the class resource pool
     */
    public boolean usesResource(final Spell spell) {
        if (!isEnabled()) {
            return false;
        }
        // Spellenkénti explicit felülbírálás (spell-balance.<id>.use-resource: false):
        // a playtest-balansz szerint néhány mobilitás-spell tudatosan a tematikus költségét
        // (éhség) égesse a kaszt-erőforrás helyett.
        final String overrideKey = "spell-balance." + spell.getId() + ".use-resource";
        if (configManager.getConfiguration() != null && configManager.getConfiguration().isSet(overrideKey)) {
            return configManager.getBoolean(overrideKey, true);
        }
        return switch (spell.getCostType()) {
            case HEALTH -> false;
            case XP -> spell.getCostAmount() < configManager.getInt("spells.resource.xp-ritual-threshold", 80);
            case HUNGER -> spell.getCostAmount() < configManager.getInt("spells.resource.hunger-heavy-threshold", 8);
        };
    }

    /**
     * Frissíti a UUID→kaszt cache-t. CSAK a játékos saját régió-szálán hívható (PDC-olvasás!) —
     * ezt a Player-t kapó publikus metódusok garantálják (HUD-tick, canAfford/consume a castnál).
     */
    private void cacheJob(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            jobIdCache.remove(player.getUniqueId());
        } else {
            jobIdCache.put(player.getUniqueId(), job.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * Kasztonkénti profil-érték: {@code spells.resource.class.<kaszt-id>.<kulcs>},
     * ha nincs beállítva (vagy a kaszt még ismeretlen), a globális default érvényes.
     */
    private double profile(final UUID id, final String key, final double globalDefault) {
        final String jobId = jobIdCache.get(id);
        if (jobId == null) {
            return globalDefault;
        }
        return configManager.getDouble("spells.resource.class." + jobId + "." + key, globalDefault);
    }

    private double max(final UUID id) {
        final java.util.function.ToDoubleFunction<UUID> bonusRef = maxMultiplier;
        final double multiplier = bonusRef == null ? 1.0D : Math.max(0.1D, bonusRef.applyAsDouble(id));
        return Math.max(10.0D, profile(id, "max",
                configManager.getDouble("spells.resource.max", 100.0D)) * multiplier);
    }

    private double regenPerSecond(final UUID id) {
        return Math.max(0.0D, profile(id, "regen-per-second",
                configManager.getDouble("spells.resource.regen-per-second", 8.0D)));
    }

    /**
     * The player's current resource, after crediting the time elapsed since the last access.
     * Players start (and rejoin) at full so they can cast immediately.
     *
     * <p>Kasztonkénti profilok: düh-típusú táraknál ({@code idle-decay-per-second > 0})
     * harcon kívül a tár NEM töltődik, hanem ürül; harcban (az utolsó ütéstől számított türelmi
     * időn belül) a normál regen fut.
     */
    private double current(final UUID id) {
        final long now = System.currentTimeMillis();
        final double maxValue = max(id);
        double value = Math.min(maxValue, resource.getOrDefault(id, maxValue));
        final Long last = lastRegen.get(id);
        if (last != null) {
            final double elapsedSeconds = (now - last) / 1000.0D;
            final double idleDecay = Math.max(0.0D, profile(id, "idle-decay-per-second", 0.0D));
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

    /**
     * A játékos ütést vitt be (ResourceCombatListener, MONITOR): harc-időbélyeg + a kaszt-profil
     * {@code combat-gain-per-hit} értékével tölti a tárat (düh-minta). Csak konkurens map-eket ír,
     * ezért BÁRMELY régió-szálról biztonságosan hívható — Player-t szándékosan nem fogad, a
     * kaszt-cache-t a játékos saját szálán futó HUD-tick/cast tartja frissen.
     */
    public void onDamageDealt(final UUID damagerId) {
        if (!isEnabled()) {
            return;
        }
        lastCombat.put(damagerId, System.currentTimeMillis());
        final double gain = Math.max(0.0D, profile(damagerId, "combat-gain-per-hit", 0.0D));
        if (gain > 0.0D) {
            resource.put(damagerId, Math.min(max(damagerId), current(damagerId) + gain));
        }
    }

    /**
     * Elszenvedett találat: csak a harc-időbélyeget frissíti (düh-decay kapu + a HUD
     * harc-fókusz módja) — a megütött játékos is "harcban van", akkor is, ha nem üt vissza.
     * Szándékosan nincs isEnabled-kapu: a harc-státusz a HUD-nak akkor is kell, ha az
     * erőforrás-rendszer ki van kapcsolva. Csak konkurens map-írás, bármely szálról hívható.
     */
    public void onDamageTaken(final UUID victimId) {
        lastCombat.put(victimId, System.currentTimeMillis());
    }

    /** Harcban van-e a játékos (az utolsó adott VAGY kapott találat a türelmi időn belül). */
    public boolean isInCombat(final UUID playerId, final long graceMillis) {
        final Long last = lastCombat.get(playerId);
        return last != null && System.currentTimeMillis() - last <= graceMillis;
    }

    /**
     * The spell's EFFECTIVE resource cost: the per-spell config override
     * ({@code spell-balance.<id>.resource-cost}) if set, otherwise the cooldown-tier default
     * ({@link Spell#getResourceCost()}). Read at cast time, so {@code /icesmp reload} (or an
     * ingame {@code /icesmp config set}) applies it immediately — the cost is no longer fixed
     * to the cooldown tier.
     */
    public int costOf(final Spell spell) {
        return Math.max(0, configManager.getInt(
                "spell-balance." + spell.getId() + ".resource-cost", spell.getResourceCost()));
    }

    /** True if the player has enough resource to cast the spell (always true when the system is off). */
    public boolean canAfford(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return true;
        }
        cacheJob(player);
        return current(player.getUniqueId()) >= costOf(spell);
    }

    /** Spends the spell's resource cost. No-op when the system is off. */
    public void consume(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return;
        }
        cacheJob(player);
        final double after = Math.max(0.0D, current(player.getUniqueId()) - costOf(spell));
        resource.put(player.getUniqueId(), after);
    }

    /** Refunds the spell's resource cost (used when a cast no-ops). No-op when the system is off. */
    public void refund(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return;
        }
        cacheJob(player);
        final double after = Math.min(max(player.getUniqueId()), current(player.getUniqueId()) + costOf(spell));
        resource.put(player.getUniqueId(), after);
    }

    /**
     * The HUD sidebar line for this player's resource: a coloured 10-segment bar plus the value, or
     * {@code null} when the system is disabled. Read by {@code HudManager} on its scoreboard tick.
     *
     * @param player the viewer
     * @return the rendered line, or null
     */
    public Component hudLine(final Player player) {
        if (!isEnabled()) {
            return null;
        }
        cacheJob(player);
        final int maxValue = (int) Math.round(max(player.getUniqueId()));
        final int value = Math.max(0, Math.min(maxValue, (int) Math.round(current(player.getUniqueId()))));
        final int filled = Math.round(value / (float) maxValue * 10.0F);
        final NamedTextColor color = colorFor(player);
        Component bar = Component.text(nameFor(player) + " ", NamedTextColor.GRAY);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        final Component core = bar.append(Component.text(" " + value, NamedTextColor.WHITE));
        final Component suffix;
        try {
            suffix = hudSuffix.apply(player);
        } catch (final RuntimeException invalid) {
            return core;
        }
        return suffix == null ? core : core.append(suffix);
    }

    /** The player's class-resource display name (Mana / Düh / Energia …) for cast/cost messages. */
    public String resourceName(final Player player) {
        return nameFor(player);
    }

    /** Current resource value (rounded), for HUD/PlaceholderAPI display. */
    public int resourceValue(final Player player) {
        cacheJob(player);
        return (int) Math.round(current(player.getUniqueId()));
    }

    /** The GLOBAL resource maximum (class-profile-agnostic fallback). */
    public int resourceMax() {
        return (int) Math.round(Math.max(10.0D, configManager.getDouble("spells.resource.max", 100.0D)));
    }

    /** The player's resource maximum (class profile aware), for HUD/PlaceholderAPI display. */
    public int resourceMax(final Player player) {
        cacheJob(player);
        return (int) Math.round(max(player.getUniqueId()));
    }

    /** Current resource as a 0–100 percentage, for HUD/PlaceholderAPI display. */
    public int resourcePercent(final Player player) {
        cacheJob(player);
        final double maxValue = max(player.getUniqueId());
        return maxValue <= 0.0D ? 0 : (int) Math.round(current(player.getUniqueId()) / maxValue * 100.0D);
    }

    /**
     * A §-colour-coded 10-segment bar string of the resource for PlaceholderAPI
     * consumers (legacy-colour-aware renderers): filled segments in the CLASS
     * resource colour (Düh=piros, Energia=sárga…, mint a saját sidebar), empty
     * ones dark grey, then a vivid numeric counter — an uncoloured bar was
     * nearly unreadable on the scoreboard.
     */
    public String resourceBarPlain(final Player player) {
        cacheJob(player);
        final int maxValue = (int) Math.round(max(player.getUniqueId()));
        final int value = Math.max(0, Math.min(maxValue, (int) Math.round(current(player.getUniqueId()))));
        final int filled = maxValue <= 0 ? 0 : Math.round(value / (float) maxValue * 10.0F);
        Component bar = Component.empty();
        final NamedTextColor color = colorFor(player);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text(" " + value, NamedTextColor.YELLOW))
                .append(Component.text("/" + maxValue, NamedTextColor.GRAY));
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(bar);
    }

    private String nameFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return "Erő";
        }
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
            default -> "Mana"; // Varázsló, Sámán, Pap
        };
    }

    private NamedTextColor colorFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        return switch (job) {
            case WARRIOR, DEATH_KNIGHT -> NamedTextColor.RED;
            case ARCHER, DRUID -> NamedTextColor.GREEN;
            case ASSASSIN, MONK, PALADIN -> NamedTextColor.YELLOW;
            case WARLOCK, DEMON_HUNTER, EVOKER -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA; // Varázsló, Sámán, Pap
        };
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        resource.remove(playerId);
        lastRegen.remove(playerId);
        jobIdCache.remove(playerId);
        lastCombat.remove(playerId);
    }
}
