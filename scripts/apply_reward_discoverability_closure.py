#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str, write: bool) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if text.count(old) != 1:
        raise SystemExit(f"{path}: closure seam drifted (expected one old block, got {text.count(old)})")
    if not write:
        raise SystemExit(f"{path}: reward/discoverability closure is not materialized")
    target.write_text(text.replace(old, new), encoding="utf-8")


def patch_mob_loot(write: bool) -> None:
    path = "src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java"
    replace_once(path,
'''import hu.taliann.icesmp.itemization.ItemIdentityService;\nimport hu.taliann.icesmp.itemization.ItemInstance;\n''',
'''import hu.taliann.icesmp.itemization.ItemIdentityService;\nimport hu.taliann.icesmp.itemization.ItemInstance;\nimport hu.taliann.icesmp.itemization.ItemTemplateCatalogIndex;\n''', write)
    replace_once(path,
'''import hu.taliann.icesmp.managers.WorldBossManager;\nimport hu.taliann.icesmp.playerprofile.application.PlayerProfileLootDiversityStore;\n''',
'''import hu.taliann.icesmp.managers.WorldBossManager;\nimport hu.taliann.icesmp.playerprofile.application.PlayerProfileLootDiversityStore;\nimport hu.taliann.icesmp.pve.MobRank;\nimport hu.taliann.icesmp.pve.MobRankLootPolicy;\n''', write)
    replace_once(path,
'''import org.bukkit.Material;\nimport org.bukkit.entity.LivingEntity;\n''',
'''import org.bukkit.Material;\nimport org.bukkit.NamespacedKey;\nimport org.bukkit.entity.LivingEntity;\n''', write)
    replace_once(path,
'''import org.bukkit.plugin.java.JavaPlugin;\n\nimport java.util.ArrayList;\n''',
'''import org.bukkit.plugin.java.JavaPlugin;\nimport org.bukkit.persistence.PersistentDataType;\n\nimport java.util.ArrayList;\n''', write)
    replace_once(path,
'''    private static final Logger LOGGER = Logger.getLogger(MobLootListener.class.getName());\n\n    private final ConfigManager configManager;\n''',
'''    private static final Logger LOGGER = Logger.getLogger(MobLootListener.class.getName());\n    private static final NamespacedKey MOB_RANK_KEY = java.util.Objects.requireNonNull(\n            NamespacedKey.fromString("icesmp:mob_rank"));\n\n    private final ConfigManager configManager;\n''', write)
    replace_once(path,
'''    private final JavaPlugin plugin;\n    private final ItemTemplateRegistry itemTemplates;\n    private final ItemIdentityService itemIdentity;\n''',
'''    private final JavaPlugin plugin;\n    private final ItemTemplateRegistry itemTemplates;\n    private final ItemTemplateCatalogIndex itemTemplateIndex;\n    private final ItemIdentityService itemIdentity;\n''', write)
    replace_once(path,
'''        this.uniqueMaterials = uniqueMaterials;\n        this.itemTemplates = java.util.Objects.requireNonNull(itemTemplates, "itemTemplates");\n        this.itemIdentity = java.util.Objects.requireNonNull(itemIdentity, "itemIdentity");\n''',
'''        this.uniqueMaterials = uniqueMaterials;\n        this.itemTemplates = java.util.Objects.requireNonNull(itemTemplates, "itemTemplates");\n        this.itemTemplateIndex = new ItemTemplateCatalogIndex(this.itemTemplates);\n        this.itemIdentity = java.util.Objects.requireNonNull(itemIdentity, "itemIdentity");\n''', write)
    replace_once(path,
'''        rollBlueprintDrop(event, bossTier);\n        final String path = bossTier ? "loot.boss-drop" : "loot.mob-drop";\n        final String tier = bossTier ? ItemRarityService.TIER_BOSS : ItemRarityService.TIER_DROP;\n        final double chance = configManager.getDouble(path + ".chance", bossTier ? 1.0D : 0.15D);\n        if (ThreadLocalRandom.current().nextDouble() >= chance) return;\n''',
'''        final MobRankLootPolicy.RewardBand rewardBand = MobRankLootPolicy.resolve(\n                rankOf(entity, bossTier), configManager);\n        rollBlueprintDrop(event, rewardBand);\n        rollRankSpecialMaterial(event, rewardBand);\n        final boolean canonicalBossBand = bossTier || rewardBand.bossLike();\n        final String path = canonicalBossBand ? "loot.boss-drop" : "loot.mob-drop";\n        final String tier = canonicalBossBand ? ItemRarityService.TIER_BOSS : ItemRarityService.TIER_DROP;\n        final double baseChance = configManager.getDouble(path + ".chance", canonicalBossBand ? 1.0D : 0.15D);\n        final double chance = bounded(baseChance + rewardBand.gearChanceAdditive(), 0.0D, 1.0D);\n        if (ThreadLocalRandom.current().nextDouble() >= chance) return;\n''', write)
    replace_once(path,
'''        final String sourceTag = path.contains("boss") ? "combat:boss"\n                : path.contains("cultist") ? "combat:event" : "combat:wilderness";\n        final List<ItemTemplate> candidates = itemTemplates.snapshot().values().stream()\n                .filter(template -> isGear(template)\n                        && (template.sourceTags().contains(sourceTag)\n                        || template.sourceTags().contains("combat:any")))\n                .toList();\n''',
'''        final MobRankLootPolicy.RewardBand rewardBand = MobRankLootPolicy.resolve(\n                rankOf(source, path.contains("boss")), configManager);\n        final String sourceTag = path.contains("cultist") ? "combat:event"\n                : rewardBand.primarySourceTag();\n        final LinkedHashSet<String> eligibleSources = new LinkedHashSet<>(rewardBand.sourceTags());\n        eligibleSources.add("combat:any");\n        if (path.contains("cultist")) eligibleSources.add("combat:event");\n        final List<ItemTemplate> candidates = itemTemplateIndex.byAnySource(eligibleSources).stream()\n                .filter(MobLootListener::isGear)\n                .toList();\n''', write)
    replace_once(path,
'''        final String sourceId = source.getType().name().toLowerCase(Locale.ROOT);\n        return killer.getScheduler().run(plugin, task -> {\n''',
'''        final String authoredMobId = hu.taliann.icesmp.managers.MobScalingManager.templateIdOf(source);\n        final String sourceId = authoredMobId == null || authoredMobId.isBlank()\n                ? source.getType().name().toLowerCase(Locale.ROOT) : authoredMobId;\n        return killer.getScheduler().run(plugin, task -> {\n''', write)
    replace_once(path,
'''                    specialization == null ? "" : specialization.getId(),\n                    currentBuildTags(killer), preferredEmptySlot(killer), Set.of(sourceTag));\n''',
'''                    specialization == null ? "" : specialization.getId(),\n                    currentBuildTags(killer), preferredEmptySlot(killer), rewardBand.sourceTags());\n''', write)
    replace_once(path,
'''    private void rollBlueprintDrop(final EntityDeathEvent event, final boolean bossTier) {\n        final double chance = configManager.getDouble(\n                bossTier ? "loot.blueprint-drop.boss-chance" : "loot.blueprint-drop.chance",\n                bossTier ? 0.05D : 0.002D);\n        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) return;\n        final List<String> ids = recipeCatalog.blueprintDropPool(bossTier);\n        if (ids.isEmpty()) return;\n        final ItemStack blueprint = blueprintFactory.create(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));\n        if (blueprint != null) event.getDrops().add(blueprint);\n    }\n\n    private Material pickGear(final List<String> pool) {\n''',
'''    private void rollBlueprintDrop(final EntityDeathEvent event,\n                                   final MobRankLootPolicy.RewardBand rewardBand) {\n        final double chance = rewardBand.blueprintChance();\n        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) return;\n        final List<String> ids = recipeCatalog.blueprintDropPool(rewardBand.bossLike());\n        if (ids.isEmpty()) return;\n        final ItemStack blueprint = blueprintFactory.create(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));\n        if (blueprint != null) event.getDrops().add(blueprint);\n    }\n\n    private void rollRankSpecialMaterial(final EntityDeathEvent event,\n                                         final MobRankLootPolicy.RewardBand rewardBand) {\n        if (rewardBand.specialMaterial().isBlank() || rewardBand.specialMaterialChance() <= 0.0D\n                || ThreadLocalRandom.current().nextDouble() >= rewardBand.specialMaterialChance()) return;\n        final ItemStack material = uniqueMaterials.create(rewardBand.specialMaterial(), 1);\n        if (material == null || material.getType().isAir()) return;\n        event.getDrops().add(material);\n        hu.taliann.icesmp.professions.ProfessionEconomyTelemetry.global().recordFaucet(\n                "combat:" + rewardBand.id(), rewardBand.specialMaterial(), 1);\n    }\n\n    private static MobRank rankOf(final LivingEntity entity, final boolean forcedBoss) {\n        if (forcedBoss) return MobRank.BOSS;\n        final String raw = entity.getPersistentDataContainer().get(MOB_RANK_KEY, PersistentDataType.STRING);\n        if (raw == null || raw.isBlank()) return MobRank.NORMAL;\n        try {\n            return MobRank.parse(raw);\n        } catch (final IllegalArgumentException ignored) {\n            return MobRank.NORMAL;\n        }\n    }\n\n    private Material pickGear(final List<String> pool) {\n''', write)


