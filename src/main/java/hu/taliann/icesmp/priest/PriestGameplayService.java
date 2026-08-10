package hu.taliann.icesmp.priest;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete Pap vertical-slice runtime.
 *
 * <p>The class core is the Litánia: a chosen prayer whose matching deeds count verses, and the
 * full verse count recites the prayer once for a blessing plus a short empowering window — a
 * discrete, re-earned payoff rather than a decaying meter. Fegyelem converts dealt damage into
 * healing and a shield web behind an explicit non-reentrant guard, so Engesztelés can never feed
 * itself. Csontpap (DARK, gated by the existing seal system) condenses controlled sacrifice into
 * Velő and Osszárium charges. Árnyék dances around the Őrület Küszöb, where the strain is a
 * refusable, floored cast cost — never a random self-kill. Durable state remains Profile v2.</p>
 */
public final class PriestGameplayService implements Listener, PlayerStateCleanup {

    private static final Set<String> PRIEST_SPECS =
            Set.of("discipline", "bone_priest", "shadow");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, PriestCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public PriestGameplayService(final JavaPlugin plugin,
                                 final ConfigManager config,
                                 final JobManager jobs,
                                 final SpecializationManager specs,
                                 final CatalystItemFactory soulbondFactory,
                                 final MessageManager messages) {
        this.plugin = Objects.requireNonNull(plugin);
        this.config = Objects.requireNonNull(config);
        this.jobs = Objects.requireNonNull(jobs);
        this.specs = Objects.requireNonNull(specs);
        this.soulbondFactory = Objects.requireNonNull(soulbondFactory);
        this.messages = Objects.requireNonNull(messages);
    }

    public void setCombatTracker(final ResourceManager resources) {
        combatTracker = Objects.requireNonNull(resources, "resources");
    }

