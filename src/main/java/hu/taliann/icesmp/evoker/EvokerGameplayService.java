package hu.taliann.icesmp.evoker;

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
import hu.taliann.icesmp.spells.SpellTargetingUtil;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * Concrete Sárkányidéző vertical-slice runtime.
 *
 * <p>This is the architectural counter-proof after the Warrior: no class meter, no target-bound
 * reverse index, no damage-amplification event path. The class core is the Felerősítés
 * (charge-and-release) lifecycle on a handful of concrete spells; Perzselés plays one red/blue
 * alternation counter through the existing cast power pipeline and Megőrzés plays the
 * Visszhang/Időlenyomat prepared-heal loop on narrow heal-only state. Every map is transient and
 * explicitly cleaned; durable class/spec/doctrine/mastery state remains Profile v2.</p>
 */
public final class EvokerGameplayService implements Listener, PlayerStateCleanup {

    private record MarkTarget(UUID id, Player entity, EntityScheduler scheduler, String label) {
        MarkTarget {
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

    private final Map<UUID, EvokerCombatState> states = new ConcurrentHashMap<>();
    private final Map<UUID, MarkTarget> markTargets = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public EvokerGameplayService(final JavaPlugin plugin,
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

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.EVOKER) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.evoker.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.evoker.active-kit." + activeSpec)) {
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
     * Felerősítés gate: the first click on an empowerable spell begins the charge without
     * spending resources; the next click within the fizzle window releases it.
     */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isEvoker(player) || spell == null) return true;
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        if (!empowerSpells().contains(spellId)) return true;
        final UUID playerId = player.getUniqueId();
        final EvokerCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if (state.releaseRank(spellId, now, rank2HoldMillis(playerId),
                rank3HoldMillis(playerId), fizzleMillis(playerId)) > 0) {
            return true;
        }
        state.startCharge(spellId, now);
        player.sendActionBar(messages.getMessage("evoker.empower.charging",
                "<light_purple>Felerősítés I — kattints újra az elengedéshez; tartsd tovább a II/III fokozatért.</light_purple>"));
        return false;
    }

    /**
     * Pure pre-cast peek for the shared power pipeline: Felerősítés rank bonus plus an armed
     * Perzselés burst. State is only committed in {@link #afterCast} after a successful cast.
     */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isEvoker(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final EvokerCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        double bonus = 0.0D;
        final int rank = state.releaseRank(spellId, now, rank2HoldMillis(playerId),
                rank3HoldMillis(playerId), fizzleMillis(playerId));
        if (rank == 2) {
            bonus += Math.max(0.0D, config.getDouble("classes.evoker.empower.rank2-power-percent", 20.0D));
        } else if (rank >= 3) {
            bonus += Math.max(0.0D, config.getDouble("classes.evoker.empower.rank3-power-percent", 40.0D));
        }
        if ("devastation".equals(activeSpec(playerId)) && essenceColorOf(spellId) != null
                && state.isBurstArmed(burstThreshold(playerId))) {
            bonus += burstPowerPercent(playerId);
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.evoker.max-power-bonus-percent", 65.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isEvoker(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final EvokerCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);

        final int releasedRank = state.releaseRank(spellId, now, rank2HoldMillis(playerId),
                rank3HoldMillis(playerId), fizzleMillis(playerId));
        if (releasedRank > 0) {
            state.clearCharge();
            player.sendActionBar(messages.getMessage("evoker.empower.released",
                    "<light_purple>Felerősítés {rank} elengedve.</light_purple>",
                    Map.of("rank", roman(releasedRank))));
            if (releasedRank >= 2 && isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.EVOKER,
                        config.getInt("classes.evoker.mastery.empowered-release-xp", 4));
            }
        }

        if ("devastation".equals(spec)) handleDevastationCast(player, state, spellId, releasedRank);
        else if ("preservation".equals(spec)) handlePreservationCast(player, state, spellId, now);
    }

