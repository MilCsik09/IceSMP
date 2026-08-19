package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.itemization.BuildAwareLootService;
import hu.taliann.icesmp.itemization.EquipmentProficiencyService;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.itemization.ItemTemplateRegistry;
import hu.taliann.icesmp.itemization.LootDiversityState;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileLootDiversityStore;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Survival combat loot: material rows stay source-authored, while gear rows use canonical
 * Itemization 2.0 templates whenever that authority is enabled. Loot personalization reads only
 * ACTIVE canonical equipment; physical wrong-family/invalid/suppressed gear cannot shape weights.
 */
public final class MobLootListener implements Listener {

    private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
    private static final Logger LOGGER = Logger.getLogger(MobLootListener.class.getName());

    private final ConfigManager configManager;
    private final ItemRarityService affixService;
    private final WorldBossManager worldBossManager;
    private final InvasionManager invasionManager;
    private final WildHuntManager wildHuntManager;
    private final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final JavaPlugin plugin;
    private final ItemTemplateRegistry itemTemplates;
    private final ItemIdentityService itemIdentity;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final BuildAwareLootService authoredLoot = new BuildAwareLootService();
    private final PlayerProfileLootDiversityStore lootDiversity = new PlayerProfileLootDiversityStore();

    public MobLootListener(final JavaPlugin plugin, final ConfigManager configManager,
                           final ItemRarityService affixService,
                           final WorldBossManager worldBossManager, final InvasionManager invasionManager,
                           final WildHuntManager wildHuntManager,
                           final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory,
                           final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog,
                           final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                           final ItemTemplateRegistry itemTemplates,
                           final ItemIdentityService itemIdentity,
                           final JobManager jobManager,
                           final SpecializationManager specializationManager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.configManager = configManager;
        this.affixService = affixService;
        this.worldBossManager = worldBossManager;
        this.invasionManager = invasionManager;
        this.wildHuntManager = wildHuntManager;
        this.blueprintFactory = blueprintFactory;
        this.recipeCatalog = recipeCatalog;
        this.uniqueMaterials = uniqueMaterials;
        this.itemTemplates = java.util.Objects.requireNonNull(itemTemplates, "itemTemplates");
        this.itemIdentity = java.util.Objects.requireNonNull(itemIdentity, "itemIdentity");
        this.jobManager = java.util.Objects.requireNonNull(jobManager, "jobManager");
        this.specializationManager = java.util.Objects.requireNonNull(specializationManager, "specializationManager");
    }

    private volatile hu.taliann.icesmp.managers.AfkManager afkManager;

