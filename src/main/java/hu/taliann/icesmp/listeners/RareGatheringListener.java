package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.BlockRegenService;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyBudgetStore;
import hu.taliann.icesmp.progression.BlockRewardOriginTracker;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Genuine vanilla mining adapter for the Itemization 2.0 survival material loop. */
public final class RareGatheringListener implements Listener {

    private record RareSource(String materialId, double chance, int minimumY, int maximumY,
                              Set<Material> blocks, String environment) {
        boolean matches(final org.bukkit.block.Block block) {
            return blocks.contains(block.getType()) && block.getY() >= minimumY
                    && block.getY() <= maximumY
                    && (environment.isBlank()
                    || block.getWorld().getEnvironment().name().equalsIgnoreCase(environment));
        }
    }

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
                || player.getInventory().getItemInMainHand()
                .containsEnchantment(Enchantment.SILK_TOUCH)
                || (afk != null && config.getBoolean("afk.block-rewards", true)
                && afk.isAfk(player.getUniqueId()))
                || !BlockRewardOriginTracker.isRewardEligible(event.getBlock())
                || regen.isPending(event.getBlock()) || regen.isRestoredShielded(event.getBlock())) {
            return;
        }
        final List<RareSource> sources = sources().stream()
                .filter(source -> source.matches(event.getBlock())).toList();
        if (sources.isEmpty()) return;
        final double roll = ThreadLocalRandom.current().nextDouble();
        double cursor = 0.0D;
        RareSource selected = null;
        for (final RareSource source : sources) {
            cursor = Math.min(1.0D, cursor + source.chance());
            if (roll < cursor) {
                selected = source;
                break;
            }
        }
        if (selected == null) return;
        final ItemStack preview = materials.create(selected.materialId(), 1);
        if (preview == null || !canFit(player, preview)) return;
        final RareSource rewardSource = selected;

        final long day = Math.floorDiv(System.currentTimeMillis(), 86_400_000L);
        final long cap = Math.max(1L,
                config.getLong("itemization.gathering.rare-mining.daily-cap", 6L));
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
                    final ItemStack reward = materials.create(rewardSource.materialId(), 1);
                    if (reward == null || !canFit(player, reward)
                            || !player.getInventory().addItem(reward).isEmpty()) {
                        budget.rollback(player.getUniqueId(), BUDGET_ID, reservation, 1L);
                        return;
                    }
                    player.sendMessage(messages.get("itemization-rare-mining-found",
                            "&bA kő mélyén ritka szakmaalapanyagot találtál."));
                    }, () -> budget.rollback(player.getUniqueId(), BUDGET_ID, reservation, 1L));
                });
    }

    private List<RareSource> sources() {
        final ConfigurationSection root = config.getConfiguration().getConfigurationSection(
                "itemization.gathering.rare-mining.resources");
        if (root == null) {
            return List.of(new RareSource(config.getString(
                    "itemization.gathering.rare-mining.material-id", "sarkfeny_cseppko"),
                    boundedChance(config.getDouble(
                            "itemization.gathering.rare-mining.chance", 0.0125D)),
                    config.getInt("itemization.gathering.rare-mining.minimum-y", -64),
                    config.getInt("itemization.gathering.rare-mining.maximum-y", 16),
                    materials(config.getConfiguration().getStringList(
                            "itemization.gathering.rare-mining.source-blocks")), ""));
        }
        final ArrayList<RareSource> result = new ArrayList<>();
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            final Set<Material> blocks = materials(section.getStringList("source-blocks"));
            if (!blocks.isEmpty()) result.add(new RareSource(id,
                    boundedChance(section.getDouble("chance", 0.0D)),
                    section.getInt("minimum-y", -64), section.getInt("maximum-y", 320),
                    blocks, section.getString("environment", "")));
        }
        return List.copyOf(result);
    }

    private static Set<Material> materials(final List<String> configured) {
        final java.util.LinkedHashSet<Material> result = new java.util.LinkedHashSet<>();
        for (final String raw : configured) {
            final Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) result.add(material);
        }
        return Set.copyOf(result);
    }

    private static double boundedChance(final double chance) {
        return Double.isFinite(chance) ? Math.max(0.0D, Math.min(1.0D, chance)) : 0.0D;
    }

    private static boolean canFit(final Player player, final ItemStack incoming) {
        for (final ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) return true;
            if (existing.isSimilar(incoming)
                    && existing.getAmount() < Math.max(1, existing.getMaxStackSize())) return true;
        }
        return false;
    }
}
