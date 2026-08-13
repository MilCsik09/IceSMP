package hu.taliann.icesmp.warrior;

import hu.taliann.icesmp.classspec.application.TargetRegistry;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCooldownStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.spells.SpellTargetingUtil;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * Concrete Harcos vertical-slice runtime.
 *
 * <p>No generic meter/state-machine framework lives here. Csatatempó is the class decision layer,
 * Vérőrület/Kimerülés belongs only to Berserker and Őrség/Eskütárs only to Guardian. Every map is
 * transient and explicitly cleaned; durable class/spec/doctrine/mastery state remains Profile v2.</p>
 */
public final class WarriorGameplayService implements Listener, PlayerStateCleanup {

    /** Builder/event systems may mark a supported protectable NPC/objective with this scoreboard tag. */
    public static final String GUARD_OBJECTIVE_TAG = "icesmp_guard_objective";
    private static final String DEFIANT_COOLDOWN_KEY = "warrior.berserker.defiant";

    private record OathTarget(UUID id, LivingEntity entity, EntityScheduler scheduler, String label) {
        OathTarget {
            Objects.requireNonNull(id);
            Objects.requireNonNull(entity);
            Objects.requireNonNull(scheduler);
            label = label == null || label.isBlank() ? id.toString() : label;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;
    private final PlayerProfileCooldownStore cooldowns = new PlayerProfileCooldownStore();

    private final Map<UUID, WarriorCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;
    private final Map<UUID, OathTarget> oathTargets = new ConcurrentHashMap<>();
    private final TargetRegistry oathLinks = new TargetRegistry();
    private final Set<UUID> defiantPending = ConcurrentHashMap.newKeySet();

    public WarriorGameplayService(final JavaPlugin plugin,
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

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.WARRIOR) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.warrior.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.warrior.active-kit." + activeSpec)) {
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

    /** Mastery is combat-gated; the existing combat tracker is the anti-AFK/dummy-farm witness. */
    public void setCombatTracker(final ResourceManager resources) {
        combatTracker = java.util.Objects.requireNonNull(resources, "resources");
    }

    private boolean isInCombat(final UUID playerId) {
        final ResourceManager tracker = combatTracker;
        final long windowMillis = Math.max(1L, config.getLong(
                "classes.warrior.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    /**
     * The ONLY path to Warrior class mastery. Routing every contribution through this funnel is
     * what makes the combat gate unbypassable: there is no second call site to forget.
     */
    private void awardMastery(final Player player, final boolean conditionMet,
                              final String configKey, final int fallbackXp) {
        if (!WarriorCombatState.awardsMastery(conditionMet, isInCombat(player.getUniqueId()))) {
            return;
        }
        specs.contributeWarriorMastery(player, config.getInt(configKey, fallbackXp));
    }

    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isWarrior(player) || spell == null) return true;
        final String spec = activeSpec(player.getUniqueId());
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final WarriorCombatState state = state(player.getUniqueId());
        final long now = System.currentTimeMillis();
        if ("berserker".equals(spec) && ("rage".equals(spellId) || "berserk".equals(spellId))) {
            refreshBerserker(state, player.getUniqueId(), now);
            final int threshold = highFuryThreshold();
            if (state.bloodFrenzy(now, threshold,
                    exhaustionRate(player.getUniqueId()), exhaustionRecovery(),
                    overdriveExhaustionRate(), aftermathMillis(player.getUniqueId()),
                    overdriveEndExhaustion()) < threshold) {
                player.sendActionBar(messages.getMessage("warrior.berserker.need-fury",
                        "<red>A túlhajtáshoz legalább {amount} Vérőrület kell.</red>",
                        Map.of("amount", Integer.toString(threshold))));
                return false;
            }
            if (state.aftermathActive(now) || state.overdriveActive(now)) {
                player.sendActionBar(messages.getMessage("warrior.berserker.cooling",
                        "<red>Kimerülés alatt nem indíthatsz új túlhajtást.</red>"));
                return false;
            }
        }
        if ("guardian".equals(spec)) {
            final int guardCost = guardCost(player.getUniqueId(), spellId);
            if (guardCost > 0 && state.guard() < guardCost) {
                player.sendActionBar(messages.getMessage("warrior.guardian.need-guard",
                        "<red>Nincs elég Őrséged. Szükséges: {amount}.</red>",
                        Map.of("amount", Integer.toString(guardCost))));
                return false;
            }
            if (("shield_charge".equals(spellId) || "aegis".equals(spellId))
                    && !oathTargets.containsKey(player.getUniqueId())) {
                player.sendActionBar(messages.getMessage("warrior.guardian.need-oath",
                        "<red>Előbb jelölj ki Eskütársat a Lélekkapoccsal.</red>"));
                return false;
            }
        }
        return true;
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isWarrior(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final String spec = activeSpec(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final WarriorCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if ("heroic_leap".equals(spellId)) {
            addTempo(state, config.getInt("classes.warrior.battle-tempo.mobility-gain", 8), now);
        } else if ("pommel_strike".equals(spellId)) {
            addTempo(state, config.getInt("classes.warrior.battle-tempo.interrupt-gain", 18), now);
        }
        if ("berserker".equals(spec)) handleBerserkerCast(player, state, spellId, now);
        else if ("guardian".equals(spec)) handleGuardianCast(player, state, spellId, now);
        if ("whirlwind".equals(spellId)) {
            final WarriorCombatState.TempoTier tier = tempoTier(state, now);
            final int retain = tier == WarriorCombatState.TempoTier.TULCSORDULO
                    ? Math.max(0, Math.min(100, config.getInt(
                    "classes.warrior.battle-tempo.overflow-finisher-retain", 25))) : 0;
            state.finishTempo(retain);
            player.sendActionBar(messages.getMessage("warrior.tempo.finisher",
                    "<gold>Csatatempó levezetve: <white>{tier}</white> → {value}</gold>",
                    Map.of("tier", tempoName(tier), "value", Integer.toString(retain))));
        }
    }

    public Component hudSuffix(final Player player) {
        if (!isWarrior(player)) return Component.empty();
        final WarriorCombatState state = state(player.getUniqueId());
        final long now = System.currentTimeMillis();
        final WarriorCombatState.TempoTier tier = tempoTier(state, now);
        final int tempo = battleTempo(state, now);
        Component suffix = Component.text("  • Tempó " + tempoName(tier) + " " + tempo,
                NamedTextColor.GOLD);
        final String spec = activeSpec(player.getUniqueId());
        if ("berserker".equals(spec)) {
            refreshBerserker(state, player.getUniqueId(), now);
            suffix = suffix.append(Component.text("  • Vér "
                    + state.bloodFrenzy(now, highFuryThreshold(), exhaustionRate(player.getUniqueId()),
                    exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(player.getUniqueId()),
                    overdriveEndExhaustion()) + " / Kim "
                    + state.exhaustion(now, highFuryThreshold(), exhaustionRate(player.getUniqueId()),
                    exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(player.getUniqueId()),
                    overdriveEndExhaustion()), NamedTextColor.RED));
        } else if ("guardian".equals(spec)) {
            final String oath = state.oathTargetLabel().isBlank() ? "—" : state.oathTargetLabel();
            suffix = suffix.append(Component.text("  • Őrség " + state.guard()
                    + " • Eskü " + oath, NamedTextColor.YELLOW));
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isWarrior(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final WarriorCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final WarriorCombatState.TempoTier tier = tempoTier(combat, now);
        final int tempo = battleTempo(combat, now);
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "battle_tempo", "Tempó", "Tempó " + tempoName(tier) + " " + tempo,
                tempo, 100, tier.name().toLowerCase(Locale.ROOT));
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = "";
        String proc = "";
        final String spec = activeSpec(id);
        if ("berserker".equals(spec)) {
            refreshBerserker(combat, id, now);
            final int blood = combat.bloodFrenzy(now, highFuryThreshold(), exhaustionRate(id),
                    exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(id),
                    overdriveEndExhaustion());
            final int exhaustion = combat.exhaustion(now, highFuryThreshold(), exhaustionRate(id),
                    exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(id),
                    overdriveEndExhaustion());
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "blood_frenzy", "Vér", "Vér " + blood + " / Kim " + exhaustion,
                    blood, 100, blood >= highFuryThreshold() ? "overdrive" : "building");
            stateText = combat.overdriveActive(now) ? "Túlpörgés aktív"
                    : combat.aftermathActive(now) ? "Utóhatás " + exhaustion
                    : "Kimerülés " + exhaustion;
            proc = blood >= highFuryThreshold() && !combat.overdriveActive(now)
                    ? "Túlpörgés kész" : "";
        } else if ("guardian".equals(spec)) {
            final String oath = combat.oathTargetLabel().isBlank() ? "—" : combat.oathTargetLabel();
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "guard", "Őrség", "Őrség " + combat.guard(), combat.guard(), 100,
                    oath.equals("—") ? "unbound" : "oath");
            stateText = "Eskü " + oath;
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, stateText, proc, 0, 0);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.WARRIOR) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"berserker".equals(spec) && !"guardian".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        clearOath(playerId);
        final WarriorCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
        defiantPending.remove(playerId);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        clearOath(playerId);
        final WarriorCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
        defiantPending.remove(playerId);
        for (final UUID guardian : oathLinks.unlinkTarget(playerId)) {
            final WarriorCombatState guardianState = states.get(guardian);
            if (guardianState != null) guardianState.clearOathTarget();
            oathTargets.remove(guardian);
        }
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
        oathTargets.clear();
        oathLinks.clear();
        defiantPending.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOutgoingDamage(final EntityDamageByEntityEvent event) {
        final UUID attackerId = attackerId(event);
        if (attackerId == null || !"berserker".equals(activeSpec(attackerId))) return;
        final WarriorCombatState state = state(attackerId);
        final long now = System.currentTimeMillis();
        refreshBerserker(state, attackerId, now);
        double bonusPercent = 0.0D;
        if (state.overdriveActive(now)) {
            bonusPercent += event.getEntity() instanceof Player
                    ? config.getDouble("classes.warrior.berserker.overdrive.pvp-damage-percent", 14.0D)
                    : config.getDouble("classes.warrior.berserker.overdrive.pve-damage-percent", 30.0D);
        }
        if ("executioner".equals(doctrine(attackerId, 40))
                && event.getEntity() instanceof LivingEntity living
                && healthRatio(living) <= config.getDouble(
                "classes.warrior.berserker.executioner.health-threshold", 0.25D)) {
            bonusPercent += event.getEntity() instanceof Player
                    ? config.getDouble("classes.warrior.berserker.executioner.pvp-damage-percent", 8.0D)
                    : config.getDouble("classes.warrior.berserker.executioner.pve-damage-percent", 18.0D);
        }
        final double cap = event.getEntity() instanceof Player
                ? config.getDouble("classes.warrior.berserker.pvp-max-bonus-percent", 25.0D)
                : config.getDouble("classes.warrior.berserker.pve-max-bonus-percent", 60.0D);
        bonusPercent = Math.max(0.0D, Math.min(cap, bonusPercent));
        if (bonusPercent > 0.0D) event.setDamage(event.getDamage() * (1.0D + bonusPercent / 100.0D));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatResolved(final EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0D) return;
        final long now = System.currentTimeMillis();
        final UUID attackerId = attackerId(event);
        if (attackerId != null && isWarrior(attackerId)) {
            final WarriorCombatState attacker = state(attackerId);
            attacker.addTempoFromAttack(event.getEntity().getUniqueId(),
                    config.getInt("classes.warrior.battle-tempo.attack-gain", 7),
                    config.getInt("classes.warrior.battle-tempo.target-switch-gain", 10),
                    Math.max(0L, config.getLong(
                            "classes.warrior.battle-tempo.target-switch-cooldown-millis", 1500L)),
                    now, tempoDecayDelayMillis(), tempoDecayPerSecond());
            if ("berserker".equals(activeSpec(attackerId))) {
                attacker.addBloodFrenzy(furyGain(event.getFinalDamage(), false), now,
                        highFuryThreshold(), exhaustionRate(attackerId), exhaustionRecovery(),
                        overdriveExhaustionRate(), aftermathMillis(attackerId), overdriveEndExhaustion());
            }
        }
        if (event.getEntity() instanceof Player victim && isWarrior(victim)) {
            final UUID victimId = victim.getUniqueId();
            final WarriorCombatState victimState = state(victimId);
            final String spec = activeSpec(victimId);
            if ("berserker".equals(spec)) {
                victimState.addBloodFrenzy(furyGain(event.getFinalDamage(), true), now,
                        highFuryThreshold(), exhaustionRate(victimId), exhaustionRecovery(),
                        overdriveExhaustionRate(), aftermathMillis(victimId), overdriveEndExhaustion());
                tryDefiantRecovery(victim, event.getFinalDamage());
            } else if ("guardian".equals(spec)) {
                int gain = config.getInt("classes.warrior.guardian.guard.damage-taken-gain", 7);
                if (victim.isBlocking()) {
                    gain += config.getInt("classes.warrior.guardian.guard.block-bonus", 15);
                    addTempo(victimState, config.getInt("classes.warrior.battle-tempo.block-gain", 12), now);
                }
                if ("bastion".equals(doctrine(victimId, 30)) && victim.isBlocking()) {
                    gain += config.getInt("classes.warrior.guardian.bastion.extra-guard", 8);
                }
                victimState.addGuard(gain);
            }
        }
        final Set<UUID> oathGuardians = oathLinks.ownersOf(event.getEntity().getUniqueId());
        if (!oathGuardians.isEmpty()) {
            for (final UUID guardianId : oathGuardians) {
                if (!"guardian".equals(activeSpec(guardianId))) continue;
                final int gain = Math.max(1, (int) Math.ceil(event.getFinalDamage()
                        * config.getDouble("classes.warrior.guardian.guard.oath-damage-gain-per-damage", 1.2D)));
                state(guardianId).addGuard(Math.min(gain,
                        config.getInt("classes.warrior.guardian.guard.oath-hit-gain-cap", 20)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        clearOathTarget(event.getEntity().getUniqueId());
        final MobKillUtil.KillContext kill = MobKillUtil.eligibleTrackingKill(event.getEntity());
        if (kill == null || !"berserker".equals(activeSpec(kill.killerId()))
                || !(event.getEntity() instanceof Monster)) return;
        final long now = System.currentTimeMillis();
        final WarriorCombatState state = state(kill.killerId());
        state.addBloodFrenzy(config.getInt("classes.warrior.berserker.fury.execution-gain", 16), now,
                highFuryThreshold(), exhaustionRate(kill.killerId()), exhaustionRecovery(),
                overdriveExhaustionRate(), aftermathMillis(kill.killerId()), overdriveEndExhaustion());
        addTempo(state, config.getInt("classes.warrior.battle-tempo.execution-gain", 12), now);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOathInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player guardian = event.getPlayer();
        if (!guardian.isSneaking() || !isWarrior(guardian)
                || !"guardian".equals(activeSpec(guardian.getUniqueId()))
                || !soulbondFactory.isUsableBy(
                guardian.getInventory().getItemInMainHand(), guardian.getUniqueId(), JobType.WARRIOR)
                || !(event.getRightClicked() instanceof LivingEntity target)
                || target == guardian) return;
        final boolean playerAlly = target instanceof Player && SpellTargetingUtil.isAlly(guardian, target);
        final boolean supportedObjective = target.getScoreboardTags().contains(GUARD_OBJECTIVE_TAG);
        if (!playerAlly && !supportedObjective) {
            guardian.sendActionBar(messages.getMessage("warrior.guardian.invalid-oath",
                    "<red>Eskütárs csak csapattag vagy támogatott védelmi objective lehet.</red>"));
            return;
        }
        event.setCancelled(true);
        final String label = target instanceof Player player ? player.getName()
                : target.getType().name().toLowerCase(Locale.ROOT);
        assignOath(guardian.getUniqueId(), target, label);
        guardian.sendActionBar(messages.getMessage("warrior.guardian.oath-set",
                "<gold>Eskütárs kijelölve: <white>{target}</white>.</gold>", Map.of("target", label)));
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

    private void tryDefiantRecovery(final Player player, final double incomingDamage) {
        final UUID playerId = player.getUniqueId();
        if (!"defiant".equals(doctrine(playerId, 50))) return;
        final double maxHealth = maxHealth(player);
        final double projected = player.getHealth() - Math.max(0.0D, incomingDamage);
        if (projected <= 0.0D) return;
        final double triggerRatio = Math.max(0.05D, Math.min(0.95D,
                config.getDouble("classes.warrior.berserker.defiant.trigger-health-ratio", 0.20D)));
        if (projected / maxHealth > triggerRatio) return;

        final long now = System.currentTimeMillis();
        final long readyAt;
        try {
            readyAt = cooldowns.read(playerId,
                    PlayerProfileCooldownStore.Domain.SPELLBOOK, DEFIANT_COOLDOWN_KEY);
        } catch (final RuntimeException notReady) {
            return;
        }
        if (readyAt > now || !defiantPending.add(playerId)) return;
        final long cooldownMillis = Math.max(30L, config.getLong(
                "classes.warrior.berserker.defiant.cooldown-seconds", 300L)) * 1000L;

        // Durable reservation happens before any recovery side effect. A crash before commit therefore
        // applies no Dacoló benefit; a successful commit is the witness that authorizes the heal.
        cooldowns.reserve(playerId, PlayerProfileCooldownStore.Domain.SPELLBOOK,
                        DEFIANT_COOLDOWN_KEY, now, now + cooldownMillis)
                .whenComplete((reserved, failure) -> {
                    defiantPending.remove(playerId);
                    if (failure != null) {
                        specs.profileGateway().blockSession(playerId,
                                "Dacoló cooldown persistence failed before recovery");
                        return;
                    }
                    if (!Boolean.TRUE.equals(reserved)) return;
                    player.getScheduler().run(plugin, task -> {
                        if (!player.isOnline() || player.isDead()
                                || !isWarrior(player)
                                || !"berserker".equals(activeSpec(playerId))
                                || !"defiant".equals(doctrine(playerId, 50))) return;
                        final double liveMax = maxHealth(player);
                        final double healToRatio = Math.max(triggerRatio, Math.min(1.0D,
                                config.getDouble("classes.warrior.berserker.defiant.heal-to-health-ratio", 0.35D)));
                        final double floor = Math.max(1.0D, liveMax * healToRatio);
                        if (player.getHealth() < floor) {
                            player.setHealth(Math.min(liveMax, floor));
                        }
                        state(playerId).forceMaximumExhaustion();
                        player.sendActionBar(messages.getMessage("warrior.berserker.defiant",
                                "<dark_red>Dacoló: a tartós túlélési tartalék aktiválódott, de teljesen kimerültél.</dark_red>"));
                    }, () -> specs.profileGateway().blockSession(playerId,
                            "Dacoló cooldown committed but recovery scheduler rejected"));
                });
    }

    private void handleBerserkerCast(final Player player, final WarriorCombatState state,
                                     final String spellId, final long now) {
        final UUID id = player.getUniqueId();
        switch (spellId) {
            case "reckless_swing", "blood_frenzy", "ground_slam" -> state.addBloodFrenzy(
                    config.getInt("classes.warrior.berserker.fury.ability-gain", 12), now,
                    highFuryThreshold(), exhaustionRate(id), exhaustionRecovery(),
                    overdriveExhaustionRate(), aftermathMillis(id), overdriveEndExhaustion());
            case "rage" -> startOverdrive(player, state, now, false);
            case "bloodbath" -> safeDump(player, state, now);
            case "berserk" -> startOverdrive(player, state, now, true);
            default -> { }
        }
        if ("whirlwind".equals(spellId) && "whirlwind".equals(doctrine(id, 40))) {
            final long targets = player.getNearbyEntities(4.5D, 3.0D, 4.5D).stream()
                    .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                    .filter(target -> SpellTargetingUtil.isHostileTarget(player, target)).limit(5L).count();
            if (targets > 1L) {
                state.addBloodFrenzy((int) (targets - 1L) * config.getInt(
                                "classes.warrior.berserker.whirlwind.fury-per-extra-target", 5), now,
                        highFuryThreshold(), exhaustionRate(id), exhaustionRecovery(),
                        overdriveExhaustionRate(), aftermathMillis(id), overdriveEndExhaustion());
            }
        }
    }

    private void startOverdrive(final Player player, final WarriorCombatState state,
                                final long now, final boolean capstone) {
        final UUID id = player.getUniqueId();
        final boolean kallan = capstone && "kallan_wrath".equals(doctrine(id, 50));
        final long duration = Math.max(1L, config.getLong(
                kallan ? "classes.warrior.berserker.kallan.duration-millis"
                        : "classes.warrior.berserker.overdrive.duration-millis", kallan ? 8000L : 5500L));
        if (!state.startOverdrive(now, highFuryThreshold(),
                config.getInt("classes.warrior.berserker.overdrive.max-starting-exhaustion", 70),
                duration, exhaustionRate(id), exhaustionRecovery(), overdriveExhaustionRate(),
                aftermathMillis(id), overdriveEndExhaustion())) return;
        if ("titan".equals(doctrine(id, 30))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    (int) Math.max(20L, duration / 50L), 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int) Math.max(20L, duration / 50L), 0, false, true, true));
        }
        player.sendActionBar(messages.getMessage("warrior.berserker.overdrive",
                kallan ? "<dark_red>Kallan Haragja: kontrollált túlhajtás!</dark_red>"
                        : "<red>Túlhajtás: most kell eldöntened, meddig maradsz fent.</red>"));
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || !"berserker".equals(activeSpec(id))) return;
            final WarriorCombatState live = states.get(id);
            if (live == null) return;
            refreshBerserker(live, id, System.currentTimeMillis());
            final long aftermathTicks = Math.max(20L, aftermathMillis(id) / 50L);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                    (int) Math.min(Integer.MAX_VALUE, aftermathTicks), 0, false, true, true));
        }, null, Math.max(1L, duration / 50L));
    }

