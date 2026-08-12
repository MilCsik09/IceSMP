package hu.taliann.icesmp.paladin;

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
 * Concrete Paplovag vertical-slice runtime.
 *
 * <p>The class core is Meggyőződés és Eskü: the paladin's chosen direction (Irgalom, Ítélet,
 * Oltalmazás — session choice, defaulting to the active spec's role) turns in-role casts into
 * conviction, and high conviction empowers in-role casts through the capped shared power
 * pipeline. Szentlélek plays one Fényjelző beacon whose echo is a single bounded heal;
 * Megtorló lights the three Ítélet-jelek toward a Verdict; Oltalmazó spends Pajzstöltet on
 * area protection — a different tank identity from the Warrior Guardian (charge-funded,
 * ground-anchored). Durable state remains Profile v2.</p>
 */
public final class PaladinGameplayService implements Listener, PlayerStateCleanup {

    private record BeaconTarget(UUID id, EntityScheduler scheduler, String label) {
        BeaconTarget {
            Objects.requireNonNull(id);
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

    private final Map<UUID, PaladinCombatState> states = new ConcurrentHashMap<>();
    private final Map<UUID, BeaconTarget> beaconTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> paladinsByBeaconTarget = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public PaladinGameplayService(final JavaPlugin plugin,
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

    /** Session-scoped Eskü choice from /spec esku; defaults to the active spec's role. */
    public boolean chooseOath(final Player player, final String rawOath) {
        if (!isPaladin(player) || rawOath == null) return false;
        final PaladinCombatState.Oath oath = switch (normalize(rawOath)) {
            case "irgalom" -> PaladinCombatState.Oath.IRGALOM;
            case "itelet" -> PaladinCombatState.Oath.ITELET;
            case "oltalmazas" -> PaladinCombatState.Oath.OLTALMAZAS;
            default -> null;
        };
        if (oath == null) return false;
        state(player.getUniqueId()).chooseOath(oath);
        player.sendActionBar(messages.getMessage("paladin.oath.chosen",
                "<gold>Eskü letéve: <white>{oath}</white> — a szerephez illő tettek építik a Meggyőződést.</gold>",
                Map.of("oath", oathName(oath))));
        return true;
    }

    public List<String> activeSpellIds(final Player player,
                                       final List<String> unlocked,
                                       final Set<String> favorites) {
        if (player == null || jobs.getPrimaryJob(player) != JobType.PALADIN) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.paladin.active-kit.maximum", 7)));
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
        for (final String raw : config.getStringList("classes.paladin.active-kit." + activeSpec)) {
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
        if (!isPaladin(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        if (!"protection".equals(activeSpec(playerId))) return true;
        final int cost = shieldCost(playerId, spell.getId().toLowerCase(Locale.ROOT));
        if (cost > 0 && state(playerId).shieldCharge() < cost) {
            player.sendActionBar(messages.getMessage("paladin.shield.need",
                    "<red>Nincs elég Pajzstöltet. Szükséges: {amount}.</red>",
                    Map.of("amount", Integer.toString(cost))));
            return false;
        }
        return true;
    }

    /** Pure peek: high in-role conviction and the armed Verdict empower through the capped pipeline. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isPaladin(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final PaladinCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        final PaladinCombatState.Oath oath = state.oathOrDefault(specDefaultOath(playerId));
        if (roleSpells(oath).contains(spellId)
                && state.conviction(now, convictionDecayDelayMillis(), convictionDecayPerSecond())
                >= convictionThreshold(playerId)) {
            bonus += Math.max(0.0D, config.getDouble(
                    "classes.paladin.conviction.bonus-percent", 12.0D));
        }
        if ("retribution".equals(activeSpec(playerId))
                && verdictFinishers().contains(spellId)
                && state.isVerdictArmed(now, markWindowMillis(playerId))) {
            bonus += verdictBonusPercent(playerId);
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.paladin.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isPaladin(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final PaladinCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        final PaladinCombatState.Oath oath = state.oathOrDefault(specDefaultOath(playerId));
        if (roleSpells(oath).contains(spellId)) {
            state.addConviction(convictionGain(playerId, oath), now,
                    convictionDecayDelayMillis(), convictionDecayPerSecond());
        }

        final String spec = activeSpec(playerId);
        if ("holy".equals(spec)) handleHolyCast(player, state, spellId);
        else if ("retribution".equals(spec)) handleRetributionCast(player, state, spellId, now);
        else if ("protection".equals(spec)) handleProtectionCast(player, state, spellId);
    }

    private void handleHolyCast(final Player player, final PaladinCombatState state,
                                final String spellId) {
        final UUID playerId = player.getUniqueId();
        if ("avenging_wrath".equals(spellId) && "hajnal_ereje".equals(doctrine(playerId, 50))) {
            state.addConviction(100, System.currentTimeMillis(),
                    convictionDecayDelayMillis(), convictionDecayPerSecond());
        }
        if (!beaconEchoSpells().contains(spellId)) return;
        final BeaconTarget beacon = beaconTargets.get(playerId);
        if (beacon == null) return;
        double amount = Math.max(0.5D, config.getDouble(
                "classes.paladin.beacon.echo-heal", 3.0D));
        if ("fenymeleg".equals(doctrine(playerId, 30))) {
            amount += config.getDouble("classes.paladin.beacon.warm-extra-heal", 1.5D);
        }
        final double echo = amount;
        final boolean regen = "orzo_fenye".equals(doctrine(playerId, 40));
        final boolean selfEcho = "megvalto".equals(doctrine(playerId, 50));
        beacon.scheduler().run(plugin, task -> {
            final Player ally = Bukkit.getPlayer(beacon.id());
            if (ally == null || !ally.isOnline() || ally.isDead()) {
                clearBeacon(playerId);
                return;
            }
            healPlayer(ally, echo, regen);
        }, () -> clearBeacon(playerId));
        if (selfEcho) healPlayer(player, echo / 2.0D, false);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.PALADIN,
                    config.getInt("classes.paladin.mastery.beacon-xp", 4));
        }
    }

    private void handleRetributionCast(final Player player, final PaladinCombatState state,
                                       final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (verdictFinishers().contains(spellId)) {
            if (state.consumeVerdict(now, markWindowMillis(playerId))) {
                if ("vegso_itelet".equals(doctrine(playerId, 50))) {
                    state.lightMark(PaladinCombatState.JudgmentMark.BUN, now,
                            markWindowMillis(playerId));
                }
                if ("itelet_sulya".equals(doctrine(playerId, 40))) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                            config.getInt("classes.paladin.verdict.weight-strength-ticks", 60),
                            0, false, true, true));
                }
                if ("szent_haboru".equals(doctrine(playerId, 50))) {
                    healPlayer(player, config.getDouble(
                            "classes.paladin.verdict.holy-war-heal", 4.0D), false);
                }
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.PALADIN,
                            config.getInt("classes.paladin.mastery.verdict-xp", 5));
                }
                player.sendActionBar(messages.getMessage("paladin.verdict.burst",
                        "<gold>⚖ Verdict: a három Ítélet-jel bevégeztetett.</gold>"));
            }
            return;
        }
        final PaladinCombatState.JudgmentMark mark = switch (spellId) {
            case "judgment" -> PaladinCombatState.JudgmentMark.BUN;
            case "blade_of_justice" -> PaladinCombatState.JudgmentMark.DAC;
            case "holy_fire" -> PaladinCombatState.JudgmentMark.KARHOZAT;
            default -> null;
        };
        if (mark == null) return;
        final int lit = state.lightMark(mark, now, markWindowMillis(playerId));
        if (lit == PaladinCombatState.JudgmentMark.values().length) {
            player.sendActionBar(messages.getMessage("paladin.verdict.armed",
                    "<gold>Bűn, Dac és Kárhozat együtt áll: a következő ítélet-finisher Verdict.</gold>"));
        }
    }

    private void handleProtectionCast(final Player player, final PaladinCombatState state,
                                      final String spellId) {
        final UUID playerId = player.getUniqueId();
        final int cost = shieldCost(playerId, spellId);
        if (cost <= 0) {
            if (protectionChargeSpells().contains(spellId)) {
                state.addShieldCharge(config.getInt(
                        "classes.paladin.shield.cast-gain", 12));
            }
            return;
        }
        if (!state.spendShieldCharge(cost)) return;
        final boolean capstone = "final_stand".equals(spellId);
        final int duration = config.getInt(capstone
                ? "classes.paladin.shield.capstone-duration-ticks"
                : "classes.paladin.shield.area-duration-ticks", capstone ? 120 : 80);
        int allyAmplifier = capstone ? 1 : 0;
        if ("kiralyok_orzoje".equals(doctrine(playerId, 50))) allyAmplifier++;
        double radius = Math.max(2.0D, config.getDouble(
                "classes.paladin.shield.area-radius", 7.0D));
        if ("kiterjesztett_fold".equals(doctrine(playerId, 40))) {
            radius += config.getDouble("classes.paladin.shield.extended-extra-radius", 2.0D);
        }
        int selfResistTicks = duration;
        if ("rendithetetlen".equals(doctrine(playerId, 40))) {
            selfResistTicks += config.getInt(
                    "classes.paladin.shield.steadfast-extra-ticks", 40);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                selfResistTicks, capstone ? 1 : 0, false, true, true));
        if ("utolso_bastya".equals(doctrine(playerId, 50)) && capstone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    duration, 1, false, true, true));
        }
        // Megszentelt Föld: one bounded pass at cast time; cross-region allies get scheduler hops.
        final int amplifier = allyAmplifier;
        for (final var nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player member) || !SpellTargetingUtil.isAlly(player, member)) {
                continue;
            }
            member.getScheduler().run(plugin, task -> member.addPotionEffect(
                    new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier,
                            false, true, true)), null);
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.PALADIN, capstone
                    ? config.getInt("classes.paladin.mastery.capstone-ground-xp", 8)
                    : config.getInt("classes.paladin.mastery.ground-xp", 5));
        }
        player.sendActionBar(messages.getMessage("paladin.shield.ground",
                "<gold>✟ Megszentelt Föld: a környező szövetségesek oltalmat kaptak.</gold>"));
    }

    /** Oltalmazás role: being hit also builds conviction and Pajzstöltet. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isPaladin(victim)
                || event.getFinalDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        final PaladinCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if (state.oathOrDefault(specDefaultOath(playerId))
                == PaladinCombatState.Oath.OLTALMAZAS) {
            state.addConviction(config.getInt(
                            "classes.paladin.conviction.hit-gain", 4), now,
                    convictionDecayDelayMillis(), convictionDecayPerSecond());
        }
        if ("protection".equals(activeSpec(playerId))) {
            int gain = config.getInt("classes.paladin.shield.hit-gain", 6);
            if ("acel_hit".equals(doctrine(playerId, 30))) {
                gain += config.getInt("classes.paladin.shield.steel-extra-gain", 3);
            }
            state.addShieldCharge(gain);
        }
    }

    /** Szentlélek: sneak + right-click with the Harang marks the single Fényjelző beacon ally. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBeaconInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player paladin = event.getPlayer();
        if (!paladin.isSneaking() || !isPaladin(paladin)
                || !"holy".equals(activeSpec(paladin.getUniqueId()))
                || !soulbondFactory.isUsableBy(
                paladin.getInventory().getItemInMainHand(), paladin.getUniqueId(), JobType.PALADIN)
                || !(event.getRightClicked() instanceof Player target)
                || target == paladin) return;
        if (!SpellTargetingUtil.isAlly(paladin, target)) {
            paladin.sendActionBar(messages.getMessage("paladin.beacon.invalid",
                    "<red>Fényjelző csak csapattársra tehető.</red>"));
            return;
        }
        event.setCancelled(true);
        final UUID paladinId = paladin.getUniqueId();
        assignBeacon(paladinId, target);
        paladin.sendActionBar(messages.getMessage("paladin.beacon.set",
                "<yellow>✦ Fényjelző: <white>{target}</white> — a gyógyításaid visszhangja rá is hull.</yellow>",
                Map.of("target", target.getName())));
    }

    public Component hudSuffix(final Player player) {
        if (!isPaladin(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final PaladinCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final PaladinCombatState.Oath oath = state.oathOrDefault(specDefaultOath(playerId));
        Component suffix = Component.text("  • " + oathName(oath) + " "
                        + state.conviction(now, convictionDecayDelayMillis(), convictionDecayPerSecond()),
                NamedTextColor.GOLD);
        final String spec = activeSpec(playerId);
        if ("holy".equals(spec)) {
            final BeaconTarget beacon = beaconTargets.get(playerId);
            suffix = suffix.append(Component.text("  • Fényjelző "
                    + (beacon == null ? "—" : beacon.label()), NamedTextColor.YELLOW));
        } else if ("retribution".equals(spec)) {
            final int marks = state.markCount(now, markWindowMillis(playerId));
            suffix = suffix.append(Component.text("  • Jelek " + marks + "/3"
                    + (marks == 3 ? " ⚖" : ""), NamedTextColor.GOLD));
        } else if ("protection".equals(spec)) {
            suffix = suffix.append(Component.text("  • Pajzstöltet " + state.shieldCharge(),
                    NamedTextColor.YELLOW));
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isPaladin(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final PaladinCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final PaladinCombatState.Oath oath = combat.oathOrDefault(specDefaultOath(id));
        final int conviction = combat.conviction(now, convictionDecayDelayMillis(), convictionDecayPerSecond());
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "conviction", oathName(oath), oathName(oath) + " " + conviction,
                conviction, 100, oath.name().toLowerCase(Locale.ROOT));
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        int charges = 0;
        int maximum = 0;
        String proc = "";
        switch (activeSpec(id)) {
            case "holy" -> {
                final BeaconTarget beacon = beaconTargets.get(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text(
                        "beacon", "Fényjelző", "Fényjelző " + (beacon == null ? "—" : beacon.label()),
                        beacon == null ? "idle" : "active");
            }
            case "retribution" -> {
                charges = combat.markCount(now, markWindowMillis(id)); maximum = 3;
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "judgement_marks", "Jelek", "Jelek " + charges + "/3",
                        charges, 3, charges == 3 ? "ready" : "building");
                if (charges == 3) proc = "Ítélet kész";
            }
            case "protection" -> secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                    "shield_charge", "Pajzstöltet", "Pajzstöltet " + combat.shieldCharge(),
                    combat.shieldCharge(), 100, "active");
            default -> { }
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, "", proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.PALADIN) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"holy".equals(spec) && !"retribution".equals(spec) && !"protection".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        clearBeacon(playerId);
        final PaladinCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        clearBeacon(playerId);
        clearBeaconTarget(playerId);
        final PaladinCombatState state = states.remove(playerId);
        if (state != null) state.clearAll();
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(states.keySet())) clearPlayerState(id);
        states.clear();
        beaconTargets.clear();
        paladinsByBeaconTarget.clear();
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

    private void assignBeacon(final UUID paladinId, final Player target) {
        clearBeacon(paladinId);
        final BeaconTarget beacon = new BeaconTarget(target.getUniqueId(),
                target.getScheduler(), target.getName());
        beaconTargets.put(paladinId, beacon);
        paladinsByBeaconTarget.computeIfAbsent(beacon.id(),
                ignored -> ConcurrentHashMap.newKeySet()).add(paladinId);
    }

    private void clearBeacon(final UUID paladinId) {
        final BeaconTarget beacon = beaconTargets.remove(paladinId);
        if (beacon == null) return;
        paladinsByBeaconTarget.computeIfPresent(beacon.id(), (ignored, paladins) -> {
            paladins.remove(paladinId);
            return paladins.isEmpty() ? null : paladins;
        });
    }

    private void clearBeaconTarget(final UUID targetId) {
        final Set<UUID> paladins = paladinsByBeaconTarget.remove(targetId);
        if (paladins == null) return;
        for (final UUID paladinId : List.copyOf(paladins)) {
            beaconTargets.computeIfPresent(paladinId,
                    (ignored, beacon) -> beacon.id().equals(targetId) ? null : beacon);
        }
    }

    private static void healPlayer(final Player target, final double amount, final boolean regen) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
        if (regen) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    60, 0, false, true, true));
        }
    }

    private PaladinCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new PaladinCombatState());
    }

    private boolean isPaladin(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.PALADIN;
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

    private PaladinCombatState.Oath specDefaultOath(final UUID playerId) {
        return switch (activeSpec(playerId)) {
            case "retribution" -> PaladinCombatState.Oath.ITELET;
            case "protection" -> PaladinCombatState.Oath.OLTALMAZAS;
            default -> PaladinCombatState.Oath.IRGALOM;
        };
    }

    private static String oathName(final PaladinCombatState.Oath oath) {
        return switch (oath) {
            case IRGALOM -> "Irgalom";
            case ITELET -> "Ítélet";
            case OLTALMAZAS -> "Oltalmazás";
        };
    }

    private boolean isInCombat(final UUID playerId) {
        final ResourceManager tracker = combatTracker;
        final long windowMillis = Math.max(1L, config.getLong(
                "classes.paladin.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> roleSpells(final PaladinCombatState.Oath oath) {
        final String key = switch (oath) {
            case IRGALOM -> "classes.paladin.conviction.mercy-spells";
            case ITELET -> "classes.paladin.conviction.judgment-spells";
            case OLTALMAZAS -> "classes.paladin.conviction.protection-spells";
        };
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private Set<String> beaconEchoSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.paladin.beacon.echo-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private Set<String> verdictFinishers() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.paladin.verdict.finishers")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private Set<String> protectionChargeSpells() {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList("classes.paladin.shield.charge-spells")) {
            result.add(normalize(raw));
        }
        return result;
    }

    private int convictionGain(final UUID playerId, final PaladinCombatState.Oath oath) {
        int gain = Math.max(0, config.getInt("classes.paladin.conviction.cast-gain", 8));
        if (oath == PaladinCombatState.Oath.IRGALOM
                && "gyors_aldas".equals(doctrine(playerId, 30))) {
            gain += config.getInt("classes.paladin.conviction.blessing-extra-gain", 2);
        }
        if (oath == PaladinCombatState.Oath.ITELET
                && "buzgalom".equals(doctrine(playerId, 30))) {
            gain += config.getInt("classes.paladin.conviction.zeal-extra-gain", 2);
        }
        return gain;
    }

    private int convictionThreshold(final UUID playerId) {
        final int base = Math.max(10, config.getInt(
                "classes.paladin.conviction.threshold", 70));
        return "aldott_kez".equals(doctrine(playerId, 40))
                ? Math.max(10, base - Math.max(0, config.getInt(
                "classes.paladin.conviction.blessed-reduction", 10))) : base;
    }

    private long convictionDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.paladin.conviction.decay-delay-millis", 6000L));
    }

    private double convictionDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.paladin.conviction.decay-per-second", 5.0D));
    }

    private long markWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.paladin.verdict.mark-window-millis", 8000L));
        return "gyors_itelet".equals(doctrine(playerId, 30))
                ? base + Math.max(0L, config.getLong(
                "classes.paladin.verdict.swift-extra-millis", 2500L)) : base;
    }

    private double verdictBonusPercent(final UUID playerId) {
        double bonus = Math.max(0.0D, config.getDouble(
                "classes.paladin.verdict.bonus-percent", 24.0D));
        if ("melto_harag".equals(doctrine(playerId, 40))) {
            bonus += config.getDouble("classes.paladin.verdict.wrath-extra-percent", 8.0D);
        }
        return bonus;
    }

    private int shieldCost(final UUID playerId, final String spellId) {
        int cost = switch (spellId) {
            case "shield_of_the_righteous" -> config.getInt(
                    "classes.paladin.shield.righteous-cost", 30);
            case "guardian_of_kings" -> config.getInt(
                    "classes.paladin.shield.kings-cost", 60);
            case "final_stand" -> config.getInt(
                    "classes.paladin.shield.final-stand-cost", 80);
            default -> 0;
        };
        if (cost > 0 && "szent_fal".equals(doctrine(playerId, 30))) {
            cost -= Math.max(0, config.getInt(
                    "classes.paladin.shield.holy-wall-reduction", 10));
        }
        return Math.max(0, cost);
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