def patch_mob_templates(write: bool) -> None:
    path = "src/main/java/hu/taliann/icesmp/pve/MobTemplateRegistry.java"
    replace_once(path,
'''        final Set<String> lootProfiles = lootRoot.getKeys(false).stream()\n                .map(MobTemplateRegistry::normalize).collect(java.util.stream.Collectors.toSet());\n''',
'''        final Set<String> lootProfiles = parseLootProfileReferences(lootRoot);\n''', write)
    replace_once(path,
'''    private static Set<String> normalizedSet(final List<String> values) {\n''',
'''    private static Set<String> parseLootProfileReferences(final ConfigurationSection root) {\n        final LinkedHashSet<String> ids = new LinkedHashSet<>();\n        for (final String rawId : root.getKeys(false)) {\n            final String id = normalize(rawId);\n            final ConfigurationSection profile = root.getConfigurationSection(rawId);\n            if (profile == null) throw new IllegalStateException("invalid mob loot profile reference: " + id);\n            if (profile.contains("sources") || profile.contains("rewards")) {\n                throw new IllegalStateException("mob-loot-profiles is reference-only; dead rewards/sources authoring remains: " + id);\n            }\n            final String marker = normalize(profile.getString("profile-id", id));\n            if (!id.equals(marker)) {\n                throw new IllegalStateException("mob loot profile marker mismatch: " + id + '/' + marker);\n            }\n            if (!ids.add(id)) throw new IllegalStateException("duplicate mob loot profile reference: " + id);\n        }\n        return Set.copyOf(ids);\n    }\n\n    private static Set<String> normalizedSet(final List<String> values) {\n''', write)


