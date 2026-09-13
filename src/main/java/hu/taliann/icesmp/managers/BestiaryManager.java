package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.integrity.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.PendingReward;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** PlayerProfile-backed bestiary and milestone receipt authority. */
public final class BestiaryManager {

    public enum Category { MOBS, RECIPES, TERRITORIES, BOSSES }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final PlayerProfileAchievementStore store = new PlayerProfileAchievementStore();
    private volatile hu.taliann.icesmp.pve.MobTemplateRegistry mobTemplates;
    private volatile AchievementManager rewardDelivery;
    private record Milestone(PendingReward reward, boolean broadcast) { }

    public void setRewardDelivery(final AchievementManager rewardDelivery) {
        this.rewardDelivery = java.util.Objects.requireNonNull(rewardDelivery);
    }

    public BestiaryManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final CurrencyManager currencyManager,
                           final FactionManager factionManager,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    public JavaPlugin plugin() { return plugin; }
    public boolean isEnabled() { return configManager.getBoolean("bestiary.enabled", true); }

    public void setMobTemplateRegistry(
            final hu.taliann.icesmp.pve.MobTemplateRegistry mobTemplates) {
        this.mobTemplates = mobTemplates;
    }

    public Map<String, hu.taliann.icesmp.pve.MobTemplate> mobTemplates() {
        final var registry = mobTemplates;
        return registry == null ? Map.of() : registry.all();
    }

    public hu.taliann.icesmp.pve.MobTemplate mobTemplate(final String id) {
        return mobTemplates().get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    public int knownMobEntryCount() {
        final java.util.HashSet<String> ids = new java.util.HashSet<>();
        knownMonsterTypes().forEach(type -> ids.add(type.name().toLowerCase(Locale.ROOT)));
        ids.addAll(mobTemplates().keySet());
        return ids.size();
    }

    public Set<String> entries(final Player player, final Category category) {
        return player == null ? Set.of() : entries(player.getUniqueId(), category);
    }

    public Set<String> entries(final java.util.UUID playerId, final Category category) {
        if (playerId == null || category == null) return Set.of();
        return store.bestiaryEntries(playerId, category.name().toLowerCase(Locale.ROOT));
    }

    public int count(final Player player, final Category category) {
        return entries(player, category).size();
    }

    public int count(final java.util.UUID playerId, final Category category) {
        return entries(playerId, category).size();
    }

    /**
     * Records a first entry with section CAS. Callers may ignore the returned stage; milestone
     * reward and UI effects are scheduled only after the durable commit succeeds.
     */
    public CompletionStage<Boolean> record(final Player player, final Category category, final String id) {
        if (player == null) return CompletableFuture.completedFuture(false);
        final RewardContext reward;
        try { reward = BukkitRewardSources.entity(RewardChannel.BESTIARY, player).forRecipient(player.getUniqueId()); }
        catch (RuntimeException | LinkageError unavailable) { return CompletableFuture.completedFuture(false); }
        return record(player, category, id, reward);
    }

    public CompletionStage<Boolean> record(final Player player, final Category category, final String id, final RewardContext reward) {
        if (!isEnabled() || player == null || category == null || id == null || id.isBlank()
                || !Bukkit.isOwnedByCurrentRegion(player)) return CompletableFuture.completedFuture(false);
        final UUID playerId = player.getUniqueId();
        java.util.Objects.requireNonNull(reward).require(RewardChannel.BESTIARY, playerId);
        final Map<Integer, Milestone> configured;
        try { configured = milestones(player, category); }
        catch (RuntimeException invalid) { return CompletableFuture.failedFuture(invalid); }
        final Map<Integer, PendingReward> payloads = new java.util.LinkedHashMap<>();
        configured.forEach((threshold, value) -> payloads.put(threshold, value.reward()));
        return store.recordBestiaryWithRewards(playerId, category.name().toLowerCase(Locale.ROOT),
                        id.toLowerCase(Locale.ROOT), payloads, reward)
                .thenApply(result -> {
                    final AchievementManager delivery = rewardDelivery;
                    // If the delivery adapter is unavailable, the same durable pending receipt is
                    // picked up by AchievementManager recovery. No natural history is fabricated.
                    if (delivery != null) for (final PendingReward pending : result.pending()) {
                        final int size = result.record().categoryCount();
                        final boolean broadcast = configured.get(size).broadcast();
                        delivery.settlePendingReward(playerId, pending).whenComplete((settled, failure) -> {
                            if (failure != null) {
                                plugin.getLogger().warning("Bestiary reward remains pending for " + playerId + '/' + pending.receiptId());
                            } else if (Boolean.TRUE.equals(settled)) {
                                runOnOwner(playerId, owned -> announceMilestone(owned, category, size, pending, broadcast));
                            }
                        });
                    }
                    return result.record().created();
                }).exceptionally(failure -> {
                    Throwable cause = failure;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (!(cause instanceof RewardEligibilityDeniedException))
                        plugin.getLogger().severe("PlayerProfile bestiary commit failed for " + playerId + "/" + category + ": " + cause.getClass().getSimpleName());
                    return false;
                });
    }

    private Map<Integer, Milestone> milestones(final Player player, final Category category) {
        final Map<Integer, Milestone> result = new java.util.LinkedHashMap<>();
        final String base = "bestiary.milestones." + category.name().toLowerCase(Locale.ROOT);
        final var rows = configManager.getStringList(base);
        if (rows.size() > 128) throw new IllegalArgumentException("Too many bestiary milestones");
        for (final String row : rows) {
            final String[] parts = row.split(":");
            try {
                if (parts.length < 2) continue;
                final int threshold = Integer.parseInt(parts[0].trim());
                if (threshold < 1) continue;
                final String receipt = "bestiary:" + category.name().toLowerCase(Locale.ROOT) + ':' + threshold;
                result.putIfAbsent(threshold, new Milestone(payload(player, receipt, Long.parseLong(parts[1].trim())),
                        parts.length >= 3 && "broadcast".equalsIgnoreCase(parts[2].trim())));
            } catch (NumberFormatException malformed) { /* Invalid native config rows remain unavailable. */ }
        }
        return Map.copyOf(result);
    }

    private void runOnOwner(final UUID playerId, final java.util.function.Consumer<Player> action) {
        final Player handle = Bukkit.getPlayer(playerId);
        if (handle == null) return;
        handle.getScheduler().run(plugin, task -> {
            final Player owned = Bukkit.getPlayer(playerId);
            if (owned != null && Bukkit.isOwnedByCurrentRegion(owned) && owned.isOnline()) action.accept(owned);
        }, null);
    }

    private void announceMilestone(final Player player, final Category category, final int size,
                                   final PendingReward pending, final boolean broadcast) {
        final long reward = pending.amount();
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        player.sendMessage(messageManager.getMessage("bestiary-milestone",
                "<gold>📜 Bestiárium-mérföldkő: <white>{count}</white> bejegyzés a(z) <white>{category}</white> lajstromban! Jutalom: <white>{reward} veret</white> a kezedbe.</gold>",
                Map.of("count", String.valueOf(size), "category", categoryName(category), "reward", String.valueOf(reward))));
        if (broadcast) Bukkit.getServer().broadcast(messageManager.getMessage("bestiary-milestone-broadcast",
                "<gold>📜 <white>{player}</white> lajstroma <white>{count}</white> bejegyzésre hízott a(z) <white>{category}</white> fejezetben — a krónikások főt hajtanak!</gold>",
                Map.of("player", player.getName(), "count", String.valueOf(size), "category", categoryName(category))));
    }

    private PendingReward payload(final Player player, final String receipt, final long reward) {
        if (reward <= 0L) {
            return new PendingReward(receipt, PlayerProfileAchievementStore.RewardKind.NONE, 0L, "");
        }
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(player.getUniqueId()));
        return new PendingReward(receipt, PlayerProfileAchievementStore.RewardKind.CURRENCY,
                reward, currency.name().toLowerCase(Locale.ROOT));
    }

