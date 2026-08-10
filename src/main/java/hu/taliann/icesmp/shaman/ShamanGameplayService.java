package hu.taliann.icesmp.shaman;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.TotemManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
 * Concrete Sámán vertical-slice runtime.
 *
 * <p>The class core is the Totemkerék: at most one fő and one kísérő totem lives per shaman,
 * owned end-to-end by the existing TotemManager — this service only reads its projection.
 * Elemi plays one Overload charge fed by totem-resonant casts; Erősítő plays a rhythm-built
 * Maelstrom with alternating Fegyveráldás sides; Hullámhívó plays one signed Dagály↔Apály tide
 * where direct and chain heals prepare each other. No repeating task and no proximity scan
 * lives here; durable state remains Profile v2.</p>
 */
public final class ShamanGameplayService implements Listener, PlayerStateCleanup {

    public enum Element {
        VIHAR,
        TUZ,
        FOLD,
        VIZ
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, ShamanCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;
    private volatile TotemManager totems;

    public ShamanGameplayService(final JavaPlugin plugin,
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

    /** The existing totem authority; this service only reads the Totemkerék projection. */
    public void setTotemManager(final TotemManager totemManager) {
        totems = Objects.requireNonNull(totemManager, "totemManager");
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.SHAMAN) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.shaman.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.shaman.active-kit." + activeSpec)) {
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

    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isShaman(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        if (!"enhancement".equals(activeSpec(playerId))) return true;
        final int cost = maelstromCost(playerId, spell.getId().toLowerCase(Locale.ROOT));
        if (cost > 0 && state(playerId).maelstrom() < cost) {
            player.sendActionBar(messages.getMessage("shaman.maelstrom.need",
                    "<red>Nincs elég Maelstrom. Szükséges: {amount}.</red>",
                    Map.of("amount", Integer.toString(cost))));
            return false;
        }
        return true;
    }

    /**
     * Pure pre-cast peek for the shared power pipeline: an armed Overload on a resonant spell,
     * or the reached tide side on the matching heal family. Committed only in afterCast.
     */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isShaman(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final ShamanCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);
        double bonus = 0.0D;
        if ("elemental".equals(spec) && isResonant(playerId, spellId)
                && state.isOverloadArmed(overloadThreshold(playerId))) {
            bonus += overloadBonusPercent(playerId);
        } else if ("tidal".equals(spec)) {
            if (chainHealSpells().contains(spellId) && state.isHighTide(tideThreshold(playerId))) {
                bonus += tideBonusPercent(playerId, true);
            } else if (directHealSpells().contains(spellId)
                    && state.isLowTide(tideThreshold(playerId))) {
                bonus += tideBonusPercent(playerId, false);
            }
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.shaman.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isShaman(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final ShamanCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);
        if ("elemental".equals(spec)) {
            handleElementalCast(player, state, spellId);
        } else if ("enhancement".equals(spec)) {
            handleEnhancementCast(player, state, spellId);
        } else if ("tidal".equals(spec)) {
            handleTidalCast(player, state, spellId);
        }
    }

    private void handleElementalCast(final Player player, final ShamanCombatState state,
                                     final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (!isResonant(playerId, spellId)) return;
        final int threshold = overloadThreshold(playerId);
        if (state.isOverloadArmed(threshold)) {
            state.consumeOverload(overloadRetention(playerId));
            if ("vihar_kegyeltje".equals(doctrine(playerId, 50))) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED,
                        config.getInt("classes.shaman.resonance.favored-speed-ticks", 80),
                        0, false, true, true));
            }
            player.sendActionBar(messages.getMessage("shaman.overload.burst",
                    "<gold>⚡ Túltöltés: az elemek a totempárodon át zúdultak.</gold>"));
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.SHAMAN,
                        config.getInt("classes.shaman.mastery.overload-xp", 5));
            }
            return;
        }
        final int gain = config.getInt("classes.shaman.resonance.charge-gain", 1)
                + ("eleven_szikra".equals(doctrine(playerId, 30)) ? 1 : 0);
        final int charge = state.chargeOverload(gain, threshold);
        if (state.isOverloadArmed(threshold)) {
            player.sendActionBar(messages.getMessage("shaman.overload.armed",
                    "<gold>Rezonancia kész ({value}/{threshold}): a következő rezonáns spell túltölt.</gold>",
                    Map.of("value", Integer.toString(charge),
                            "threshold", Integer.toString(threshold))));
        }
    }

    private void handleEnhancementCast(final Player player, final ShamanCombatState state,
                                       final String spellId) {
        final UUID playerId = player.getUniqueId();
        final int cost = maelstromCost(playerId, spellId);
        if (cost <= 0) return;
        final boolean vent = "doom_winds".equals(spellId);
        // The vent's affordability was gated in beforeCast; here it always empties (minus retention).
        final boolean spent = vent
                ? state.ventMaelstrom("maelstrom_ura".equals(doctrine(playerId, 50))
                ? config.getInt("classes.shaman.maelstrom.capstone-retention", 25) : 0) > 0
                : state.spendMaelstrom(cost);
        if (!spent) return;
        if ("foldrenges".equals(doctrine(playerId, 40)) && !vent) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.STRENGTH,
                    config.getInt("classes.shaman.maelstrom.quake-strength-ticks", 60),
                    0, false, true, true));
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.SHAMAN, vent
                    ? config.getInt("classes.shaman.mastery.capstone-vent-xp", 8)
                    : config.getInt("classes.shaman.mastery.maelstrom-spend-xp", 4));
        }
        player.sendActionBar(messages.getMessage(vent
                        ? "shaman.maelstrom.vent" : "shaman.maelstrom.spent",
                vent ? "<gold>Végzet Szelei: a felgyűlt Maelstrom kiszabadult.</gold>"
                        : "<aqua>Maelstrom felhasználva.</aqua>"));
    }

    private void handleTidalCast(final Player player, final ShamanCombatState state,
                                 final String spellId) {
        final UUID playerId = player.getUniqueId();
        final int threshold = tideThreshold(playerId);
        if (chainHealSpells().contains(spellId)) {
            if (state.isHighTide(threshold)) {
                state.consumeTide(tideRetentionPercent(playerId));
                applyLifeVeinAbsorption(player);
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.SHAMAN,
                            config.getInt("classes.shaman.mastery.tide-xp", 4));
                }
                player.sendActionBar(messages.getMessage("shaman.tide.dagaly-burst",
                        "<aqua>🌊 Dagály: a lánc-gyógyítás megerősödve fut végig.</aqua>"));
            } else {
                state.pushTide(-tidePush(playerId));
            }
        } else if (directHealSpells().contains(spellId)) {
            if (state.isLowTide(threshold)) {
                state.consumeTide(tideRetentionPercent(playerId));
                applyLifeVeinAbsorption(player);
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.SHAMAN,
                            config.getInt("classes.shaman.mastery.tide-xp", 4));
                }
                player.sendActionBar(messages.getMessage("shaman.tide.apaly-burst",
                        "<aqua>🌊 Apály: a közvetlen gyógyítás megerősödve talál.</aqua>"));
            } else {
                state.pushTide(tidePush(playerId));
            }
        }
    }

    /** Erősítő: melee hits build the Maelstrom; the rhythm window alternates the blessing side. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeResolved(final EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0D
                || !(event.getDamager() instanceof Player attacker)
                || !isShaman(attacker)
                || !"enhancement".equals(activeSpec(attacker.getUniqueId()))) return;
        final UUID playerId = attacker.getUniqueId();
        final ShamanCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        state.recordMeleeHit(now,
                rhythmWindowMinMillis(playerId), rhythmWindowMaxMillis(playerId),
                config.getInt("classes.shaman.maelstrom.hit-gain", 4),
                rhythmBonus(playerId));
    }

    public Component hudSuffix(final Player player) {
        if (!isShaman(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final ShamanCombatState state = state(playerId);
        Component suffix = Component.empty();
        final TotemManager totemManager = totems;
        if (totemManager != null) {
            final Map<TotemManager.TotemCategory, TotemManager.TotemType> pair =
                    totemManager.activeTotemTypes(playerId);
            final String fo = pair.containsKey(TotemManager.TotemCategory.FO)
                    ? elementName(elementOfTotem(pair.get(TotemManager.TotemCategory.FO))) : "—";
            final String kisero = pair.containsKey(TotemManager.TotemCategory.KISERO)
                    ? elementName(elementOfTotem(pair.get(TotemManager.TotemCategory.KISERO))) : "—";
            suffix = suffix.append(Component.text("  • Kerék " + fo + "/" + kisero,
                    NamedTextColor.DARK_AQUA));
        }
        final String spec = activeSpec(playerId);
        if ("elemental".equals(spec)) {
            final int threshold = overloadThreshold(playerId);
            suffix = suffix.append(Component.text("  • Rezonancia " + state.overload() + "/"
                    + threshold + (state.isOverloadArmed(threshold) ? " ⚡" : ""),
                    NamedTextColor.GOLD));
        } else if ("enhancement".equals(spec)) {
            suffix = suffix.append(Component.text("  • Maelstrom " + state.maelstrom()
                    + " • Áldás " + (state.blessingSide() == ShamanCombatState.BlessingSide.VIHAR
                    ? "Vihar" : "Föld"), NamedTextColor.AQUA));
        } else if ("tidal".equals(spec)) {
            final int tide = state.tide();
            final int threshold = tideThreshold(playerId);
            final String label = tide >= threshold ? "Dagály ✦"
                    : tide <= -threshold ? "Apály ✦" : Integer.toString(tide);
            suffix = suffix.append(Component.text("  • Ár " + label, NamedTextColor.BLUE));
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isShaman(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final ShamanCombatState combat = state(id);
        String wheel = "Kerék —/—";
        if (totems != null) {
            final Map<TotemManager.TotemCategory, TotemManager.TotemType> pair = totems.activeTotemTypes(id);
            final String main = pair.containsKey(TotemManager.TotemCategory.FO)
                    ? elementName(elementOfTotem(pair.get(TotemManager.TotemCategory.FO))) : "—";
            final String companion = pair.containsKey(TotemManager.TotemCategory.KISERO)
                    ? elementName(elementOfTotem(pair.get(TotemManager.TotemCategory.KISERO))) : "—";
            wheel = "Kerék " + main + "/" + companion;
        }
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text(
                "totem_wheel", "Kerék", wheel, wheel.endsWith("—/—") ? "idle" : "active");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String proc = "";
        int charges = 0;
        int maximum = 0;
        switch (activeSpec(id)) {
            case "elemental" -> {
                charges = combat.overload(); maximum = overloadThreshold(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "resonance", "Rezonancia", "Rezonancia " + charges + "/" + maximum,
                        charges, maximum, charges >= maximum ? "ready" : "building");
                if (charges >= maximum) proc = "Túlterhelés kész";
            }
            case "enhancement" -> secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "maelstrom", "Maelstrom", "Maelstrom " + combat.maelstrom(),
                    combat.maelstrom(), 100, combat.blessingSide().name().toLowerCase(Locale.ROOT));
            case "tidal" -> {
                final int tide = combat.tide(); maximum = tideThreshold(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "tide", "Ár", "Ár " + tide, Math.abs(tide), maximum,
                        tide >= maximum ? "high_tide" : tide <= -maximum ? "low_tide" : "flowing");
            }
            default -> { }
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, combat.blessingSide() == ShamanCombatState.BlessingSide.VIHAR
                        ? "Áldás Vihar" : "Áldás Föld", proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.SHAMAN) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"elemental".equals(spec) && !"enhancement".equals(spec) && !"tidal".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final ShamanCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final ShamanCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
        final TotemManager totemManager = totems;
        if (totemManager != null) totemManager.clearOwnerProjection(playerId);
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

    /**
     * Resonance requires the full Totemkerék pair (the mely_gyokerek doctrine accepts a single
     * live totem); the spell must match a live totem's element.
     */
    private boolean isResonant(final UUID playerId, final String spellId) {
        final TotemManager totemManager = totems;
        if (totemManager == null) return false;
        final Map<TotemManager.TotemCategory, TotemManager.TotemType> pair =
                totemManager.activeTotemTypes(playerId);
        final int required = "mely_gyokerek".equals(doctrine(playerId, 30)) ? 1 : 2;
        if (pair.size() < required) return false;
        final Element element = elementOfSpell(spellId);
        if (element == null) return false;
        for (final TotemManager.TotemType type : pair.values()) {
            if (elementOfTotem(type) == element) return true;
        }
        return false;
    }

    private ShamanCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new ShamanCombatState());
    }

    private boolean isShaman(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.SHAMAN;
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
                "classes.shaman.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Element elementOfSpell(final String spellId) {
        for (final Element element : Element.values()) {
            for (final String raw : config.getStringList("classes.shaman.resonance."
                    + element.name().toLowerCase(Locale.ROOT) + "-spells")) {
                if (normalize(raw).equals(spellId)) return element;
            }
        }
        return null;
    }

    private static Element elementOfTotem(final TotemManager.TotemType type) {
        return switch (type) {
            case SEARING -> Element.TUZ;
            case HEALING_STREAM -> Element.VIZ;
            case WINDFURY -> Element.VIHAR;
            case EARTHBIND -> Element.FOLD;
        };
    }

    private static String elementName(final Element element) {
        return switch (element) {
            case VIHAR -> "Vihar";
            case TUZ -> "Tűz";
            case FOLD -> "Föld";
            case VIZ -> "Víz";
        };
    }

    private Set<String> chainHealSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.shaman.tide.chain-heal-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private Set<String> directHealSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.shaman.tide.direct-heal-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private int maelstromCost(final UUID playerId, final String spellId) {
        int cost = switch (spellId) {
            case "stormstrike" -> config.getInt("classes.shaman.maelstrom.stormstrike-cost", 30);
            case "crash_lightning" -> config.getInt("classes.shaman.maelstrom.crash-cost", 45);
            case "doom_winds" -> config.getInt("classes.shaman.maelstrom.doom-cost", 70);
            default -> 0;
        };
        if (cost > 0 && "stormstrike".equals(spellId)
                && "vihartorok".equals(doctrine(playerId, 40))) {
            cost -= Math.max(0, config.getInt(
                    "classes.shaman.maelstrom.stormthroat-reduction", 10));
        }
        return Math.max(0, cost);
    }

    private int overloadThreshold(final UUID playerId) {
        final int base = Math.max(2, config.getInt(
                "classes.shaman.resonance.overload-threshold", 4));
        return "vihar_hirnoke".equals(doctrine(playerId, 40)) ? Math.max(2, base - 1) : base;
    }

    private double overloadBonusPercent(final UUID playerId) {
        final double base = Math.max(0.0D, config.getDouble(
                "classes.shaman.resonance.overload-bonus-percent", 25.0D));
        return "tulcsordulas".equals(doctrine(playerId, 40))
                ? base + Math.max(0.0D, config.getDouble(
                "classes.shaman.resonance.overflow-extra-percent", 8.0D)) : base;
    }

    private int overloadRetention(final UUID playerId) {
        return "orok_rezonancia".equals(doctrine(playerId, 50))
                ? Math.max(0, config.getInt("classes.shaman.resonance.echo-retention", 2)) : 0;
    }

    private long rhythmWindowMinMillis(final UUID playerId) {
        return Math.max(100L, config.getLong(
                "classes.shaman.maelstrom.rhythm-min-millis", 600L));
    }

    private long rhythmWindowMaxMillis(final UUID playerId) {
        final long base = Math.max(500L, config.getLong(
                "classes.shaman.maelstrom.rhythm-max-millis", 1600L));
        return "surito_ritmus".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.shaman.maelstrom.dense-extra-millis", 400L)) : base;
    }

    private int rhythmBonus(final UUID playerId) {
        int bonus = Math.max(0, config.getInt("classes.shaman.maelstrom.rhythm-bonus", 4));
        if ("acel_zapor".equals(doctrine(playerId, 30))) {
            bonus += Math.max(0, config.getInt("classes.shaman.maelstrom.steel-extra-bonus", 2));
        }
        if ("vihar_tanca".equals(doctrine(playerId, 50))) {
            bonus += Math.max(0, config.getInt("classes.shaman.maelstrom.dance-extra-bonus", 3));
        }
        return bonus;
    }

    private int tideThreshold(final UUID playerId) {
        final int base = Math.max(10, config.getInt("classes.shaman.tide.threshold", 60));
        return "melyviz".equals(doctrine(playerId, 30))
                ? Math.max(10, base - Math.max(0, config.getInt(
                "classes.shaman.tide.deepwater-reduction", 10))) : base;
    }

    private int tidePush(final UUID playerId) {
        final int base = Math.max(1, config.getInt("classes.shaman.tide.push", 20));
        return "aramlat".equals(doctrine(playerId, 30))
                ? base + Math.max(0, config.getInt("classes.shaman.tide.current-extra", 5)) : base;
    }

    private double tideBonusPercent(final UUID playerId, final boolean chainSide) {
        double bonus = Math.max(0.0D, config.getDouble(
                "classes.shaman.tide.bonus-percent", 20.0D));
        if (chainSide && "dagaly_ura".equals(doctrine(playerId, 40))) {
            bonus += config.getDouble("classes.shaman.tide.lord-extra-percent", 8.0D);
        } else if (!chainSide && "apaly_ura".equals(doctrine(playerId, 40))) {
            bonus += config.getDouble("classes.shaman.tide.lord-extra-percent", 8.0D);
        }
        return bonus;
    }

    private void applyLifeVeinAbsorption(final Player player) {
        if (!"eletado_veno".equals(doctrine(player.getUniqueId(), 50))) return;
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.ABSORPTION,
                config.getInt("classes.shaman.tide.life-vein-ticks", 100),
                0, false, true, true));
    }

    private int tideRetentionPercent(final UUID playerId) {
        return "szoko_ar".equals(doctrine(playerId, 50))
                ? Math.max(0, Math.min(100, config.getInt(
                "classes.shaman.tide.spring-retention-percent", 25))) : 0;
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
