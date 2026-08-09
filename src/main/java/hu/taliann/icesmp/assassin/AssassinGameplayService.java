package hu.taliann.icesmp.assassin;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

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
 * Concrete Orgyilkos vertical-slice runtime.
 *
 * <p>Lehetőség is the class layer: a flanking hit, a dodged blow, a listed interrupt or a cast
 * from stealth each arm one finisher window that the finisher spends whole. Méregkeverő fills a
 * fixed three-slot Toxinkészlet with per-slot doses and burns the whole kit on a catalyst — three
 * concrete slots, never a socket framework. Fantom trades Észleltség for a strictly time-boxed
 * stealth that any strike breaks, plus a single Visszhang per Árnyéknyom. Pestishozó (DARK, on the
 * existing seal system) carries a mutating strain whose infections are capped in count,
 * seeded only by its own strikes (never handed on mob to mob), expiring, cleaned up, blocked
 * while a blood moon runs, and refused on plugin-owned, world-boss and guard-blocked targets. Durable state remains Profile v2.</p>
 */
public final class AssassinGameplayService implements Listener, PlayerStateCleanup {

    private static final Set<String> ASSASSIN_SPECS =
            Set.of("poisoner", "phantom", "plaguebringer");

    /** The world-boss marker written by the boss lifecycle; such entities are never infected. */
    private static final NamespacedKey WORLD_BOSS_KEY =
            new NamespacedKey("icesmp", "world_boss");

    /** Guard key for the plague spread policy (territory/claim/WorldGuard, i.e. dungeon regions). */
    private static final String PLAGUE_GUARD_KEY = "plague_spread";

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, AssassinCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;
    private volatile BloodMoonManager bloodMoon;
    private volatile MinionManager minions;