    private void handleDevastationCast(final Player player, final EvokerCombatState state,
                                       final String spellId, final int releasedRank) {
        final UUID playerId = player.getUniqueId();
        EvokerCombatState.EssenceColor color = essenceColorOf(spellId);
        if (color == null) return;
        final int threshold = burstThreshold(playerId);
        if (state.isBurstArmed(threshold)) {
            state.consumeBurst(burstRetention(playerId));
            player.sendActionBar(messages.getMessage("evoker.essence.burst",
                    "<gold>✸ Izzás-kitörés: a Vörös–Kék Eszencia felszabadult!</gold>"));
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.EVOKER,
                        config.getInt("classes.evoker.mastery.resonance-burst-xp", 5));
            }
            return;
        }
        if (releasedRank >= 2 && "kettos_szikra".equals(doctrine(playerId, 50))) {
            final EvokerCombatState.EssenceColor last = state.lastEssenceColor().orElse(null);
            if (last == color) {
                color = color == EvokerCombatState.EssenceColor.VOROS
                        ? EvokerCombatState.EssenceColor.KEK
                        : EvokerCombatState.EssenceColor.VOROS;
            }
        }
        final int resonance = state.recordEssenceCast(color, threshold);
        if (state.isBurstArmed(threshold)) {
            player.sendActionBar(messages.getMessage("evoker.essence.armed",
                    "<gold>Izzás kész ({value}/{threshold}): a következő eszencia-spell kitörés lesz.</gold>",
                    Map.of("value", Integer.toString(resonance),
                            "threshold", Integer.toString(threshold))));
        }
    }

    private void handlePreservationCast(final Player player, final EvokerCombatState state,
                                        final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        switch (spellId) {
            case "echo" -> {
                state.armEcho(now, echoWindowMillis(playerId));
                player.sendActionBar(messages.getMessage("evoker.echo.armed",
                        "<aqua>Visszhang előkészítve: a következő gyógyításod megismétlődik.</aqua>"));
            }
            case "reversion" -> {
                state.recordImprint(player.getHealth(), now, imprintWindowMillis(playerId));
                if ("gyors_lenyomat".equals(doctrine(playerId, 40))) {
                    state.armEcho(now, echoWindowMillis(playerId));
                }
                player.sendActionBar(messages.getMessage("evoker.imprint.recorded",
                        "<aqua>Időlenyomat rögzítve ({seconds} mp): csak az életerőd tér vissza, semmi más.</aqua>",
                        Map.of("seconds", Long.toString(imprintWindowMillis(playerId) / 1000L))));
            }
            case "temporal_anomaly" -> tryImprintRestore(player, state, config.getDouble(
                    "classes.evoker.preservation.imprint.anomaly-restore-cap-percent", 30.0D));
            case "rewind" -> {
                tryImprintRestore(player, state, config.getDouble(
                        "classes.evoker.preservation.imprint.rewind-restore-cap-percent", 60.0D));
                state.armEcho(now, echoWindowMillis(playerId));
            }
            default -> {
                if (echoHealSpells().contains(spellId) && state.consumeEcho(now)) {
                    scheduleEchoHeal(player);
                }
            }
        }
    }

    /**
     * Heal-only Időlenyomat consumption: restores health toward the recorded value, bounded by
     * the configured cap, single-use. Nothing else is rolled back by design.
     */
    private void tryImprintRestore(final Player player, final EvokerCombatState state,
                                   final double restoreCapPercent) {
        final UUID playerId = player.getUniqueId();
        final long now = System.currentTimeMillis();
        final double maxHealth = maxHealth(player);
        final double gainCap = maxHealth * Math.max(0.0D, Math.min(100.0D, restoreCapPercent)) / 100.0D;
        final double target = state.consumeImprintRestore(now, player.getHealth(), gainCap);
        if (target <= 0.0D) return;
        player.setHealth(Math.min(maxHealth, target));
        if ("tiszta_ido".equals(doctrine(playerId, 50))) {
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.WITHER);
            player.setFireTicks(0);
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.EVOKER,
                    config.getInt("classes.evoker.mastery.imprint-restore-xp", 5));
        }
        player.sendActionBar(messages.getMessage("evoker.imprint.restored",
                "<aqua>Időlenyomat: az életerőd visszatért a rögzített szint felé.</aqua>"));
    }

    private void scheduleEchoHeal(final Player player) {
        final UUID playerId = player.getUniqueId();
        final double amount = Math.max(0.5D, config.getDouble(
                "classes.evoker.preservation.echo.heal-amount", 3.0D)
                + ("melyebb_visszhang".equals(doctrine(playerId, 30))
                ? config.getDouble("classes.evoker.preservation.echo.deep-extra-heal", 2.0D) : 0.0D));
        final boolean absorption = "orzo_pajzs".equals(doctrine(playerId, 50));
        final long delayTicks = Math.max(10L, config.getLong(
                "classes.evoker.preservation.echo.delay-millis", 2000L) / 50L);
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || player.isDead()
                    || !"preservation".equals(activeSpec(playerId))) return;
            final boolean healed = healEntity(player, amount, absorption);
            if (healed && isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.EVOKER,
                        config.getInt("classes.evoker.mastery.echo-heal-xp", 4));
            }
            player.sendActionBar(messages.getMessage("evoker.echo.landed",
                    "<aqua>A gyógyítás visszhangja végigfutott.</aqua>"));
            final MarkTarget mark = markTargets.get(playerId);
            if (mark != null) {
                mark.scheduler().run(plugin, allyTask -> {
                    final Player ally = mark.entity();
                    if (!ally.isOnline() || ally.isDead()) {
                        clearMark(playerId);
                        return;
                    }
                    healEntity(ally, amount, absorption);
                }, () -> clearMark(playerId));
            }
        }, null, delayTicks);
    }

    private static boolean healEntity(final Player target, final double amount,
                                      final boolean absorption) {
        final double maxHealth = maxHealth(target);
        final double before = target.getHealth();
        final double after = Math.min(maxHealth, before + Math.max(0.0D, amount));
        if (after > before) target.setHealth(after);
        if (absorption) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    100, 0, false, true, true));
        }
        return after > before;
    }

    public Component hudSuffix(final Player player) {
        if (!isEvoker(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final EvokerCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        Component suffix = Component.empty();
        final String chargingSpell = state.chargingSpellId();
        if (!chargingSpell.isEmpty()) {
            final int rank = state.releaseRank(chargingSpell, now, rank2HoldMillis(playerId),
                    rank3HoldMillis(playerId), fizzleMillis(playerId));
            if (rank > 0) {
                suffix = suffix.append(Component.text("  • Felerősítés " + roman(rank),
                        NamedTextColor.LIGHT_PURPLE));
            }
        }
        final String spec = activeSpec(playerId);
        if ("devastation".equals(spec)) {
            final int threshold = burstThreshold(playerId);
            suffix = suffix.append(Component.text("  • Izzás " + state.resonance() + "/" + threshold
                    + (state.isBurstArmed(threshold) ? " ✸" : ""), NamedTextColor.GOLD));
        } else if ("preservation".equals(spec)) {
            if (state.isEchoArmed(now)) {
                suffix = suffix.append(Component.text("  • Visszhang kész", NamedTextColor.AQUA));
            }
            final long imprintMillis = state.imprintRemainingMillis(now);
            if (imprintMillis > 0L) {
                suffix = suffix.append(Component.text("  • Lenyomat "
                        + (imprintMillis + 999L) / 1000L + "s", NamedTextColor.AQUA));
            }
            if (!state.markedAllyLabel().isBlank()) {
                suffix = suffix.append(Component.text("  • Jel: " + state.markedAllyLabel(),
                        NamedTextColor.GREEN));
            }
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isEvoker(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final EvokerCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final String charging = combat.chargingSpellId();
        final int rank = charging.isEmpty() ? 0 : combat.releaseRank(charging, now,
                rank2HoldMillis(id), rank3HoldMillis(id), fizzleMillis(id));
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "empower", "Felerősítés", rank > 0 ? "Felerősítés " + roman(rank) : "Felerősítés —",
                rank, 3, rank > 0 ? "charging" : "idle");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = "";
        String proc = "";
        final String spec = activeSpec(id);
        if ("devastation".equals(spec)) {
            final int threshold = burstThreshold(id);
            final boolean armed = combat.isBurstArmed(threshold);
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "resonance", "Izzás", "Izzás " + combat.resonance() + "/" + threshold,
                    combat.resonance(), threshold, armed ? "ready" : "building");
            stateText = combat.lastEssenceColor().map(color -> color == EvokerCombatState.EssenceColor.VOROS
                    ? "Eszencia Vörös" : "Eszencia Kék").orElse("");
            proc = armed ? "Kitörés kész" : "";
        } else if ("preservation".equals(spec)) {
            final long imprint = combat.imprintRemainingMillis(now);
            secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "imprint", "Lenyomat", imprint > 0 ? "Lenyomat " + ((imprint + 999L) / 1000L) + "s" : "Lenyomat —",
                    imprint, fizzleMillis(id), imprint > 0 ? "active" : "idle");
            stateText = combat.markedAllyLabel().isBlank() ? "" : "Jel: " + combat.markedAllyLabel();
            proc = combat.isEchoArmed(now) ? "Visszhang kész" : "";
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, stateText, proc, rank, 3);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.EVOKER) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"devastation".equals(spec) && !"preservation".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        clearMark(playerId);
        final EvokerCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        clearMark(playerId);
        final EvokerCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
        markTargets.clear();
    }

    /** A meaningful hit interrupts a held Felerősítés charge — the commitment risk of the class core. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatResolved(final EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0D
                || !(event.getEntity() instanceof Player victim) || !isEvoker(victim)) return;
        final EvokerCombatState state = state(victim.getUniqueId());
        if (state.chargingSpellId().isEmpty()) return;
        final double interruptAt = Math.max(0.5D, config.getDouble(
                "classes.evoker.empower.interrupt-damage", 4.0D));
        if (event.getFinalDamage() < interruptAt) return;
        state.clearCharge();
        victim.sendActionBar(messages.getMessage("evoker.empower.interrupted",
                "<red>A Felerősítés megszakadt: túl nagy találat érte a töltést.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMarkInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player evoker = event.getPlayer();
        if (!evoker.isSneaking() || !isEvoker(evoker)
                || !"preservation".equals(activeSpec(evoker.getUniqueId()))
                || !soulbondFactory.isUsableBy(
                evoker.getInventory().getItemInMainHand(), evoker.getUniqueId(), JobType.EVOKER)
                || !(event.getRightClicked() instanceof Player target)
                || target == evoker) return;
        if (!SpellTargetingUtil.isAlly(evoker, target)) {
            evoker.sendActionBar(messages.getMessage("evoker.mark.invalid",
                    "<red>Csak csapattársat jelölhetsz a Sárkányvér-fiolával.</red>"));
            return;
        }
        event.setCancelled(true);
        assignMark(evoker.getUniqueId(), target);
        evoker.sendActionBar(messages.getMessage("evoker.mark.set",
                "<green>Megjelölt szövetséges: <white>{target}</white> — a visszhangzó gyógyítás rá is hat.</green>",
                Map.of("target", target.getName())));
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

    private void assignMark(final UUID evokerId, final Player target) {
        markTargets.put(evokerId, new MarkTarget(target.getUniqueId(), target,
                target.getScheduler(), target.getName()));
        state(evokerId).setMarkedAlly(target.getUniqueId(), target.getName());
    }

    private void clearMark(final UUID evokerId) {
        markTargets.remove(evokerId);
        final EvokerCombatState state = states.get(evokerId);
        if (state != null) state.clearMarkedAlly();
    }

    private EvokerCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new EvokerCombatState());
    }

    private boolean isEvoker(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.EVOKER;
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
                "classes.evoker.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> empowerSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.evoker.empower.spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private Set<String> echoHealSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(
                "classes.evoker.preservation.echo.heal-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private EvokerCombatState.EssenceColor essenceColorOf(final String spellId) {
        for (final String raw : config.getStringList("classes.evoker.devastation.essence.red-spells")) {
            if (normalize(raw).equals(spellId)) return EvokerCombatState.EssenceColor.VOROS;
        }
        for (final String raw : config.getStringList("classes.evoker.devastation.essence.blue-spells")) {
            if (normalize(raw).equals(spellId)) return EvokerCombatState.EssenceColor.KEK;
        }
        return null;
    }

    private long rank2HoldMillis(final UUID playerId) {
        final long base = Math.max(200L, config.getLong(
                "classes.evoker.empower.rank2-hold-millis", 1200L));
        return "gyujtopont".equals(doctrine(playerId, 30)) ? base * 3L / 4L : base;
    }

    private long rank3HoldMillis(final UUID playerId) {
        final long base = Math.max(400L, config.getLong(
                "classes.evoker.empower.rank3-hold-millis", 2400L));
        return "gyujtopont".equals(doctrine(playerId, 30)) ? base * 3L / 4L : base;
    }

    private long fizzleMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.evoker.empower.fizzle-millis", 6000L));
        return "hosszu_lelegzet".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.evoker.empower.long-breath-extra-millis", 2000L)) : base;
    }

    private int burstThreshold(final UUID playerId) {
        final int base = Math.max(2, config.getInt(
                "classes.evoker.devastation.essence.burst-threshold", 4));
        return "iker_aram".equals(doctrine(playerId, 40)) ? Math.max(2, base - 1) : base;
    }

    private double burstPowerPercent(final UUID playerId) {
        final double base = Math.max(0.0D, config.getDouble(
                "classes.evoker.devastation.essence.burst-power-percent", 25.0D));
        return "tulhevites".equals(doctrine(playerId, 40))
                ? base + Math.max(0.0D, config.getDouble(
                "classes.evoker.devastation.essence.overheat-extra-percent", 8.0D)) : base;
    }

    private int burstRetention(final UUID playerId) {
        return "orok_izzas".equals(doctrine(playerId, 50))
                ? Math.max(0, config.getInt(
                "classes.evoker.devastation.essence.ember-retention", 2)) : 0;
    }

    private long echoWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.evoker.preservation.echo.window-millis", 6000L));
        return "hosszu_visszhang".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.evoker.preservation.echo.long-echo-extra-millis", 3000L)) : base;
    }

    private long imprintWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.evoker.preservation.imprint.window-millis", 8000L));
        return "idofonal".equals(doctrine(playerId, 40))
                ? base + Math.max(0L, config.getLong(
                "classes.evoker.preservation.imprint.timeline-extra-millis", 4000L)) : base;
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String roman(final int rank) {
        return switch (rank) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
