package hu.taliann.icesmp.demonhunter;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
 * Concrete Démonvadász vertical-slice runtime.
 *
 * <p>The class core is Kárhozat-terhelés: demonic casts build load whose heated band empowers
 * demonic casts through the capped shared power pipeline, while the overloaded band keeps the
 * bigger bonus but makes every incoming hit bite harder — a readable, player-controlled trade,
 * ventable with consume_magic, never a random punish. Tombolás plays a lightweight Lélektöredék
 * counter collected by mobility casts into heals and a Momentum window; Bosszú plays the
 * Fájdalom pool with at most two concurrently armed Sigils. Durable state remains
 * Profile v2.</p>
 */
public final class DemonHunterGameplayService implements Listener, PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, DemonHunterCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public DemonHunterGameplayService(final JavaPlugin plugin,
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
        if (player == null || jobs.getPrimaryJob(player) != JobType.DEMON_HUNTER) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.demon_hunter.active-kit.maximum", 7)));
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
                "classes.demon_hunter.active-kit." + activeSpec)) {
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
        if (!isDemonHunter(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        if (!"vengeance".equals(activeSpec(playerId))) return true;
        final DemonHunterCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final int cost = painCost(playerId, spellId);
        if (cost > 0 && state.pain() < cost) {
            player.sendActionBar(messages.getMessage("demonhunter.pain.need",
                    "<red>Nincs elég Fájdalom. Szükséges: {amount}.</red>",
                    Map.of("amount", Integer.toString(cost))));
            return false;
        }
        if (sigilSpells().contains(spellId)
                && state.armedSigils(System.currentTimeMillis()) >= 2) {
            player.sendActionBar(messages.getMessage("demonhunter.sigil.full",
                    "<red>Egyszerre legfeljebb két Sigil állhat; várd ki az egyik lejártát.</red>"));
            return false;
        }
        return true;
    }

    /** Pure peek: the load band and an armed Momentum empower demonic casts through the capped pipeline. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isDemonHunter(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final DemonHunterCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        if (demonicSpells().contains(spellId)) {
            final DemonHunterCombatState.LoadBand band = state.loadBand(now,
                    heatedThreshold(), overloadThreshold(),
                    loadDecayDelayMillis(), loadDecayPerSecond());
            if (band == DemonHunterCombatState.LoadBand.ATHEVULT) {
                bonus += Math.max(0.0D, config.getDouble(
                        "classes.demon_hunter.load.heated-bonus-percent", 10.0D));
            } else if (band == DemonHunterCombatState.LoadBand.TULTERHELT) {
                bonus += Math.max(0.0D, config.getDouble(
                        "classes.demon_hunter.load.overload-bonus-percent", 20.0D));
            }
        }
        if ("havoc".equals(activeSpec(playerId)) && damageSpells().contains(spellId)
                && state.isMomentumArmed(now)) {
            double momentum = Math.max(0.0D, config.getDouble(
                    "classes.demon_hunter.momentum.bonus-percent", 15.0D));
            if ("tancos".equals(doctrine(playerId, 40))) {
                momentum += config.getDouble(
                        "classes.demon_hunter.momentum.dancer-extra-percent", 6.0D);
            }
            bonus += momentum;
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.demon_hunter.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isDemonHunter(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final DemonHunterCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        if (demonicSpells().contains(spellId)) {
            state.addLoad(config.getInt("classes.demon_hunter.load.cast-gain", 9), now,
                    loadDecayDelayMillis(), loadDecayPerSecond());
        }
        if ("consume_magic".equals(spellId)) {
            final int vented = state.ventLoad(config.getInt(
                    "classes.demon_hunter.load.consume-vent", 40));
            if (vented > 0) {
                player.sendActionBar(messages.getMessage("demonhunter.load.vented",
                        "<green>A démoni terhelés egy része kioltva.</green>"));
            }
        }

        final String spec = activeSpec(playerId);
        if ("havoc".equals(spec)) handleHavocCast(player, state, spellId, now);
        else if ("vengeance".equals(spec)) handleVengeanceCast(player, state, spellId, now);
    }

    private void handleHavocCast(final Player player, final DemonHunterCombatState state,
                                 final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (state.isMomentumArmed(now) && damageSpells().contains(spellId)) {
            state.consumeMomentum(now);
        }
        if (mobilitySpells().contains(spellId)) {
            final int collected = state.collectFragments();
            if (collected > 0) {
                double healPerFragment = Math.max(0.0D, config.getDouble(
                        "classes.demon_hunter.fragments.heal-per-fragment", 1.0D));
                if ("tulvilagi_lendulet".equals(doctrine(playerId, 50))) {
                    healPerFragment += config.getDouble(
                            "classes.demon_hunter.fragments.otherworld-extra-heal", 1.0D);
                }
                healPlayer(player, collected * healPerFragment);
                final int charges = "vadaszat_ura".equals(doctrine(playerId, 50)) ? 2 : 1;
                state.armMomentum(charges, now, momentumWindowMillis(playerId));
                if (collected >= config.getInt(
                        "classes.demon_hunter.mastery.collect-threshold", 3)
                        && isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.DEMON_HUNTER,
                            config.getInt("classes.demon_hunter.mastery.collect-xp", 4));
                }
                player.sendActionBar(messages.getMessage("demonhunter.fragments.collected",
                        "<light_purple>👁 {count} Lélektöredék begyűjtve — Momentum!</light_purple>",
                        Map.of("count", Integer.toString(collected))));
            }
            return;
        }
        if (damageSpells().contains(spellId)) {
            int gain = config.getInt("classes.demon_hunter.fragments.cast-gain", 1);
            if ("elso_vagas".equals(doctrine(playerId, 30))) gain++;
            final int maximum = Math.max(1, config.getInt(
                    "classes.demon_hunter.fragments.maximum", 5));
            final int total = state.addFragments(gain, maximum);
            if (total >= maximum && "vadaszat".equals(doctrine(playerId, 30))) {
                final int collected = state.collectFragments();
                healPlayer(player, collected * Math.max(0.0D, config.getDouble(
                        "classes.demon_hunter.fragments.heal-per-fragment", 1.0D)));
                state.armMomentum(1, now, momentumWindowMillis(playerId));
            }
        }
    }

    private void handleVengeanceCast(final Player player, final DemonHunterCombatState state,
                                     final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final int cost = painCost(playerId, spellId);
        if (cost <= 0) return;
        if (!state.spendPain(cost)) return;
        if (sigilSpells().contains(spellId)) {
            long duration = Math.max(1000L, config.getLong(
                    "classes.demon_hunter.sigil.duration-millis", 8000L));
            if ("egeto_marka".equals(doctrine(playerId, 40))) {
                duration += Math.max(0L, config.getLong(
                        "classes.demon_hunter.sigil.searing-extra-millis", 2000L));
            }
            state.armSigil(now, duration);
            if ("demontuskek".equals(doctrine(playerId, 50))) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        config.getInt("classes.demon_hunter.sigil.spikes-resist-ticks", 60),
                        0, false, true, true));
            }
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DEMON_HUNTER,
                        config.getInt("classes.demon_hunter.mastery.sigil-xp", 4));
            }
        } else if ("soul_cleave".equals(spellId)) {
            double heal = Math.max(0.5D, config.getDouble(
                    "classes.demon_hunter.pain.cleave-heal", 4.0D));
            if ("lelekvago".equals(doctrine(playerId, 40))) {
                heal += config.getDouble("classes.demon_hunter.pain.cutter-extra-heal", 2.0D);
            }
            healPlayer(player, heal);
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DEMON_HUNTER,
                        config.getInt("classes.demon_hunter.mastery.cleave-xp", 3));
            }
        } else if ("fel_blade".equals(spellId)) {
            if ("pokoli_pusztitas".equals(doctrine(playerId, 50))) {
                state.ventLoad(100);
            }
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DEMON_HUNTER,
                        config.getInt("classes.demon_hunter.mastery.capstone-xp", 8));
            }
        }
    }

    /**
     * The overload trade and the Bosszú Fájdalom: while overloaded every hit bites harder
     * (readable on the HUD, entirely player-controlled), and Vengeance converts pain to fuel.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isDemonHunter(victim)
                || event.getDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        final DemonHunterCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        if (state.loadBand(now, heatedThreshold(), overloadThreshold(),
                loadDecayDelayMillis(), loadDecayPerSecond())
                == DemonHunterCombatState.LoadBand.TULTERHELT) {
            final double penalty = Math.max(0.0D, config.getDouble(
                    "classes.demon_hunter.load.overload-taken-penalty-percent", 15.0D));
            event.setDamage(event.getDamage() * (1.0D + penalty / 100.0D));
        }
        if ("vengeance".equals(activeSpec(playerId))) {
            int gain = config.getInt("classes.demon_hunter.pain.hit-gain", 7);
            if ("vastag_tuske".equals(doctrine(playerId, 30))) {
                gain += config.getInt("classes.demon_hunter.pain.thick-extra-gain", 2);
            }
            state.addPain(gain);
        }
    }

    public Component hudSuffix(final Player player) {
        if (!isDemonHunter(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final DemonHunterCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final DemonHunterCombatState.LoadBand band = state.loadBand(now,
                heatedThreshold(), overloadThreshold(),
                loadDecayDelayMillis(), loadDecayPerSecond());
        Component suffix = Component.text("  • Terhelés "
                        + state.load(now, loadDecayDelayMillis(), loadDecayPerSecond())
                        + " " + bandName(band),
                band == DemonHunterCombatState.LoadBand.TULTERHELT
                        ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE);
        final String spec = activeSpec(playerId);
        if ("havoc".equals(spec)) {
            suffix = suffix.append(Component.text("  • Töredék " + state.fragments()
                    + (state.isMomentumArmed(now) ? " • Momentum ➤" : ""),
                    NamedTextColor.LIGHT_PURPLE));
        } else if ("vengeance".equals(spec)) {
            suffix = suffix.append(Component.text("  • Fájdalom " + state.pain()
                    + " • Sigil " + state.armedSigils(now) + "/2", NamedTextColor.DARK_RED));
        }
        return suffix;
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.DEMON_HUNTER) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        final String spec = activeSpec(player.getUniqueId());
        if (!"havoc".equals(spec) && !"vengeance".equals(spec)) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final DemonHunterCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final DemonHunterCombatState state = states.remove(playerId);
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

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private DemonHunterCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new DemonHunterCombatState());
    }

    private boolean isDemonHunter(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.DEMON_HUNTER;
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
                "classes.demon_hunter.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> demonicSpells() {
        return configSet("classes.demon_hunter.load.demonic-spells");
    }

    private Set<String> damageSpells() {
        return configSet("classes.demon_hunter.fragments.damage-spells");
    }

    private Set<String> mobilitySpells() {
        return configSet("classes.demon_hunter.fragments.mobility-spells");
    }

    private Set<String> sigilSpells() {
        return configSet("classes.demon_hunter.sigil.spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private int heatedThreshold() {
        return Math.max(1, Math.min(99, config.getInt(
                "classes.demon_hunter.load.heated-threshold", 40)));
    }

    private int overloadThreshold() {
        return Math.max(2, Math.min(100, config.getInt(
                "classes.demon_hunter.load.overload-threshold", 80)));
    }

    private long loadDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.demon_hunter.load.decay-delay-millis", 5000L));
    }

    private double loadDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.demon_hunter.load.decay-per-second", 6.0D));
    }

    private long momentumWindowMillis(final UUID playerId) {
        final long base = Math.max(1000L, config.getLong(
                "classes.demon_hunter.momentum.window-millis", 4000L));
        return "lendulet_mestere".equals(doctrine(playerId, 40))
                ? base + Math.max(0L, config.getLong(
                "classes.demon_hunter.momentum.master-extra-millis", 2000L)) : base;
    }

    private int painCost(final UUID playerId, final String spellId) {
        int cost;
        if (sigilSpells().contains(spellId)) {
            cost = config.getInt("classes.demon_hunter.pain.sigil-cost", 25);
            if ("olcso_pecset".equals(doctrine(playerId, 30))) {
                cost -= Math.max(0, config.getInt(
                        "classes.demon_hunter.pain.cheap-seal-reduction", 5));
            }
        } else {
            cost = switch (spellId) {
                case "soul_cleave" -> config.getInt(
                        "classes.demon_hunter.pain.cleave-cost", 30);
                case "fel_blade" -> config.getInt(
                        "classes.demon_hunter.pain.capstone-cost", 60);
                default -> 0;
            };
        }
        return Math.max(0, cost);
    }

    private static String bandName(final DemonHunterCombatState.LoadBand band) {
        return switch (band) {
            case STABIL -> "Stabil";
            case ATHEVULT -> "Áthevült";
            case TULTERHELT -> "Túlterhelt!";
        };
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