def patch_menu(write: bool) -> None:
    path = "src/main/java/hu/taliann/icesmp/gui/CommandMenus.java"
    replace_once(path,
'''        put(inv, holder, 33, GuiUtil.icon(Material.SMITHING_TABLE, title("Szakma-céh heti cél"),\n                List.of(grey("A szakmád közös heti számlálója"),\n                        grey("és jutalma — infó chatben."), click())), "OPEN:szakmacel");\n''',
'''        put(inv, holder, 33, GuiUtil.icon(Material.SMITHING_TABLE, title("Szakma-műhely"),\n                List.of(grey("Mestermű, rúna, újrakovácsolás és Felemelkedés"),\n                        grey("a meglévő canonical Item Forge felületén."), click())), "OPEN:profession forge");\n''', write)


def patch_recipe_gui(write: bool) -> None:
    path = "src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java"
    replace_once(path,
'''            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/"\n                            + entry.getValue() + " " + uniqueMaterials.displayName(entry.getKey()),\n                    enough ? NamedTextColor.AQUA : NamedTextColor.RED)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n''',
'''            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/"\n                            + entry.getValue() + " " + uniqueMaterials.displayName(entry.getKey()),\n                    enough ? NamedTextColor.AQUA : NamedTextColor.RED)\n                    .decoration(TextDecoration.ITALIC, false));\n            for (final String hint : MaterialSourceHints.forManagedMaterial(entry.getKey())) {\n                lore.add(Component.text("    " + hint, NamedTextColor.DARK_GRAY)\n                        .decoration(TextDecoration.ITALIC, false));\n            }\n        }\n''', write)
    replace_once(path,
'''        if (template.armorFamily() != null) {\n            lore.add(Component.text("Páncélcsalád: " + template.armorFamily().displayName()\n                            + " (" + template.armorFamily().name() + ")", NamedTextColor.GRAY)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n        if (template.rolledStats().isEmpty()) return;\n''',
'''        if (template.armorFamily() != null) {\n            lore.add(Component.text("Páncélcsalád: " + template.armorFamily().displayName()\n                            + " (" + template.armorFamily().name() + ")", NamedTextColor.GRAY)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n        if (!template.sourceTags().isEmpty()) {\n            lore.add(Component.text("Forrás: " + template.sourceTags().stream().limit(3)\n                            .map(MaterialSourceHints::humanizeTag).collect(java.util.stream.Collectors.joining(", ")),\n                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));\n        }\n        if (!template.gatheringTags().isEmpty()) {\n            lore.add(Component.text("Gyűjtés: " + template.gatheringTags().stream().limit(3)\n                            .map(MaterialSourceHints::humanizeTag).collect(java.util.stream.Collectors.joining(", ")),\n                    NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));\n        }\n        if (template.rolledStats().isEmpty()) return;\n''', write)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.write and not args.check:
        args.check = True
    patch_mob_loot(args.write)
    patch_mob_templates(args.write)
    patch_menu(args.write)
    patch_recipe_gui(args.write)
    print("Reward/discoverability source closure: materialized" if args.write else "Reward/discoverability source closure: OK")


if __name__ == "__main__":
    main()