    public static String categoryName(final Category category) {
        return switch (category) {
            case MOBS -> "Szörnyek";
            case RECIPES -> "Receptek";
            case TERRITORIES -> "Territóriumok";
            case BOSSES -> "Világbossok";
        };
    }

    /**
     * A lajstrom-kulcs kánonja: ritka variánsnál `<variáns>_<típus>` — a kill-oldali rögzítés
     * (BestiaryListener, StatsCombatListener) és a GUI-megjelenítés ugyanezt a formát használja.
     */
    public static String entryId(final org.bukkit.entity.Entity entity) {
        final String template = MobScalingManager.templateIdOf(entity);
        if (template != null && !template.isBlank()) return template.toLowerCase(Locale.ROOT);
        final String variant = MobScalingManager.rareVariantOf(entity);
        return ((variant == null ? "" : variant + "_") + entity.getType().name())
                .toLowerCase(Locale.ROOT);
    }

    /** Az ismert szörny-fajok nevezője: minden Monster-besorolású vanilla típus. */
    public static java.util.List<org.bukkit.entity.EntityType> knownMonsterTypes() {
        final java.util.ArrayList<org.bukkit.entity.EntityType> types = new java.util.ArrayList<>();
        for (final org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
            final Class<?> entityClass = type.getEntityClass();
            if (entityClass != null && org.bukkit.entity.Monster.class.isAssignableFrom(entityClass)) {
                types.add(type);
            }
        }
        types.sort(java.util.Comparator.comparing(Enum::name));
        return types;
    }

    /** A tudás-fokozat küszöbei (élő-config); a lista i. eleme az (i+1). fokozat kill-igénye. */
    public java.util.List<Integer> knowledgeTiers() {
        final java.util.ArrayList<Integer> tiers = new java.util.ArrayList<>();
        for (final String raw : configManager.getStringList("bestiary.knowledge-tiers")) {
            try {
                tiers.add(Integer.parseInt(raw.trim()));
            } catch (final NumberFormatException ignored) {
                // hibás sor kimarad; a fallback lent kezeli az üres listát
            }
        }
        if (tiers.isEmpty()) {
            tiers.add(1);
            tiers.add(10);
            tiers.add(50);
        }
        tiers.sort(Integer::compareTo);
        return tiers;
    }

    public int knowledgeTier(final long speciesKills) {
        int tier = 0;
        for (final int threshold : knowledgeTiers()) {
            if (speciesKills >= threshold) tier++;
        }
        return tier;
    }

    /** Kódex-jegyzet a bejegyzéshez (config-katalógus, kódex-konzisztens szövegekkel). */
    public String codexNote(final String entryId) {
        return configManager.getString("bestiary.codex-notes." + entryId, "");
    }
}
