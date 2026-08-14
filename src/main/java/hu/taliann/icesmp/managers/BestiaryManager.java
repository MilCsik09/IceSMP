package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
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
    public CompletionStage<Boolean> record(final Player player, final Category category,
                                           final String id) {
        if (!isEnabled() || player == null || category == null || id == null || id.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return store.recordBestiary(player.getUniqueId(),
                        category.name().toLowerCase(Locale.ROOT),
                        id.toLowerCase(Locale.ROOT))
                .thenApply(result -> {
                    if (result.created()) {
                        player.getScheduler().run(plugin,
                                task -> checkMilestone(player, category, result.categoryCount()), null);
                    }
                    return result.created();
                }).exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile bestiary commit failed for "
                            + player.getUniqueId() + "/" + category + "/" + id + ": "
                            + failure.getMessage());
                    return false;
                });
    }

    /** Milestone payout is guarded by a durable per-threshold receipt. */
    private void checkMilestone(final Player player, final Category category, final int size) {
        final String base = "bestiary.milestones." + category.name().toLowerCase(Locale.ROOT);
        for (final String row : configManager.getStringList(base)) {
            final String[] parts = row.split(":");
            final int threshold;
            final long reward;
            try {
                if (parts.length < 2) continue;
                threshold = Integer.parseInt(parts[0].trim());
                reward = Long.parseLong(parts[1].trim());
            } catch (final NumberFormatException ignored) {
                continue;
            }
            if (threshold != size) continue;
            final String receipt = "bestiary:" + category.name().toLowerCase(Locale.ROOT)
                    + ':' + threshold;
            // The payout currency is part of the durable reservation identity, so a faction change
            // between reserving and paying cannot alter what was promised.
            reserve(player, receipt, reward)
                    .whenComplete((reservation, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().severe("PlayerProfile bestiary reward receipt failed for "
                                    + player.getUniqueId() + '/' + receipt + ": "
                                    + failure.getMessage());
                            return;
                        }
                        if (reservation == null) return;
                        final PendingReward pending = reservation.reward();
                        player.getScheduler().run(plugin, task ->
                                deliverMilestone(player, category, size, pending, parts), null);
                    });
        }
    }

    /**
     * Reservation-first payout. An already reserved but undelivered milestone stays PENDING and is
     * replayed on reconnect by the shared achievement recovery loop, so a logout can no longer make
     * the reward disappear. Both paths credit under the SAME operation id, so a replay is a no-op.
     */
    private void deliverMilestone(final Player player, final Category category,
                                  final int size, final PendingReward pending,
                                  final String[] parts) {
        final long reward = pending.amount();
        if (!player.isOnline()) {
            plugin.getLogger().warning("Bestiary reward stays pending until reconnect: "
                    + player.getUniqueId() + '/' + category + '/' + size);
            return;
        }
        if (pending.kind() == PlayerProfileAchievementStore.RewardKind.CURRENCY && reward > 0L) {
            final CurrencyType currency = CurrencyType.fromInput(pending.currencyId());
            if (currency == null) {
                plugin.getLogger().severe("Bestiary reward has an unknown reserved currency: "
                        + pending.currencyId());
                return;
            }
            currencyManager.creditOnceDurably(player.getUniqueId(), currency, reward,
                    "achievement-currency:" + pending.receiptId());
        }
        store.settleReward(player.getUniqueId(), pending)
                .exceptionally(failure -> {
                    plugin.getLogger().warning("Bestiary reward remains pending for "
                            + player.getUniqueId() + '/' + pending.receiptId() + ": "
                            + failure.getMessage());
                    return false;
                });
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
        player.sendMessage(messageManager.getMessage("bestiary-milestone",
                "<gold>📜 Bestiárium-mérföldkő: <white>{count}</white> bejegyzés a(z) <white>{category}</white> lajstromban! Jutalom: <white>{reward} veret</white> a kezedbe.</gold>",
                Map.of("count", String.valueOf(size), "category", categoryName(category),
                        "reward", String.valueOf(reward))));
        if (parts.length >= 3 && "broadcast".equalsIgnoreCase(parts[2].trim())) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "bestiary-milestone-broadcast",
                    "<gold>📜 <white>{player}</white> lajstroma <white>{count}</white> bejegyzésre hízott a(z) <white>{category}</white> fejezetben — a krónikások főt hajtanak!</gold>",
                    Map.of("player", player.getName(), "count", String.valueOf(size),
                            "category", categoryName(category))));
        }
    }

    private CompletionStage<PlayerProfileAchievementStore.RewardReservation> reserve(
            final Player player, final String receipt, final long reward) {
        final java.util.Optional<PendingReward> existing =
                store.pendingReward(player.getUniqueId(), receipt);
        if (existing.isPresent()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new PlayerProfileAchievementStore.RewardReservation(
                            PlayerProfileAchievementStore.RewardState.PENDING,
                            existing.orElseThrow(), false));
        }
        if (store.rewardSettled(player.getUniqueId(), receipt)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return store.reserveReward(player.getUniqueId(), payload(player, receipt, reward));
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