    public AssassinGameplayService(final JavaPlugin plugin,
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

    /** Event precedence: the existing blood moon owns the night, the plague stands down. */
    public void setBloodMoonManager(final BloodMoonManager manager) {
        bloodMoon = Objects.requireNonNull(manager, "manager");
    }

    /** Scripted/admin immunity: plugin-owned minions are never valid plague carriers. */
    public void setMinionManager(final MinionManager manager) {
        minions = Objects.requireNonNull(manager, "manager");
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.ASSASSIN) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.assassin.active-kit.maximum", 7)));
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
                "classes.assassin.active-kit." + activeSpec)) {
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

    /** Finishers need a live Lehetőség, and a full Toxinkészlet must be catalysed before refilling. */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isAssassin(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final AssassinCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        if (finisherSpells().contains(spellId) && !state.isOpportunityOpen(now)) {
            player.sendActionBar(messages.getMessage("assassin.opportunity.missing",
                    "<red>Nincs nyitott Lehetőség — előbb teremts egyet.</red>"));
            return false;
        }
        if ("poisoner".equals(activeSpec(playerId)) && toxinOf(spellId) != null
                && state.filledToxinSlots() >= AssassinCombatState.TOXIN_SLOTS
                && state.dose(toxinOf(spellId)) == 0) {
            player.sendActionBar(messages.getMessage("assassin.toxin.full",
                    "<red>A Toxinkészlet mindhárom helye foglalt — előbb katalizálj.</red>"));
            return false;
        }
        return true;
    }

    /** Pure peek: the opening that armed the window and the kit's dose empower the payoff. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isAssassin(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final AssassinCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        if (finisherSpells().contains(spellId)) {
            final AssassinCombatState.Opening opening = state.opening(now);
            if (opening != null) {
                bonus += Math.max(0.0D, config.getDouble(
                        "classes.assassin.opportunity.bonus-percent", 15.0D));
                if (opening == AssassinCombatState.Opening.ESZREVETLEN
                        && "arnyekbol".equals(doctrine(playerId, 40))) {
                    bonus += config.getDouble(
                            "classes.assassin.opportunity.unseen-extra-percent", 8.0D);
                }
            }
        }
        if ("poisoner".equals(activeSpec(playerId)) && catalystSpells().contains(spellId)) {
            double perDose = Math.max(0.0D, config.getDouble(
                    "classes.assassin.poisoner.per-dose-percent", 4.0D));
            if ("halalos_fozet".equals(doctrine(playerId, 50))) {
                perDose += config.getDouble("classes.assassin.poisoner.lethal-extra-percent", 1.5D);
            }
            bonus += state.totalDose() * perDose;
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.assassin.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isAssassin(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final AssassinCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        // Interrupt and unseen approach are the two openings a cast itself can create.
        if (interruptSpells().contains(spellId)) {
            state.armOpportunity(AssassinCombatState.Opening.INTERRUPT, now,
                    opportunityWindowMillis(playerId));
        } else if (state.isStealthed(now, detectionBreak(playerId),
                detectionDecayDelayMillis(), detectionDecayPerSecond())
                && !stealthSpells().contains(spellId)) {
            state.armOpportunity(AssassinCombatState.Opening.ESZREVETLEN, now,
                    opportunityWindowMillis(playerId));
        }
        if (finisherSpells().contains(spellId)) {
            final AssassinCombatState.Opening spent = state.consumeOpportunity(now);
            if (spent != null && isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.ASSASSIN,
                        config.getInt("classes.assassin.mastery.finisher-xp", 5));
            }
        }
        // Any loud act raises Észleltség; the stealth check itself then ends a broken stealth.
        if (!quietSpells().contains(spellId)) {
            state.addDetection(config.getInt("classes.assassin.phantom.cast-detection", 12),
                    now, detectionDecayDelayMillis(), detectionDecayPerSecond());
        }

        switch (activeSpec(playerId)) {
            case "poisoner" -> handlePoisonerCast(player, state, spellId);
            case "phantom" -> handlePhantomCast(player, state, spellId, now);
            case "plaguebringer" -> handlePlaguebringerCast(player, state, spellId);
            default -> { }
        }
    }

    // ===== Méregkeverő =====

    private void handlePoisonerCast(final Player player, final AssassinCombatState state,
                                    final String spellId) {
        final UUID playerId = player.getUniqueId();
        final String toxin = toxinOf(spellId);
        if (toxin != null) {
            if (state.applyToxin(toxin, maxDose(playerId))) {
                player.sendActionBar(messages.getMessage("assassin.toxin.applied",
                        "<green>☠ {toxin} — dózis {dose}, készlet {slots}/3.</green>",
                        Map.of("toxin", toxin, "dose", Integer.toString(state.dose(toxin)),
                                "slots", Integer.toString(state.filledToxinSlots()))));
            }
            return;
        }
        if (!catalystSpells().contains(spellId)) return;
        final List<String> held = state.heldToxins();
        final int total = state.catalyse();
        if (total <= 0) return;
        applyCatalysedEffects(player, held, total, playerId);
        if ("gyors_kever".equals(doctrine(playerId, 30))) {
            healPlayer(player, Math.max(0.0D, config.getDouble(
                    "classes.assassin.poisoner.quick-mix-heal", 2.0D)));
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.ASSASSIN,
                    config.getInt("classes.assassin.mastery.catalyse-xp", 6));
        }
        player.sendActionBar(messages.getMessage("assassin.toxin.catalysed",
                "<green>A Toxinkészlet katalizált — összdózis {dose}.</green>",
                Map.of("dose", Integer.toString(total))));
    }

    /** Each of the three toxins has one concrete catalysed effect — an explicit trio, not a DSL. */
    private void applyCatalysedEffects(final Player player, final List<String> held,
                                       final int totalDose, final UUID playerId) {
        int base = config.getInt("classes.assassin.poisoner.catalyse-ticks", 80);
        if ("szeles_hatas".equals(doctrine(playerId, 40))) {
            base += Math.max(0, config.getInt("classes.assassin.poisoner.wide-extra-ticks", 20));
        }
        final int duration = Math.max(20, base * Math.max(1, totalDose) / 2);
        for (final String toxin : held) {
            switch (toxin) {
                case "benito" -> player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, duration, 0, false, true, true));
                case "marokod" -> player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, duration, 0, false, true, true));
                case "sorvaszto" -> player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, duration, 0, false, true, true));
                default -> { }
            }
        }
    }

    // ===== Fantom =====

    private void handlePhantomCast(final Player player, final AssassinCombatState state,
                                   final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (stealthSpells().contains(spellId)) {
            if (state.detection(now, detectionDecayDelayMillis(), detectionDecayPerSecond())
                    >= detectionBreak(playerId)) {
                player.sendActionBar(messages.getMessage("assassin.stealth.detected",
                        "<red>Túl feltűnő vagy — az Észleltség nem enged az árnyékba.</red>"));
                return;
            }
            if ("kisertet".equals(doctrine(playerId, 50))) {
                state.addDetection(0, now, detectionDecayDelayMillis(), detectionDecayPerSecond());
                state.ventDetection(config.getInt(
                        "classes.assassin.phantom.ghost-detection-vent", 100));
            }
            state.enterStealth(now, stealthDurationMillis(playerId));
            player.sendActionBar(messages.getMessage("assassin.stealth.entered",
                    "<dark_aqua>Az árnyékba olvadsz — de nem örökre.</dark_aqua>"));
            return;
        }
        if (trailSpells().contains(spellId)) {
            long window = Math.max(1000L, config.getLong(
                    "classes.assassin.phantom.trail-window-millis", 5000L));
            if ("hosszu_nyom".equals(doctrine(playerId, 30))) {
                window += Math.max(0L, config.getLong(
                        "classes.assassin.phantom.long-trail-extra-millis", 2000L));
            }
            state.armTrail(now, window);
            if ("kettos_lepes".equals(doctrine(playerId, 40))) {
                state.armOpportunity(AssassinCombatState.Opening.ESZREVETLEN, now,
                        opportunityWindowMillis(playerId));
            }
            return;
        }
        if (!echoSpells().contains(spellId) || !state.consumeEcho(now)) return;
        double echoHeal = Math.max(0.0D, config.getDouble(
                "classes.assassin.phantom.echo-heal", 3.0D));
        if ("tiszta_visszhang".equals(doctrine(playerId, 40))) {
            echoHeal += config.getDouble("classes.assassin.phantom.clear-echo-extra-heal", 2.0D);
        }
        healPlayer(player, echoHeal);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.ASSASSIN,
                    config.getInt("classes.assassin.mastery.echo-xp", 4));
        }
        player.sendActionBar(messages.getMessage("assassin.phantom.echo",
                "<dark_aqua>Az Árnyéknyom visszhangzik.</dark_aqua>"));
    }

    // ===== Pestishozó (DARK) =====

    private void handlePlaguebringerCast(final Player player, final AssassinCombatState state,
                                         final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (!mutateSpells().contains(spellId)) return;
        final int stage = state.mutateStrain(strainMaximum(playerId));
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.ASSASSIN,
                    config.getInt("classes.assassin.mastery.strain-xp", 5));
        }
        player.sendActionBar(messages.getMessage("assassin.strain.mutated",
                "<dark_green>🦠 A Járványtörzs {stage}. fokozatra mutálódott.</dark_green>",
                Map.of("stage", Integer.toString(stage))));
    }

    /**
     * The infection entry point. Every mandatory cap is enforced here: blood-moon precedence,
     * plugin-owned/world-boss/guard-blocked immunity, a hard entity cap, and a generation cap
     * that makes mob-to-mob spread finite by construction.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingDamage(final EntityDamageByEntityEvent event) {
        final UUID attackerId = attackerId(event);
        if (attackerId == null || !isAssassin(attackerId)) return;
        if (event.getFinalDamage() <= 0.0D) return;
        final AssassinCombatState state = state(attackerId);
        final long now = System.currentTimeMillis();

        // Pozíció: a hit taken from behind is the cleanest opening the class has.
        if (event.getDamager() instanceof Player attacker
                && event.getEntity() instanceof LivingEntity victim
                && isBehind(attacker, victim)) {
            state.armOpportunity(AssassinCombatState.Opening.POZICIO, now,
                    opportunityWindowMillis(attackerId));
        }
        // Any strike is loud, and a loud strike ends stealth.
        state.addDetection(config.getInt("classes.assassin.phantom.strike-detection", 25),
                now, detectionDecayDelayMillis(), detectionDecayPerSecond());
        state.breakStealth();

        if (!"plaguebringer".equals(activeSpec(attackerId))) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getDamager() instanceof Player carrier)) return;
        tryInfect(carrier, state, target, now);
    }

    /**
     * The only path into the registry. Carriers never hand the strain on to other mobs: every
     * infection is seeded by the plaguebringer's own strike, so the spread is finite by
     * construction — there is no mob-to-mob chain to run away with.
     */
    private void tryInfect(final Player owner, final AssassinCombatState state,
                           final LivingEntity target, final long now) {
        if (!config.getBoolean("classes.assassin.plague.allow-infection", true)) return;
        final BloodMoonManager moon = bloodMoon;
        if (moon != null && moon.isActive()) return;
        if (target instanceof Player) return;
        if (isImmune(target)) return;
        final EventSpawnGuard guard = EventSpawnGuard.current();
        if (guard != null && guard.isBlocked(PLAGUE_GUARD_KEY, target.getLocation())) return;
        if (!state.infect(target.getUniqueId(), entityCap(owner.getUniqueId()),
                now, infectionMillis(owner.getUniqueId()))) {
            return;
        }
        int amplifierValue = Math.max(0, Math.min(2, state.strainStage() - 1));
        if ("gyors_lappangas".equals(doctrine(owner.getUniqueId(), 30))) amplifierValue++;
        int tickValue = Math.max(20, config.getInt("classes.assassin.plague.effect-ticks", 100));
        if ("mely_fertozes".equals(doctrine(owner.getUniqueId(), 40))) {
            tickValue += Math.max(0, config.getInt("classes.assassin.plague.deep-extra-ticks", 40));
        }
        final PotionEffectType type =
                "fekete_halal".equals(doctrine(owner.getUniqueId(), 50))
                        && state.strainStage() >= strainMaximum(owner.getUniqueId())
                        ? PotionEffectType.WITHER : PotionEffectType.POISON;
        final int amplifier = Math.min(3, amplifierValue);
        final int ticks = tickValue;
        // Folia: the carrier lives on its own region thread — never touch it inline.
        target.getScheduler().run(plugin, task -> target.addPotionEffect(new PotionEffect(
                type, ticks, amplifier, false, true, true)), null);
    }

    /**
     * Admin/scripted, plugin-owned and world-boss entities are immune, and the configured type
     * denylist covers the dungeon/boss policy alongside the guard region check.
     */
    private boolean isImmune(final LivingEntity target) {
        final MinionManager minionManager = minions;
        if (minionManager != null && minionManager.isMinion(target)) return true;
        if (MinionManager.isMinionTagged(target)) return true;
        if (target.getPersistentDataContainer().has(WORLD_BOSS_KEY, PersistentDataType.BYTE)) {
            return true;
        }
        final String type = target.getType().name().toLowerCase(Locale.ROOT);
        return configSet("classes.assassin.plague.immune-types").contains(type);
    }

    /** Kitérés: a cancelled or fully absorbed blow is a dodge, and a dodge is an opening. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isAssassin(victim)) return;
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) return;
        state(victim.getUniqueId()).armOpportunity(AssassinCombatState.Opening.KITERES,
                System.currentTimeMillis(), opportunityWindowMillis(victim.getUniqueId()));
    }

    /** Cleanup: a dead carrier leaves the registry immediately, it never lingers as a stale id. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        final UUID deadId = event.getEntity().getUniqueId();
        for (final AssassinCombatState state : states.values()) {
            state.cure(deadId);
        }
    }

    public Component hudSuffix(final Player player) {
        if (!isAssassin(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final AssassinCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final AssassinCombatState.Opening opening = state.opening(now);
        Component suffix = Component.text("  • Lehetőség "
                        + (opening == null ? "—" : openingName(opening)),
                opening == null ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW);
        switch (activeSpec(playerId)) {
            case "poisoner" -> suffix = suffix.append(Component.text("  • Toxin "
                            + state.filledToxinSlots() + "/3 • Dózis " + state.totalDose(),
                    NamedTextColor.GREEN));
            case "phantom" -> {
                final int detection = state.detection(now, detectionDecayDelayMillis(),
                        detectionDecayPerSecond());
                suffix = suffix.append(Component.text("  • Észleltség " + detection
                                + (state.isStealthed(now, detectionBreak(playerId),
                                detectionDecayDelayMillis(), detectionDecayPerSecond())
                                ? " • Rejtve" : ""),
                        NamedTextColor.DARK_AQUA));
            }
            case "plaguebringer" -> suffix = suffix.append(Component.text("  • Törzs "
                            + state.strainStage() + ". fok • Fertőzött "
                            + state.infectionCount(now) + "/" + entityCap(playerId),
                    NamedTextColor.DARK_GREEN));
            default -> { }
        }
        return suffix;
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.ASSASSIN) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!ASSASSIN_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final AssassinCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final AssassinCombatState state = states.remove(playerId);
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

    /** Deterministic flank check: the attacker stands behind the victim's facing. */
    private static boolean isBehind(final Player attacker, final LivingEntity victim) {
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return false;
        final Vector facing = victim.getLocation().getDirection().setY(0.0D);
        final Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0.0D);
        if (facing.lengthSquared() <= 0.0D || toAttacker.lengthSquared() <= 0.0D) return false;
        return facing.normalize().dot(toAttacker.normalize()) < -0.2D;
    }

    private static String openingName(final AssassinCombatState.Opening opening) {
        return switch (opening) {
            case POZICIO -> "Pozíció";
            case KITERES -> "Kitérés";
            case INTERRUPT -> "Interrupt";
            case ESZREVETLEN -> "Észrevétlen";
        };
    }

    /** The three concrete toxins; a spell either maps to one of them or to none. */
    private String toxinOf(final String spellId) {
        for (final String toxin : new String[]{"benito", "marokod", "sorvaszto"}) {
            if (configSet("classes.assassin.poisoner." + toxin + "-spells").contains(spellId)) {
                return toxin;
            }
        }
        return null;
    }

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private AssassinCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new AssassinCombatState());
    }

    private boolean isAssassin(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.ASSASSIN;
    }

    private boolean isAssassin(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile != null && "assassin".equals(profile.primaryClassId());
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
                "classes.assassin.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> finisherSpells() {
        return configSet("classes.assassin.opportunity.finisher-spells");
    }

    private Set<String> interruptSpells() {
        return configSet("classes.assassin.opportunity.interrupt-spells");
    }

    private Set<String> quietSpells() {
        return configSet("classes.assassin.phantom.quiet-spells");
    }

    private Set<String> stealthSpells() {
        return configSet("classes.assassin.phantom.stealth-spells");
    }

    private Set<String> trailSpells() {
        return configSet("classes.assassin.phantom.trail-spells");
    }

    private Set<String> echoSpells() {
        return configSet("classes.assassin.phantom.echo-spells");
    }

    private Set<String> catalystSpells() {
        return configSet("classes.assassin.poisoner.catalyst-spells");
    }

    private Set<String> mutateSpells() {
        return configSet("classes.assassin.plague.mutate-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private long opportunityWindowMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.assassin.opportunity.window-millis", 5000L));
        if ("hosszu_pillanat".equals(doctrine(playerId, 30))) {
            millis += Math.max(0L, config.getLong(
                    "classes.assassin.opportunity.long-extra-millis", 2000L));
        }
        return millis;
    }

    private int maxDose(final UUID playerId) {
        int dose = Math.max(1, config.getInt("classes.assassin.poisoner.max-dose", 3));
        if ("melyebb_dozis".equals(doctrine(playerId, 50))) {
            dose += Math.max(0, config.getInt("classes.assassin.poisoner.deep-extra-dose", 1));
        }
        return dose;
    }

    private int detectionBreak(final UUID playerId) {
        int threshold = Math.max(1, Math.min(100,
                config.getInt("classes.assassin.phantom.detection-break", 60)));
        if ("halk_lepes".equals(doctrine(playerId, 30))) {
            threshold += Math.max(0, config.getInt(
                    "classes.assassin.phantom.quiet-extra-threshold", 10));
        }
        return Math.min(100, threshold);
    }

    private long stealthDurationMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.assassin.phantom.stealth-millis", 8000L));
        if ("mely_arny".equals(doctrine(playerId, 50))) {
            millis += Math.max(0L, config.getLong(
                    "classes.assassin.phantom.deep-extra-millis", 3000L));
        }
        return millis;
    }

    private long detectionDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.assassin.phantom.decay-delay-millis", 4000L));
    }

    private double detectionDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.assassin.phantom.decay-per-second", 8.0D));
    }

    private int strainMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.assassin.plague.strain-maximum", 3));
        if ("torzs_mestere".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.assassin.plague.master-extra-stage", 1));
        }
        return maximum;
    }

    private int entityCap(final UUID playerId) {
        int cap = Math.max(1, Math.min(16, config.getInt("classes.assassin.plague.entity-cap", 6)));
        if ("szeles_jarvany".equals(doctrine(playerId, 40))) {
            cap = Math.min(16, cap + Math.max(0, config.getInt(
                    "classes.assassin.plague.wide-extra-cap", 2)));
        }
        return cap;
    }

    private long infectionMillis(final UUID playerId) {
        long millis = Math.max(2000L, config.getLong(
                "classes.assassin.plague.infection-millis", 15000L));
        if ("szivos_torzs".equals(doctrine(playerId, 30))) {
            millis += Math.max(0L, config.getLong(
                    "classes.assassin.plague.tough-extra-millis", 5000L));
        }
        return millis;
    }

    private static UUID attackerId(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player.getUniqueId();
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player.getUniqueId();
        return null;
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
