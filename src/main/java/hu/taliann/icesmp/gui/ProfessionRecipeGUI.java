package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.icon;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.itemization.ItemTemplateRegistry;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.professions.ProfessionCraftQualityPolicy;
import hu.taliann.icesmp.professions.ProfessionSpecializationEconomyPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Paged Professions 2.0 recipe book and deterministic effective CraftPlan preview. */
public final class ProfessionRecipeGUI {

    private static final int PAGE_SIZE = 45;

    private ProfessionRecipeGUI() { }

    public static List<ProfessionRecipeCatalog.Recipe> visibleRecipes(
            final Player player, final ProfessionManager professionManager,
            final ProfessionRecipeCatalog catalog) {
        final List<ProfessionRecipeCatalog.Recipe> recipes = new ArrayList<>();
        for (final ProfessionType profession : professionManager.getActiveProfessions(player)) {
            recipes.addAll(catalog.recipesFor(profession));
        }
        recipes.sort((a, b) -> {
            final int profession = a.profession().name().compareTo(b.profession().name());
            return profession != 0 ? profession : Integer.compare(a.level(), b.level());
        });
        return recipes;
    }

    public static void open(final Player player, final int page,
                            final ProfessionManager professionManager,
                            final ProfessionRecipeCatalog catalog,
                            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        final List<ProfessionRecipeCatalog.Recipe> recipes =
                visibleRecipes(player, professionManager, catalog);
        final int maxPage = Math.max(0, (recipes.size() - 1) / PAGE_SIZE);
        final int shownPage = Math.max(0, Math.min(page, maxPage));
        final ProfessionRecipeHolder holder =
                new ProfessionRecipeHolder(player.getUniqueId(), shownPage);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<dark_aqua>» Recept-könyv «</dark_aqua>"));
        holder.setInventory(inventory);

        if (recipes.isEmpty()) {
            inventory.setItem(22, icon(Material.BOOK, accent("Nincs recepted"),
                    List.of(grey("Válassz szakmát (/profession join), és lépj szintet,"),
                            grey("hogy receptek nyíljanak meg."))));
        }
        final int start = shownPage * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < recipes.size(); slot++) {
            final ProfessionRecipeCatalog.Recipe recipe = recipes.get(start + slot);
            inventory.setItem(slot, buildTile(player, professionManager, catalog,
                    recipe, uniqueMaterials));
            holder.map(slot, recipe.id());
        }
        if (shownPage > 0) {
            inventory.setItem(45, icon(Material.ARROW,
                    Component.text("Előző oldal", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false), List.of()));
            holder.map(45, "PREV");
        }
        inventory.setItem(49, icon(Material.BARRIER,
                Component.text("Bezárás", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(grey("Oldal " + (shownPage + 1) + "/" + (maxPage + 1)))));
        holder.map(49, "CLOSE");
        if (shownPage < maxPage) {
            inventory.setItem(53, icon(Material.ARROW,
                    Component.text("Következő oldal", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false), List.of()));
            holder.map(53, "NEXT");
        }
        player.openInventory(inventory);
    }

