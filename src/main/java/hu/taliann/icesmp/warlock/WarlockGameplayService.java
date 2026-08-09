package hu.taliann.icesmp.warlock;

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
 * Concrete Boszorkánymester vertical-slice runtime.
 *
 * <p>Paktum és Lélekadósság is the class layer: a pact cast is empowered and books the debt that
 * paid for it. The debt never decays — only listed repayment acts work it off — and at the ceiling
 * every pact is refused until it is paid down, while a heavy debt also blunts the healing the
 * warlock receives. It is strictly a combat meter: nothing here reads or writes any wallet,
 * balance or currency. Átok inscribes a three-slot Átokgrimoár and ties one re-tieable Lélekfonal
 * that drains the linked target. Pusztítás banks Izzó Parázs and buys burst with a deterministic
 * Túlhevülés lockout. Demonológus (DARK, on the existing seal system) keeps a bounded roster of
 * called demon kinds under the demonologist.roster namespace. Durable state remains Profile v2.</p>
 */
public final class WarlockGameplayService implements Listener, PlayerStateCleanup {

    private static final Set<String> WARLOCK_SPECS =
            Set.of("affliction", "destruction", "demonologist");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, WarlockCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public WarlockGameplayService(final JavaPlugin plugin,
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
        if (player == null || jobs.getPrimaryJob(player) != JobType.WARLOCK) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.warlock.active-kit.maximum", 7)));
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
                "classes.warlock.active-kit." + activeSpec)) {
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

    /** At the debt ceiling the pacts are refused, and Túlhevülés locks the fire out. */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isWarlock(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final WarlockCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        if (pactSpells().contains(spellId) && state.isDebtCeilingReached(debtCeiling(playerId))) {
            player.sendActionBar(messages.getMessage("warlock.debt.ceiling",
                    "<red>A Lélekadósság betelt — előbb törleszd, csak utána köss új paktumot.</red>"));
            return false;
        }
        if ("destruction".equals(activeSpec(playerId)) && emberSpells().contains(spellId)
                && state.isOverheated(now)) {
            player.sendActionBar(messages.getMessage("warlock.overheat.locked",
                    "<red>Túlhevültél — a parázs most nem fogad több tüzet.</red>"));
            return false;
        }
        return true;
    }

    /** Pure peek: the pact, the banked embers and the roster empower their own casts. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isWarlock(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final WarlockCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        double bonus = 0.0D;
        if (pactSpells().contains(spellId)) {
            double pact = Math.max(0.0D, config.getDouble(
                    "classes.warlock.pact.bonus-percent", 18.0D));
            if ("mely_alku".equals(doctrine(playerId, 40))) {
                pact += config.getDouble("classes.warlock.pact.deep-extra-percent", 6.0D);
            }
            bonus += pact;
        }
        final String spec = activeSpec(playerId);
        if ("destruction".equals(spec) && burstSpells().contains(spellId)) {
            double perEmber = Math.max(0.0D, config.getDouble(
                    "classes.warlock.destruction.per-ember-percent", 5.0D));
            if ("robbano_mag".equals(doctrine(playerId, 40))) {
                perEmber += config.getDouble("classes.warlock.destruction.core-extra-percent", 1.5D);
            }
            bonus += state.embers() * perEmber;
        }
        if ("affliction".equals(spec) && drainSpells().contains(spellId)) {
            bonus += state.activeCurses(now) * Math.max(0.0D, config.getDouble(
                    "classes.warlock.affliction.per-curse-percent", 6.0D));
        }
        if ("demonologist".equals(spec) && demonicSpells().contains(spellId)) {
            double perDemon = Math.max(0.0D, config.getDouble(
                    "classes.warlock.demonologist.per-demon-percent", 6.0D));
            if ("vasbor".equals(doctrine(playerId, 40))) {
                perDemon += config.getDouble("classes.warlock.demonologist.hide-extra-percent", 2.0D);
            }
            bonus += state.rosterSize() * perDemon;
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.warlock.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isWarlock(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final WarlockCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        if (pactSpells().contains(spellId)) {
            int booked = config.getInt("classes.warlock.pact.debt-per-pact", 20);
            if ("olcso_paktum".equals(doctrine(playerId, 30))) {
                booked -= Math.max(0, config.getInt("classes.warlock.pact.cheap-reduction", 5));
            }
            final int total = state.addDebt(Math.max(0, booked), debtCeiling(playerId));
            player.sendActionBar(messages.getMessage("warlock.pact.sealed",
                    "<dark_purple>Paktum megkötve — Lélekadósság {debt}/{ceiling}.</dark_purple>",
                    Map.of("debt", Integer.toString(total),
                            "ceiling", Integer.toString(debtCeiling(playerId)))));
        }
        if (repaySpells().contains(spellId)) {
            int repaid = config.getInt("classes.warlock.pact.repay-per-cast", 12);
            if ("gyors_torlesztes".equals(doctrine(playerId, 50))) {
                repaid += Math.max(0, config.getInt("classes.warlock.pact.quick-extra-repay", 5));
            }
            if (state.repayDebt(Math.max(0, repaid)) > 0) {
                if (isInCombat(playerId)) {
                    specs.contributeClassMastery(player, JobType.WARLOCK,
                            config.getInt("classes.warlock.mastery.repay-xp", 4));
                }
                player.sendActionBar(messages.getMessage("warlock.debt.repaid",
                        "<light_purple>Törlesztettél — Lélekadósság {debt}.</light_purple>",
                        Map.of("debt", Integer.toString(state.debt()))));
            }
        }

        switch (activeSpec(playerId)) {
            case "affliction" -> handleAfflictionCast(player, state, spellId, now);
            case "destruction" -> handleDestructionCast(player, state, spellId, now);
            case "demonologist" -> handleDemonologistCast(player, state, spellId);
            default -> { }
        }
    }

    // ===== Átok =====

    private void handleAfflictionCast(final Player player, final WarlockCombatState state,
                                      final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final String curse = curseOf(spellId);
        if (curse != null) {
            if (state.inscribeCurse(curse, now, curseDurationMillis(playerId))) {
                player.sendActionBar(messages.getMessage("warlock.curse.inscribed",
                        "<dark_purple>Átok bejegyezve: {curse} ({count}/3).</dark_purple>",
                        Map.of("curse", curse,
                                "count", Integer.toString(state.activeCurses(now)))));
            } else {
                player.sendActionBar(messages.getMessage("warlock.curse.full",
                        "<red>Az Átokgrimoár mindhárom lapja tele — várd ki egy átok lejártát.</red>"));
            }
            return;
        }
        if (!drainSpells().contains(spellId)) return;
        final int curses = state.activeCurses(now);
        if (curses <= 0) return;
        double heal = curses * Math.max(0.0D, config.getDouble(
                "classes.warlock.affliction.drain-heal-per-curse", 1.5D));
        if (state.threadTarget(now) != null) {
            heal += Math.max(0.0D, config.getDouble(
                    "classes.warlock.affliction.thread-extra-heal", 2.0D));
        }
        healPlayer(player, heal);
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.WARLOCK,
                    config.getInt("classes.warlock.mastery.drain-xp", 4));
        }
    }

    // ===== Pusztítás =====

    private void handleDestructionCast(final Player player, final WarlockCombatState state,
                                       final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        if (burstSpells().contains(spellId)) {
            final int maximum = emberMaximum(playerId);
            final boolean atMaximum = state.embers() >= maximum;
            final int spent = state.spendEmbers();
            if (spent <= 0) return;
            if (atMaximum) {
                // The risk is entirely opt-in: only a maxed-out burst buys the lockout.
                long lockout = Math.max(1000L, config.getLong(
                        "classes.warlock.destruction.overheat-millis", 5000L));
                if ("hideg_kez".equals(doctrine(playerId, 40))) {
                    lockout -= Math.max(0L, config.getLong(
                            "classes.warlock.destruction.cold-reduction-millis", 1500L));
                }
                state.enterOverheat(now, Math.max(1000L, lockout));
                player.sendActionBar(messages.getMessage("warlock.overheat.entered",
                        "<gold>🔥 Túlhevülés — a teljes parázs elégett a robbanásban.</gold>"));
            }
            if ("tuzvihar".equals(doctrine(playerId, 50))) {
                healPlayer(player, spent * Math.max(0.0D, config.getDouble(
                        "classes.warlock.destruction.storm-heal-per-ember", 1.0D)));
            }
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.WARLOCK,
                        config.getInt("classes.warlock.mastery.burst-xp", 6));
            }
            return;
        }
        if (!emberSpells().contains(spellId)) return;
        int gain = config.getInt("classes.warlock.destruction.ember-per-cast", 1);
        if ("elenk_parazs".equals(doctrine(playerId, 30))) gain++;
        state.addEmbers(gain, emberMaximum(playerId));
        if ("szikra_ora".equals(doctrine(playerId, 30))) {
            state.repayDebt(Math.max(0, config.getInt(
                    "classes.warlock.destruction.spark-repay", 2)));
        }
    }

    // ===== Demonológus (DARK) =====

    private void handleDemonologistCast(final Player player, final WarlockCombatState state,
                                        final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (dismissSpells().contains(spellId)) {
            final int dismissed = state.dismissRoster();
            if (dismissed <= 0) return;
            double perDemonHeal = Math.max(0.0D, config.getDouble(
                    "classes.warlock.demonologist.dismiss-heal-per-demon", 2.0D));
            if ("hu_szolga".equals(doctrine(playerId, 30))) {
                perDemonHeal += config.getDouble(
                        "classes.warlock.demonologist.loyal-extra-heal", 1.0D);
            }
            if ("nagy_legio".equals(doctrine(playerId, 50))
                    && dismissed >= WarlockCombatState.ROSTER_SLOTS) {
                perDemonHeal *= 2.0D;
            }
            healPlayer(player, dismissed * perDemonHeal);
            player.sendActionBar(messages.getMessage("warlock.roster.dismissed",
                    "<dark_purple>{count} démon elbocsátva a paktumból.</dark_purple>",
                    Map.of("count", Integer.toString(dismissed))));
            return;
        }
        final String kind = demonKindOf(spellId);
        if (kind == null || !state.callDemon(kind)) return;
        if ("gyors_hivas".equals(doctrine(playerId, 30))) {
            state.repayDebt(Math.max(0, config.getInt(
                    "classes.warlock.demonologist.call-repay", 4)));
        }
        if ("legios_rend".equals(doctrine(playerId, 40))
                || "orok_paktum".equals(doctrine(playerId, 50))) {
            healPlayer(player, Math.max(0.0D, config.getDouble(
                    "classes.warlock.demonologist.order-heal", 2.0D)));
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.WARLOCK,
                    config.getInt("classes.warlock.mastery.call-xp", 5));
        }
        player.sendActionBar(messages.getMessage("warlock.roster.called",
                "<dark_purple>😈 {kind} a paktumban ({count}/3).</dark_purple>",
                Map.of("kind", kind, "count", Integer.toString(state.rosterSize()))));
    }

    /** Lélekfonal: the thread names exactly one victim, and a new tie moves it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingDamage(final EntityDamageByEntityEvent event) {
        final UUID attackerId = attackerId(event);
        if (attackerId == null || !isWarlock(attackerId)
                || !"affliction".equals(activeSpec(attackerId))) return;
        if (event.getFinalDamage() <= 0.0D) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        state(attackerId).tieThread(victim.getUniqueId(), System.currentTimeMillis(),
                threadWindowMillis(attackerId));
    }

    /**
     * The debt's weight: a heavy Lélekadósság blunts the healing the warlock receives. This is a
     * combat consequence and the only thing the debt ever touches.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isWarlock(victim)
                || event.getDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        final WarlockCombatState state = state(playerId);
        final int ceiling = debtCeiling(playerId);
        if (state.debt() < Math.max(1, ceiling) / 2) return;
        final double penalty = Math.max(0.0D, Math.min(50.0D, config.getDouble(
                "classes.warlock.pact.debt-taken-penalty-percent", 10.0D)));
        event.setDamage(event.getDamage() * (1.0D + penalty / 100.0D));
    }

    public Component hudSuffix(final Player player) {
        if (!isWarlock(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final WarlockCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final int ceiling = debtCeiling(playerId);
        Component suffix = Component.text("  • Lélekadósság " + state.debt() + "/" + ceiling,
                state.debt() >= ceiling ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE);
        switch (activeSpec(playerId)) {
            case "affliction" -> suffix = suffix.append(Component.text("  • Átok "
                            + state.activeCurses(now) + "/3"
                            + (state.threadTarget(now) != null ? " • Lélekfonal" : ""),
                    NamedTextColor.DARK_PURPLE));
            case "destruction" -> suffix = suffix.append(Component.text("  • Parázs "
                            + state.embers() + "/" + emberMaximum(playerId)
                            + (state.isOverheated(now) ? " • Túlhevült!" : ""),
                    state.isOverheated(now) ? NamedTextColor.RED : NamedTextColor.GOLD));
            case "demonologist" -> suffix = suffix.append(Component.text("  • Démonok "
                    + state.rosterSize() + "/3", NamedTextColor.DARK_PURPLE));
            default -> { }
        }
        return suffix;
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.WARLOCK) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!WARLOCK_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final WarlockCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final WarlockCombatState state = states.remove(playerId);
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

    /** The three concrete curses of the Átokgrimoár. */
    private String curseOf(final String spellId) {
        for (final String curse : new String[]{"gyotrelem", "romlas", "aszaly"}) {
            if (configSet("classes.warlock.affliction." + curse + "-spells").contains(spellId)) {
                return curse;
            }
        }
        return null;
    }

    /** The three concrete demon kinds of the roster. */
    private String demonKindOf(final String spellId) {
        for (final String kind : new String[]{"imp", "szolga", "infernal"}) {
            if (configSet("classes.warlock.demonologist." + kind + "-spells").contains(spellId)) {
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

    private WarlockCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new WarlockCombatState());
    }

    private boolean isWarlock(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.WARLOCK;
    }

    private boolean isWarlock(final UUID playerId) {
        final var profile = specs.profileGateway().currentProfile(playerId).orElse(null);
        return profile != null && "warlock".equals(profile.primaryClassId());
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
                "classes.warlock.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> pactSpells() {
        return configSet("classes.warlock.pact.pact-spells");
    }

    private Set<String> repaySpells() {
        return configSet("classes.warlock.pact.repay-spells");
    }

    private Set<String> drainSpells() {
        return configSet("classes.warlock.affliction.drain-spells");
    }

    private Set<String> emberSpells() {
        return configSet("classes.warlock.destruction.ember-spells");
    }

    private Set<String> burstSpells() {
        return configSet("classes.warlock.destruction.burst-spells");
    }

    private Set<String> demonicSpells() {
        return configSet("classes.warlock.demonologist.demonic-spells");
    }

    private Set<String> dismissSpells() {
        return configSet("classes.warlock.demonologist.dismiss-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private int debtCeiling(final UUID playerId) {
        int ceiling = Math.max(1, config.getInt("classes.warlock.pact.debt-ceiling", 100));
        if ("teher_biras".equals(doctrine(playerId, 50))) {
            ceiling += Math.max(0, config.getInt("classes.warlock.pact.bearing-extra", 20));
        }
        return ceiling;
    }

    private long curseDurationMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.warlock.affliction.curse-millis", 12000L));
        if ("tarto_atok".equals(doctrine(playerId, 30))) {
            millis += Math.max(0L, config.getLong(
                    "classes.warlock.affliction.lasting-extra-millis", 4000L));
        }
        return millis;
    }

    private long threadWindowMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.warlock.affliction.thread-millis", 8000L));
        if ("eros_fonal".equals(doctrine(playerId, 40))) {
            millis += Math.max(0L, config.getLong(
                    "classes.warlock.affliction.strong-extra-millis", 3000L));
        }
        return millis;
    }

    private int emberMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.warlock.destruction.ember-maximum", 4));
        if ("mely_zsarat".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.warlock.destruction.deep-extra-ember", 1));
        }
        return maximum;
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
