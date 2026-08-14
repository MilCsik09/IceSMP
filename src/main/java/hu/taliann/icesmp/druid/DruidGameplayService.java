package hu.taliann.icesmp.druid;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.DruidFormSpell;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * Concrete Druida vertical-slice runtime.
 *
 * <p>The class mechanic is Harmónia and Évszak: nature casts build Harmónia, while the separate
 * primary ResourceManager pool remains Természeti Erő. A shapeshift on the existing form system
 * releases Harmónia as the season bound to that form.
 * Vadőr follows one prey trail for combo points, Holdjós swings the Nap↔Hold balance into an
 * Eclipse window, Védelmező stacks self-only bark layers with a root retaliation window (never a
 * target-bound guardian index), and Helyreállító plants seeds that must ripen before a bloom can
 * harvest them. Durable state remains Profile v2.</p>
 */
public final class DruidGameplayService implements Listener, PlayerStateCleanup {

    /** The four seasons, each bound to one existing Druid form. */
    public enum Season {
        TAVASZ,
        NYAR,
        OSZ,
        TEL
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, DruidCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public DruidGameplayService(final JavaPlugin plugin,
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
        if (player == null || jobs.getPrimaryJob(player) != JobType.DRUID) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.druid.active-kit.maximum", 7)));
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
                "classes.druid.active-kit." + activeSpec)) {
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

    /** Preparation gates: a finisher needs combo points and a bloom needs a ripe seed. */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isDruid(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final DruidCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final String spec = activeSpec(playerId);
        if ("feral".equals(spec) && finisherSpells().contains(spellId) && state.combo() <= 0) {
            player.sendActionBar(messages.getMessage("druid.combo.need",
                    "<red>Nincs kombópontod — előbb marcangolj.</red>"));
            return false;
        }
        if ("restoration".equals(spec) && bloomSpells().contains(spellId)
                && state.ripeSeedCount(System.currentTimeMillis(),
                ripenMillis(playerId), seedExpiryMillis()) <= 0) {
            player.sendActionBar(messages.getMessage("druid.seed.unripe",
                    "<red>Egyetlen Magod sem ért még be — a Virágzás előkészítést kér.</red>"));
            return false;
        }
        return true;
    }

    /** Pure peek: the Ősz window, an Eclipse and the stacked combo points empower the cast. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isDruid(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final DruidCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        if (state.isAutumnWindowArmed(now)) {
            bonus += Math.max(0.0D, config.getDouble(
                    "classes.druid.seasons.autumn-bonus-percent", 12.0D));
        }
        final String spec = activeSpec(playerId);
        if ("lunar".equals(spec) && state.isEclipseArmed(now)
                && (solarSpells().contains(spellId) || lunarSpells().contains(spellId))) {
            double eclipse = Math.max(0.0D, config.getDouble(
                    "classes.druid.lunar.eclipse-bonus-percent", 18.0D));
            if ("ket_egbolt".equals(doctrine(playerId, 50))) {
                eclipse += config.getDouble("classes.druid.lunar.two-skies-extra-percent", 6.0D);
            }
            bonus += eclipse;
        }
        if ("feral".equals(spec) && finisherSpells().contains(spellId)) {
            bonus += state.combo() * Math.max(0.0D, config.getDouble(
                    "classes.druid.feral.per-combo-percent", 5.0D));
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.druid.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isDruid(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final DruidCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        if (natureSpells().contains(spellId)) {
            state.addHarmony(config.getInt("classes.druid.harmony.cast-gain", 8), now,
                    harmonyDecayDelayMillis(), harmonyDecayPerSecond());
        }
        final Season season = seasonOf(spellId);
        if (season != null) {
            releaseSeason(player, state, season, now);
            return;
        }

        switch (activeSpec(playerId)) {
            case "feral" -> handleFeralCast(player, state, spellId, now);
            case "lunar" -> handleLunarCast(player, state, spellId, now);
            case "ironbark" -> handleIronbarkCast(player, state, spellId, now);
            case "restoration" -> handleRestorationCast(player, state, spellId, now);
            default -> { }
        }
    }

    /**
     * Alakváltás: the shapeshift releases the whole harmony pool as its season. Below the
     * threshold the form still works — only the blessing is withheld, so the existing form
     * system keeps its own behavior untouched.
     */
    private void releaseSeason(final Player player, final DruidCombatState state,
                               final Season season, final long now) {
        final int released = state.releaseHarmony(
                config.getInt("classes.druid.harmony.release-threshold", 30));
        if (released <= 0) return;
        final double magnitude = released / 100.0D;
        final int duration = (int) Math.round(Math.max(1.0D, config.getDouble(
                "classes.druid.seasons.base-duration-ticks", 100.0D)) * (0.5D + magnitude));
        switch (season) {
            case TAVASZ -> {
                healPlayer(player, Math.max(0.5D, config.getDouble(
                        "classes.druid.seasons.spring-heal", 6.0D) * magnitude));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        duration, 0, false, true, true));
            }
            case NYAR -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    duration, 0, false, true, true));
            case OSZ -> state.armAutumnWindow(now, Math.max(1000L, config.getLong(
                    "classes.druid.seasons.autumn-window-millis", 6000L)));
            case TEL -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        duration, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                        duration, 0, false, true, true));
            }
        }
        if (isInCombat(player.getUniqueId())) {
            specs.contributeClassMastery(player, JobType.DRUID,
                    config.getInt("classes.druid.mastery.season-xp", 5));
        }
        player.sendActionBar(messages.getMessage("druid.season.released",
                "<green>🍃 {season}: {amount} Harmónia szabadult fel.</green>",
                Map.of("season", seasonName(season), "amount", Integer.toString(released))));
    }

    // ===== Vadőr =====

    private void handleFeralCast(final Player player, final DruidCombatState state,
                                 final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (finisherSpells().contains(spellId)) {
            final int spent = state.spendAllCombo();
            if (spent <= 0) return;
            double heal = spent * Math.max(0.0D, config.getDouble(
                    "classes.druid.feral.finisher-heal-per-combo", 1.0D));
            if ("orok_uldozo".equals(doctrine(playerId, 50))) {
                heal += config.getDouble("classes.druid.feral.eternal-extra-heal", 2.0D);
            }
            if (isInCombat(playerId)) {
                healPlayer(player, heal);
                specs.contributeClassMastery(player, JobType.DRUID,
                        config.getInt("classes.druid.mastery.finisher-xp", 5));
            }
            player.sendActionBar(messages.getMessage("druid.combo.spent",
                    "<dark_green>🐾 {count} kombópont kifutott a végzésbe.</dark_green>",
                    Map.of("count", Integer.toString(spent))));
            return;
        }
        if (!clawSpells().contains(spellId)) return;
        int gain = config.getInt("classes.druid.feral.combo-per-cast", 1);
        if (state.isScentLive(now) && "ragadozo_osztone".equals(doctrine(playerId, 30))) {
            gain += Math.max(0, config.getInt("classes.druid.feral.instinct-extra-combo", 1));
        }
        state.addCombo(gain, comboMaximum(playerId));
    }

    // ===== Holdjós =====

    private void handleLunarCast(final Player player, final DruidCombatState state,
                                 final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final int shift = config.getInt("classes.druid.lunar.shift-per-cast", 25);
        final int delta;
        if (solarSpells().contains(spellId)) delta = shift;
        else if (lunarSpells().contains(spellId)) delta = -shift;
        else return;
        final int cap = Math.max(10, Math.min(100,
                config.getInt("classes.druid.lunar.balance-cap", 100)));
        final int balance = state.shiftBalance(delta);
        if (Math.abs(balance) < cap) return;
        long window = Math.max(1000L, config.getLong(
                "classes.druid.lunar.eclipse-window-millis", 6000L));
        if ("hosszu_egyuttallas".equals(doctrine(playerId, 40))) {
            window += Math.max(0L, config.getLong(
                    "classes.druid.lunar.long-alignment-extra-millis", 2000L));
        }
        state.armEclipse(now, window);
        // The sweep starts over: an Eclipse is earned by swinging the balance, never by camping it.
        state.resetBalance("orok_egyuttallas".equals(doctrine(playerId, 50))
                ? -Integer.signum(balance) * config.getInt(
                        "classes.druid.lunar.eternal-restart-value", 20)
                : 0);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.DRUID,
                    config.getInt("classes.druid.mastery.eclipse-xp", 6));
        }
        player.sendActionBar(messages.getMessage("druid.eclipse.armed",
                "<blue>🌙 Eclipse — a Nap és a Hold együtt áll.</blue>"));
    }

    // ===== Védelmező =====

    private void handleIronbarkCast(final Player player, final DruidCombatState state,
                                    final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (barkSpells().contains(spellId)) {
            final int layers = state.addBarkLayer(barkMaximum(playerId));
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DRUID,
                        config.getInt("classes.druid.mastery.bark-xp", 4));
            }
            player.sendActionBar(messages.getMessage("druid.bark.layered",
                    "<dark_green>🌳 Kéregréteg {count}/{max}.</dark_green>",
                    Map.of("count", Integer.toString(layers),
                            "max", Integer.toString(barkMaximum(playerId)))));
            return;
        }
        if (rootSpells().contains(spellId)) {
            long window = Math.max(1000L, config.getLong(
                    "classes.druid.ironbark.roots-window-millis", 6000L));
            if ("gyors_gyokerek".equals(doctrine(playerId, 30))) {
                window += Math.max(0L, config.getLong(
                        "classes.druid.ironbark.quick-roots-extra-millis", 2000L));
            }
            state.armRoots(now, window);
            player.sendActionBar(messages.getMessage("druid.roots.armed",
                    "<dark_green>🌿 Gyökérháló feszül — aki rád támad, beleakad.</dark_green>"));
        }
    }

    // ===== Helyreállító =====

    private void handleRestorationCast(final Player player, final DruidCombatState state,
                                       final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (seedSpells().contains(spellId)) {
            if (state.plantSeed(now, seedMaximum(playerId), seedExpiryMillis())) {
                player.sendActionBar(messages.getMessage("druid.seed.planted",
                        "<green>🌱 Mag elültetve ({count}/{max}) — érnie kell.</green>",
                        Map.of("count", Integer.toString(state.seedCount(now, seedExpiryMillis())),
                                "max", Integer.toString(seedMaximum(playerId)))));
            }
            return;
        }
        if (!bloomSpells().contains(spellId)) return;
        final int ripe = state.collectRipeSeeds(now, ripenMillis(playerId), seedExpiryMillis());
        if (ripe <= 0) return;
        double heal = ripe * Math.max(0.0D, config.getDouble(
                "classes.druid.restoration.heal-per-ripe-seed", 3.0D));
        if ("eletfa".equals(doctrine(playerId, 50))) {
            heal += config.getDouble("classes.druid.restoration.worldtree-extra-heal", 2.0D);
        }
        healPlayer(player, heal);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.DRUID,
                    config.getInt("classes.druid.mastery.bloom-xp", 5));
        }
        player.sendActionBar(messages.getMessage("druid.seed.bloomed",
                "<green>🌸 {count} beérett Mag virágzott ki.</green>",
                Map.of("count", Integer.toString(ripe))));
    }

    // ===== Events =====

    /** Kéregrétegek: every hit cracks one layer and is blunted by it — self-only, no zone scan. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isDruid(victim)
                || event.getDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        if (!"ironbark".equals(activeSpec(playerId))) return;
        if (event.getDamage() < Math.max(0.0D, config.getDouble(
                "classes.druid.ironbark.min-damage-to-crack", 2.0D))) return;
        final DruidCombatState state = state(playerId);
        if (!state.crackBarkLayer()) return;
        double reduction = Math.max(0.0D, Math.min(60.0D, config.getDouble(
                "classes.druid.ironbark.layer-reduction-percent", 12.0D)));
        if ("vastag_kereg".equals(doctrine(playerId, 30))) {
            reduction = Math.min(60.0D, reduction + config.getDouble(
                    "classes.druid.ironbark.thick-extra-reduction-percent", 4.0D));
        }
        event.setDamage(event.getDamage() * (1.0D - reduction / 100.0D));
    }

    /** Gyökérháló retaliation: the attacker gets slowed on their own region thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeReceived(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isDruid(victim)
                || event.getFinalDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        if (!"ironbark".equals(activeSpec(playerId))) return;
        final long now = System.currentTimeMillis();
        if (!state(playerId).isRootsArmed(now)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        int ticks = Math.max(1, config.getInt("classes.druid.ironbark.roots-slow-ticks", 40));
        if ("melyre_nyulo_gyokerek".equals(doctrine(playerId, 40))) {
            ticks += Math.max(0, config.getInt(
                    "classes.druid.ironbark.deep-roots-extra-ticks", 20));
        }
        final int duration = ticks;
        // Folia: the attacker lives on its own region thread — never touch it inline.
        attacker.getScheduler().run(plugin, task -> attacker.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, duration, 0,
                        false, true, true)), null);
    }

    /**
     * Szagnyom: the trail is simply the prey the Vadőr keeps hitting. Staying on one target
     * pays, switching prey starts a new trail — no reverse index, no target-bound registry.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOutgoingDamage(final EntityDamageByEntityEvent event) {
        final UUID attackerId = attackerId(event);
        if (attackerId == null || !isDruid(attackerId)
                || !"feral".equals(activeSpec(attackerId))) return;
        final DruidCombatState state = state(attackerId);
        final long now = System.currentTimeMillis();
        final UUID victimId = event.getEntity().getUniqueId();
        long window = Math.max(1000L, config.getLong(
                "classes.druid.feral.scent-window-millis", 8000L));
        if ("szagnyom_mestere".equals(doctrine(attackerId, 40))) {
            window += Math.max(0L, config.getLong(
                    "classes.druid.feral.scent-master-extra-millis", 3000L));
        }
        if (!victimId.equals(state.scentTarget(now))) {
            state.markScent(victimId, now, window);
            return;
        }
        state.markScent(victimId, now, window);
        final double bonus = Math.max(0.0D, event.getEntity() instanceof Player
                ? config.getDouble("classes.druid.feral.scent-pvp-percent", 6.0D)
                : config.getDouble("classes.druid.feral.scent-pve-percent", 14.0D));
        if (bonus > 0.0D) event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
    }

    public Component hudSuffix(final Player player) {
        if (!isDruid(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final DruidCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final Season season = currentSeason(playerId);
        final String harmonyLabel = messages.get("druid.hud.harmony-label", "Harmónia");
        Component suffix = Component.text("  • " + harmonyLabel + " "
                        + state.harmony(now, harmonyDecayDelayMillis(), harmonyDecayPerSecond())
                        + (season == null ? "" : " • " + seasonName(season))
                        + (state.isAutumnWindowArmed(now) ? " ➤" : ""),
                NamedTextColor.GREEN);
        switch (activeSpec(playerId)) {
            case "feral" -> suffix = suffix.append(Component.text("  • Kombó " + state.combo()
                            + (state.isScentLive(now) ? " • Szagnyom" : ""),
                    NamedTextColor.DARK_GREEN));
            case "lunar" -> suffix = suffix.append(Component.text("  • Mérleg " + state.balance()
                            + (state.isEclipseArmed(now) ? " • Eclipse" : ""),
                    NamedTextColor.BLUE));
            case "ironbark" -> suffix = suffix.append(Component.text("  • Kéreg "
                            + state.barkLayers() + "/" + barkMaximum(playerId)
                            + (state.isRootsArmed(now) ? " • Gyökérháló" : ""),
                    NamedTextColor.DARK_GREEN));
            case "restoration" -> suffix = suffix.append(Component.text("  • Mag "
                            + state.ripeSeedCount(now, ripenMillis(playerId), seedExpiryMillis())
                            + "/" + state.seedCount(now, seedExpiryMillis()) + " érett",
                    NamedTextColor.GREEN));
            default -> { }
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection; no rendered-text parsing. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isDruid(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final DruidCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final Season season = currentSeason(id);
        final int harmony = combat.harmony(now, harmonyDecayDelayMillis(), harmonyDecayPerSecond());
        final String harmonyLabel = messages.get("druid.hud.harmony-label", "Harmónia");
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "harmony", harmonyLabel, harmonyLabel + " " + harmony,
                harmony, 100, season == null ? "natural" : season.name().toLowerCase(Locale.ROOT));
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = season == null ? "" : seasonName(season);
        String proc = combat.isAutumnWindowArmed(now) ? "Őszi ablak" : "";
        int charges = 0;
        int maximum = 0;
        switch (activeSpec(id)) {
            case "feral" -> {
                charges = combat.combo(); maximum = 5;
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "combo", "Kombó", "Kombó " + charges, charges, maximum,
                        combat.isScentLive(now) ? "scent" : "active");
                if (combat.isScentLive(now)) stateText = "Szagnyom";
            }
            case "lunar" -> {
                final int balance = combat.balance();
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "balance", "Mérleg", "Mérleg " + balance, Math.abs(balance), 100,
                        balance < 0 ? "lunar" : balance > 0 ? "solar" : "balanced");
                if (combat.isEclipseArmed(now)) proc = "Eclipse";
            }
            case "ironbark" -> {
                charges = combat.barkLayers(); maximum = barkMaximum(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "bark", "Kéreg", "Kéreg " + charges + "/" + maximum,
                        charges, maximum, combat.isRootsArmed(now) ? "roots" : "active");
                if (combat.isRootsArmed(now)) proc = "Gyökérháló";
            }
            case "restoration" -> {
                charges = combat.ripeSeedCount(now, ripenMillis(id), seedExpiryMillis());
                maximum = combat.seedCount(now, seedExpiryMillis());
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "seeds", "Mag", "Mag " + charges + "/" + maximum + " érett",
                        charges, Math.max(1, maximum), charges > 0 ? "ripe" : "growing");
            }
            default -> { }
        }
        return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.of(
                primary, secondary, stateText, proc, charges, maximum);
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.DRUID) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!DRUID_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final DruidCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final DruidCombatState state = states.remove(playerId);
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

    private static final Set<String> DRUID_SPECS =
            Set.of("feral", "lunar", "ironbark", "restoration");

    /** The season is bound to the existing form spell — the form system itself stays untouched. */
    private static Season seasonOf(final String spellId) {
        return switch (spellId) {
            case "druid_moonkin_form" -> Season.TAVASZ;
            case "druid_cat_form" -> Season.NYAR;
            case "druid_travel_form" -> Season.OSZ;
            case "druid_bear_form" -> Season.TEL;
            default -> null;
        };
    }

    private static String seasonName(final Season season) {
        return switch (season) {
            case TAVASZ -> "Tavasz";
            case NYAR -> "Nyár";
            case OSZ -> "Ősz";
            case TEL -> "Tél";
        };
    }

    /** Read-only: which season the player currently stands in, or none outside a form. */
    public Season currentSeason(final UUID playerId) {
        final DruidFormSpell.Form form = DruidFormSpell.activeForm(playerId);
        if (form == null) return null;
        return switch (form) {
            case MOONKIN -> Season.TAVASZ;
            case CAT -> Season.NYAR;
            case TRAVEL -> Season.OSZ;
            case BEAR -> Season.TEL;
        };
    }

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private DruidCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new DruidCombatState());
    }

    private boolean isDruid(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.DRUID;
    }

    private boolean isDruid(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile != null && "druid".equals(profile.primaryClassId());
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
                "classes.druid.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> natureSpells() {
        return configSet("classes.druid.harmony.nature-spells");
    }

    private Set<String> clawSpells() {
        return configSet("classes.druid.feral.claw-spells");
    }

    private Set<String> finisherSpells() {
        return configSet("classes.druid.feral.finisher-spells");
    }

    private Set<String> solarSpells() {
        return configSet("classes.druid.lunar.solar-spells");
    }

    private Set<String> lunarSpells() {
        return configSet("classes.druid.lunar.lunar-spells");
    }

    private Set<String> barkSpells() {
        return configSet("classes.druid.ironbark.bark-spells");
    }

    private Set<String> rootSpells() {
        return configSet("classes.druid.ironbark.root-spells");
    }

    private Set<String> seedSpells() {
        return configSet("classes.druid.restoration.seed-spells");
    }

    private Set<String> bloomSpells() {
        return configSet("classes.druid.restoration.bloom-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private long harmonyDecayDelayMillis() {
        return Math.max(0L, config.getLong(
                "classes.druid.harmony.decay-delay-millis", 6000L));
    }

    private double harmonyDecayPerSecond() {
        return Math.max(0.0D, config.getDouble(
                "classes.druid.harmony.decay-per-second", 4.0D));
    }

    private int comboMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.druid.feral.combo-maximum", 5));
        if ("vad_hajsza_ura".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.druid.feral.hunt-extra-combo", 1));
        }
        return maximum;
    }

    private int barkMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.druid.ironbark.maximum-layers", 3));
        if ("oreg_tolgy".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.druid.ironbark.oak-extra-layers", 1));
        }
        return maximum;
    }

    private int seedMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.druid.restoration.maximum-seeds", 3));
        if ("bo_vetes".equals(doctrine(playerId, 30))) {
            maximum += Math.max(0, config.getInt("classes.druid.restoration.rich-extra-seeds", 1));
        }
        return maximum;
    }

    private long ripenMillis(final UUID playerId) {
        long ripen = Math.max(1L, config.getLong(
                "classes.druid.restoration.ripen-millis", 4000L));
        if ("korai_eres".equals(doctrine(playerId, 30))) {
            ripen -= Math.max(0L, config.getLong(
                    "classes.druid.restoration.early-ripen-reduction-millis", 1000L));
        }
        if ("gyors_viragzas".equals(doctrine(playerId, 40))) {
            ripen -= Math.max(0L, config.getLong(
                    "classes.druid.restoration.quick-bloom-reduction-millis", 500L));
        }
        return Math.max(500L, ripen);
    }

    private long seedExpiryMillis() {
        return Math.max(2000L, config.getLong(
                "classes.druid.restoration.expiry-millis", 20000L));
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

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
