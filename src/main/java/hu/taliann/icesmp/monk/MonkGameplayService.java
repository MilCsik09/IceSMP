package hu.taliann.icesmp.monk;

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
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
 * Concrete Szerzetes vertical-slice runtime.
 *
 * <p>Áramlás rewards technique variety on the class layer. Szélfutó plays one explicit,
 * config-declared martial chain (no generic combo engine); Sörfőző defers a bounded part of
 * incoming damage into a Stagger pool that drains via direct health steps — never a duplicated
 * damage event, never clearable without consequence; Ködszövő keeps at most three Ködszál links
 * whose ripple heals always run on the linked ally's scheduler. Durable state remains
 * Profile v2.</p>
 */
public final class MonkGameplayService implements Listener, PlayerStateCleanup {

    private record LinkTarget(UUID id, Player entity, EntityScheduler scheduler) {
        LinkTarget {
            Objects.requireNonNull(id);
            Objects.requireNonNull(entity);
            Objects.requireNonNull(scheduler);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, MonkCombatState> states = new ConcurrentHashMap<>();
    private final Map<UUID, List<LinkTarget>> linkTargets = new ConcurrentHashMap<>();
    private final Set<UUID> drainActive = ConcurrentHashMap.newKeySet();

    private volatile ResourceManager combatTracker;

    public MonkGameplayService(final JavaPlugin plugin,
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

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.MONK) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.monk.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.monk.active-kit." + activeSpec)) {
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
        return true;
    }

