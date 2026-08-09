package hu.taliann.icesmp.wizard;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SoulforgeManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
 * Concrete Varázsló vertical-slice runtime — the last class slice.
 *
 * <p>Rúnaszövés is the class layer: the runtime keeps only the last two schools cast and turns
 * that ordered pair into one of five explicitly enumerated rune reactions, each arming a short
 * window that empowers the next cast. There is no combo DSL and no rule engine. Elementalista
 * reads one three-slot attunement array through exactly two threshold checks — Konvergencia (two
 * at or above the bar) and Elemi Korona (all three) — rather than three parallel subsystems.
 * Nekromanta (DARK, on the existing seal system) keeps only the bounded Holtak Udvara of raised
 * kinds under the necromancer.court namespace; the Lélekszilánk economy is <em>not</em>
 * reimplemented here — the existing {@link SoulforgeManager} keeps its CAS, receipt, shard and
 * refund/recovery authority, and this runtime only reads its ranks to size the court. Durable
 * state remains Profile v2.</p>
 */
public final class WizardGameplayService implements Listener, PlayerStateCleanup {

    private static final Set<String> WIZARD_SPECS = Set.of("elementalist", "necromancer");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, WizardCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;
    private volatile SoulforgeManager soulforge;

    public WizardGameplayService(final JavaPlugin plugin,
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

    /** The existing shard authority; the Nekromanta only reads it, it never duplicates it. */
    public void setSoulforgeManager(final SoulforgeManager manager) {
        soulforge = Objects.requireNonNull(manager, "manager");
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.WIZARD) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.wizard.active-kit.maximum", 7)));
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
                "classes.wizard.active-kit." + activeSpec)) {
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

    /** Raising beyond the court's size is refused before the cast, never silently ignored. */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isWizard(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        if (!"necromancer".equals(activeSpec(playerId))) return true;
        final String kind = raiseKindOf(spellId);
        if (kind == null) return true;
        final WizardCombatState state = state(playerId);
        if (state.holds(kind) || state.courtSize() < courtCapacity(player)) return true;
        player.sendActionBar(messages.getMessage("wizard.court.full",
                "<dark_gray>A Holtak Udvara megtelt ({count}/{max}) — előbb arasd le.</dark_gray>",
                Map.of("count", Integer.toString(state.courtSize()),
                        "max", Integer.toString(courtCapacity(player)))));
        return false;
    }

    /** Pure peek: an armed rune reaction, Konvergencia/Elemi Korona and the court empower casts. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isWizard(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final WizardCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        final WizardCombatState.Reaction armed = state.armedReaction(now);
        if (armed != null) {
            bonus += reactionBonusPercent(armed, playerId);
        }
        final String spec = activeSpec(playerId);
        if ("elementalist".equals(spec) && schoolOf(spellId) != null) {
            if (state.isCrowned(attunementThreshold(playerId), now,
                    attunementDecayDelayMillis(), attunementDecayPerSecond())) {
                bonus += Math.max(0.0D, config.getDouble(
                        "classes.wizard.elementalist.crown-bonus-percent", 22.0D));
            } else if (state.isConvergent(attunementThreshold(playerId), now,
                    attunementDecayDelayMillis(), attunementDecayPerSecond())) {
                bonus += Math.max(0.0D, config.getDouble(
                        "classes.wizard.elementalist.convergence-bonus-percent", 12.0D));
            }
        }
        if ("necromancer".equals(spec) && necroticSpells().contains(spellId)) {
            double perCourt = Math.max(0.0D, config.getDouble(
                    "classes.wizard.necromancer.per-court-percent", 5.0D));
            if ("csontkiraly".equals(doctrine(playerId, 40))) {
                perCourt += config.getDouble("classes.wizard.necromancer.bone-king-extra-percent", 1.5D);
            }
            if ("halalmester".equals(doctrine(playerId, 50))) {
                perCourt += config.getDouble("classes.wizard.necromancer.master-extra-percent", 2.0D);
            }
            bonus += state.courtSize() * perCourt;
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.wizard.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isWizard(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final WizardCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        // A reaction armed before this cast is what empowered it, so this cast spends it.
        if (state.armedReaction(now) != null) {
            state.consumeReaction(now);
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.WIZARD,
                        config.getInt("classes.wizard.mastery.reaction-xp", 5));
            }
        }
        final WizardCombatState.School school = schoolOf(spellId);
        if (school != null) {
            final WizardCombatState.Reaction produced = state.weave(school, now,
                    reactionWindowMillis(playerId));
            if (produced != null) {
                player.sendActionBar(messages.getMessage("wizard.rune.reaction",
                        "<aqua>✶ Rúnareakció: {reaction}.</aqua>",
                        Map.of("reaction", reactionName(produced))));
            }
        }

        switch (activeSpec(playerId)) {
            case "elementalist" -> handleElementalistCast(player, state, spellId, now);
            case "necromancer" -> handleNecromancerCast(player, state, spellId);
            default -> { }
        }
    }

    // ===== Elementalista =====

    private void handleElementalistCast(final Player player, final WizardCombatState state,
                                        final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final int index = attunementIndexOf(spellId);
        if (index < 0) return;
        int gain = config.getInt("classes.wizard.elementalist.attunement-per-cast", 18);
        if ("gyors_rahangolodas".equals(doctrine(playerId, 30))) {
            gain += Math.max(0, config.getInt("classes.wizard.elementalist.quick-extra-gain", 5));
        }
        state.addAttunement(index, gain, now, attunementDecayDelayMillis(),
                attunementDecayPerSecond());
        if ("elemi_egyensuly".equals(doctrine(playerId, 40))) {
            int weakest = 0;
            for (int i = 1; i < WizardCombatState.ATTUNEMENTS; i++) {
                if (state.attunement(i, now, attunementDecayDelayMillis(),
                        attunementDecayPerSecond())
                        < state.attunement(weakest, now, attunementDecayDelayMillis(),
                        attunementDecayPerSecond())) {
                    weakest = i;
                }
            }
            state.addAttunement(weakest, gain / 2, now, attunementDecayDelayMillis(),
                    attunementDecayPerSecond());
        }
        final int threshold = attunementThreshold(playerId);
        if (state.isCrowned(threshold, now, attunementDecayDelayMillis(),
                attunementDecayPerSecond())) {
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.WIZARD,
                        config.getInt("classes.wizard.mastery.crown-xp", 8));
            }
            player.sendActionBar(messages.getMessage("wizard.crown.worn",
                    "<gold>👑 Elemi Korona — mindhárom ráhangolódás áll.</gold>"));
        } else if (state.isConvergent(threshold, now, attunementDecayDelayMillis(),
                attunementDecayPerSecond())) {
            player.sendActionBar(messages.getMessage("wizard.convergence.reached",
                    "<aqua>Konvergencia — két elem összeért.</aqua>"));
        }
    }

    // ===== Nekromanta (DARK) =====

    private void handleNecromancerCast(final Player player, final WizardCombatState state,
                                       final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (harvestSpells().contains(spellId)) {
            final boolean wasFull = state.courtSize() >= courtCapacity(player);
            final int harvested = state.harvestCourt();
            if (harvested <= 0) return;
            double healPerCourt = Math.max(0.0D, config.getDouble(
                    "classes.wizard.necromancer.harvest-heal-per-court", 2.0D));
            if ("hu_holtak".equals(doctrine(playerId, 30))) {
                healPerCourt += config.getDouble(
                        "classes.wizard.necromancer.loyal-extra-heal", 1.0D);
            }
            if (wasFull && "lelekaratas".equals(doctrine(playerId, 40))) {
                healPerCourt *= 2.0D;
            }
            healPlayer(player, harvested * healPerCourt);
            if ("orok_udvar".equals(doctrine(playerId, 50))) {
                state.raise(config.getString(
                        "classes.wizard.necromancer.eternal-kept-kind", "csontvaz"),
                        courtCapacity(player));
            }
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.WIZARD,
                        config.getInt("classes.wizard.mastery.harvest-xp", 6));
            }
            player.sendActionBar(messages.getMessage("wizard.court.harvested",
                    "<dark_gray>💀 {count} udvaronc lelke betakarítva.</dark_gray>",
                    Map.of("count", Integer.toString(harvested))));
            return;
        }
        final String kind = raiseKindOf(spellId);
        if (kind == null || !state.raise(kind, courtCapacity(player))) return;
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.WIZARD,
                    config.getInt("classes.wizard.mastery.raise-xp", 5));
        }
        player.sendActionBar(messages.getMessage("wizard.court.raised",
                "<dark_gray>💀 {kind} a Holtak Udvarában ({count}/{max}).</dark_gray>",
                Map.of("kind", kind, "count", Integer.toString(state.courtSize()),
                        "max", Integer.toString(courtCapacity(player)))));
    }

    public Component hudSuffix(final Player player) {
        if (!isWizard(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final WizardCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final WizardCombatState.Reaction armed = state.armedReaction(now);
        Component suffix = Component.text("  • Rúnaszövés "
                        + (armed == null ? "—" : reactionName(armed)),
                armed == null ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA);
        switch (activeSpec(playerId)) {
            case "elementalist" -> {
                final int threshold = attunementThreshold(playerId);
                final boolean crowned = state.isCrowned(threshold, now,
                        attunementDecayDelayMillis(), attunementDecayPerSecond());
                suffix = suffix.append(Component.text("  • Elemek "
                                + state.attunement(0, now, attunementDecayDelayMillis(),
                                attunementDecayPerSecond()) + "/"
                                + state.attunement(1, now, attunementDecayDelayMillis(),
                                attunementDecayPerSecond()) + "/"
                                + state.attunement(2, now, attunementDecayDelayMillis(),
                                attunementDecayPerSecond())
                                + (crowned ? " • Korona" : state.isConvergent(threshold, now,
                                attunementDecayDelayMillis(), attunementDecayPerSecond())
                                ? " • Konvergencia" : ""),
                        crowned ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            }
            case "necromancer" -> suffix = suffix.append(Component.text("  • Udvar "
                            + state.courtSize() + "/" + courtCapacity(player),
                    NamedTextColor.DARK_GRAY));
            default -> { }
        }
        return suffix;
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.WIZARD) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!WIZARD_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final WizardCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final WizardCombatState state = states.remove(playerId);
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

    private WizardCombatState.School schoolOf(final String spellId) {
        for (final WizardCombatState.School school : WizardCombatState.School.values()) {
            final String key = "classes.wizard.weave."
                    + school.name().toLowerCase(Locale.ROOT) + "-spells";
            if (configSet(key).contains(spellId)) return school;
        }
        return null;
    }

    /** Only the three elemental schools feed an attunement; Árny and Arkán do not. */
    private int attunementIndexOf(final String spellId) {
        final WizardCombatState.School school = schoolOf(spellId);
        if (school == null) return -1;
        return switch (school) {
            case TUZ -> 0;
            case FAGY -> 1;
            case VIHAR -> 2;
            default -> -1;
        };
    }

    private double reactionBonusPercent(final WizardCombatState.Reaction reaction,
                                        final UUID playerId) {
        double bonus = Math.max(0.0D, config.getDouble(
                "classes.wizard.weave.reaction-bonus-percent", 15.0D));
        if ("mely_szoves".equals(doctrine(playerId, 40))) {
            bonus += config.getDouble("classes.wizard.weave.deep-extra-percent", 5.0D);
        }
        if (reaction == WizardCombatState.Reaction.ARKAN_EROSITES
                && "arkan_ura".equals(doctrine(playerId, 50))) {
            bonus += config.getDouble("classes.wizard.weave.arcane-extra-percent", 5.0D);
        }
        return bonus;
    }

    private static String reactionName(final WizardCombatState.Reaction reaction) {
        return switch (reaction) {
            case GOZROBBANAS -> "Gőzrobbanás";
            case JEGVIHAR -> "Jégvihar";
            case KOHO -> "Kohó";
            case ARNYVISSZHANG -> "Árnyvisszhang";
            case ARKAN_EROSITES -> "Arkán Erősítés";
        };
    }

    /** The court's size rides the EXISTING Soulforge LETSZAM authority — no parallel ladder. */
    private int courtCapacity(final Player player) {
        int capacity = Math.max(1, Math.min(WizardCombatState.COURT_SLOTS,
                config.getInt("classes.wizard.necromancer.court-capacity", 2)));
        if ("nagyobb_udvar".equals(doctrine(player.getUniqueId(), 30))) {
            capacity += Math.max(0, config.getInt(
                    "classes.wizard.necromancer.wider-extra-slot", 1));
        }
        final SoulforgeManager forge = soulforge;
        if (forge != null) {
            capacity = Math.min(WizardCombatState.COURT_SLOTS,
                    capacity + Math.max(0, forge.extraSlots(player)));
        }
        return Math.min(WizardCombatState.COURT_SLOTS, capacity);
    }

    private String raiseKindOf(final String spellId) {
        for (final String kind : new String[]{"zombi", "csontvaz", "lidercz"}) {
            if (configSet("classes.wizard.necromancer." + kind + "-spells").contains(spellId)) {
                return kind;
            }
        }
        return null;
    }

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private WizardCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new WizardCombatState());
    }

    private boolean isWizard(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.WIZARD;
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
                "classes.wizard.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> necroticSpells() {
        return configSet("classes.wizard.necromancer.necrotic-spells");
    }

    private Set<String> harvestSpells() {
        return configSet("classes.wizard.necromancer.harvest-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private long reactionWindowMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.wizard.weave.reaction-window-millis", 5000L));
        if ("hosszu_visszacsatolas".equals(doctrine(playerId, 30))) {
            millis += Math.max(0L, config.getLong(
                    "classes.wizard.weave.long-extra-millis", 2000L));
        }
        return millis;
    }

    private int attunementThreshold(final UUID playerId) {
        int threshold = Math.max(1, Math.min(100,
                config.getInt("classes.wizard.elementalist.attunement-threshold", 70)));
        if ("konnyu_korona".equals(doctrine(playerId, 50))) {
            threshold -= Math.max(0, config.getInt(
                    "classes.wizard.elementalist.light-crown-reduction", 10));
        }
        return Math.max(1, threshold);
    }

    private long attunementDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.wizard.elementalist.decay-delay-millis", 6000L));
    }

    private double attunementDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.wizard.elementalist.decay-per-second", 6.0D));
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
