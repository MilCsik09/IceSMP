package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WoW-style mob loot: slain mobs have a chance to drop a unique, random-attribute gear item
 * (rolled by {@link ItemRarityService}). The tier balances the source — ordinary mobs roll
 * the weaker {@code drop} tier, while world bosses / invasion champions / wild-hunt beasts roll
 * the {@code boss} tier, on par with profession crafts. Config: {@code loot} (loot.yml).
 */
public final class MobLootListener implements Listener {

    private final ConfigManager configManager;
    private final ItemRarityService affixService;
    private final WorldBossManager worldBossManager;
    private final InvasionManager invasionManager;
    private final WildHuntManager wildHuntManager;
    private final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;

    public MobLootListener(final ConfigManager configManager, final ItemRarityService affixService,
                           final WorldBossManager worldBossManager, final InvasionManager invasionManager,
                           final WildHuntManager wildHuntManager,
                           final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory,
                           final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog,
                           final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        this.configManager = configManager;
        this.affixService = affixService;
        this.worldBossManager = worldBossManager;
        this.invasionManager = invasionManager;
        this.wildHuntManager = wildHuntManager;
        this.blueprintFactory = blueprintFactory;
        this.recipeCatalog = recipeCatalog;
        this.uniqueMaterials = uniqueMaterials;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!configManager.getBoolean("loot.enabled", true) || !affixService.isEnabled()) {
            return;
        }
        final LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        final boolean bossTier = worldBossManager.isWorldBoss(entity)
                || invasionManager.isInvasionMob(entity.getUniqueId())
                || wildHuntManager.isWildHunt(entity.getUniqueId());

        // Ordinary mobs may require a player kill; boss/event mobs always yield their loot.
        if (!bossTier && configManager.getBoolean("loot.require-player-kill", true) && entity.getKiller() == null) {
            return;
        }

        // Rare blueprint drop: teaches a blueprint-gated profession recipe (config loot.blueprint-drop).
        rollBlueprintDrop(event, bossTier);

        final String path = bossTier ? "loot.boss-drop" : "loot.mob-drop";
        final String tier = bossTier ? ItemRarityService.TIER_BOSS : ItemRarityService.TIER_DROP;

        final double chance = configManager.getDouble(path + ".chance", bossTier ? 1.0D : 0.15D);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        final ItemStack drop = rollTable(path, tier);
        if (drop != null) {
            event.getDrops().add(drop);
        }
    }

    /**
     * Picks one entry from the source's weighted loot table (config {@code <path>.table}) and builds
     * the drop. Entry {@code type}: {@code gear} (affix-rolled gear from {@code <path>.gear-pool}),
     * {@code material} (a plain item {@code item} × {@code min..max}), or {@code unique} (a unique
     * material {@code id} — including mob-only ones that recipes need). Falls back to the gear-pool
     * when no table is configured.
     */
    private ItemStack rollTable(final String path, final String tier) {
        final List<Map<?, ?>> table = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList(path + ".table");
        if (table.isEmpty()) {
            final Material base = pickGear(configManager.getStringList(path + ".gear-pool"));
            return base == null ? null : affixService.roll(new ItemStack(base), tier, true);
        }

        int total = 0;
        for (final Map<?, ?> entry : table) {
            total += toInt(entry.get("weight"), 1);
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        Map<?, ?> chosen = table.get(0);
        for (final Map<?, ?> entry : table) {
            roll -= toInt(entry.get("weight"), 1);
            if (roll < 0) {
                chosen = entry;
                break;
            }
        }

        final String type = String.valueOf(chosen.getOrDefault("type", "gear")).toLowerCase(Locale.ROOT);
        switch (type) {
            case "material" -> {
                final Material material = Material.matchMaterial(String.valueOf(chosen.get("item")).toUpperCase(Locale.ROOT));
                if (material == null || material.isAir()) {
                    return null;
                }
                final int min = toInt(chosen.get("min"), 1);
                final int max = Math.max(min, toInt(chosen.get("max"), min));
                return new ItemStack(material, min + ThreadLocalRandom.current().nextInt(max - min + 1));
            }
            case "unique" -> {
                final int min = toInt(chosen.get("min"), 1);
                final int max = Math.max(min, toInt(chosen.get("max"), min));
                return uniqueMaterials.create(String.valueOf(chosen.get("id")),
                        min + ThreadLocalRandom.current().nextInt(max - min + 1));
            }
            default -> {
                final Material base = pickGear(configManager.getStringList(path + ".gear-pool"));
                return base == null ? null : affixService.roll(new ItemStack(base), tier, true);
            }
        }
    }

    private static int toInt(final Object value, final int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private void rollBlueprintDrop(final EntityDeathEvent event, final boolean bossTier) {
        final double chance = configManager.getDouble(
                bossTier ? "loot.blueprint-drop.boss-chance" : "loot.blueprint-drop.chance", bossTier ? 0.05D : 0.002D);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        final List<String> ids = recipeCatalog.blueprintRecipeIds();
        if (ids.isEmpty()) {
            return;
        }
        final ItemStack blueprint = blueprintFactory.create(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
        if (blueprint != null) {
            event.getDrops().add(blueprint);
        }
    }

    private Material pickGear(final List<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        // Try a few times to land on a valid material name (skips admin typos gracefully).
        for (int attempt = 0; attempt < 4; attempt++) {
            final String name = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            final Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) {
                return material;
            }
        }
        return null;
    }
}