    /** Pure peek: the reached chain empowers a finisher through the capped power pipeline. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isMonk(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        if (!"windwalker".equals(activeSpec(playerId))) return 0.0D;
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        if (!finisherSpells().contains(spellId)) return 0.0D;
        final MonkCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if (state.chainStep(now, chainWindowMillis(playerId)) < chainThreshold(playerId)) {
            return 0.0D;
        }
        double bonus = Math.max(0.0D, config.getDouble(
                "classes.monk.chain.finisher-bonus-percent", 22.0D));
        if ("parducsap".equals(doctrine(playerId, 30))) {
            bonus += Math.max(0.0D, config.getDouble(
                    "classes.monk.chain.panther-extra-percent", 6.0D));
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.monk.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isMonk(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final MonkCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        state.recordTechnique(spellId, now, varietyGain(playerId),
                flowDecayDelayMillis(), flowDecayPerSecond());

        final String spec = activeSpec(playerId);
        if ("windwalker".equals(spec)) handleWindwalkerCast(player, state, spellId, now);
        else if ("brewmaster".equals(spec)) handleBrewmasterCast(player, state, spellId);
        else if ("mistweaver".equals(spec)) handleMistweaverCast(player, state, spellId);
    }

    private void handleWindwalkerCast(final Player player, final MonkCombatState state,
                                      final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (finisherSpells().contains(spellId)) {
            if (state.consumeChain(chainThreshold(playerId),
                    "derus_eg".equals(doctrine(playerId, 50)) ? 1 : 0)) {
                if ("sarkany_lendulet".equals(doctrine(playerId, 40))) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                            config.getInt("classes.monk.chain.dragon-speed-ticks", 80),
                            0, false, true, true));
                }
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.MONK,
                            config.getInt("classes.monk.mastery.finisher-xp", 5));
                }
                player.sendActionBar(messages.getMessage("monk.chain.finisher",
                        "<gold>☯ Harcművészeti Lánc bevégezve.</gold>"));
            }
            return;
        }
        final int step = state.recordChainStep(spellId, chainSteps(), now,
                chainWindowMillis(playerId));
        if (step == chainThreshold(playerId)) {
            player.sendActionBar(messages.getMessage("monk.chain.ready",
                    "<gold>Lánc kész ({value}): a következő befejező technika erősebb.</gold>",
                    Map.of("value", Integer.toString(step))));
        }
    }

    private void handleBrewmasterCast(final Player player, final MonkCombatState state,
                                      final String spellId) {
        final UUID playerId = player.getUniqueId();
        double percent = 0.0D;
        switch (spellId) {
            case "purifying_brew" -> {
                percent = config.getDouble("classes.monk.stagger.purify-percent", 50.0D);
                if ("gyors_korty".equals(doctrine(playerId, 30))) {
                    percent += config.getDouble("classes.monk.stagger.quick-sip-extra-percent", 10.0D);
                }
            }
            case "breath_of_fire" -> {
                if ("langlehelet".equals(doctrine(playerId, 40))) {
                    percent = config.getDouble("classes.monk.stagger.breath-purify-percent", 15.0D);
                }
            }
            case "invoke_niuzao" -> percent = "niuzao_oltalma".equals(doctrine(playerId, 50))
                    ? 100.0D : config.getDouble("classes.monk.stagger.capstone-purify-percent", 60.0D);
            default -> {
            }
        }
        if (percent <= 0.0D) return;
        final double cleared = state.purifyStagger(percent);
        if (cleared <= 0.0D) return;
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.MONK,
                    config.getInt("classes.monk.mastery.purify-xp", 4));
        }
        player.sendActionBar(messages.getMessage("monk.stagger.purified",
                "<green>🍺 Főzet: a halasztott fájdalom egy része elpárolgott.</green>"));
    }

    private void handleMistweaverCast(final Player player, final MonkCombatState state,
                                      final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (!rippleHealSpells().contains(spellId)) return;
        final List<LinkTarget> links = linkTargets.get(playerId);
        if (links == null || links.isEmpty()) return;
        final boolean capstone = "revival".equals(spellId);
        double amount = Math.max(0.5D, config.getDouble(
                "classes.monk.mist.ripple-heal", 3.0D));
        if ("melyebb_kod".equals(doctrine(playerId, 40))) {
            amount += config.getDouble("classes.monk.mist.deep-extra-heal", 2.0D);
        }
        if (capstone && "eletviraga".equals(doctrine(playerId, 50))) {
            amount *= 2.0D;
        }
        final double rippleAmount = amount;
        final boolean regen = "friss_kod".equals(doctrine(playerId, 30));
        final boolean absorption = "vedo_kod".equals(doctrine(playerId, 40));
        final boolean selfRipple = "szellemkod".equals(doctrine(playerId, 50));
        boolean anyScheduled = false;
        for (final LinkTarget link : List.copyOf(links)) {
            final Player ally = link.entity();
            anyScheduled = true;
            link.scheduler().run(plugin, task -> {
                if (!ally.isOnline() || ally.isDead()) {
                    removeLink(playerId, link.id());
                    return;
                }
                healPlayer(ally, rippleAmount, regen, absorption);
            }, () -> removeLink(playerId, link.id()));
        }
        if (!anyScheduled) return;
        if (selfRipple) healPlayer(player, rippleAmount / 2.0D, false, false);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.MONK,
                    config.getInt("classes.monk.mastery.ripple-xp", 4));
        }
        player.sendActionBar(messages.getMessage("monk.mist.ripple",
                "<aqua>🌫 A gyógyítás végighullámzott a Ködszálakon.</aqua>"));
    }

    private static void healPlayer(final Player target, final double amount,
                                   final boolean regen, final boolean absorption) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
        if (regen) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    60, 0, false, true, true));
        }
        if (absorption) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    100, 0, false, true, true));
        }
    }

    /** Sörfőző: a bounded part of every hit is deferred into the Stagger pool. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isMonk(victim)
                || !"brewmaster".equals(activeSpec(victim.getUniqueId()))
                || event.getDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        final MonkCombatState state = state(playerId);
        double staggerPercent = Math.max(0.0D, Math.min(80.0D, config.getDouble(
                "classes.monk.stagger.defer-percent", 35.0D)));
        if ("surubb_fozet".equals(doctrine(playerId, 30))) {
            staggerPercent += config.getDouble("classes.monk.stagger.thick-extra-percent", 5.0D);
        }
        final double poolCap = staggerPoolCap(victim);
        // Phase one: decide the share of the FINAL (already mitigated) damage to defer, and scale
        // the event by that share. Every modifier in the pipeline is multiplicative, so scaling the
        // base by (1 - q) reduces the final damage by exactly q of it — no mitigation is applied
        // twice and none is bypassed.
        final double finalBefore = event.getFinalDamage();
        if (finalBefore <= 0.0D) return;
        final double room = poolCap - state.staggerPool();
        final double accepted = MonkCombatState.acceptedDefer(finalBefore, staggerPercent, room);
        if (accepted <= 0.0D) return;
        final double fraction = accepted / finalBefore;
        state.setPendingDeferFraction(fraction);
        event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - fraction)));
    }

    /** The Stagger pool ceiling, in health units; both phases bound themselves by it. */
    private double staggerPoolCap(final Player victim) {
        final UUID playerId = victim.getUniqueId();
        double poolCap = maxHealth(victim) * Math.max(0.0D, Math.min(100.0D, config.getDouble(
                "classes.monk.stagger.pool-cap-health-percent", 60.0D))) / 100.0D;
        if ("vas_bendo".equals(doctrine(playerId, 40))) {
            poolCap += maxHealth(victim) * config.getDouble(
                    "classes.monk.stagger.iron-extra-percent", 10.0D) / 100.0D;
        }
        return poolCap;
    }