    private void safeDump(final Player player, final WarriorCombatState state, final long now) {
        final UUID id = player.getUniqueId();
        final int before = state.bloodFrenzy(now, highFuryThreshold(), exhaustionRate(id),
                exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(id), overdriveEndExhaustion());
        state.safeDump(config.getInt("classes.warrior.berserker.safe-dump.fury-spend", 55),
                config.getInt("classes.warrior.berserker.safe-dump.exhaustion-reduction", 30));
        if ("bloodlust".equals(doctrine(id, 30))
                && healthRatio(player) <= config.getDouble(
                "classes.warrior.berserker.bloodlust.health-threshold", 0.45D)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    config.getInt("classes.warrior.berserker.bloodlust.regen-ticks", 80),
                    0, false, true, true));
        }
        awardMastery(player, before >= highFuryThreshold(),
                "classes.warrior.mastery.safe-dump-xp", 4);
        player.sendActionBar(messages.getMessage("warrior.berserker.safe-dump",
                "<gold>Biztonságos levezetés: Vérőrület és Kimerülés csökkent.</gold>"));
    }

    private void handleGuardianCast(final Player player, final WarriorCombatState state,
                                    final String spellId, final long now) {
        final UUID id = player.getUniqueId();
        switch (spellId) {
            case "bulwark" -> {
                state.addGuard(config.getInt("classes.warrior.guardian.guard.bulwark-gain", 22));
                if ("shield_wall".equals(doctrine(id, 40))) {
                    state.addGuard(config.getInt("classes.warrior.guardian.shield-wall.extra-guard", 10));
                }
            }
            case "taunt" -> state.addGuard(config.getInt("classes.warrior.guardian.guard.taunt-gain", 14));
            case "fortify", "shield_wall" -> state.addGuard(config.getInt("classes.warrior.guardian.guard.defense-gain", 18));
            case "shield_charge" -> {
                if (state.spendGuard(guardCost(id, spellId))) {
                    protectOathTarget(player, false, false);
                    addTempo(state, config.getInt("classes.warrior.battle-tempo.intercept-gain", 15), now);
                    awardMastery(player, true, "classes.warrior.mastery.intercept-xp", 4);
                }
            }
            case "aegis" -> {
                if (state.spendGuard(guardCost(id, spellId))) {
                    final boolean forAll = "for_all".equals(doctrine(id, 50));
                    protectOathTarget(player, true, !forAll);
                    if (forAll) protectNearbyAllies(player);
                    if ("war_signal".equals(doctrine(id, 40))) applyWarSignal(player);
                    awardMastery(player, true, "classes.warrior.mastery.guard-spend-xp", 5);
                }
            }
            case "last_stand" -> {
                if (state.spendGuard(guardCost(id, spellId))) {
                    final boolean forAll = "for_all".equals(doctrine(id, 50));
                    protectOathTarget(player, true, !forAll);
                    if (forAll) protectNearbyAllies(player);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                            config.getInt("classes.warrior.guardian.last-stand.self-duration-ticks", 100),
                            1, false, true, true));
                    awardMastery(player, true,
                            "classes.warrior.mastery.capstone-protection-xp", 8);
                }
            }
            default -> { }
        }
    }

    private void protectOathTarget(final Player guardian, final boolean major,
                                   final boolean strongestSingleTarget) {
        final UUID guardianId = guardian.getUniqueId();
        final OathTarget target = oathTargets.get(guardianId);
        if (target == null) return;
        final int duration = config.getInt(
                major ? "classes.warrior.guardian.shield.major-duration-ticks"
                        : "classes.warrior.guardian.shield.intercept-duration-ticks", major ? 100 : 60);
        int absorptionAmplifier = major ? 1 : 0;
        if (strongestSingleTarget && "for_one".equals(doctrine(guardianId, 50))) {
            absorptionAmplifier = Math.max(absorptionAmplifier,
                    config.getInt("classes.warrior.guardian.for-one.absorption-amplifier", 2));
        }
        final int finalAmplifier = absorptionAmplifier;
        target.scheduler().run(plugin, task -> {
            final LivingEntity entity = target.entity();
            if (!entity.isValid() || entity.isDead()) {
                clearOath(guardianId);
                return;
            }
            entity.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    duration, finalAmplifier, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Math.max(20, duration / 2), 0, false, true, true));
        }, () -> clearOath(guardianId));
    }

    private void protectNearbyAllies(final Player guardian) {
        final int duration = config.getInt("classes.warrior.guardian.for-all.duration-ticks", 70);
        final int amplifier = config.getInt("classes.warrior.guardian.for-all.absorption-amplifier", 0);
        final double radius = Math.max(1.0D, config.getDouble("classes.warrior.guardian.for-all.radius", 8.0D));
        for (final var nearby : guardian.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player member) || !SpellTargetingUtil.isAlly(guardian, member)) continue;
            member.getScheduler().run(plugin, task -> member.addPotionEffect(
                    new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier, false, true, true)), null);
        }
    }

    private void applyWarSignal(final Player guardian) {
        final int duration = config.getInt("classes.warrior.guardian.war-signal.duration-ticks", 60);
        final double radius = Math.max(1.0D, config.getDouble("classes.warrior.guardian.war-signal.radius", 8.0D));
        for (final var nearby : guardian.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player member) || !SpellTargetingUtil.isAlly(guardian, member)) continue;
            member.getScheduler().run(plugin, task -> member.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, duration, 0, false, true, true)), null);
        }
    }

    private WarriorCombatState state(final UUID id) { return states.computeIfAbsent(id, ignored -> new WarriorCombatState()); }

    private boolean isWarrior(final Player player) { return player != null && jobs.getPrimaryJob(player) == JobType.WARRIOR; }

    private boolean isWarrior(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile != null && "warrior".equals(profile.primaryClassId());
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

    private int battleTempo(final WarriorCombatState state, final long now) {
        return state.battleTempo(now, tempoDecayDelayMillis(), tempoDecayPerSecond());
    }

    private WarriorCombatState.TempoTier tempoTier(final WarriorCombatState state, final long now) {
        return state.tempoTier(now,
                config.getInt("classes.warrior.battle-tempo.heated-threshold", 35),
                config.getInt("classes.warrior.battle-tempo.overflowing-threshold", 75),
                tempoDecayDelayMillis(), tempoDecayPerSecond());
    }

    private void addTempo(final WarriorCombatState state, final int amount, final long now) {
        state.addTempo(amount, now, tempoDecayDelayMillis(), tempoDecayPerSecond());
    }

    private long tempoDecayDelayMillis() {
        return Math.max(0L, config.getLong("classes.warrior.battle-tempo.decay-delay-millis", 5000L));
    }

    private double tempoDecayPerSecond() {
        return Math.max(0.0D, config.getDouble("classes.warrior.battle-tempo.decay-per-second", 8.0D));
    }

    private int highFuryThreshold() {
        return Math.max(1, Math.min(100, config.getInt("classes.warrior.berserker.fury.high-threshold", 70)));
    }

    private int furyGain(final double damage, final boolean taken) {
        final String path = taken ? "taken" : "dealt";
        final double perDamage = Math.max(0.0D, config.getDouble(
                "classes.warrior.berserker.fury." + path + "-gain-per-damage", taken ? 1.2D : 0.9D));
        final int cap = Math.max(1, config.getInt(
                "classes.warrior.berserker.fury." + path + "-gain-cap", taken ? 18 : 14));
        return Math.max(1, Math.min(cap, (int) Math.ceil(damage * perDamage)));
    }

    private double exhaustionRate(final UUID playerId) {
        final double base = Math.max(0.0D,
                config.getDouble("classes.warrior.berserker.exhaustion.high-fury-per-second", 5.0D));
        return "titan".equals(doctrine(playerId, 30))
                ? base * Math.max(0.1D,
                config.getDouble("classes.warrior.berserker.titan.exhaustion-multiplier", 0.65D)) : base;
    }

    private double exhaustionRecovery() {
        return Math.max(0.0D, config.getDouble("classes.warrior.berserker.exhaustion.recovery-per-second", 7.0D));
    }

    private double overdriveExhaustionRate() {
        return Math.max(0.0D, config.getDouble("classes.warrior.berserker.exhaustion.overdrive-per-second", 9.0D));
    }

    private int overdriveEndExhaustion() {
        return Math.max(0, config.getInt("classes.warrior.berserker.exhaustion.overdrive-end-add", 25));
    }

    private long aftermathMillis(final UUID playerId) {
        final boolean kallan = "kallan_wrath".equals(doctrine(playerId, 50));
        return Math.max(0L, config.getLong(
                kallan ? "classes.warrior.berserker.kallan.aftermath-millis"
                        : "classes.warrior.berserker.overdrive.aftermath-millis", kallan ? 12000L : 7000L));
    }

    private void refreshBerserker(final WarriorCombatState state, final UUID playerId, final long now) {
        state.refreshBerserker(now, highFuryThreshold(), exhaustionRate(playerId),
                exhaustionRecovery(), overdriveExhaustionRate(), aftermathMillis(playerId), overdriveEndExhaustion());
    }

    private int guardCost(final UUID playerId, final String spellId) {
        int cost = switch (spellId) {
            case "shield_charge" -> config.getInt("classes.warrior.guardian.guard.shield-charge-cost", 30);
            case "aegis" -> config.getInt("classes.warrior.guardian.guard.aegis-cost", 60);
            case "last_stand" -> config.getInt("classes.warrior.guardian.guard.last-stand-cost", 80);
            default -> 0;
        };
        if (cost > 0 && "shield_charge".equals(spellId)
                && "vanguard".equals(doctrine(playerId, 30))) {
            cost -= Math.max(0, config.getInt(
                    "classes.warrior.guardian.vanguard.intercept-cost-reduction", 8));
        }
        return Math.max(0, cost);
    }

    private void assignOath(final UUID guardianId, final LivingEntity target, final String label) {
        clearOath(guardianId);
        final OathTarget handle = new OathTarget(target.getUniqueId(), target, target.getScheduler(), label);
        oathTargets.put(guardianId, handle);
        oathLinks.link(guardianId, handle.id());
        state(guardianId).setOathTarget(handle.id(), label);
    }

    private void clearOath(final UUID guardianId) {
        final OathTarget old = oathTargets.remove(guardianId);
        if (old != null) oathLinks.unlink(guardianId, old.id());
        else oathLinks.unlinkOwner(guardianId);
        final WarriorCombatState state = states.get(guardianId);
        if (state != null) state.clearOathTarget();
    }

    private void clearOathTarget(final UUID targetId) {
        for (final UUID guardian : oathLinks.unlinkTarget(targetId)) {
            oathTargets.remove(guardian);
            final WarriorCombatState state = states.get(guardian);
            if (state != null) state.clearOathTarget();
        }
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

    private static double healthRatio(final LivingEntity entity) {
        return Math.max(0.0D, Math.min(1.0D, entity.getHealth() / maxHealth(entity)));
    }

    private static String tempoName(final WarriorCombatState.TempoTier tier) {
        return switch (tier) {
            case RENDEZETT -> "Rendezett";
            case HEVES -> "Heves";
            case TULCSORDULO -> "Túlcsorduló";
        };
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