    public void setAfkManager(final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvasionMobDeath(final EntityDeathEvent event) {
        invasionManager.handleMobDeath(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!configManager.getBoolean("loot.enabled", true)) return;
        final LivingEntity entity = event.getEntity();
        if (entity instanceof Player || hu.taliann.icesmp.managers.MinionManager.isMinionTagged(entity)) return;

        final hu.taliann.icesmp.managers.CultistEventManager cultists = this.cultistEventManagerRef;
        if (cultists != null && cultists.isCultist(entity)) {
            if (hu.taliann.icesmp.utils.MobKillUtil.eligibleKill(entity,
                    hu.taliann.icesmp.utils.MobKillUtil.RewardKind.FAUCET, configManager, afkManager) != null
                    && ThreadLocalRandom.current().nextDouble()
                    < configManager.getDouble("cultists.loot.chance", 0.35D)) {
                final ItemStack cultDrop = rollTable("cultists.loot", ItemRarityService.TIER_DROP, entity);
                if (cultDrop != null) event.getDrops().add(cultDrop);
            }
            return;
        }

        final boolean bossTier = worldBossManager.isWorldBoss(entity)
                || invasionManager.isInvasionMob(entity.getUniqueId())
                || wildHuntManager.isWildHunt(entity.getUniqueId());
        final Player killer = entity.getKiller();
        if (killer != null && hu.taliann.icesmp.utils.MobKillUtil.isAfkRewardBlocked(
                killer.getUniqueId(), configManager, afkManager)) return;
        if (!bossTier && configManager.getBoolean("loot.require-player-kill", true)
                && hu.taliann.icesmp.utils.MobKillUtil.eligibleKill(entity,
                        hu.taliann.icesmp.utils.MobKillUtil.RewardKind.FAUCET,
                        configManager, afkManager) == null) return;

        rollBlueprintDrop(event, bossTier);
        final String path = bossTier ? "loot.boss-drop" : "loot.mob-drop";
        final String tier = bossTier ? ItemRarityService.TIER_BOSS : ItemRarityService.TIER_DROP;
        final double chance = configManager.getDouble(path + ".chance", bossTier ? 1.0D : 0.15D);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        final ItemStack drop = rollTable(path, tier, entity);
        if (drop != null) event.getDrops().add(bossTier && cursedGearService != null
                ? cursedGearService.maybeCurse(drop) : drop);
    }

    private volatile hu.taliann.icesmp.managers.CultistEventManager cultistEventManagerRef;

    public void setCultistEventManager(final hu.taliann.icesmp.managers.CultistEventManager cultistEventManager) {
        this.cultistEventManagerRef = cultistEventManager;
    }

    private hu.taliann.icesmp.managers.CursedGearService cursedGearService;

    public void setCursedGearService(final hu.taliann.icesmp.managers.CursedGearService cursedGearService) {
        this.cursedGearService = cursedGearService;
    }

    private ItemStack rollTable(final String path, final String tier, final LivingEntity source) {
        final List<Map<?, ?>> raw = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList(path + ".table");
        final List<Map<?, ?>> table = new ArrayList<>();
        for (final Map<?, ?> entry : raw) {
            if (Boolean.parseBoolean(String.valueOf(entry.get("undead-only")))
                    && !hu.taliann.icesmp.utils.UndeadUtil.isUndead(source)) continue;
            table.add(entry);
        }
        if (table.isEmpty()) {
            if (configManager.getBoolean("itemization.loot.enabled", true)) {
                scheduleAuthoredGear(path, source);
                return null;
            }
            final Material base = pickGear(configManager.getStringList(path + ".gear-pool"));
            return base == null ? null : affixService.roll(new ItemStack(base), tier, true);
        }

        int total = 0;
        for (final Map<?, ?> entry : table) total += toInt(entry.get("weight"), 1);
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        Map<?, ?> chosen = table.get(0);
        for (final Map<?, ?> entry : table) {
            roll -= toInt(entry.get("weight"), 1);
            if (roll < 0) {
                chosen = entry;
                break;
            }
        }

        final Object typeObj = chosen.get("type");
        final String type = typeObj != null ? String.valueOf(typeObj).toLowerCase(Locale.ROOT) : "gear";
        switch (type) {
            case "material" -> {
                final Material material = Material.matchMaterial(String.valueOf(chosen.get("item")).toUpperCase(Locale.ROOT));
                if (material == null || material.isAir()) return null;
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
            case "named" -> {
                final Material material = Material.matchMaterial(String.valueOf(chosen.get("item")).toUpperCase(Locale.ROOT));
                if (material == null || material.isAir()) return null;
                final ItemStack item = new ItemStack(material);
                if (item.getMaxStackSize() == 1 && configManager.getBoolean("itemization.loot.enabled", true)) {
                    scheduleAuthoredGear(path, source);
                    return null;
                }
                final ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(LEGACY.deserialize(String.valueOf(chosen.get("name")))
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    final Object loreObj = chosen.get("lore");
                    if (loreObj instanceof List<?> lines) {
                        final List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                        for (final Object line : lines) {
                            lore.add(LEGACY.deserialize(String.valueOf(line))
                                    .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                        }
                        meta.lore(lore);
                    }
                    item.setItemMeta(meta);
                }
                final ItemStack rolledNamed = affixService.roll(item, tier, false);
                final Object namedModel = chosen.get("item-model");
                final Object namedEquipmentAsset = chosen.get("equipment-asset");
                final String itemModel = namedModel == null ? null : String.valueOf(namedModel);
                final String equipmentAsset = namedEquipmentAsset == null ? null : String.valueOf(namedEquipmentAsset);
                final hu.taliann.icesmp.items.WearablePresentation.Result presentation =
                        hu.taliann.icesmp.items.WearablePresentation.applyWearablePresentation(
                                rolledNamed, itemModel, equipmentAsset);
                if (equipmentAsset != null && !equipmentAsset.isBlank() && !presentation.equipmentApplied()) {
                    LOGGER.warning(path + ".table named equipment-asset '" + equipmentAsset
                            + "' cannot be applied (" + presentation.equipmentStatus() + ")");
                    return null;
                }
                return rolledNamed;
            }
            default -> {
                if (configManager.getBoolean("itemization.loot.enabled", true)) {
                    scheduleAuthoredGear(path, source);
                    return null;
                }
                final Material base = pickGear(configManager.getStringList(path + ".gear-pool"));
                return base == null ? null : affixService.roll(new ItemStack(base), tier, true);
            }
        }
    }

    private boolean scheduleAuthoredGear(final String path, final LivingEntity source) {
        if (!configManager.getBoolean("itemization.loot.enabled", true)) return false;
        final Player killer = source.getKiller();
        if (killer == null) return false;
        final String sourceTag = path.contains("boss") ? "combat:boss"
                : path.contains("cultist") ? "combat:event" : "combat:wilderness";
        final List<ItemTemplate> candidates = itemTemplates.snapshot().values().stream()
                .filter(template -> isGear(template)
                        && (template.sourceTags().contains(sourceTag)
                        || template.sourceTags().contains("combat:any")))
                .toList();
        if (candidates.isEmpty()) return false;
        final UUID playerId = killer.getUniqueId();
        try {
            lootDiversity.current(playerId);
        } catch (final RuntimeException profileUnavailable) {
            return false;
        }
        final String sourceId = source.getType().name().toLowerCase(Locale.ROOT);
        return killer.getScheduler().run(plugin, task -> {
            if (!killer.isOnline()) return;
            final LootDiversityState history;
            try {
                history = lootDiversity.current(playerId);
            } catch (final RuntimeException profileUnavailable) {
                return;
            }
            final JobType job = jobManager.getPrimaryJob(killer);
            final SpecializationType specialization = specializationManager.getClassSpecialization(killer);
            final BuildAwareLootService.Context context = new BuildAwareLootService.Context(
                    Math.max(0, jobManager.getPrimaryLevel(killer)),
                    job == null ? "" : job.getId(),
                    specialization == null ? "" : specialization.getId(),
                    currentBuildTags(killer), preferredEmptySlot(killer), Set.of(sourceTag));
            final BuildAwareLootService.Selection selection = authoredLoot.select(
                    candidates, context, history, liveLootTuning(),
                    ThreadLocalRandom.current().nextDouble()).orElse(null);
            if (selection == null) return;
            final ItemTemplate template = selection.template();
            final UUID itemId = UUID.randomUUID();
            final ItemInstance instance = itemIdentity.rollInstance(template, itemId,
                    sourceTag, sourceId, null, System.currentTimeMillis(),
                    () -> ThreadLocalRandom.current().nextDouble());
            final ItemStack drop = itemIdentity.render(template, instance);
            killer.getWorld().dropItemNaturally(killer.getLocation(), drop);
            lootDiversity.record(playerId, LootDiversityState.Drop.of(itemId, template))
                    .whenComplete((recorded, failure) -> {
                        if (failure != null) LOGGER.warning("Itemization loot diversity receipt failed for "
                                + playerId + ": " + failure.getMessage());
                    });
        }, null) != null;
    }

    private BuildAwareLootService.Tuning liveLootTuning() {
        return new BuildAwareLootService.Tuning(
                bounded(configManager.getDouble(
                        "itemization.loot.max-personalization-multiplier", 1.5D), 1.0D, 1.5D),
                Math.max(1, Math.min(LootDiversityState.MAX_DROPS, configManager.getInt(
                        "itemization.loot.history-window", 24))),
                bounded(configManager.getDouble(
                        "itemization.loot.repeated-template-penalty", 0.12D), 0.0D, 0.25D),
                bounded(configManager.getDouble(
                        "itemization.loot.repeated-category-penalty", 0.035D), 0.0D, 0.10D),
                bounded(configManager.getDouble(
                        "itemization.loot.unseen-category-boost", 0.04D), 0.0D, 0.10D));
    }

    /** Only ACTIVE canonical equipment can shape Itemization build relevance. BASIC is ignored. */
    private Set<String> currentBuildTags(final Player player) {
        final LinkedHashSet<String> tags = new LinkedHashSet<>();
        addBuildTags(tags, player, player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND);
        addBuildTags(tags, player, player.getInventory().getItemInOffHand(), ItemTemplate.Slot.OFF_HAND);
        addBuildTags(tags, player, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD);
        addBuildTags(tags, player, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST);
        addBuildTags(tags, player, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS);
        addBuildTags(tags, player, player.getInventory().getBoots(), ItemTemplate.Slot.FEET);
        return Set.copyOf(tags);
    }

    private void addBuildTags(final Set<String> tags, final Player player, final ItemStack item,
                              final ItemTemplate.Slot slot) {
        if (!EquipmentProficiencyService.isCanonicalActive(player, item, slot)) return;
        final ItemIdentityService.Inspection inspection = itemIdentity.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID) return;
        final String stage = inspection.instance().ascension().stageId();
        inspection.template().fixedStatsAt(stage).keySet().forEach(stat -> tags.add("stat:" + stat));
        inspection.template().rolledStatsAt(stage).keySet().forEach(stat -> tags.add("stat:" + stat));
    }

    private static ItemTemplate.Slot preferredEmptySlot(final Player player) {
        if (empty(player.getInventory().getHelmet())) return ItemTemplate.Slot.HEAD;
        if (empty(player.getInventory().getChestplate())) return ItemTemplate.Slot.CHEST;
        if (empty(player.getInventory().getLeggings())) return ItemTemplate.Slot.LEGS;
        if (empty(player.getInventory().getBoots())) return ItemTemplate.Slot.FEET;
        if (empty(player.getInventory().getItemInMainHand())) return ItemTemplate.Slot.MAIN_HAND;
        if (empty(player.getInventory().getItemInOffHand())) return ItemTemplate.Slot.OFF_HAND;
        return ItemTemplate.Slot.NONE;
    }

    private static boolean empty(final ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean isGear(final ItemTemplate template) {
        return template.slot() != ItemTemplate.Slot.NONE
                && template.family() != ItemTemplate.Family.MATERIAL
                && template.family() != ItemTemplate.Family.CONSUMABLE;
    }

    private static double bounded(final double value, final double minimum, final double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int toInt(final Object value, final int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private void rollBlueprintDrop(final EntityDeathEvent event, final boolean bossTier) {
        final double chance = configManager.getDouble(
                bossTier ? "loot.blueprint-drop.boss-chance" : "loot.blueprint-drop.chance",
                bossTier ? 0.05D : 0.002D);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) return;
        final List<String> ids = recipeCatalog.blueprintDropPool(bossTier);
        if (ids.isEmpty()) return;
        final ItemStack blueprint = blueprintFactory.create(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
        if (blueprint != null) event.getDrops().add(blueprint);
    }

    private Material pickGear(final List<String> pool) {
        if (pool == null || pool.isEmpty()) return null;
        for (int attempt = 0; attempt < 4; attempt++) {
            final String name = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            final Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) return material;
        }
        return null;
    }
}