    /**
     * Phase two: the pipeline has settled, so the exact deferred amount is recovered from the
     * authoritative final damage and banked. Reading only — this never modifies the event, never
     * duplicates a damage event and never overrides a later plugin's adjustment.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIncomingDamageResolved(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isMonk(victim)) return;
        final MonkCombatState state = state(victim.getUniqueId());
        final double fraction = state.takePendingDeferFraction();
        if (fraction <= 0.0D) return;
        final double banked = MonkCombatState.bankedFromReducedFinal(
                event.getFinalDamage(), fraction);
        if (banked <= 0.0D) return;
        state.stagger(banked, staggerPoolCap(victim));
        scheduleStaggerDrain(victim);
    }

    /**
     * Self-rescheduling drain on the player's scheduler; stops when the pool is empty. The drain
     * steps health directly (floored at half a heart), so it can never duplicate a damage event
     * and never kills on its own.
     */
    private void scheduleStaggerDrain(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!drainActive.add(playerId)) return;
        final long periodTicks = Math.max(10L, config.getLong(
                "classes.monk.stagger.drain-period-ticks", 20L));
        player.getScheduler().runDelayed(plugin, task -> {
            drainActive.remove(playerId);
            if (!player.isOnline() || player.isDead()
                    || !"brewmaster".equals(activeSpec(playerId))) return;
            final MonkCombatState state = states.get(playerId);
            if (state == null) return;
            double perTick = maxHealth(player) * Math.max(0.1D, config.getDouble(
                    "classes.monk.stagger.drain-health-percent", 2.5D)) / 100.0D;
            if ("celesztialis_nyugalom".equals(doctrine(playerId, 50))) {
                perTick *= 0.75D;
            }
            final double drained = state.drainStagger(perTick);
            if (drained > 0.0D) {
                player.setHealth(Math.max(1.0D, player.getHealth() - drained));
            }
            if (state.staggerPool() > 0.0D) scheduleStaggerDrain(player);
        }, () -> drainActive.remove(playerId), periodTicks);
    }

