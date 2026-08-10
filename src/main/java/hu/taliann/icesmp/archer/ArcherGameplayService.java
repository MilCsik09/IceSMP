package hu.taliann.icesmp.archer;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
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
 * Concrete Íjász vertical-slice runtime.
 *
 * <p>The class core is Szélolvasás: a paced, fully drawn, real-distance hit arms one read that
 * empowers exactly the next disciplined shot — camping alone earns nothing, spam breaks the read.
 * Mesterlövész plays one prey target with a bounded precision chain and a weak-point finisher;
 * Vadmester plays one Kötelék percentage on top of the existing PetManager/CompanionProfile
 * stable — no new pet framework. Every map is transient and explicitly cleaned; durable state
 * remains Profile v2.</p>
 */
public final class ArcherGameplayService implements Listener, PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, ArcherCombatState> states = new ConcurrentHashMap<>();

    /**
     * In-flight arrows and the discipline of the shot that launched them. The verdict has to
     * travel with the arrow: several may be airborne at once, and a fresh shot must never lend
     * its discipline to an older one.
     */
    private final ArcherShotLedger shots = new ArcherShotLedger();

    private volatile ResourceManager combatTracker;
    private volatile PetManager pets;

    public ArcherGameplayService(final JavaPlugin plugin,
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

    /** Mastery is combat-gated; the existing combat tracker is the anti-AFK/dummy-farm witness. */
    public void setCombatTracker(final ResourceManager resources) {
        combatTracker = Objects.requireNonNull(resources, "resources");
    }

    /** The existing companion authority; Vadmester only reads its live projections. */
    public void setPetManager(final PetManager petManager) {
        pets = Objects.requireNonNull(petManager, "petManager");
    }

    /** Pet death consequence: the Kötelék collapses unless the level-50 doctrine retains part. */
    public void onPetDeath(final UUID ownerId) {
        if (ownerId == null) return;
        final ArcherCombatState state = states.get(ownerId);
        if (state == null) return;
        final int retained = "orok_kotelek".equals(doctrine(ownerId, 50))
                ? Math.max(0, config.getInt("classes.archer.bond.death-retention", 50))
                : 0;
        state.collapseBond(retained);
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.ARCHER) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.archer.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.archer.active-kit." + activeSpec)) {
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
        if (!isArcher(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        if ("beast_master".equals(activeSpec(playerId))) {
            final int bondCost = bondCost(playerId, spellId);
            if (bondCost > 0) {
                if (pets == null || pets.activePetEntityId(playerId).isEmpty()) {
                    player.sendActionBar(messages.getMessage("archer.bond.need-pet",
                            "<red>Ehhez élő, aktív társ kell melletted.</red>"));
                    return false;
                }
                if (state(playerId).bond() < bondCost) {
                    player.sendActionBar(messages.getMessage("archer.bond.need-bond",
                            "<red>Nincs elég Kötelék. Szükséges: {amount}.</red>",
                            Map.of("amount", Integer.toString(bondCost))));
                    return false;
                }
            }
        }
        return true;
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isArcher(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);
        final ArcherCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if ("sharpshooter".equals(spec)) {
            if ("perfect_focus".equals(spellId)) {
                state.armWindRead(now, windReadWindowMillis(playerId));
                player.sendActionBar(messages.getMessage("archer.wind.focused",
                        "<green>Tökéletes Fókusz: a következő fegyelmezett lövésed olvasott.</green>"));
            } else if ("masterful_shot".equals(spellId)
                    && state.consumeWeakPoint(weakPointThreshold(playerId), 0)) {
                if ("egy_loves_egy_elet".equals(doctrine(playerId, 50))) {
                    state.armWindRead(now, windReadWindowMillis(playerId));
                }
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.ARCHER,
                            config.getInt("classes.archer.mastery.weak-point-xp", 5));
                }
                player.sendActionBar(messages.getMessage("archer.precision.masterful",
                        "<gold>Mesterlövés: a Pontossági lánc a gyenge pontba futott.</gold>"));
            }
        } else if ("beast_master".equals(spec)) {
            if ("primal_bond".equals(spellId)
                    && state.spendBond(bondCost(playerId, spellId))) {
                buffActivePet(player, false);
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.ARCHER,
                            config.getInt("classes.archer.mastery.bond-spend-xp", 4));
                }
            } else if ("king_of_beasts".equals(spellId)
                    && state.spendBond(bondCost(playerId, spellId))) {
                buffActivePet(player, true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        config.getInt("classes.archer.bond.king-self-speed-ticks", 100),
                        0, false, true, true));
                if ("falka_vezere".equals(doctrine(playerId, 50))) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                            config.getInt("classes.archer.bond.king-self-speed-ticks", 100),
                            0, false, true, true));
                }
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.ARCHER,
                            config.getInt("classes.archer.mastery.capstone-bond-xp", 8));
                }
            }
        }
    }

    public Component hudSuffix(final Player player) {
        if (!isArcher(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final ArcherCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        Component suffix = Component.text("  • Szél "
                        + (state.isWindReadArmed(now) ? "olvasva ➶" : "—"),
                NamedTextColor.GREEN);
        final String spec = activeSpec(playerId);
        if ("sharpshooter".equals(spec)) {
            final int chain = state.precisionChain(now, chainWindowMillis(playerId));
            suffix = suffix.append(Component.text("  • Lánc " + chain + "/"
                    + weakPointThreshold(playerId)
                    + (chain >= weakPointThreshold(playerId) ? " ✹" : ""), NamedTextColor.GOLD));
        } else if ("beast_master".equals(spec)) {
            suffix = suffix.append(Component.text("  • Kötelék " + state.bond(),
                    NamedTextColor.AQUA));
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isArcher(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final ArcherCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final boolean wind = combat.isWindReadArmed(now);
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text(
                "wind_read", "Szélolvasás", wind ? "Szél olvasva" : "Szél —", wind ? "ready" : "idle");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String proc = wind ? "Szélolvasás kész" : "";
        String stateText = "";
        int charges = 0;
        int maximum = 0;
        if ("sharpshooter".equals(activeSpec(id))) {
            charges = combat.precisionChain(now, chainWindowMillis(id));
            maximum = weakPointThreshold(id);
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "precision_chain", "Lánc", "Lánc " + charges + "/" + maximum,
                    charges, maximum, charges >= maximum ? "ready" : "building");
            stateText = combat.preyTargetId().isPresent() ? "Préda kijelölve" : "";
            if (charges >= maximum) proc = "Gyengepont kész";
        } else if ("beast_master".equals(activeSpec(id))) {
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "bond", "Kötelék", "Kötelék " + combat.bond(), combat.bond(), 100, "active");
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, stateText, proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.ARCHER) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"sharpshooter".equals(spec) && !"beast_master".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final ArcherCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final ArcherCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
        shots.forgetOwner(playerId);
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
        shots.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(final EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isArcher(player)) return;
        final UUID playerId = player.getUniqueId();
        final boolean fullDraw = event.getForce() >= fullDrawForce();
        final var origin = player.getLocation();
        final long now = System.currentTimeMillis();
        // The pacing verdict is the shot's own; spam still breaks an armed read here.
        final boolean paced = state(playerId).recordShot(now, fullDraw,
                shotPacingMillis(playerId), origin.getX(), origin.getY(), origin.getZ());
        if (event.getProjectile() == null) return;
        shots.record(event.getProjectile().getUniqueId(),
                new ArcherShotLedger.ShotRecord(playerId, fullDraw, paced,
                        origin.getX(), origin.getY(), origin.getZ(), now),
                now, inFlightMaximum(), inFlightExpiryMillis());
    }

    /** Damage bonus application: the armed read and the weak-point finisher, with PvE/PvP caps. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(final EntityDamageByEntityEvent event) {
        final Player archer = arrowShooter(event);
        if (archer == null || !isArcher(archer)) return;
        final UUID archerId = archer.getUniqueId();
        final ArcherCombatState state = state(archerId);
        final long now = System.currentTimeMillis();
        final boolean pvp = event.getEntity() instanceof Player;
        double bonusPercent = 0.0D;
        if (state.consumeWindRead(now)) {
            bonusPercent += config.getDouble(pvp
                    ? "classes.archer.wind.pvp-bonus-percent"
                    : "classes.archer.wind.pve-bonus-percent", pvp ? 8.0D : 18.0D);
        }
        if ("sharpshooter".equals(activeSpec(archerId))
                && event.getEntity() instanceof LivingEntity
                && state.preyTargetId().filter(event.getEntity().getUniqueId()::equals).isPresent()
                && state.consumeWeakPoint(weakPointThreshold(archerId),
                weakPointRetention(archerId))) {
            double weakPoint = config.getDouble(pvp
                    ? "classes.archer.precision.weak-point-pvp-percent"
                    : "classes.archer.precision.weak-point-pve-percent", pvp ? 12.0D : 28.0D);
            if ("mely_loves".equals(doctrine(archerId, 40))) {
                weakPoint += config.getDouble(
                        "classes.archer.precision.deep-shot-extra-percent", 6.0D);
            }
            bonusPercent += weakPoint;
            archer.sendActionBar(messages.getMessage("archer.precision.weak-point",
                    "<gold>✹ Gyenge pont: a Pontossági lánc bevégezte.</gold>"));
        }
        final double cap = Math.max(0.0D, config.getDouble(pvp
                ? "classes.archer.pvp-max-bonus-percent"
                : "classes.archer.pve-max-bonus-percent", pvp ? 20.0D : 45.0D));
        bonusPercent = Math.min(cap, bonusPercent);
        if (bonusPercent > 0.0D) {
            event.setDamage(event.getDamage() * (1.0D + bonusPercent / 100.0D));
        }
    }

    /** State building after the hit resolved: read arming, precision chain, Kötelék assist. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHitResolved(final EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0D) return;
        final Player archer = arrowShooter(event);
        if (archer == null || !isArcher(archer)
                || !(event.getEntity() instanceof LivingEntity victim)) return;
        final UUID archerId = archer.getUniqueId();
        final ArcherCombatState state = state(archerId);
        final long now = System.currentTimeMillis();
        // The arrow carries its own verdict: an unpaced shot can never rebuild what it broke,
        // and two arrows in flight never trade discipline.
        final ArcherShotLedger.ShotRecord shot = shots
                .consume(event.getDamager().getUniqueId(), now, inFlightExpiryMillis())
                .orElse(null);
        final var victimLocation = victim.getLocation();
        final boolean disciplined = shot != null
                && archerId.equals(shot.ownerId())
                && shot.buildsWindRead(victimLocation.getX(), victimLocation.getY(),
                victimLocation.getZ(), windReadMinimumDistance(archerId));
        final boolean fullDraw = shot != null ? shot.fullDraw()
                : event.getDamager() instanceof AbstractArrow arrow && arrow.isCritical();
        if (disciplined && !state.isWindReadArmed(now)) {
            state.armWindRead(now, windReadWindowMillis(archerId));
            archer.sendActionBar(messages.getMessage("archer.wind.read",
                    "<green>➶ Szélolvasás: a következő fegyelmezett lövésed erősebb.</green>"));
        }

        final String spec = activeSpec(archerId);
        if ("sharpshooter".equals(spec) && fullDraw) {
            final int chain = state.recordPreyHit(victim.getUniqueId(), now,
                    chainWindowMillis(archerId), maximumChain(archerId));
            if (chain == weakPointThreshold(archerId)) {
                archer.sendActionBar(messages.getMessage("archer.precision.armed",
                        "<gold>Pontossági lánc kész ({value}): a következő találat gyenge pontot keres.</gold>",
                        Map.of("value", Integer.toString(chain))));
            }
        } else if ("beast_master".equals(spec)) {
            final PetManager petManager = pets;
            if (petManager != null && petManager.currentCombatTarget(archerId)
                    .filter(victim.getUniqueId()::equals).isPresent()) {
                int gain = config.getInt("classes.archer.bond.assist-gain", 7);
                if ("vadasz_osztone".equals(doctrine(archerId, 30))) {
                    gain += config.getInt("classes.archer.bond.instinct-extra-gain", 3);
                }
                state.addBond(gain);
                if (isInCombat(archerId)) {
                    specs.contributeClassMastery(archer, JobType.ARCHER,
                            config.getInt("classes.archer.mastery.coordination-xp", 3));
                }
            }
        }
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

    /** Cross-entity pet effect always runs on the pet entity's scheduler. */
    private void buffActivePet(final Player owner, final boolean capstone) {
        final PetManager petManager = pets;
        if (petManager == null) return;
        final Mob pet = petManager.livePet(owner);
        if (pet == null) return;
        final int regenTicks = config.getInt(capstone
                ? "classes.archer.bond.king-pet-regen-ticks"
                : "classes.archer.bond.primal-regen-ticks", capstone ? 200 : 120);
        final int amplifier = (capstone ? 1 : 0)
                + ("vastag_bor".equals(doctrine(owner.getUniqueId(), 40)) ? 1 : 0);
        final boolean caretaker = "gondozo".equals(doctrine(owner.getUniqueId(), 30));
        pet.getScheduler().run(plugin, task -> {
            if (!pet.isValid() || pet.isDead()) return;
            pet.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    regenTicks + (caretaker ? config.getInt(
                            "classes.archer.bond.caretaker-extra-ticks", 60) : 0),
                    amplifier, false, true, true));
            pet.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    regenTicks, amplifier, false, true, true));
            if (capstone) {
                pet.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        regenTicks, 0, false, true, true));
            }
        }, null);
        owner.sendActionBar(messages.getMessage(capstone
                        ? "archer.bond.king" : "archer.bond.primal",
                capstone ? "<gold>Vadak Királya: a falka ereje a társadban lüktet.</gold>"
                        : "<aqua>Ősi Kötelék: a társad megerősödött.</aqua>"));
    }

    private ArcherCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new ArcherCombatState());
    }

    private boolean isArcher(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.ARCHER;
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
                "classes.archer.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private int bondCost(final UUID playerId, final String spellId) {
        int cost = switch (spellId) {
            case "primal_bond" -> Math.max(0, config.getInt(
                    "classes.archer.bond.primal-cost", 40));
            case "king_of_beasts" -> Math.max(0, config.getInt(
                    "classes.archer.bond.king-cost", 80));
            default -> 0;
        };
        if (cost > 0 && "primal_bond".equals(spellId)
                && "osszhang".equals(doctrine(playerId, 40))) {
            cost -= Math.max(0, config.getInt(
                    "classes.archer.bond.harmony-cost-reduction", 10));
        }
        return Math.max(0, cost);
    }

    private float fullDrawForce() {
        return (float) Math.max(0.1D, Math.min(1.0D,
                config.getDouble("classes.archer.wind.full-draw-force", 0.9D)));
    }

    private long shotPacingMillis(final UUID playerId) {
        final long base = Math.max(0L, config.getLong(
                "classes.archer.wind.shot-pacing-millis", 900L));
        return "gyors_felhuzas".equals(doctrine(playerId, 30)) ? base * 3L / 4L : base;
    }

    private int inFlightMaximum() {
        return Math.max(1, Math.min(256, config.getInt(
                "classes.archer.wind.in-flight-maximum", 32)));
    }

    private long inFlightExpiryMillis() {
        return Math.max(1000L, config.getLong(
                "classes.archer.wind.in-flight-expiry-millis", 15000L));
    }

    private double windReadMinimumDistance(final UUID playerId) {
        return Math.max(1.0D, config.getDouble(
                "classes.archer.wind.minimum-distance", 12.0D));
    }

    private long windReadWindowMillis(final UUID playerId) {
        return Math.max(1000L, config.getLong(
                "classes.archer.wind.read-window-millis", 5000L));
    }

    private long chainWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.archer.precision.chain-window-millis", 6000L));
        return "nyugodt_kez".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.archer.precision.calm-extra-millis", 3000L)) : base;
    }

    private int maximumChain(final UUID playerId) {
        return Math.max(2, config.getInt("classes.archer.precision.maximum-chain", 5));
    }

    private int weakPointThreshold(final UUID playerId) {
        final int base = Math.max(2, config.getInt(
                "classes.archer.precision.weak-point-threshold", 4));
        return "eles_szem".equals(doctrine(playerId, 40)) ? Math.max(2, base - 1) : base;
    }

    private int weakPointRetention(final UUID playerId) {
        return "sorozat".equals(doctrine(playerId, 50))
                ? Math.max(0, config.getInt("classes.archer.precision.volley-retention", 2)) : 0;
    }

    private static Player arrowShooter(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow arrow
                && arrow.getShooter() instanceof Player player) return player;
        return null;
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
