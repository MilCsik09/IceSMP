package hu.taliann.icesmp.wizard;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.PetManager;
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
 * Nekromanta (DARK, on the existing seal system) raises its Holtak Udvara into the durable Profile
 * v2 necromancer.court companion roster through the existing PetManager gateway: that roster is the
 * single authority, a kind is only an attribute of an instance (so the capacity is reachable), and
 * one admission rule decides both before the cast and at the commit. The Lélekszilánk economy is <em>not</em>
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

    private volatile PetManager pets;

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

    /** The one companion gateway: the court never gets a second framework of its own. */
    public void setPetManager(final PetManager petManager) {
        pets = Objects.requireNonNull(petManager, "petManager");
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

    /**
     * Raising beyond the court's size is refused before the cast, never silently ignored. The check
     * is the shared admission rule evaluated on the durable roster — exactly what the commit will
     * re-evaluate — so a pre-cast pass can never turn into a refused mutation and a phantom cast.
     */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isWizard(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        if (!"necromancer".equals(activeSpec(playerId))) return true;
        if (raiseKindOf(spellId) == null) return true;
        if (admitsRaise(player)) return true;
        courtFullMessage(player);
        return false;
    }

    private boolean admitsRaise(final Player player) {
        return ClassSpecCatalog.admitsCompanion(activeLoadout(player.getUniqueId()),
                "necromancer.court", courtCapacity(player));
    }

    private void courtFullMessage(final Player player) {
        player.sendActionBar(messages.getMessage("wizard.court.full",
                "<dark_gray>A Holtak Udvara megtelt ({count}/{max}) — előbb arasd le.</dark_gray>",
                Map.of("count", Integer.toString(court(player).size()),
                        "max", Integer.toString(courtCapacity(player)))));
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
            bonus += court(player).size() * perCourt;
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
        final PetManager gateway = pets;
        if (gateway == null) return;
        if (harvestSpells().contains(spellId)) {
            final boolean wasFull = !admitsRaise(player);
            // Durable-first: the harvest pays for what the durable court actually released.
            gateway.releaseCourtV2(player).thenAccept(harvested -> {
                if (harvested <= 0) return;
                gateway.runOnPlayer(player, () -> rewardHarvest(player, harvested, wasFull));
            });
            return;
        }
        final String kind = raiseKindOf(spellId);
        if (kind == null) return;
        gateway.raiseCourtV2(player, kind, courtEntityType(kind), courtCapacity(player))
                .thenAccept(result -> gateway.runOnPlayer(player, () -> {
                    if (!result.committed()) {
                        // A refused commit is never silent, but it may only claim the reason it had.
                        if ("pet-court-full".equals(result.error())) courtFullMessage(player);
                        return;
                    }
                    rewardRaise(player, kind);
                }));
    }

    /** Runs on the player's own region thread, after the durable harvest committed. */
    private void rewardHarvest(final Player player, final int harvested, final boolean wasFull) {
        final UUID playerId = player.getUniqueId();
        double healPerCourt = Math.max(0.0D, config.getDouble(
                "classes.wizard.necromancer.harvest-heal-per-court", 2.0D));
        if ("hu_holtak".equals(doctrine(playerId, 30))) {
            healPerCourt += config.getDouble("classes.wizard.necromancer.loyal-extra-heal", 1.0D);
        }
        if (wasFull && "lelekaratas".equals(doctrine(playerId, 40))) {
            healPerCourt *= 2.0D;
        }
        healPlayer(player, harvested * healPerCourt);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.WIZARD,
                    config.getInt("classes.wizard.mastery.harvest-xp", 6));
        }
        player.sendActionBar(messages.getMessage("wizard.court.harvested",
                "<dark_gray>💀 {count} udvaronc lelke betakarítva.</dark_gray>",
                Map.of("count", Integer.toString(harvested))));
        if ("orok_udvar".equals(doctrine(playerId, 50))) {
            final String kept = ClassSpecCatalog.normalize(config.getString(
                    "classes.wizard.necromancer.eternal-kept-kind", "csontvaz"));
            final PetManager gateway = pets;
            if (gateway != null) {
                gateway.raiseCourtV2(player, kept, courtEntityType(kept), courtCapacity(player));
            }
        }
    }

    /** Runs on the player's own region thread, after the durable raise committed. */
    private void rewardRaise(final Player player, final String kind) {
        final UUID playerId = player.getUniqueId();
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.WIZARD,
                    config.getInt("classes.wizard.mastery.raise-xp", 5));
        }
        player.sendActionBar(messages.getMessage("wizard.court.raised",
                "<dark_gray>💀 {kind} a Holtak Udvarában ({count}/{max}).</dark_gray>",
                Map.of("kind", kind, "count", Integer.toString(court(player).size()),
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
                            + court(player).size() + "/" + courtCapacity(player),
                    NamedTextColor.DARK_GRAY));
            default -> { }
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isWizard(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final WizardCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final WizardCombatState.Reaction reaction = combat.armedReaction(now);
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text(
                "runewaving", "Rúnaszövés", "Rúnaszövés " + (reaction == null ? "—" : reactionName(reaction)),
                reaction == null ? "idle" : reaction.name().toLowerCase(Locale.ROOT));
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = "";
        String proc = reaction == null ? "" : "Reakció kész";
        int charges = 0;
        int maximum = 0;
        final java.util.ArrayList<hu.taliann.icesmp.classspec.integration.ClassHudMetric> metrics =
                new java.util.ArrayList<>();
        metrics.add(primary);
        if (reaction == null && combat.lastSchool() != null) {
            stateText = "Első rúna " + switch (combat.lastSchool()) {
                case TUZ -> "Tűz";
                case FAGY -> "Fagy";
                case VIHAR -> "Vihar";
                case ARNY -> "Árny";
                case ARKAN -> "Arkán";
            };
        }
        if ("elementalist".equals(activeSpec(id))) {
            final int threshold = attunementThreshold(id);
            final int fire = combat.attunement(0, now, attunementDecayDelayMillis(), attunementDecayPerSecond());
            final int frost = combat.attunement(1, now, attunementDecayDelayMillis(), attunementDecayPerSecond());
            final int arcane = combat.attunement(2, now, attunementDecayDelayMillis(), attunementDecayPerSecond());
            final boolean crowned = combat.isCrowned(threshold, now,
                    attunementDecayDelayMillis(), attunementDecayPerSecond());
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "attunement", "Elemek", "Elemek " + fire + "/" + frost + "/" + arcane,
                    Math.min(fire, Math.min(frost, arcane)), threshold, crowned ? "crowned" : "active");
            metrics.add(secondary);
            metrics.add(hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "attunement_fire", "Tűz", Integer.toString(fire), fire, threshold, "fire"));
            metrics.add(hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "attunement_frost", "Fagy", Integer.toString(frost), frost, threshold, "frost"));
            metrics.add(hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "attunement_arcane", "Arkán", Integer.toString(arcane), arcane, threshold, "arcane"));
            if (crowned) proc = "Korona";
            else if (combat.isConvergent(threshold, now, attunementDecayDelayMillis(),
                    attunementDecayPerSecond())) proc = "Konvergencia";
        } else if ("necromancer".equals(activeSpec(id))) {
            charges = court(player).size(); maximum = courtCapacity(player);
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "court", "Udvar", "Udvar " + charges + "/" + maximum,
                    charges, maximum, charges >= maximum ? "full" : "active");
            metrics.add(secondary);
        }
        return new hu.taliann.icesmp.classspec.integration.ClassHudMechanics(
                primary, secondary, stateText, proc, charges, maximum, metrics,
                hu.taliann.icesmp.classspec.integration.ClassHudSlot.charges(
                        secondary.id(), secondary.id(), secondary.label(), charges, maximum));
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

    /**
     * The court's size rides the EXISTING Soulforge LETSZAM authority — no parallel ladder. The
     * ceiling is a plain configured number: nothing about it depends on how many kinds exist, so
     * every slot is reachable by raising the same kind again.
     */
    private int courtCapacity(final Player player) {
        final int ceiling = Math.max(1, config.getInt(
                "classes.wizard.necromancer.court-slots", 4));
        int capacity = Math.max(1, Math.min(ceiling,
                config.getInt("classes.wizard.necromancer.court-capacity", 2)));
        if ("nagyobb_udvar".equals(doctrine(player.getUniqueId(), 30))) {
            capacity += Math.max(0, config.getInt(
                    "classes.wizard.necromancer.wider-extra-slot", 1));
        }
        final SoulforgeManager forge = soulforge;
        if (forge != null) {
            capacity += Math.max(0, forge.extraSlots(player));
        }
        return Math.min(ceiling, capacity);
    }

    /**
     * The court projection. It is a pure read of the durable necromancer.court, so it is always
     * reconstructible from Profile v2 alone: a relog, a spec switch or a DARK seal changes what the
     * projection reports without any transient state having to be kept in step with it.
     */
    private List<CompanionProfile> court(final Player player) {
        final PetManager gateway = pets;
        return gateway == null ? List.of() : gateway.courtRoster(player);
    }

    private ClassLoadout activeLoadout(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile == null || profile.activeSlot() == null
                ? null : profile.loadout(profile.activeSlot());
    }

    /** The vanilla body each raised kind wears, admin-tunable and never invented in code. */
    private String courtEntityType(final String kind) {
        return config.getString("classes.wizard.necromancer." + kind + "-entity", "");
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
