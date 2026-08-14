package hu.taliann.icesmp.deathknight;

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
 * Concrete Halállovag vertical-slice runtime.
 *
 * <p>The class core is the Rúnakör: Vér and Fagy runes recharge lazily, the Halál rune only ever
 * appears where the knight transmutes a full natural rune into it, and every declared spender is
 * refused without its rune. Vérlovag cashes a fixed-size recent-damage ring into either a heal or
 * a shield — the chosen spell IS the decision. Fagylovag stacks Fagyjelek that a strike consumes
 * partially and Zúzás consumes whole. Szentségtelen (DARK, on the existing seal system) builds
 * Dögvész that bursts through the existing summon/minion spells and mutates its ghoul one bounded
 * stage per burst — deliberately separate from the Nekromanta Soulforge. Durable state remains
 * Profile v2.</p>
 */
public final class DeathKnightGameplayService implements Listener, PlayerStateCleanup {

    private static final Set<String> DK_SPECS = Set.of("blood", "frost", "unholy");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final CatalystItemFactory soulbondFactory;
    private final MessageManager messages;

    private final Map<UUID, DeathKnightCombatState> states = new ConcurrentHashMap<>();

    private volatile ResourceManager combatTracker;

    public DeathKnightGameplayService(final JavaPlugin plugin,
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
        if (player == null || jobs.getPrimaryJob(player) != JobType.DEATH_KNIGHT) {
            return List.copyOf(unlocked);
        }
        final int maximum = Math.max(1, Math.min(7,
                config.getInt("classes.death_knight.active-kit.maximum", 7)));
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
                "classes.death_knight.active-kit." + activeSpec)) {
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

    /** The Rúnakör gate: a declared spender without its rune is refused, never silently free. */
    public boolean beforeCast(final Player player, final Spell spell) {
        if (!isDeathKnight(player) || spell == null) return true;
        final UUID playerId = player.getUniqueId();
        final DeathKnightCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        state.prime(naturalCapacity(playerId), now);
        final DeathKnightCombatState.Rune required = runeCost(spell.getId().toLowerCase(Locale.ROOT));
        if (required == null) return true;
        if (state.runes(required, naturalCapacity(playerId), now, rechargeMillis(playerId)) > 0) {
            return true;
        }
        player.sendActionBar(messages.getMessage("deathknight.rune.missing",
                "<red>Nincs {rune} rúnád — a Rúnakör üres.</red>",
                Map.of("rune", runeName(required))));
        return false;
    }

    /** Pure peek: the consumed Fagyjelek and the fed Dögvész empower their finishers. */
    public double castPowerBonusPercent(final Player player, final Spell spell) {
        if (!isDeathKnight(player) || spell == null) return 0.0D;
        final UUID playerId = player.getUniqueId();
        final DeathKnightCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        double bonus = 0.0D;
        final String spec = activeSpec(playerId);
        if ("frost".equals(spec) && crushSpells().contains(spellId)) {
            double perMark = Math.max(0.0D, config.getDouble(
                    "classes.death_knight.frost.per-mark-percent", 4.0D));
            if ("toresvonal".equals(doctrine(playerId, 40))) {
                perMark += config.getDouble("classes.death_knight.frost.fracture-extra-percent", 1.5D);
            }
            bonus += state.frostMarks() * perMark;
        }
        if ("unholy".equals(spec) && burstSpells().contains(spellId)) {
            double perPlague = Math.max(0.0D, config.getDouble(
                    "classes.death_knight.unholy.per-plague-percent", 3.0D));
            if ("pusztito_kor".equals(doctrine(playerId, 40))) {
                perPlague += config.getDouble("classes.death_knight.unholy.ravage-extra-percent", 1.5D);
            }
            bonus += state.plague() * perPlague;
            bonus += state.mutation() * Math.max(0.0D, config.getDouble(
                    "classes.death_knight.unholy.per-mutation-percent", 4.0D));
        }
        final double cap = Math.max(0.0D,
                config.getDouble("classes.death_knight.max-power-bonus-percent", 40.0D));
        return Math.min(cap, bonus);
    }

    public void afterCast(final Player player, final Spell spell,
                          final boolean resourceSpent, final int spentAmount) {
        if (!isDeathKnight(player) || spell == null) return;
        final UUID playerId = player.getUniqueId();
        final DeathKnightCombatState state = state(playerId);
        final String spellId = spell.getId().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        final DeathKnightCombatState.Rune required = runeCost(spellId);
        if (required != null) {
            state.spendRune(required, naturalCapacity(playerId), now, rechargeMillis(playerId));
        }
        if (transmuteSpells().contains(spellId)
                && state.transmuteToDeath(naturalCapacity(playerId), deathCapacity(playerId),
                now, rechargeMillis(playerId))) {
            player.sendActionBar(messages.getMessage("deathknight.rune.transmuted",
                    "<dark_red>Egy rúna Halál-rúnává hűlt.</dark_red>"));
        }

        switch (activeSpec(playerId)) {
            case "blood" -> handleBloodCast(player, state, spellId, now);
            case "frost" -> handleFrostCast(player, state, spellId);
            case "unholy" -> handleUnholyCast(player, state, spellId);
            default -> { }
        }
    }

    // ===== Vérlovag =====

    private void handleBloodCast(final Player player, final DeathKnightCombatState state,
                                 final String spellId, final long now) {
        final UUID playerId = player.getUniqueId();
        final boolean heals = memoryHealSpells().contains(spellId);
        final boolean shields = memoryShieldSpells().contains(spellId);
        if (!heals && !shields) return;
        final double remembered = state.consumeMemory(now, memoryWindowMillis(playerId));
        if (remembered <= 0.0D) return;
        double percent = Math.max(0.0D, Math.min(100.0D, config.getDouble(
                heals ? "classes.death_knight.blood.heal-percent"
                        : "classes.death_knight.blood.shield-percent", 30.0D)));
        if (heals && "suru_ver".equals(doctrine(playerId, 30))) {
            percent += config.getDouble("classes.death_knight.blood.thick-extra-percent", 8.0D);
        }
        final double converted = remembered * Math.min(100.0D, percent) / 100.0D;
        if (heals) {
            healPlayer(player, converted);
        } else {
            final int amplifier = Math.max(0, Math.min(4,
                    (int) Math.floor(converted / Math.max(1.0D, config.getDouble(
                            "classes.death_knight.blood.shield-step", 4.0D)))));
            int ticks = config.getInt("classes.death_knight.blood.shield-ticks", 120);
            if ("vertenger".equals(doctrine(playerId, 50))) {
                ticks += Math.max(0, config.getInt("classes.death_knight.blood.sea-extra-ticks", 60));
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    ticks, amplifier, false, true, true));
        }
        if (isInCombat(playerId)) {
            specs.contributeClassMastery(player, JobType.DEATH_KNIGHT,
                    config.getInt("classes.death_knight.mastery.memory-xp", 5));
        }
        player.sendActionBar(messages.getMessage(
                heals ? "deathknight.memory.healed" : "deathknight.memory.shielded",
                heals ? "<dark_red>🩸 A Vér Emlékezete gyógyítássá vált.</dark_red>"
                        : "<dark_red>🩸 A Vér Emlékezete pajzzsá dermedt.</dark_red>"));
    }

    // ===== Fagylovag =====

    private void handleFrostCast(final Player player, final DeathKnightCombatState state,
                                 final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (crushSpells().contains(spellId)) {
            final int consumed = state.consumeAllFrostMarks();
            if (consumed <= 0) return;
            double healPerMark = Math.max(0.0D, config.getDouble(
                    "classes.death_knight.frost.crush-heal-per-mark", 0.5D));
            if ("zuzmara".equals(doctrine(playerId, 40))) {
                healPerMark += config.getDouble("classes.death_knight.frost.rime-extra-heal", 0.5D);
            }
            healPlayer(player, consumed * healPerMark);
            if ("sindragosa_lehelete".equals(doctrine(playerId, 50))) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        config.getInt("classes.death_knight.frost.breath-resist-ticks", 60),
                        0, false, true, true));
            }
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DEATH_KNIGHT,
                        config.getInt("classes.death_knight.mastery.crush-xp", 5));
            }
            player.sendActionBar(messages.getMessage("deathknight.frost.crushed",
                    "<aqua>❄ Zúzás: {count} Fagyjel tört szét.</aqua>",
                    Map.of("count", Integer.toString(consumed))));
            return;
        }
        if (partialCrushSpells().contains(spellId)) {
            int partial = Math.max(1, config.getInt(
                    "classes.death_knight.frost.partial-consume", 2));
            if ("jeges_szel".equals(doctrine(playerId, 30))) partial++;
            final int consumed = state.consumeFrostMarks(partial);
            if (consumed > 0) {
                player.sendActionBar(messages.getMessage("deathknight.frost.partial",
                        "<aqua>{count} Fagyjel hasadt le.</aqua>",
                        Map.of("count", Integer.toString(consumed))));
            }
            return;
        }
        if (!markSpells().contains(spellId)) return;
        int gain = config.getInt("classes.death_knight.frost.mark-per-cast", 1);
        if ("dermeszto_kez".equals(doctrine(playerId, 30))) gain++;
        state.addFrostMarks(gain, markMaximum(playerId));
    }

    // ===== Szentségtelen (DARK) =====

    private void handleUnholyCast(final Player player, final DeathKnightCombatState state,
                                  final String spellId) {
        final UUID playerId = player.getUniqueId();
        if (burstSpells().contains(spellId)) {
            final int burst = state.burstPlague();
            if (burst <= 0) return;
            if ("orok_jarvany".equals(doctrine(playerId, 50))) {
                state.addPlague(Math.max(0, config.getInt(
                        "classes.death_knight.unholy.eternal-retention", 1)), plagueMaximum(playerId));
            }
            if ("savas_ver".equals(doctrine(playerId, 30))) {
                healPlayer(player, burst * Math.max(0.0D, config.getDouble(
                        "classes.death_knight.unholy.acid-heal-per-plague", 0.5D)));
            }
            final int stage = state.advanceMutation(mutationMaximum(playerId));
            if (isInCombat(playerId)) {
                specs.contributeClassMastery(player, JobType.DEATH_KNIGHT,
                        config.getInt("classes.death_knight.mastery.burst-xp", 6));
            }
            player.sendActionBar(messages.getMessage("deathknight.plague.burst",
                    "<dark_green>☣ {count} Dögvész robbant — a ghúl {stage}. fokozatra mutálódott.</dark_green>",
                    Map.of("count", Integer.toString(burst), "stage", Integer.toString(stage))));
            return;
        }
        if (!plagueSpells().contains(spellId)) return;
        int gain = config.getInt("classes.death_knight.unholy.plague-per-cast", 1);
        if ("terjedo_kor".equals(doctrine(playerId, 30))) gain++;
        state.addPlague(gain, plagueMaximum(playerId));
    }

    /** The Vér Emlékezete only ever records into its fixed-size ring. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIncomingDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isDeathKnight(victim)
                || event.getFinalDamage() <= 0.0D) return;
        final UUID playerId = victim.getUniqueId();
        if (!"blood".equals(activeSpec(playerId))) return;
        state(playerId).rememberDamage(event.getFinalDamage(), System.currentTimeMillis());
    }

    public Component hudSuffix(final Player player) {
        if (!isDeathKnight(player)) return Component.empty();
        final UUID playerId = player.getUniqueId();
        final DeathKnightCombatState state = state(playerId);
        final long now = System.currentTimeMillis();
        final int capacity = naturalCapacity(playerId);
        final long recharge = rechargeMillis(playerId);
        Component suffix = Component.text("  • Rúnák V"
                        + state.runes(DeathKnightCombatState.Rune.VER, capacity, now, recharge)
                        + " F" + state.runes(DeathKnightCombatState.Rune.FAGY, capacity, now, recharge)
                        + " H" + state.runes(DeathKnightCombatState.Rune.HALAL, capacity, now, recharge),
                NamedTextColor.DARK_RED);
        switch (activeSpec(playerId)) {
            case "blood" -> suffix = suffix.append(Component.text(String.format(
                            "  • Emlékezet %.1f", state.recentDamage(now, memoryWindowMillis(playerId))),
                    NamedTextColor.RED));
            case "frost" -> suffix = suffix.append(Component.text("  • Fagyjel "
                    + state.frostMarks() + "/" + markMaximum(playerId), NamedTextColor.AQUA));
            case "unholy" -> suffix = suffix.append(Component.text("  • Dögvész "
                            + state.plague() + " • Ghúl " + state.mutation() + ". fokozat",
                    NamedTextColor.DARK_GREEN));
            default -> { }
        }
        return suffix;
    }

    /** Owner-thread, structured HUD projection with individual rune slots. */
    public hu.taliann.icesmp.classspec.integration.ClassHudMechanics hudState(final Player player) {
        if (!isDeathKnight(player)) return hu.taliann.icesmp.classspec.integration.ClassHudMechanics.empty();
        final UUID id = player.getUniqueId();
        final DeathKnightCombatState combat = state(id);
        final long now = System.currentTimeMillis();
        final int naturalMax = naturalCapacity(id);
        final int deathMax = deathCapacity(id);
        final long recharge = rechargeMillis(id);
        final int blood = combat.runes(DeathKnightCombatState.Rune.VER, naturalMax, now, recharge);
        final int frost = combat.runes(DeathKnightCombatState.Rune.FAGY, naturalMax, now, recharge);
        final int death = combat.runes(DeathKnightCombatState.Rune.HALAL, naturalMax, now, recharge);
        final int total = blood + frost + death;
        final int totalMax = naturalMax * 2 + deathMax;
        final var primary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                "rune_wheel", "Rúnakör", "Rúnák",
                total, totalMax, total == totalMax ? "ready" : "active");
        var secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.text("", "", "", "");
        String stateText = "";
        String proc = "";
        switch (activeSpec(id)) {
            case "blood" -> {
                final double memory = combat.recentDamage(now, memoryWindowMillis(id));
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "blood_memory", "Emlékezet", String.format(Locale.ROOT, "Emlékezet %.1f", memory),
                        memory, 100, memory > 0 ? "stored" : "empty");
            }
            case "frost" -> {
                final int marks = combat.frostMarks();
                final int maximum = markMaximum(id);
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "frost_marks", "Fagyjel", "Fagyjel " + marks + "/" + maximum,
                        marks, maximum, marks >= maximum ? "ready" : "building");
                if (marks >= maximum) proc = "Zúzás kész";
            }
            case "unholy" -> {
                secondary = hu.taliann.icesmp.classspec.integration.ClassHudMetric.value(
                        "plague", "Dögvész", "Dögvész " + combat.plague(),
                        combat.plague(), 100, "active");
                stateText = "Ghúl " + combat.mutation() + ". fokozat";
            }
            default -> { }
        }
        final java.util.ArrayList<hu.taliann.icesmp.classspec.integration.ClassHudSlot> slots =
                new java.util.ArrayList<>();
        addRuneSlots(slots, "blood", "Vér", blood, naturalMax,
                combat.rechargePercent(DeathKnightCombatState.Rune.VER, naturalMax, now, recharge), true);
        addRuneSlots(slots, "frost", "Fagy", frost, naturalMax,
                combat.rechargePercent(DeathKnightCombatState.Rune.FAGY, naturalMax, now, recharge), true);
        addRuneSlots(slots, "death", "Halál", death, deathMax, death > 0 ? 100 : 0, false);
        return new hu.taliann.icesmp.classspec.integration.ClassHudMechanics(
                primary, secondary, stateText, proc, total, totalMax,
                java.util.List.of(primary, secondary), slots);
    }

    private static void addRuneSlots(
            final java.util.List<hu.taliann.icesmp.classspec.integration.ClassHudSlot> slots,
            final String kind, final String label, final int ready, final int capacity,
            final int rechargeProgress, final boolean regenerates) {
        for (int index = 1; index <= capacity; index++) {
            final String state;
            final int progress;
            if (index <= ready) {
                state = "ready";
                progress = 100;
            } else if (regenerates && index == ready + 1) {
                state = "regenerating";
                progress = rechargeProgress;
            } else {
                state = "spent";
                progress = 0;
            }
            slots.add(new hu.taliann.icesmp.classspec.integration.ClassHudSlot(
                    "rune_" + kind + "_" + index, kind, state, progress, label));
        }
    }

    public void reconcileProfile(final Player player) {
        if (player == null) return;
        if (jobs.getPrimaryJob(player) != JobType.DEATH_KNIGHT) {
            clearPlayerState(player.getUniqueId());
            return;
        }
        if (!DK_SPECS.contains(activeSpec(player.getUniqueId()))) {
            clearSpecializationState(player.getUniqueId());
        }
    }

    public void clearSpecializationState(final UUID playerId) {
        if (playerId == null) return;
        final DeathKnightCombatState state = states.get(playerId);
        if (state != null) state.clearSpecializationState();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) return;
        final DeathKnightCombatState state = states.remove(playerId);
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

    /** Declared per-spell rune cost; anything unlisted needs no rune. */
    private DeathKnightCombatState.Rune runeCost(final String spellId) {
        if (configSet("classes.death_knight.runes.blood-spells").contains(spellId)) {
            return DeathKnightCombatState.Rune.VER;
        }
        if (configSet("classes.death_knight.runes.frost-spells").contains(spellId)) {
            return DeathKnightCombatState.Rune.FAGY;
        }
        if (configSet("classes.death_knight.runes.death-spells").contains(spellId)) {
            return DeathKnightCombatState.Rune.HALAL;
        }
        return null;
    }

    private Set<String> transmuteSpells() {
        return configSet("classes.death_knight.runes.transmute-spells");
    }

    private static String runeName(final DeathKnightCombatState.Rune rune) {
        return switch (rune) {
            case VER -> "Vér";
            case FAGY -> "Fagy";
            case HALAL -> "Halál";
        };
    }

    private static void healPlayer(final Player target, final double amount) {
        final double maxHealth = maxHealth(target);
        final double after = Math.min(maxHealth, target.getHealth() + Math.max(0.0D, amount));
        if (after > target.getHealth()) target.setHealth(after);
    }

    private DeathKnightCombatState state(final UUID id) {
        return states.computeIfAbsent(id, ignored -> new DeathKnightCombatState());
    }

    private boolean isDeathKnight(final Player player) {
        return player != null && jobs.getPrimaryJob(player) == JobType.DEATH_KNIGHT;
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
                "classes.death_knight.mastery.combat-window-seconds", 10L)) * 1000L;
        return tracker != null && tracker.isInCombat(playerId, windowMillis);
    }

    private Set<String> memoryHealSpells() {
        return configSet("classes.death_knight.blood.heal-spells");
    }

    private Set<String> memoryShieldSpells() {
        return configSet("classes.death_knight.blood.shield-spells");
    }

    private Set<String> markSpells() {
        return configSet("classes.death_knight.frost.mark-spells");
    }

    private Set<String> partialCrushSpells() {
        return configSet("classes.death_knight.frost.partial-spells");
    }

    private Set<String> crushSpells() {
        return configSet("classes.death_knight.frost.crush-spells");
    }

    private Set<String> plagueSpells() {
        return configSet("classes.death_knight.unholy.plague-spells");
    }

    private Set<String> burstSpells() {
        return configSet("classes.death_knight.unholy.burst-spells");
    }

    private Set<String> configSet(final String key) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String raw : config.getStringList(key)) result.add(normalize(raw));
        return result;
    }

    private int naturalCapacity(final UUID playerId) {
        int capacity = Math.max(1, config.getInt("classes.death_knight.runes.natural-capacity", 2));
        if ("melyebb_rovas".equals(doctrine(playerId, 40))) {
            capacity += Math.max(0, config.getInt("classes.death_knight.runes.deep-extra", 1));
        }
        return capacity;
    }

    private int deathCapacity(final UUID playerId) {
        int capacity = Math.max(1, config.getInt("classes.death_knight.runes.death-capacity", 1));
        if ("halal_jegye".equals(doctrine(playerId, 50))) {
            capacity += Math.max(0, config.getInt("classes.death_knight.runes.death-extra", 1));
        }
        return capacity;
    }

    private long rechargeMillis(final UUID playerId) {
        long millis = Math.max(500L, config.getLong(
                "classes.death_knight.runes.recharge-millis", 6000L));
        if ("gyors_verkor".equals(doctrine(playerId, 40))) {
            millis -= Math.max(0L, config.getLong(
                    "classes.death_knight.runes.quick-reduction-millis", 1500L));
        }
        return Math.max(500L, millis);
    }

    private long memoryWindowMillis(final UUID playerId) {
        long millis = Math.max(1000L, config.getLong(
                "classes.death_knight.blood.memory-window-millis", 8000L));
        if ("hosszu_emlekezet".equals(doctrine(playerId, 30))) {
            millis += Math.max(0L, config.getLong(
                    "classes.death_knight.blood.long-memory-extra-millis", 4000L));
        }
        return millis;
    }

    private int markMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.death_knight.frost.mark-maximum", 5));
        if ("jegpancel".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.death_knight.frost.armor-extra-mark", 2));
        }
        return maximum;
    }

    private int plagueMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.death_knight.unholy.plague-maximum", 6));
        if ("dus_kor".equals(doctrine(playerId, 40))) {
            maximum += Math.max(0, config.getInt("classes.death_knight.unholy.rich-extra-plague", 2));
        }
        return maximum;
    }

    private int mutationMaximum(final UUID playerId) {
        int maximum = Math.max(1, config.getInt("classes.death_knight.unholy.mutation-maximum", 3));
        if ("torz_ghul".equals(doctrine(playerId, 50))) {
            maximum += Math.max(0, config.getInt("classes.death_knight.unholy.twisted-extra-stage", 1));
        }
        return maximum;
    }

    private static double maxHealth(final LivingEntity entity) {
        final var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
