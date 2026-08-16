package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.BlockRegenService;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyBudgetStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Genuine vanilla mining adapter for the Itemization 2.0 survival material loop. */
public final class RareGatheringListener implements Listener {

    private static final String BUDGET_ID = "itemization.rare-mining";
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ProfessionManager professions;
    private final UniqueMaterialFactory materials;
    private final BlockRegenService regen;
    private final AfkManager afk;
    private final MessageManager messages;
    private final PlayerProfileDailyBudgetStore budget = new PlayerProfileDailyBudgetStore();

    public RareGatheringListener(final JavaPlugin plugin, final ConfigManager config,
                                 final ProfessionManager professions,
                                 final UniqueMaterialFactory materials,
                                 final BlockRegenService regen, final AfkManager afk,
                                 final MessageManager messages) {
        this.plugin = plugin;
        this.config = config;
        this.professions = professions;
        this.materials = materials;
        this.regen = regen;
        this.afk = afk;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!config.getBoolean("itemization.gathering.rare-mining.enabled", true)
                || !event.isDropItems()
                || (player.getGameMode() != GameMode.SURVIVAL
                && player.getGameMode() != GameMode.ADVENTURE)
                || !professions.hasProfession(player, ProfessionType.MINER)
                || (afk != null && config.getBoolean("afk.block-rewards", true)
                && afk.isAfk(player.getUniqueId()))
                || regen.isPending(event.getBlock()) || regen.isRestoredShielded(event.getBlock())) {
            return;
        }
        final int y = event.getBlock().getY();
        if (y < config.getInt("itemization.gathering.rare-mining.minimum-y", -64)
                || y > config.getInt("itemization.gathering.rare-mining.maximum-y", 16)
                || !sourceBlocks().contains(event.getBlock().getType())) {
            return;
        }
        final double chance = Math.max(0.0D, Math.min(1.0D,
                config.getDouble("itemization.gathering.rare-mining.chance", 0.0125D)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        final long day = Math.floorDiv(System.currentTimeMillis(), 86_400_000L);
        final long cap = Math.max(1L,
                config.getLong("itemization.gathering.rare-mining.daily-cap", 3L));
        budget.reserve(player.getUniqueId(), BUDGET_ID, day, 1L, cap)
                .whenComplete((reservation, failure) -> {
                    if (failure != null || reservation == null || !reservation.allowed()) {
                        return;
                    }
                    player.getScheduler().run(plugin, task -> {
                    if (!player.isOnline()) {
                        budget.rollback(player.getUniqueId(), BUDGET_ID, reservation, 1L);
                        return;
                    }
                    final String materialId = config.getString(
                            "itemization.gathering.rare-mining.material-id", "sarkfeny_cseppko");
                    final ItemStack reward = materials.create(materialId, 1);
                    if (reward == null) {
                        budget.rollback(player.getUniqueId(), BUDGET_ID, reservation, 1L);
                        plugin.getLogger().severe("Rare mining material is undefined: " + materialId);
                        return;
                    }
                    for (final ItemStack overflow : player.getInventory().addItem(reward).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                    }
                    player.sendMessage(messages.get("itemization-rare-mining-found",
                            "&bA kő mélyén Sarkfény-cseppkövet találtál."));
                    }, () -> budget.rollback(player.getUniqueId(), BUDGET_ID, reservation, 1L));
                });
    }

    private Set<Material> sourceBlocks() {
        final Set<Material> result = new HashSet<>();
        for (final String raw : config.getConfiguration().getStringList(
                "itemization.gathering.rare-mining.source-blocks")) {
            final Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) result.add(material);
        }
        return result;
    }
}