    /** `/spec ima <vigasz|ostor|csend>` — the prayer is a class-level choice. */
    public boolean chooseLitany(final Player player, final String rawLitany) {
        if (!isPriest(player) || rawLitany == null) return false;
        final PriestCombatState.Litany litany = switch (normalize(rawLitany)) {
            case "vigasz" -> PriestCombatState.Litany.VIGASZ;
            case "ostor" -> PriestCombatState.Litany.OSTOR;
            case "csend" -> PriestCombatState.Litany.CSEND;
            default -> null;
        };
        if (litany == null) return false;
        state(player.getUniqueId()).chooseLitany(litany);
        player.sendActionBar(messages.getMessage("priest.litany.chosen",
                "<white>Litánia felvéve: <yellow>{litany}</yellow> — a hozzá illő tettek mondják el a verseket.</white>",
                Map.of("litany", litanyName(litany))));
        return true;
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.PRIEST) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.priest.active-kit.maximum", 7)));
        final Set<String> available = new LinkedHashSet<>();
        for (final String id : unlocked) available.add(normalize(id));
        final List<String> chosen = new ArrayList<>();
        if (favorites != null && !favorites.isEmpty()) {
            for (final String id : unlocked) {
                if (favorites.contains(id) && chosen.size() < maximum) chosen.add(id);
            }
        }
        if (!chosen.isEmpty()) return List.copyOf(chosen);
        final String activeSpec = activeSpec(player.getUniqueId());
        for (final String raw : config.getStringList(
                "classes.priest.active-kit." + activeSpec)) {
            final String id = normalize(raw);
            if (available.contains(id) && !chosen.contains(id) && chosen.size() < maximum) {
                chosen.add(id);
            }
        }
        for (final String id : unlocked) {
            if (chosen.size() >= maximum) break;
            if (!chosen.contains(id)) chosen.add(id);
        }
        return List.copyOf(chosen);
    }

    /**
     * Safety gates: a sacrifice and an over-threshold shadow cast both cost health, so both are
     * refused outright below the configured floor. The strain can never be the killing blow.
     */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isPriest(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);
        if ("bone_priest".equals(spec) && sacrificeSpells().contains(spellId)
                && player.getHealth() <= healthFloor(player,
                "classes.priest.bone.min-health-ratio", 0.35D)) {
            player.sendActionBar(messages.getMessage("priest.sacrifice.weak",
                    "<red>Túl kevés a véred az áldozathoz — az Osszárium vár.</red>"));
            return false;
        }
        if ("shadow".equals(spec) && madnessSpells().contains(spellId)
                && state(playerId).isBeyondThreshold(madnessThreshold(playerId),
                System.currentTimeMillis(), madnessDecayDelayMillis(), madnessDecayPerSecond())
                && player.getHealth() <= healthFloor(player,
                "classes.priest.shadow.min-health-ratio", 0.25D)) {
            player.sendActionBar(messages.getMessage("priest.madness.floor",
                    "<red>A Küszöbön túl az Őrület vámot szed — előbb szórd szét (dispersion).</red>"));
            return false;
        }
        return true;
    }

    /** Pure peek: a recited Litánia and the over-threshold Őrület empower the matching casts. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isPriest(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final PriestCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        if (state.isRecited(now) && litanySpells(litany(playerId, state)).contains(spellId)) {
            bonus += Math.max(0.0D, config.getDouble(
                    "classes.priest.litany.bonus-percent", 15.0D));
        }
        if ("shadow".equals(activeSpec(playerId)) && madnessSpells().contains(spellId)
                && state.isBeyondThreshold(madnessThreshold(playerId), now,
                madnessDecayDelayMillis(), madnessDecayPerSecond())) {
            double madness = Math.max(0.0D, config.getDouble(
                    "classes.priest.shadow.threshold-bonus-percent", 20.0D));
            if ("uresseg_ura".equals(doctrine(playerId, 50))) {
                madness += config.getDouble("classes.priest.shadow.void-extra-percent", 6.0D);
            }
            bonus += madness;
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.priest.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isPriest(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final PriestCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        recordVerse(player, state, spellId, now);

        switch (activeSpec(playerId)) {
            case "discipline" -> handleDisciplineCast(player, state, spellId, now);
            case "bone_priest" -> handleBonePriestCast(player, state, spellId);
            case "shadow" -> handleShadowCast(player, state, spellId, now);
            default -> { }
        }
    }

    /** A deed matching the chosen prayer speaks one verse; the full count recites it once. */
    private void recordVerse(final Player player, final PriestCombatState state,
                             final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final PriestCombatState.Litany litany = litany(playerId, state);
        if (!litanySpells(litany).contains(spellId)) return;
        final int required = versesRequired(playerId);
        if (state.addVerse(required) < required) return;
        if (!state.recite(required, now, Math.max(1000L, config.getLong(
                "classes.priest.litany.window-millis", 6000L)))) return;
        applyRecitationBlessing(player, litany);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.PRIEST,
                    config.getInt("classes.priest.mastery.recite-xp", 5));
        }
        player.sendActionBar(messages.getMessage("priest.litany.recited",
                "<yellow>✝ A(z) {litany} litánia elmondva.</yellow>",
                Map.of("litany", litanyName(litany))));
    }

    private void applyRecitationBlessing(final Player player,
                                         final PriestCombatState.Litany litany) {
        final int duration = Math.max(1, config.getInt(
                "classes.priest.litany.blessing-ticks", 100));
        switch (litany) {
            case VIGASZ -> healPlayer(player, Math.max(0.5D, config.getDouble(
                    "classes.priest.litany.solace-heal", 4.0D)));
            case OSTOR -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    duration, 0, false, true, true));
            case CSEND -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    duration, 0, false, true, true));
        }
    }

    // ===== Fegyelem =====

    private void handleDisciplineCast(final Player player, final PriestCombatState state,
                                      final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (!atonementSpells().contains(spellId)) return;
        long window = Math.max(1000L, config.getLong(
                "classes.priest.discipline.window-millis", 8000L));
        if ("tarto_vezekles".equals(doctrine(playerId, 40))) {
            window += Math.max(0L, config.getLong(
                    "classes.priest.discipline.lasting-extra-millis", 3000L));
        }
        state.armAtonement(now, window);
        player.sendActionBar(messages.getMessage("priest.atonement.armed",
                "<white>Engesztelés ébred — a sebzésed gyógyítássá és pajzzsá válik.</white>"));
    }

    /**
     * Engesztelés: dealt damage becomes healing and shield. The conversion runs inside the
     * explicit non-reentrant guard, so the heal it produces can never re-enter and feed itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingDamage(final EntityDamageByEntityEvent event) {
        final UUID attackerId = attackerId(event);
        if (attackerId == null || !isPriest(attackerId)
                || !"discipline".equals(activeSpec(attackerId))) return;
        if (event.getFinalDamage() <= 0.0D) return;
        final PriestCombatState state = state(attackerId);
        final long now = System.currentTimeMillis();
        if (!state.isAtonementActive(now)) return;
        if (!(event.getDamager() instanceof Player priest)) return;
        if (!state.beginConversion()) return;
        try {
            final double share = Math.max(0.0D, Math.min(100.0D, config.getDouble(
                    "classes.priest.discipline.conversion-percent", 35.0D))) / 100.0D;
            final double converted = event.getFinalDamage() * share;
            healPlayer(priest, converted);
            int shieldCap = Math.max(0, config.getInt(
                    "classes.priest.discipline.shield-cap", 20));
            if ("szeles_pajzs".equals(doctrine(attackerId, 30))) {
                shieldCap += Math.max(0, config.getInt(
                        "classes.priest.discipline.wide-extra-cap", 6));
            }
            state.addShield((int) Math.round(converted), shieldCap);
            if (isInCombat(attackerId)) {
                specs.contributeClassMastery(priest, JobType.PRIEST,
                        config.getInt("classes.priest.mastery.atonement-xp", 3));
            }
        } finally {
            state.endConversion();
        }
    }

    /** The shield web absorbs before the hit lands; Árnyék pays its Küszöb strain here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isPriest(victim)
                || event.getDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        if (!"discipline".equals(activeSpec(playerId))) return;
        final int absorbed = state(playerId).absorb((int) Math.floor(event.getDamage()));
        if (absorbed <= 0) return;
        event.setDamage(Math.max(0.0D, event.getDamage() - absorbed));
    }

    // ===== Csontpap (DARK) =====

    private void handleBonePriestCast(final Player player, final PriestCombatState state,
                                      final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (sacrificeSpells().contains(spellId)) {
            int gain = config.getInt("classes.priest.bone.marrow-per-sacrifice", 20);
            if ("mely_velo".equals(doctrine(playerId, 30))) {
                gain += Math.max(0, config.getInt("classes.priest.bone.deep-extra-marrow", 5));
            }
            state.addMarrow(gain, marrowMaximum(playerId));
            int threshold = Math.max(1, config.getInt(
                    "classes.priest.bone.ossuary-threshold", 40));
            if ("olcso_aldozat".equals(doctrine(playerId, 40))) {
                threshold -= Math.max(0, config.getInt(
                        "classes.priest.bone.cheap-threshold-reduction", 8));
            }
            if (state.condenseOssuary(threshold, ossuaryMaximum(playerId))) {
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.PRIEST,
                            config.getInt("classes.priest.mastery.ossuary-xp", 5));
                }
                player.sendActionBar(messages.getMessage("priest.ossuary.condensed",
                        "<gray>☠ Osszárium {count}/{max} — a csontok készen állnak.</gray>",
                        Map.of("count", Integer.toString(state.ossuary()),
                                "max", Integer.toString(ossuaryMaximum(playerId)))));
            }
            return;
        }
        if (!darkMendSpells().contains(spellId) || !state.consumeOssuary()) return;
        double heal = Math.max(0.5D, config.getDouble(
                "classes.priest.bone.empowered-heal", 5.0D));
        if ("nema_kiralyno_kegye".equals(doctrine(playerId, 50))) {
            heal += config.getDouble("classes.priest.bone.queen-extra-heal", 2.0D);
        }
        healPlayer(player, heal);
        if ("orok_csontfal".equals(doctrine(playerId, 50))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    config.getInt("classes.priest.bone.wall-absorption-ticks", 100),
                    0, false, true, true));
        }
        player.sendActionBar(messages.getMessage("priest.ossuary.spent",
                "<gray>Egy Osszárium-töltet elégett a gyógyításban.</gray>"));
    }

    // ===== Árnyék =====

    private void handleShadowCast(final Player player, final PriestCombatState state,
                                  final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (ventSpells().contains(spellId)) {
            int vent = config.getInt("classes.priest.shadow.vent-amount", 40);
            if ("gyors_szorodas".equals(doctrine(playerId, 40))) {
                vent += Math.max(0, config.getInt("classes.priest.shadow.quick-extra-vent", 15));
            }
            if (state.ventMadness(vent) > 0) {
                player.sendActionBar(messages.getMessage("priest.madness.vented",
                        "<dark_purple>Az Őrület szétszóródik.</dark_purple>"));
            }
            return;
        }
        if (!madnessSpells().contains(spellId)) return;
        final boolean beyond = state.isBeyondThreshold(madnessThreshold(playerId), now,
                madnessDecayDelayMillis(), madnessDecayPerSecond());
        int gain = config.getInt("classes.priest.shadow.cast-gain", 12);
        if ("higgadt_elme".equals(doctrine(playerId, 30))) {
            gain -= Math.max(0, config.getInt("classes.priest.shadow.calm-reduction", 3));
        }
        state.addMadness(Math.max(0, gain), now, madnessDecayDelayMillis(),
                madnessDecayPerSecond());
        if (!beyond) return;
        // Beyond the Küszöb every cast pays a floored, refusable toll; beforeCast blocks the
        // cast entirely below the floor, so the strain can never be the killing blow.
        double strain = Math.max(0.0D, config.getDouble(
                "classes.priest.shadow.strain-health", 2.0D));
        if ("tiszta_orulet".equals(doctrine(playerId, 50))) {
            strain = Math.max(0.0D, strain - config.getDouble(
                    "classes.priest.shadow.pure-strain-reduction", 0.5D));
        }
        final double floor = healthFloor(player, "classes.priest.shadow.min-health-ratio", 0.25D);
        final double after = Math.max(floor, player.getHealth() - strain);
        if (after < player.getHealth()) player.setHealth(after);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.PRIEST,
                    config.getInt("classes.priest.mastery.threshold-xp", 4));
        }
    }

    public Component hudSuffix(final Player player) {
        if (!isPriest(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final PriestCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final PriestCombatState.Litany litany = litany(playerId, state);
        Component suffix = Component.text("  • " + litanyName(litany) + " "
                        + state.verses() + "/" + versesRequired(playerId)
                        + (state.isRecited(now) ? " ✝" : ""),
                NamedTextColor.WHITE);
        switch (activeSpec(playerId)) {
            case "discipline" -> suffix = suffix.append(Component.text("  • Pajzsháló "
                            + state.shield() + (state.isAtonementActive(now) ? " • Engesztelés" : ""),
                    NamedTextColor.GOLD));
            case "bone_priest" -> suffix = suffix.append(Component.text("  • Velő "
                            + state.marrow() + " • Osszárium " + state.ossuary()
                            + "/" + ossuaryMaximum(playerId), NamedTextColor.GRAY));
            case "shadow" -> {
                final int madness = state.madness(now, madnessDecayDelayMillis(),
                        madnessDecayPerSecond());
                final boolean beyond = madness >= madnessThreshold(playerId);
                suffix = suffix.append(Component.text("  • Őrület " + madness
                                + (beyond ? " • Küszöbön túl!" : ""),
                        beyond ? NamedTextColor.RED : NamedTextColor.DARK_PURPLE));
            }
            default -> { }
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isPriest(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final PriestCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final PriestCombatState.Litany litany = litany(id, combat);
        final int verses = combat.verses();
        final int required = versesRequired(id);
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "litany", litanyName(litany), litanyName(litany) + " " + verses + "/" + required,
                verses, required, combat.isRecited(now) ? "recited" : "building");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = "";
        String proc = combat.isRecited(now) ? "Litánia kész" : "";
        int charges = verses;
        int maximum = required;
        switch (activeSpec(id)) {
            case "discipline" -> {
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "shield_web", "Pajzsháló", "Pajzsháló " + combat.shield(),
                        combat.shield(), 100, combat.isAtonementActive(now) ? "atonement" : "active");
                if (combat.isAtonementActive(now)) stateText = "Engesztelés";
            }
            case "bone_priest" -> {
                charges = combat.ossuary(); maximum = ossuaryMaximum(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "marrow", "Velő", "Velő " + combat.marrow(), combat.marrow(), 100, "active");
                stateText = "Osszárium " + charges + "/" + maximum;
            }
            case "shadow" -> {
                final int madness = combat.madness(now, madnessDecayDelayMillis(), madnessDecayPerSecond());
                final int threshold = madnessThreshold(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "madness", "Őrület", "Őrület " + madness, madness, threshold,
                        madness >= threshold ? "beyond" : "building");
                if (madness >= threshold) proc = "Küszöbön túl";
            }
            default -> { }
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, stateText, proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.PRIEST) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!PRIEST_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final PriestCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final PriestCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) { clearPlayerState(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) { clearPlayerState(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) { clearPlayerState(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onPluginDisable(final PluginDisableEvent event) {
        if (event.getPlugin() == plugin) shutdown();
    }

    // ===== Helpers =====

    private PriestCombatState.Litany litany(final UUID playerId, final PriestCombatState state) {
        return state.litanyOrDefault(switch (activeSpec(playerId)) {
            case "shadow" -> PriestCombatState.Litany.OSTOR;
            case "bone_priest" -> PriestCombatState.Litany.CSEND;
            default -> PriestCombatState.Litany.VIGASZ;
        });
    }

    private Set<String> litanySpells(final PriestCombatState.Litany litany) {
        return switch (litany) {
            case VIGASZ -> configSet("classes.priest.litany.vigasz-spells");
            case OSTOR -> configSet("classes.priest.litany.ostor-spells");
            case CSEND -> configSet("classes.priest.litany.csend-spells");
        };
    }

    private static String litanyName(final PriestCombatState.Litany litany) {
        return switch (litany) {
            case VIGASZ -> "Vigasz";
            case OSTOR -> "Ostor";
            case CSEND -> "Csend";
        };
    }

    private int versesRequired(final UUID playerId) {
        int required = Math.max(1, config.getInt("classes.priest.litany.verses-required", 3));
        if ("korai_kegyelem".equals(doctrine(playerId, 30))) {
            required = Math.max(1, required - 1);
        }
        return required;
    }

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private double healthFloor(final Player player, final String key, final double fallback) {
        final double ratio = Math.max(0.0D, Math.min(0.9D, config.getDouble(key, fallback)));
        return Math.max(1.0D, maxHealth(player) * ratio);
    }

    private PriestCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new PriestCombatState());
    }

    private boolean isPriest(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.PRIEST;
    }

    private boolean isPriest(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile != null && "priest".equals(profile.primaryClassId());
    }

    private String activeSpec(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        if (profile == null || !profile.isGameplayUsable() || profile.activeSlot() == null) return "";
        final ClassLoadout loadout = profile.loadout(profile.activeSlot());
        return loadout.status() == LoadoutStatus.ACTIVE ? loadout.specializationId() : "";
    }

    private String doctrine(final UUID playerId, final int level) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        if (profile == null || profile.activeSlot() == null) return "";
        return profile.loadout(profile.activeSlot()).doctrineChoices().getOrDefault("level_" + level, "");
    }

    private boolean isInCombat(final UUID playerId) {
        final ResourceManager tracker = combatTracker;
        final long windowMillis = Math.max(1L, config.getLong(
                "classes.priest.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> atonementSpells() {
        return configSet("classes.priest.discipline.atonement-spells");
    }

    private Set<String> sacrificeSpells() {
        return configSet("classes.priest.bone.sacrifice-spells");
    }

    private Set<String> darkMendSpells() {
        return configSet("classes.priest.bone.mend-spells");
    }

    private Set<String> madnessSpells() {
        return configSet("classes.priest.shadow.madness-spells");
    }

    private Set<String> ventSpells() {
        return configSet("classes.priest.shadow.vent-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private int marrowMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.priest.bone.marrow-maximum", 100));
        if ("csonttar".equals(doctrine(playerId, 30))) {
            maximum += Math.max(0, config.getInt("classes.priest.bone.store-extra-marrow", 20));
        }
        return maximum;
    }

    private int ossuaryMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.priest.bone.ossuary-maximum", 2));
        if ("gazdag_osszarium".equals(doctrine(playerId, 40))) {
            maximum += Math.max(0, config.getInt("classes.priest.bone.rich-extra-charge", 1));
        }
        return maximum;
    }

    private int madnessThreshold(final UUID playerId) {
        int threshold = Math.max(1, Math.min(100,
                config.getInt("classes.priest.shadow.threshold", 60)));
        if ("mely_arnyek".equals(doctrine(playerId, 30))) {
            threshold -= Math.max(0, config.getInt(
                    "classes.priest.shadow.deep-threshold-reduction", 10));
        }
        if ("kuszob_mestere".equals(doctrine(playerId, 40))) {
            threshold -= Math.max(0, config.getInt(
                    "classes.priest.shadow.master-threshold-reduction", 5));
        }
        return Math.max(1, threshold);
    }

    private long madnessDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.priest.shadow.decay-delay-millis", 5000L));
    }

    private double madnessDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.priest.shadow.decay-per-second", 5.0D));
    }

    private static UUID attackerId(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player.getUniqueId();
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player.getUniqueId();
        return null;
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