    private static org.bukkit.inventory.ItemStack buildTile(
            final Player player, final ProfessionManager professionManager,
            final ProfessionRecipeCatalog catalog,
            final ProfessionRecipeCatalog.Recipe recipe,
            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        final int level = professionManager.getLevel(player, recipe.profession());
        final boolean levelOk = level >= recipe.level();
        final boolean learned = !recipe.blueprint()
                || professionManager.hasLearnedRecipe(player, recipe.id());
        final ProfessionRecipeCatalog.EconomyMetadata economy = catalog.economy(recipe.id());
        final ProfessionSpecializationEconomyPolicy.Effect specialization =
                ProfessionSpecializationEconomyPolicy.effectFor(player, recipe);
        final hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan plan =
                hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan.of(recipe, specialization, 1);
        final hu.taliann.icesmp.professions.ProfessionCraftTransaction transaction =
                new hu.taliann.icesmp.professions.ProfessionCraftTransaction(uniqueMaterials);
        final List<org.bukkit.inventory.ItemStack> previewOutputs =
                rawPreviewOutputs(recipe, 1, uniqueMaterials);
        final var inventoryPreflight = transaction.preflight(player, plan, previewOutputs);
        final boolean craftable = levelOk && learned && inventoryPreflight.applied();

        final List<Component> lore = new ArrayList<>();
        lore.add(grey("Szakma: ").append(recipe.profession().getDisplayName()));
        lore.add(grey("Kategória: " + recipe.category()));
        lore.add(grey("Gazdasági szerep: " + economy.category() + " • " + economy.tier()));
        if (!economy.dependencies().isEmpty()) {
            lore.add(Component.text("↔ Feldolgozási lánc: "
                            + String.join(", ", economy.dependencies()), NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (specialization.role() != ProfessionSpecializationEconomyPolicy.Role.NONE) {
            lore.add(Component.text("★ Szakosodás: " + specializationLabel(specialization),
                    NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        appendCanonicalPreview(lore, recipe, level);

        if ("gyakorlo".equals(recipe.kind())) {
            lore.add(Component.text("✎ Gyakorló recept — szakma-XP-ért, nem nyereségért",
                    NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(levelOk
                ? Component.text("✔ Szint " + recipe.level(), NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text("✘ Szint " + recipe.level() + " kell (most " + level + ")",
                        NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        if (recipe.blueprint()) {
            lore.add(learned
                    ? Component.text("✔ Recept megtanulva", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                    : recipe.lootOnly()
                    ? Component.text("✘ Csak legendás ellenfelektől szerezhető tervrajz",
                            NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
                    : Component.text("✘ Tervrajz kell (NPC / mob-drop)", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
        }
        if (recipe.faction() != null) {
            lore.add(Component.text("⚑ Csak: " + recipe.faction().getDisplayName()
                            + " (" + recipe.faction().getFullName() + ")", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(grey("Hozzávalók:"));
        for (final Map.Entry<Material, Integer> entry : plan.materialInputs().entrySet()) {
            final int have = countMaterial(player, entry.getKey(), uniqueMaterials);
            final boolean enough = have >= entry.getValue();
            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/"
                            + entry.getValue() + " " + prettyName(entry.getKey()),
                    enough ? NamedTextColor.GRAY : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        for (final Map.Entry<String, Integer> entry : plan.uniqueInputs().entrySet()) {
            final int have = countUnique(player, entry.getKey(), uniqueMaterials);
            final boolean enough = have >= entry.getValue();
            lore.add(Component.text("  " + (enough ? "✔ " : "✘ ") + have + "/"
                            + entry.getValue() + " " + uniqueMaterials.displayName(entry.getKey()),
                    enough ? NamedTextColor.AQUA : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        final int effectiveOutput = plan.effectiveOutputAmount(recipe.resultAmount());
        lore.add(Component.text("Eredmény: " + effectiveOutput + "× " + recipe.displayName(),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (recipe.affixTier() != null) {
            lore.add(Component.empty());
            lore.add(Component.text("✦ Sorsolt minőség + toldat-bónuszok",
                    NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (economy.batchable()) {
            final int maxCraftable = maxCraftableBatches(player, recipe, specialization,
                    economy.batchLimit(), uniqueMaterials, transaction);
            lore.add(Component.text("⇧ Shift+katt: 5-ös batch • effektív max: " + maxCraftable,
                    NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        if (inventoryPreflight.status()
                == hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status.INVENTORY_FULL) {
            lore.add(Component.text("✘ Nincs hely az effektív outputnak", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(craftable
                ? Component.text("» Kattints a craftoláshoz", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text("Zárolva", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));

        final NamedTextColor nameColor = craftable ? NamedTextColor.GREEN
                : (levelOk && learned ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
        final Component name = Component.text(
                (effectiveOutput > 1 ? effectiveOutput + "× " : "")
                        + recipe.displayName(), nameColor)
                .decoration(TextDecoration.ITALIC, false);
        return icon(recipe.result(), name, lore, recipe.affixTier() != null);
    }

    private static void appendCanonicalPreview(final List<Component> lore,
                                               final ProfessionRecipeCatalog.Recipe recipe,
                                               final int professionLevel) {
        if (recipe.templateId() == null) return;
        lore.add(Component.text("◆ Canonical template: " + recipe.templateId(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        final ItemTemplateRegistry registry = ItemTemplateRegistry.current();
        final ItemTemplate template = registry == null ? null
                : registry.find(recipe.templateId()).orElse(null);
        if (template == null) return;
        lore.add(Component.text("Tárgyszint: " + template.itemLevel()
                        + " • " + template.rarity().displayName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (template.armorFamily() != null) {
            lore.add(Component.text("Páncélcsalád: " + template.armorFamily().displayName()
                            + " (" + template.armorFamily().name() + ")", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (template.rolledStats().isEmpty()) return;
        final ConfigManager config = ConfigManager.current();
        if (config == null) return;
        final ProfessionCraftQualityPolicy.Tuning tuning = ProfessionCraftQualityPolicy.from(config);
        final double baseFloor = ProfessionCraftQualityPolicy.minimumQualityFloor(
                professionLevel, recipe.blueprint(), false, tuning);
        lore.add(Component.text("Roll-minőség: " + percent(baseFloor) + "%–100%",
                NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        final double chance = ProfessionCraftQualityPolicy.masterworkChance(
                professionLevel, recipe.masterwork(), tuning);
        if (chance > 0.0D) {
            final double masterworkFloor = ProfessionCraftQualityPolicy.minimumQualityFloor(
                    professionLevel, recipe.blueprint(), true, tuning);
            lore.add(Component.text("✦ Mestermű: " + percent(chance) + "% esély • floor "
                            + percent(masterworkFloor) + "%", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    private static String specializationLabel(
            final ProfessionSpecializationEconomyPolicy.Effect effect) {
        final String role = switch (effect.role()) {
            case PROCESSING_EFFICIENCY -> "feldolgozási hatékonyság";
            case PROCESSING_YIELD -> "feldolgozási hozam";
            case EQUIPMENT_EXPERTISE -> "felszerelés-szakértelem";
            case CONSUMABLE_EFFICIENCY -> "fogyóeszköz-hatékonyság";
            case CONSUMABLE_YIELD -> "fogyóeszköz-hozam";
            case SERVICE_EXPERTISE -> "szolgáltatás-szakértelem";
            case BLUEPRINT_EFFICIENCY -> "tervrajz-hatékonyság";
            case NONE -> "nincs";
        };
        if (effect.inputMultiplier() < 1.0D) {
            return role + " • -" + percent(1.0D - effect.inputMultiplier()) + "% alapanyag";
        }
        if (effect.outputMultiplier() > 1.0D) {
            return role + " • +" + percent(effect.outputMultiplier() - 1.0D) + "% batch hozam";
        }
        return role;
    }

    private static int percent(final double value) {
        return (int) Math.round(value * 100.0D);
    }

    private static List<org.bukkit.inventory.ItemStack> rawPreviewOutputs(
            final ProfessionRecipeCatalog.Recipe recipe, final int batches,
            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        final ArrayList<org.bukkit.inventory.ItemStack> result = new ArrayList<>(batches);
        for (int index = 0; index < batches; index++) {
            final org.bukkit.inventory.ItemStack stack = recipe.uniqueResult() == null
                    ? new org.bukkit.inventory.ItemStack(recipe.result(), recipe.resultAmount())
                    : uniqueMaterials.create(recipe.uniqueResult(), recipe.resultAmount());
            if (stack == null || stack.getType().isAir()) return List.of();
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static int maxCraftableBatches(
            final Player player, final ProfessionRecipeCatalog.Recipe recipe,
            final ProfessionSpecializationEconomyPolicy.Effect specialization, final int limit,
            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
            final hu.taliann.icesmp.professions.ProfessionCraftTransaction transaction) {
        int result = 0;
        final int bounded = Math.max(1, Math.min(64, limit));
        for (int candidate = 1; candidate <= bounded; candidate++) {
            final var plan = hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan.of(
                    recipe, specialization, candidate);
            final var outputs = rawPreviewOutputs(recipe, candidate, uniqueMaterials);
            if (!transaction.preflight(player, plan, outputs).applied()) break;
            result = candidate;
        }
        return result;
    }

    public static int countUnique(final Player player, final String uniqueId,
                                  final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        int count = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && uniqueId.equals(uniqueMaterials.idOf(item))) count += item.getAmount();
        }
        return count;
    }

    public static int countMaterial(final Player player, final Material material,
                                    final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {
        int count = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && uniqueMaterials.idOf(item) == null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static String prettyName(final Material material) {
        final String text = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