    /** Ködszövő: sneak + right-click with the Élet Ága links an ally (max three Ködszál). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLinkInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player monk = event.getPlayer();
        if (!monk.isSneaking() || !isMonk(monk)
                || !"mistweaver".equals(activeSpec(monk.getUniqueId()))
                || !soulbondFactory.isUsableBy(
                monk.getInventory().getItemInMainHand(), monk.getUniqueId(), JobType.MONK)
                || !(event.getRightClicked() instanceof Player target)
                || target == monk) return;
        if (!SpellTargetingUtil.isAlly(monk, target)) {
            monk.sendActionBar(messages.getMessage("monk.mist.invalid",
                    "<red>Ködszál csak csapattársra szőhető.</red>"));
            return;
        }
        event.setCancelled(true);
        final UUID monkId = monk.getUniqueId();
        final MonkCombatState state = state(monkId);
        final int maximum = Math.max(1, Math.min(3, config.getInt(
                "classes.monk.mist.maximum-links", 3)));
        if (!state.addLink(target.getUniqueId(), target.getName(), maximum)) {
            monk.sendActionBar(messages.getMessage("monk.mist.already",
                    "<yellow>Ez a szövetséges már kapcsolódik hozzád.</yellow>"));
            return;
        }
        final List<LinkTarget> links = linkTargets.computeIfAbsent(monkId,
                ignored -> new java.util.concurrent.CopyOnWriteArrayList<>());
        links.removeIf(link -> !state.linkIds().contains(link.id()));
        links.add(new LinkTarget(target.getUniqueId(), target, target.getScheduler()));
        if ("gyors_szoves".equals(doctrine(monkId, 30))) {
            target.getScheduler().run(plugin, task -> healPlayer(target, config.getDouble(
                    "classes.monk.mist.weave-heal", 2.0D), false, false), null);
        }
        monk.sendActionBar(messages.getMessage("monk.mist.linked",
                "<aqua>Ködszál szőve: <white>{target}</white> ({count}/{max}).</aqua>",
                Map.of("target", target.getName(),
                        "count", Integer.toString(state.linkIds().size()),
                        "max", Integer.toString(maximum))));
    }

    public Component hudSuffix(final Player player) {
        if (!isMonk(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final MonkCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        Component suffix = Component.text("  • Áramlás "
                        + state.flow(now, flowDecayDelayMillis(), flowDecayPerSecond()),
                NamedTextColor.GREEN);
        final String spec = activeSpec(playerId);
        if ("windwalker".equals(spec)) {
            final int step = state.chainStep(now, chainWindowMillis(playerId));
            suffix = suffix.append(Component.text("  • Lánc " + step + "/"
                    + chainThreshold(playerId)
                    + (step >= chainThreshold(playerId) ? " ☯" : ""), NamedTextColor.GOLD));
        } else if ("brewmaster".equals(spec)) {
            suffix = suffix.append(Component.text(String.format(Locale.ROOT,
                    "  • Stagger %.1f", state.staggerPool()), NamedTextColor.YELLOW));
        } else if ("mistweaver".equals(spec)) {
            suffix = suffix.append(Component.text("  • Szálak "
                            + state.linkIds().size() + "/" + Math.max(1, Math.min(3,
                            config.getInt("classes.monk.mist.maximum-links", 3))),
                    NamedTextColor.AQUA));
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isMonk(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final MonkCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final int flow = combat.flow(now, flowDecayDelayMillis(), flowDecayPerSecond());
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "flow", "Áramlás", "Áramlás " + flow, flow, 100, "active");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        int charges = 0;
        int maximum = 0;
        String proc = "";
        switch (activeSpec(id)) {
            case "windwalker" -> {
                charges = combat.chainStep(now, chainWindowMillis(id)); maximum = chainThreshold(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "combo_chain", "Lánc", "Lánc " + charges + "/" + maximum,
                        charges, maximum, charges >= maximum ? "ready" : "building");
                if (charges >= maximum) proc = "Kombó kész";
            }
            case "brewmaster" -> secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "stagger", "Stagger", String.format(Locale.ROOT, "Stagger %.1f", combat.staggerPool()),
                    combat.staggerPool(), 100, "active");
            case "mistweaver" -> {
                charges = combat.linkIds().size(); maximum = Math.max(1, Math.min(3,
                        config.getInt("classes.monk.mist.maximum-links", 3)));
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "mist_threads", "Szálak", "Szálak " + charges + "/" + maximum,
                        charges, maximum, charges >= maximum ? "full" : "active");
            }
            default -> { }
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, "", proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.MONK) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"windwalker".equals(spec) && !"brewmaster".equals(spec)
                && !"mistweaver".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    /**
     * Spec-switch cleanup with the Sörfőző consequence: the remaining Stagger pool lands
     * immediately (never lethal on its own) before the transient state clears.
     */
    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        applyStaggerConsequence(playerId);
        linkTargets.remove(playerId);
        final MonkCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        linkTargets.remove(playerId);
        drainActive.remove(playerId);
        final MonkCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
    }

    private void applyStaggerConsequence(final UUID playerId) {
        final MonkCombatState state = states.get(playerId);
        if (state == null) return;
        final double pool = state.collapseStagger();
        if (pool <= 0.0D) return;
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && !player.isDead()) {
            player.setHealth(Math.max(1.0D, player.getHealth() - pool));
        }
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
        linkTargets.clear();
        drainActive.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) { clearPlayerState(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        applyStaggerConsequence(event.getPlayer().getUniqueId());
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(final PlayerKickEvent event) {
        applyStaggerConsequence(event.getPlayer().getUniqueId());
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(final PluginDisableEvent event) {
        if (event.getPlugin() == plugin) shutdown();
    }

    private void removeLink(final UUID monkId, final UUID allyId) {
        final MonkCombatState state = states.get(monkId);
        if (state != null) state.removeLink(allyId);
        final List<LinkTarget> links = linkTargets.get(monkId);
        if (links != null) links.removeIf(link -> link.id().equals(allyId));
    }

    private MonkCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new MonkCombatState());
    }

    private boolean isMonk(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.MONK;
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
                "classes.monk.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private int varietyGain(final UUID playerId) {
        int gain = Math.max(0, config.getInt("classes.monk.flow.variety-gain", 12));
        if ("ezer_okol".equals(doctrine(playerId, 50))) {
            gain += Math.max(0, config.getInt("classes.monk.flow.thousand-extra-gain", 4));
        }
        return gain;
    }

    private long flowDecayDelayMillis() {
        return Math.max(0L, config.getLong("classes.monk.flow.decay-delay-millis", 6000L));
    }

    private double flowDecayPerSecond() {
        return Math.max(0.0D, config.getDouble("classes.monk.flow.decay-per-second", 6.0D));
    }

    private List<String> chainSteps() {
        return config.getStringList("classes.monk.chain.steps");
    }

    private Set<String> finisherSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.monk.chain.finishers")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private long chainWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.monk.chain.window-millis", 5000L));
        return "konnyed_lepes".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.monk.chain.light-step-extra-millis", 2500L)) : base;
    }

    private int chainThreshold(final UUID playerId) {
        final int base = Math.max(2, config.getInt("classes.monk.chain.threshold", 3));
        return "vihar_okle".equals(doctrine(playerId, 40)) ? Math.max(2, base - 1) : base;
    }

    private Set<String> rippleHealSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.monk.mist.heal-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
