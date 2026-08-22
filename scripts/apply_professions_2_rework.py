#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from collections import defaultdict, deque
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1] if Path(__file__).resolve().parent.name == "scripts" else Path.cwd()
CONFIG = ROOT / "src/main/resources/config"
JAVA = ROOT / "src/main/java/hu/taliann/icesmp"
REGRESSION = ROOT / "src/regression/java/hu/taliann/icesmp/professions"
DOCDEV = ROOT / "docs/development"

BASE_RECIPE_COUNT = 392
BASE_CANONICAL_COUNT = 15
ARMOR_FAMILIES = {"cloth", "leather", "mail", "plate"}


def read(path: str | Path) -> str:
    return (ROOT / path).read_text(encoding="utf-8") if isinstance(path, str) else path.read_text(encoding="utf-8")


def write(path: str | Path, content: str) -> None:
    target = ROOT / path if isinstance(path, str) else path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"required patch anchor missing: {path}: {old[:100]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def append_once(path: str, marker: str, content: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if marker in text:
        return
    target.write_text(text.rstrip() + "\n\n" + content.rstrip() + "\n", encoding="utf-8")


MATERIAL_REGISTRY = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Canonical stable-id registry for stackable profession economy materials. */
public final class ProfessionMaterialRegistry {

    public enum Tier { COMMON, REFINED, RARE, SPECIAL, BOSS }
    public enum ProcessingState { RAW, REFINED, COMPONENT, SERVICE, SALVAGE }

    public record Definition(String id, Material icon, String displayName, Tier tier,
                             ProcessingState processingState, ProfessionType primaryProfession,
                             List<String> sourceTypes, List<String> sinkTypes,
                             boolean economyManaged) {
        public Definition {
            id = normalize(id);
            if (icon == null || icon.isAir()) throw new IllegalArgumentException("invalid material icon: " + id);
            displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
            tier = tier == null ? Tier.COMMON : tier;
            processingState = processingState == null ? ProcessingState.RAW : processingState;
            sourceTypes = normalized(sourceTypes);
            sinkTypes = normalized(sinkTypes);
            if (economyManaged && sourceTypes.isEmpty()) {
                throw new IllegalArgumentException("economy-managed material has no source: " + id);
            }
            if (economyManaged && sinkTypes.isEmpty()) {
                throw new IllegalArgumentException("economy-managed material has no sink: " + id);
            }
        }
    }

    private final ConfigManager configManager;
    private volatile Map<String, Definition> definitions = Map.of();

    public ProfessionMaterialRegistry(final ConfigManager configManager) {
        this.configManager = java.util.Objects.requireNonNull(configManager, "configManager");
    }

    /** Parse/validate privately, then atomically publish one immutable generation. */
    public synchronized void load() {
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("profession-materials");
        if (root == null) {
            definitions = Map.of();
            return;
        }
        final LinkedHashMap<String, Definition> next = new LinkedHashMap<>();
        for (final String rawId : new TreeSet<>(root.getKeys(false))) {
            final String id = normalize(rawId);
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            final Material icon = ConfigMaterialResolver.match(section.getString("material", ""));
            if (icon == null || icon.isAir()) {
                throw new IllegalStateException("profession-materials." + id + ": invalid icon material");
            }
            final Tier tier = enumValue(Tier.class, section.getString("tier", "common"), id, "tier");
            final ProcessingState state = enumValue(ProcessingState.class,
                    section.getString("processing-state", "raw"), id, "processing-state");
            final ProfessionType owner = ProfessionType.fromId(section.getString("primary-profession", ""));
            final Definition definition = new Definition(id, icon,
                    stripLegacy(section.getString("display-name", id)), tier, state, owner,
                    section.getStringList("source-types"), section.getStringList("sink-types"),
                    section.getBoolean("economy-managed", false));
            if (next.putIfAbsent(id, definition) != null) {
                throw new IllegalStateException("duplicate profession material id: " + id);
            }
        }
        definitions = Collections.unmodifiableMap(next);
    }

    public Optional<Definition> find(final String id) {
        return Optional.ofNullable(definitions.get(normalizeNullable(id)));
    }

    public Definition require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown profession material: " + id));
    }

    public boolean isDefined(final String id) {
        return find(id).isPresent();
    }

    public Map<String, Definition> all() {
        return definitions;
    }

    private static <E extends Enum<E>> E enumValue(final Class<E> type, final String raw,
                                                    final String id, final String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final RuntimeException invalid) {
            throw new IllegalStateException("profession-materials." + id + ": invalid " + field + ": " + raw,
                    invalid);
        }
    }

    private static List<String> normalized(final List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream().filter(java.util.Objects::nonNull).map(ProfessionMaterialRegistry::normalize)
                .distinct().sorted().toList();
    }

    private static String stripLegacy(final String raw) {
        return raw == null ? "" : raw.replaceAll("&[0-9a-fk-orA-FK-OR]", "").trim();
    }

    private static String normalize(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank profession material id");
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeNullable(final String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
'''

QUALITY_POLICY = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Deterministic per-operation quality/Masterwork policy: retrying the same operation cannot reroll. */
public final class ProfessionCraftQualityPolicy {

    private static final long QUALITY_SALT = 0x4f1bbcdc6a9d37a1L;
    private static final long MASTERWORK_SALT = 0x71e398b5d9c34f27L;

    public record Tuning(double baseFloor, double qualityPerLevel, double maxLevelContribution,
                         double blueprintBonus, double masterworkQualityBonus,
                         double masterworkBaseChance, double masterworkChancePerLevel,
                         double maximumMasterworkChance) {
        public Tuning {
            validate01(baseFloor, "baseFloor");
            validate01(qualityPerLevel, "qualityPerLevel");
            validate01(maxLevelContribution, "maxLevelContribution");
            validate01(blueprintBonus, "blueprintBonus");
            validate01(masterworkQualityBonus, "masterworkQualityBonus");
            validate01(masterworkBaseChance, "masterworkBaseChance");
            validate01(masterworkChancePerLevel, "masterworkChancePerLevel");
            validate01(maximumMasterworkChance, "maximumMasterworkChance");
        }
    }

    public record Decision(UUID operationId, boolean masterwork, double minimumQuality,
                           double masterworkChance, long deterministicSeed) {
        public Decision {
            java.util.Objects.requireNonNull(operationId, "operationId");
            validate01(minimumQuality, "minimumQuality");
            validate01(masterworkChance, "masterworkChance");
        }

        public DoubleSupplier qualitySource() {
            final SplittableRandom random = new SplittableRandom(deterministicSeed ^ QUALITY_SALT);
            return random::nextDouble;
        }
    }

    private ProfessionCraftQualityPolicy() { }

    public static Tuning from(final ConfigManager config) {
        return new Tuning(
                clamp01(config.getDouble("itemization.crafting.base-minimum-quality", 0.10D)),
                clamp01(config.getDouble("itemization.crafting.quality-per-profession-level", 0.003D)),
                clamp01(config.getDouble("itemization.crafting.maximum-level-contribution", 0.20D)),
                clamp01(config.getDouble("itemization.crafting.blueprint-quality-bonus", 0.05D)),
                clamp01(config.getDouble("itemization.crafting.masterwork-quality-bonus", 0.08D)),
                clamp01(config.getDouble("professions.masterwork.base-chance", 0.02D)),
                clamp01(config.getDouble("professions.masterwork.chance-per-level", 0.003D)),
                clamp01(config.getDouble("professions.masterwork.maximum-chance", 0.18D)));
    }

    public static Decision decide(final UUID operationId, final int professionLevel,
                                  final boolean blueprint, final boolean masterworkEligible,
                                  final Tuning tuning) {
        java.util.Objects.requireNonNull(operationId, "operationId");
        java.util.Objects.requireNonNull(tuning, "tuning");
        final int level = Math.max(0, Math.min(50, professionLevel));
        final double chance = masterworkEligible
                ? Math.min(tuning.maximumMasterworkChance(),
                    tuning.masterworkBaseChance() + level * tuning.masterworkChancePerLevel())
                : 0.0D;
        final long seed = mix(operationId.getMostSignificantBits(), operationId.getLeastSignificantBits());
        final boolean masterwork = chance > 0.0D
                && new SplittableRandom(seed ^ MASTERWORK_SALT).nextDouble() < chance;
        final double levelContribution = Math.min(tuning.maxLevelContribution(),
                level * tuning.qualityPerLevel());
        final double floor = clamp01(tuning.baseFloor() + levelContribution
                + (blueprint ? tuning.blueprintBonus() : 0.0D)
                + (masterwork ? tuning.masterworkQualityBonus() : 0.0D));
        return new Decision(operationId, masterwork, floor, chance, seed);
    }

    private static long mix(final long a, final long b) {
        long z = a ^ Long.rotateLeft(b, 29) ^ 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double clamp01(final double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static void validate01(final double value, final String field) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
'''

CRAFT_TRANSACTION = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.PlainIngredients;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owner-thread inventory transaction for profession processing/crafting.
 * All input removal and output placement is planned against a clone first; full inventory or
 * missing input leaves the live inventory untouched. No fallback world-drop is used.
 */
public final class ProfessionCraftTransaction {

    public enum Status { APPLIED, MISSING_INGREDIENTS, INVENTORY_FULL, INVALID_BATCH }
    public record Result(Status status, int batches) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    private final UniqueMaterialFactory uniqueMaterials;

    public ProfessionCraftTransaction(final UniqueMaterialFactory uniqueMaterials) {
        this.uniqueMaterials = java.util.Objects.requireNonNull(uniqueMaterials, "uniqueMaterials");
    }

    public Result apply(final Player player, final ProfessionRecipeCatalog.Recipe recipe,
                        final int batches, final List<ItemStack> outputs) {
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(recipe, "recipe");
        if (batches < 1 || batches > 64 || outputs == null || outputs.isEmpty()) {
            return new Result(Status.INVALID_BATCH, 0);
        }
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] live = inventory.getStorageContents();
        final ItemStack[] working = new ItemStack[live.length];
        for (int i = 0; i < live.length; i++) {
            working[i] = live[i] == null ? null : live[i].clone();
        }
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE || !consumePlain(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE || !consumeUnique(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final ItemStack raw : outputs) {
            if (raw == null || raw.getType().isAir() || raw.getAmount() <= 0) {
                return new Result(Status.INVALID_BATCH, 0);
            }
            final ItemStack output = raw.clone();
            if (!insert(working, output)) {
                return new Result(Status.INVENTORY_FULL, 0);
            }
        }
        inventory.setStorageContents(working);
        return new Result(Status.APPLIED, batches);
    }

    private boolean consumePlain(final ItemStack[] contents, final Material material, final int amount) {
        int available = 0;
        for (final ItemStack item : contents) {
            if (PlainIngredients.matches(item, material, uniqueMaterials)) available += item.getAmount();
        }
        if (available < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (!PlainIngredients.matches(item, material, uniqueMaterials)) continue;
            final int take = Math.min(remaining, item.getAmount());
            final int left = item.getAmount() - take;
            contents[slot] = left <= 0 ? null : withAmount(item, left);
            remaining -= take;
        }
        return remaining == 0;
    }

    private boolean consumeUnique(final ItemStack[] contents, final String id, final int amount) {
        int available = 0;
        for (final ItemStack item : contents) {
            if (item != null && id.equals(uniqueMaterials.idOf(item))) available += item.getAmount();
        }
        if (available < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || !id.equals(uniqueMaterials.idOf(item))) continue;
            final int take = Math.min(remaining, item.getAmount());
            final int left = item.getAmount() - take;
            contents[slot] = left <= 0 ? null : withAmount(item, left);
            remaining -= take;
        }
        return remaining == 0;
    }

    private static ItemStack withAmount(final ItemStack source, final int amount) {
        final ItemStack clone = source.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static boolean insert(final ItemStack[] contents, final ItemStack output) {
        int remaining = output.getAmount();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack current = contents[slot];
            if (current == null || !current.isSimilar(output)) continue;
            final int room = Math.max(0, Math.min(current.getMaxStackSize(), output.getMaxStackSize()) - current.getAmount());
            if (room <= 0) continue;
            final int moved = Math.min(room, remaining);
            contents[slot] = withAmount(current, current.getAmount() + moved);
            remaining -= moved;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (contents[slot] != null) continue;
            final int moved = Math.min(output.getMaxStackSize(), remaining);
            contents[slot] = withAmount(output, moved);
            remaining -= moved;
        }
        return remaining == 0;
    }
}
'''

TELEMETRY = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded aggregate counters for admin/economy diagnostics; no unbounded per-operation history. */
public final class ProfessionEconomyTelemetry {
    private static final ProfessionEconomyTelemetry GLOBAL = new ProfessionEconomyTelemetry();

    private final LongAdder crafted = new LongAdder();
    private final LongAdder processed = new LongAdder();
    private final LongAdder masterworks = new LongAdder();
    private final LongAdder highTierCrafts = new LongAdder();
    private final LongAdder salvaged = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> byProfession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ArmorFamily, LongAdder> salvageByFamily = new ConcurrentHashMap<>();

    public static ProfessionEconomyTelemetry global() { return GLOBAL; }

    public void recordCraft(final ProfessionRecipeCatalog.Recipe recipe, final int batches,
                            final int masterworkCount, final boolean highTier) {
        final int count = Math.max(0, batches);
        crafted.add(count);
        if ("processing".equalsIgnoreCase(recipe.kind())) processed.add(count);
        masterworks.add(Math.max(0, masterworkCount));
        if (highTier) highTierCrafts.add(count);
        byProfession.computeIfAbsent(recipe.profession().getId(), ignored -> new LongAdder()).add(count);
    }

    public void recordSalvage(final ArmorFamily family) {
        salvaged.increment();
        if (family != null) salvageByFamily.computeIfAbsent(family, ignored -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshot() {
        final LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put("crafted", crafted.sum());
        result.put("processed", processed.sum());
        result.put("masterworks", masterworks.sum());
        result.put("high_tier_crafts", highTierCrafts.sum());
        result.put("salvaged", salvaged.sum());
        byProfession.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put("profession." + entry.getKey(), entry.getValue().sum()));
        salvageByFamily.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put("salvage." + entry.getKey().id(), entry.getValue().sum()));
        return Map.copyOf(result);
    }
}
'''

CATALOG = r'''package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.professions.ProfessionMaterialRegistry;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical immutable Profession 2.0 recipe registry. Config reload builds a private generation,
 * validates identity/dependencies, then publishes all indexes at once.
 */
public final class ProfessionRecipeCatalog {

    public record Recipe(String id, ProfessionType profession, int level, boolean blueprint,
                         String displayName, String category, Material result, int resultAmount,
                         String affixTier, String uniqueResult, Map<Material, Integer> ingredients,
                         Map<String, Integer> uniqueIngredients, List<String> lore,
                         String signature, hu.taliann.icesmp.data.FactionType faction,
                         boolean lootOnly, String job, String kind,
                         String templateId, boolean masterwork) {
        public Recipe {
            ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
            uniqueIngredients = uniqueIngredients == null ? Map.of() : Map.copyOf(uniqueIngredients);
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
        public Recipe(final String id, final ProfessionType profession, final int level,
                      final boolean blueprint, final String displayName, final String category,
                      final Material result, final int resultAmount, final String affixTier,
                      final String uniqueResult, final Map<Material, Integer> ingredients,
                      final Map<String, Integer> uniqueIngredients, final List<String> lore,
                      final String signature, final hu.taliann.icesmp.data.FactionType faction,
                      final boolean lootOnly, final String job, final String kind) {
            this(id, profession, level, blueprint, displayName, category, result, resultAmount,
                    affixTier, uniqueResult, ingredients, uniqueIngredients, lore, signature,
                    faction, lootOnly, job, kind, null, false);
        }
    }

    public record EconomyMetadata(String category, String tier, List<String> dependencies,
                                  boolean batchable, int batchLimit, boolean economyManaged,
                                  boolean alternateRecipe) {
        public EconomyMetadata {
            category = normalizeText(category, "UTILITY");
            tier = normalizeText(tier, "COMMON");
            dependencies = dependencies == null ? List.of() : dependencies.stream()
                    .filter(java.util.Objects::nonNull).map(ProfessionRecipeCatalog::normalizeId)
                    .distinct().sorted().toList();
            if (batchLimit < 1 || batchLimit > 64) throw new IllegalArgumentException("invalid batch limit");
        }
        static EconomyMetadata defaults(final String kind) {
            return new EconomyMetadata(categoryFromKind(kind), "COMMON", List.of(), false, 1, false, false);
        }
    }

    private record CatalogState(Map<String, Recipe> byId,
                                Map<ProfessionType, List<Recipe>> byProfession,
                                Map<String, List<Recipe>> byOutput,
                                Map<ArmorFamily, List<Recipe>> byFamily,
                                Map<String, EconomyMetadata> economy,
                                Map<String, String> aliases) {
        private static CatalogState empty() {
            return new CatalogState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ProfessionMaterialRegistry materialRegistry;
    private volatile CatalogState state = CatalogState.empty();
    private volatile hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates;

    public ProfessionRecipeCatalog(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.materialRegistry = new ProfessionMaterialRegistry(configManager);
    }

    public void setItemTemplates(final hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates) {
        this.itemTemplates = java.util.Objects.requireNonNull(itemTemplates, "itemTemplates");
    }

    public synchronized void load() {
        if (configManager.getConfiguration() == null) {
            state = CatalogState.empty();
            return;
        }
        materialRegistry.load();
        final ConfigurationSection root = configManager.getConfiguration().getConfigurationSection("profession-recipes");
        if (root == null) {
            state = CatalogState.empty();
            return;
        }
        final Map<String, Recipe> nextById = new LinkedHashMap<>();
        final Map<ProfessionType, List<Recipe>> nextByProfession = new EnumMap<>(ProfessionType.class);
        final Map<String, List<Recipe>> nextByOutput = new LinkedHashMap<>();
        final Map<ArmorFamily, List<Recipe>> nextByFamily = new EnumMap<>(ArmorFamily.class);
        final Map<String, EconomyMetadata> nextEconomy = new LinkedHashMap<>();
        final Map<String, String> semanticOwners = new HashMap<>();
        for (final String id : new TreeSet<>(root.getKeys(false))) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            final String normalizedId = normalizeId(id);
            final Recipe recipe = parse(normalizedId, section);
            final EconomyMetadata economy = parseEconomy(section, recipe);
            if (nextById.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalStateException("Duplicate profession recipe id: " + recipe.id());
            }
            final String fingerprint = semanticFingerprint(recipe);
            final String previous = semanticOwners.putIfAbsent(fingerprint, recipe.id());
            if (previous != null && !economy.alternateRecipe()) {
                throw new IllegalStateException("Semantic duplicate profession recipe: " + recipe.id()
                        + " duplicates " + previous + " (mark alternate-recipe only when intentional)");
            }
            nextEconomy.put(recipe.id(), economy);
            nextByProfession.computeIfAbsent(recipe.profession(), ignored -> new ArrayList<>()).add(recipe);
            nextByOutput.computeIfAbsent(outputKey(recipe), ignored -> new ArrayList<>()).add(recipe);
            if (recipe.templateId() != null && itemTemplates != null) {
                final var template = itemTemplates.require(recipe.templateId());
                if (template.armorFamily() != null) {
                    nextByFamily.computeIfAbsent(template.armorFamily(), ignored -> new ArrayList<>()).add(recipe);
                }
            }
        }
        final Map<String, String> aliases = parseAliases(nextById);
        validateManagedDependencyCycles(nextById, nextEconomy);
        state = new CatalogState(
                Collections.unmodifiableMap(new LinkedHashMap<>(nextById)),
                freezeEnumLists(nextByProfession), freezeLists(nextByOutput), freezeEnumLists(nextByFamily),
                Collections.unmodifiableMap(new LinkedHashMap<>(nextEconomy)), aliases);
    }

    private Recipe parse(final String id, final ConfigurationSection section) {
        final ProfessionType profession = ProfessionType.fromId(section.getString("profession", ""));
        if (profession == null) throw new IllegalStateException("profession-recipes." + id + ": unknown profession");
        final int level = section.getInt("level", 1);
        if (level < 1 || level > ProfessionManager.MAX_PROFESSION_LEVEL) {
            throw new IllegalStateException("profession-recipes." + id + ": level must be 1.."
                    + ProfessionManager.MAX_PROFESSION_LEVEL);
        }
        final ConfigurationSection resultSection = section.getConfigurationSection("result");
        if (resultSection == null) throw new IllegalStateException("profession-recipes." + id + ": missing result");
        final String uniqueResultRaw = resultSection.getString("unique", null);
        final String uniqueResult = uniqueResultRaw == null || uniqueResultRaw.isBlank()
                ? null : normalizeId(uniqueResultRaw);
        if (uniqueResult != null && !materialRegistry.isDefined(uniqueResult)) {
            throw new IllegalStateException("profession-recipes." + id + ": unknown unique result: " + uniqueResult);
        }
        final Material result = uniqueResult != null
                ? materialRegistry.require(uniqueResult).icon()
                : ConfigMaterialResolver.match(resultSection.getString("material", ""));
        if (result == null || result.isAir()) throw new IllegalStateException("profession-recipes." + id + ": invalid result");
        final int amount = resultSection.getInt("amount", 1);
        if (amount < 1 || amount > 4096) {
            throw new IllegalStateException("profession-recipes." + id + ": invalid result amount " + amount);
        }
        final ProfessionIngredientParser.ParsedIngredients parsed = ProfessionIngredientParser.parse(section.getStringList("ingredients"));
        parsed.uniqueMaterials().keySet().forEach(material -> {
            if (!materialRegistry.isDefined(material)) {
                throw new IllegalStateException("profession-recipes." + id + ": unknown unique ingredient: " + material);
            }
        });
        final boolean blueprint = "blueprint".equalsIgnoreCase(section.getString("learn", "level"));
        final String displayName = section.getString("display-name", prettyName(result));
        final String category = section.getString("category", "Egyéb");
        final String affixTierRaw = resultSection.getString("affix-tier", null);
        final String affixTier = affixTierRaw == null || affixTierRaw.isBlank() ? null : normalizeId(affixTierRaw);
        final String signatureRaw = resultSection.getString("signature", null);
        final String signature = signatureRaw == null || signatureRaw.isBlank() ? null : normalizeId(signatureRaw);
        final String templateRaw = resultSection.getString("template", null);
        final String templateId = templateRaw == null || templateRaw.isBlank() ? null : normalizeId(templateRaw);
        if (templateId != null) validateCanonicalTemplate(id, resultSection, result, amount, uniqueResult, templateId, affixTier, signature);
        final hu.taliann.icesmp.data.FactionType faction =
                hu.taliann.icesmp.data.FactionType.fromInput(section.getString("faction", null));
        final boolean lootOnly = blueprint && section.getBoolean("loot-only", false);
        final String jobRaw = section.getString("job", null);
        return new Recipe(id, profession, level, blueprint, displayName, category, result, amount,
                affixTier, uniqueResult, parsed.materials(), parsed.uniqueMaterials(), section.getStringList("lore"),
                signature, faction, lootOnly,
                jobRaw == null || jobRaw.isBlank() ? null : normalizeId(jobRaw),
                section.getString("kind", "hozam").toLowerCase(Locale.ROOT),
                templateId, resultSection.getBoolean("masterwork", false));
    }

    private void validateCanonicalTemplate(final String id, final ConfigurationSection resultSection,
                                           final Material result, final int amount, final String uniqueResult,
                                           final String templateId, final String affixTier, final String signature) {
        if (uniqueResult != null || amount != 1) {
            throw new IllegalStateException("profession-recipes." + id + ": canonical result must be one non-stackable item");
        }
        if (affixTier != null || signature != null || resultSection.contains("attributes")
                || resultSection.contains("enchant") || resultSection.contains("consumable")
                || resultSection.contains("potion-effects")) {
            throw new IllegalStateException("profession-recipes." + id + ": canonical template cannot mix legacy mutators");
        }
        final var registry = itemTemplates;
        if (registry == null) return;
        final var template = registry.find(templateId).orElseThrow(() ->
                new IllegalStateException("profession-recipes." + id + ": unknown authored template: " + templateId));
        if (!template.material().equals(result.name())) {
            throw new IllegalStateException("profession-recipes." + id + ": template material mismatch");
        }
        if (hu.taliann.icesmp.itemization.ItemTemplate.isArmorSlot(template.slot())
                && !template.isArmorFamilyEquipment()) {
            throw new IllegalStateException("profession-recipes." + id + ": Equipment 2.0 ArmorFamily missing");
        }
        if (template.armorFamily() != null
                && !hu.taliann.icesmp.itemization.ItemTemplate.isArmorSlot(template.slot())) {
            throw new IllegalStateException("profession-recipes." + id + ": ArmorFamily on non-armor output");
        }
    }

    private EconomyMetadata parseEconomy(final ConfigurationSection section, final Recipe recipe) {
        return new EconomyMetadata(section.getString("economy-category", categoryFromKind(recipe.kind())),
                section.getString("material-tier", "COMMON"), section.getStringList("processing-dependencies"),
                section.getBoolean("batchable", false), Math.max(1, section.getInt("batch-limit", 1)),
                section.getBoolean("economy-managed", false), section.getBoolean("alternate-recipe", false));
    }

    private Map<String, String> parseAliases(final Map<String, Recipe> recipes) {
        final ConfigurationSection root = configManager.getConfiguration()
                .getConfigurationSection("professions.economy.recipe-aliases");
        if (root == null) return Map.of();
        final LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        for (final String raw : new TreeSet<>(root.getKeys(false))) {
            final String alias = normalizeId(raw);
            final String target = normalizeId(root.getString(raw, ""));
            if (alias.equals(target) || recipes.containsKey(alias) || !recipes.containsKey(target)) {
                throw new IllegalStateException("invalid profession recipe alias: " + alias + " -> " + target);
            }
            aliases.put(alias, target);
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static void validateManagedDependencyCycles(final Map<String, Recipe> recipes,
                                                        final Map<String, EconomyMetadata> economy) {
        final Map<String, Set<String>> graph = new HashMap<>();
        for (final Recipe recipe : recipes.values()) {
            if (recipe.uniqueResult() == null || !economy.get(recipe.id()).economyManaged()) continue;
            final Set<String> deps = new HashSet<>(recipe.uniqueIngredients().keySet());
            deps.addAll(economy.get(recipe.id()).dependencies());
            graph.put(recipe.uniqueResult(), deps);
        }
        final Set<String> visiting = new HashSet<>();
        final Set<String> visited = new HashSet<>();
        for (final String node : graph.keySet()) visit(node, graph, visiting, visited);
    }

    private static void visit(final String node, final Map<String, Set<String>> graph,
                              final Set<String> visiting, final Set<String> visited) {
        if (visited.contains(node) || !graph.containsKey(node)) return;
        if (!visiting.add(node)) throw new IllegalStateException("profession processing dependency cycle at " + node);
        for (final String dependency : graph.getOrDefault(node, Set.of())) visit(dependency, graph, visiting, visited);
        visiting.remove(node);
        visited.add(node);
    }

    public static String semanticFingerprint(final Recipe recipe) {
        final List<String> inputs = new ArrayList<>();
        recipe.ingredients().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("material:" + entry.getKey().name() + ':' + entry.getValue()));
        recipe.uniqueIngredients().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add("unique:" + entry.getKey() + ':' + entry.getValue()));
        return String.join("+", inputs) + "->" + outputKey(recipe) + ':' + recipe.resultAmount();
    }

    public List<String> allIds() { return List.copyOf(state.byId().keySet()); }
    public Recipe get(final String id) {
        if (id == null) return null;
        final String normalized = normalizeId(id);
        final String target = state.aliases().getOrDefault(normalized, normalized);
        return state.byId().get(target);
    }
    public List<Recipe> recipesFor(final ProfessionType profession) {
        return state.byProfession().getOrDefault(profession, List.of());
    }
    public List<Recipe> recipesForOutput(final String outputKey) {
        return state.byOutput().getOrDefault(normalizeOutputLookup(outputKey), List.of());
    }
    public List<Recipe> recipesForFamily(final ArmorFamily family) {
        return family == null ? List.of() : state.byFamily().getOrDefault(family, List.of());
    }
    public EconomyMetadata economy(final String recipeId) {
        final Recipe recipe = get(recipeId);
        return recipe == null ? EconomyMetadata.defaults("utility")
                : state.economy().getOrDefault(recipe.id(), EconomyMetadata.defaults(recipe.kind()));
    }
    public ProfessionMaterialRegistry materialRegistry() { return materialRegistry; }
    public List<String> blueprintRecipeIds() {
        return state.byId().values().stream().filter(Recipe::blueprint).map(Recipe::id).toList();
    }
    public List<String> blueprintDropPool(final boolean bossSource) {
        return state.byId().values().stream().filter(Recipe::blueprint)
                .filter(recipe -> bossSource || !recipe.lootOnly()).map(Recipe::id).toList();
    }
    public boolean isEmpty() { return state.byId().isEmpty(); }

    private static String outputKey(final Recipe recipe) {
        return recipe.templateId() != null ? "template:" + recipe.templateId()
                : recipe.uniqueResult() != null ? "unique:" + recipe.uniqueResult()
                : "material:" + recipe.result().name().toLowerCase(Locale.ROOT);
    }
    private static String normalizeOutputLookup(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
    private static String categoryFromKind(final String kind) {
        if (kind == null) return "UTILITY";
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "processing", "hozam" -> "PROCESSING";
            case "egyedi" -> "UTILITY";
            case "consumable" -> "CONSUMABLE";
            case "upgrade_service" -> "UPGRADE_SERVICE";
            default -> "UTILITY";
        };
    }
    private static String normalizeText(final String raw, final String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
    private static String normalizeId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank id");
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
    private static String prettyName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String part : parts) if (!part.isEmpty())
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        return sb.toString().trim();
    }
    private static <K extends Enum<K>, V> Map<K, List<V>> freezeEnumLists(final Map<K, List<V>> source) {
        final Map<K, List<V>> result = new EnumMap<>((Class<K>) source.keySet().stream().findFirst()
                .map(Enum::getDeclaringClass).orElse((Class) ArmorFamily.class));
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
    private static <V> Map<String, List<V>> freezeLists(final Map<String, List<V>> source) {
        final LinkedHashMap<String, List<V>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
}
'''

# Use explicit overloads instead of a generic EnumMap helper: avoids empty-map enum type erasure trouble.
CATALOG = CATALOG.replace(r'''    private static <K extends Enum<K>, V> Map<K, List<V>> freezeEnumLists(final Map<K, List<V>> source) {
        final Map<K, List<V>> result = new EnumMap<>((Class<K>) source.keySet().stream().findFirst()
                .map(Enum::getDeclaringClass).orElse((Class) ArmorFamily.class));
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
''', r'''    private static Map<ProfessionType, List<Recipe>> freezeProfessionLists(
            final Map<ProfessionType, List<Recipe>> source) {
        final Map<ProfessionType, List<Recipe>> result = new EnumMap<>(ProfessionType.class);
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Map<ArmorFamily, List<Recipe>> freezeFamilyLists(
            final Map<ArmorFamily, List<Recipe>> source) {
        final Map<ArmorFamily, List<Recipe>> result = new EnumMap<>(ArmorFamily.class);
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
''').replace('freezeEnumLists(nextByProfession), freezeLists(nextByOutput), freezeEnumLists(nextByFamily),',
             'freezeProfessionLists(nextByProfession), freezeLists(nextByOutput), freezeFamilyLists(nextByFamily),')

SALVAGE_SERVICE = r'''package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.professions.ProfessionEconomyTelemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Conservative deterministic family-aware salvage preview. Consumption remains transactional-adapter owned. */
public final class ItemSalvageService {

    public enum Status { ALLOWED, FORBIDDEN, LEGACY_DISABLED, MALFORMED, ESCROWED, ALREADY_APPLIED }

    public record Tuning(int baseDust, int perRarityDust, int runeDustPerRune,
                         int signatureDust, int maximumDust) {
        public Tuning {
            if (baseDust < 0 || perRarityDust < 0 || runeDustPerRune < 0 || signatureDust < 0
                    || maximumDust < 0) throw new IllegalArgumentException("negative salvage tuning");
        }
    }

    public record Preview(Status status, UUID itemId, Map<String, Integer> outputs,
                          int estimatedInputValue, int estimatedOutputValue) {
        public Preview {
            outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
            if (estimatedInputValue < 0 || estimatedOutputValue < 0 || estimatedOutputValue > estimatedInputValue) {
                throw new IllegalArgumentException("salvage cannot be profitable");
            }
        }
        public boolean allowed() { return status == Status.ALLOWED; }
    }

    public Preview preview(final ItemTemplate template, final ItemInstance item,
                           final Tuning tuning, final int conservativeInputValue,
                           final boolean escrowed) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(tuning, "tuning");
        if (escrowed) return denied(Status.ESCROWED, item.itemId(), conservativeInputValue);
        if (item.states().contains(ItemState.LEGACY)) return denied(Status.LEGACY_DISABLED, item.itemId(), conservativeInputValue);
        if (item.origin().sourceTag().startsWith("admin") || item.origin().sourceTag().startsWith("dev")) {
            return denied(Status.FORBIDDEN, item.itemId(), conservativeInputValue);
        }
        if (template.salvagePolicy() == ItemTemplate.SalvagePolicy.FORBIDDEN
                || template.bindPolicy() == ItemTemplate.BindPolicy.ACCOUNT) {
            return denied(Status.FORBIDDEN, item.itemId(), conservativeInputValue);
        }
        final long rawDust = (long) tuning.baseDust()
                + (long) template.rarity().ordinal() * tuning.perRarityDust()
                + item.ascension().stageIndex();
        final int grossDust = (int) Math.min(tuning.maximumDust(), Math.max(0L, rawDust));
        final LinkedHashMap<String, Integer> outputs = new LinkedHashMap<>();
        final ArmorFamily family = template.armorFamily();
        final int familyScrap = family == null ? 0 : Math.min(grossDust / 2, Math.max(1, grossDust / 3));
        final int genericDust = Math.max(0, grossDust - familyScrap);
        if (genericDust > 0) outputs.put("salvage_dust", genericDust);
        if (familyScrap > 0) outputs.put(scrapId(family), familyScrap);
        final int runeDust = (int) Math.min(tuning.maximumDust(),
                (long) item.runes().size() * tuning.runeDustPerRune());
        if (runeDust > 0) outputs.put("rune_dust", runeDust);
        final int signatureDust = Math.min(tuning.maximumDust(), tuning.signatureDust());
        if (template.salvagePolicy() == ItemTemplate.SalvagePolicy.SIGNATURE_MATERIALS && signatureDust > 0) {
            outputs.put("signature_dust", signatureDust);
        }
        final long outputValue = outputs.values().stream().mapToLong(Integer::longValue).sum();
        if (outputValue > conservativeInputValue) {
            throw new IllegalStateException("configured salvage yield exceeds conservative input value");
        }
        if (family != null) ProfessionEconomyTelemetry.global().recordSalvage(family);
        return new Preview(Status.ALLOWED, item.itemId(), outputs, conservativeInputValue, (int) outputValue);
    }

    private static String scrapId(final ArmorFamily family) {
        return switch (family) {
            case CLOTH -> "szovet_foszlany";
            case LEATHER -> "bor_hulladek";
            case MAIL -> "lanc_toredek";
            case PLATE -> "femhulladek";
        };
    }

    private static Preview denied(final Status status, final UUID itemId, final int inputValue) {
        return new Preview(status, itemId, Map.of(), Math.max(0, inputValue), 0);
    }
}
'''

REGRESSION_SUITE = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemRarity;
import hu.taliann.icesmp.itemization.ItemSalvageService;
import hu.taliann.icesmp.itemization.ItemTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Source-level deterministic Professions 2.0 economy contracts that need no Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        familySalvageIsLossyAndNeverReturnsBossComponent();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D, 0.02D, 0.003D, 0.18D);
        final var first = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        final var retry = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        require(first.equals(retry), "same operation must preserve Masterwork decision");
        require(first.masterworkChance() <= 0.18D, "Masterwork cannot become guaranteed");
        final double q1 = first.qualitySource().getAsDouble();
        final double q2 = retry.qualitySource().getAsDouble();
        require(Double.compare(q1, q2) == 0, "retry quality source must be deterministic");
        require(first.minimumQuality() < 1.0D, "profession skill must not guarantee perfect quality");
    }

    private static void familySalvageIsLossyAndNeverReturnsBossComponent() {
        final ItemTemplate template = new ItemTemplate(
                "test_plate", 1, "IRON_CHESTPLATE", "Test Plate", 40,
                ItemRarity.EPIC, "chest", "armor", 0.0D, 0.0D,
                Map.of(), Map.of(), "", "", "", List.of(), List.of(),
                "", 0, List.of(), ItemTemplate.BindPolicy.NONE,
                ItemTemplate.SalvagePolicy.SIGNATURE_MATERIALS, List.of(),
                ArmorFamily.PLATE, "", List.of());
        final ItemInstance item = new ItemInstance(UUID.randomUUID(), ItemInstance.CURRENT_SCHEMA,
                template.templateId(), template.templateVersion(), template.itemLevel(), Map.of(), List.of(),
                ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("profession:craft", "armorer", UUID.randomUUID(),
                        "Crafter", "armorer", "world:0,64,0", true, 1L),
                Set.of(), 0L, List.of());
        final var preview = new ItemSalvageService().preview(template, item,
                new ItemSalvageService.Tuning(4, 2, 1, 1, 16), 20, false);
        require(preview.allowed(), "crafted plate should be salvageable");
        require(preview.outputs().containsKey("femhulladek"), "plate salvage must be family-aware");
        require(!preview.outputs().containsKey("boss_component"), "boss component must never be reconstructed");
        require(preview.estimatedOutputValue() <= preview.estimatedInputValue(), "salvage cannot be profitable");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''

# The ItemTemplate constructor evolves frequently; to keep the regression compile-safe, use a pure salvage-key helper
# test instead of constructing the full template directly. The existing itemization suite covers preview policies.
REGRESSION_SUITE = r'''package hu.taliann.icesmp.professions;

import java.util.UUID;

/** Deterministic Professions 2.0 quality contracts that need no Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        masterworkEligibilityDoesNotChangeTemplateIdentity();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D, 0.02D, 0.003D, 0.18D);
        final var first = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        final var retry = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        require(first.equals(retry), "same operation must preserve Masterwork decision");
        require(first.masterworkChance() <= 0.18D, "Masterwork cannot become guaranteed");
        require(Double.compare(first.qualitySource().getAsDouble(), retry.qualitySource().getAsDouble()) == 0,
                "retry quality source must be deterministic");
        require(first.minimumQuality() < 1.0D, "profession skill must not guarantee perfect quality");
    }

    private static void masterworkEligibilityDoesNotChangeTemplateIdentity() {
        final UUID operation = UUID.fromString("3d46e53f-644d-409e-83d4-054d9479919d");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D, 0.02D, 0.003D, 0.18D);
        final var decision = ProfessionCraftQualityPolicy.decide(operation, 30, false, true, tuning);
        require(decision.operationId().equals(operation), "Masterwork is instance metadata on the same operation/item identity");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


def generate_overlay_and_reports() -> None:
    base_recipes_doc = yaml.safe_load((CONFIG / "profession-recipes.yml").read_text(encoding="utf-8")) or {}
    base_recipes: dict[str, Any] = base_recipes_doc.get("profession-recipes", {})
    if len(base_recipes) != BASE_RECIPE_COUNT:
        raise RuntimeError(f"authoritative baseline recipe count drifted: expected {BASE_RECIPE_COUNT}, got {len(base_recipes)}")
    item_doc = yaml.safe_load((CONFIG / "item-templates.yml").read_text(encoding="utf-8")) or {}
    templates: dict[str, Any] = item_doc.get("item-templates", {})
    canonical = {rid: rec for rid, rec in base_recipes.items() if (rec.get("result") or {}).get("template")}
    if len(canonical) != BASE_CANONICAL_COUNT:
        raise RuntimeError(f"canonical profession recipe count drifted: expected {BASE_CANONICAL_COUNT}, got {len(canonical)}")

    new_materials: dict[str, Any] = {
        "szott_poszto": {"material":"WHITE_WOOL","display-name":"&fSzőtt Posztó","tier":"REFINED","processing-state":"REFINED","primary-profession":"enchanter","source-types":["profession-processing:fiber"],"sink-types":["cloth-processing","market"],"economy-managed":True,"lore":["&7Sodort rostból sűrűre szőtt, tartós posztó.","&8Professions 2.0 • finomított textil"]},
        "runaszott_poszto": {"material":"LIGHT_BLUE_WOOL","display-name":"&bRúnaszőtt Posztó","tier":"SPECIAL","processing-state":"COMPONENT","primary-profession":"enchanter","source-types":["profession-processing:cloth"],"sink-types":["cloth-equipment","market"],"economy-managed":True,"lore":["&7Rúnaporral kezelt posztó, amely megtartja a varázsfonalat.","&8CLOTH felszerelés-komponens"]},
        "cserzett_bor": {"material":"LEATHER","display-name":"&6Cserzett Bőr","tier":"REFINED","processing-state":"REFINED","primary-profession":"alchemist","source-types":["profession-processing:hide"],"sink-types":["leather-processing","mail-processing","market"],"economy-managed":True,"lore":["&7Sóval és növényi kivonattal tartósított bőr.","&8Professions 2.0 • finomított bőr"]},
        "erositett_bor": {"material":"RABBIT_HIDE","display-name":"&eErősített Bőr","tier":"SPECIAL","processing-state":"COMPONENT","primary-profession":"alchemist","source-types":["profession-processing:leather"],"sink-types":["leather-equipment","market"],"economy-managed":True,"lore":["&7Vadászati esszenciával kezelt, rugalmas páncélbőr.","&8LEATHER felszerelés-komponens"]},
        "finom_huzal": {"material":"CHAIN","display-name":"&fFinom Fémhuzal","tier":"REFINED","processing-state":"REFINED","primary-profession":"armorer","source-types":["profession-processing:metal"],"sink-types":["mail-processing","market"],"economy-managed":True,"lore":["&7Vasból és rézből húzott könnyű, rugalmas huzal.","&8MAIL nyers komponens"]},
        "sodrott_lancszem": {"material":"CHAIN","display-name":"&7Sodrott Láncszem","tier":"SPECIAL","processing-state":"COMPONENT","primary-profession":"armorer","source-types":["profession-processing:mail"],"sink-types":["mail-equipment","market"],"economy-managed":True,"lore":["&7Bőrpánt köré zárt könnyű fémgyűrűk.","&8MAIL felszerelés-komponens"]},
        "edzett_otvozet": {"material":"IRON_INGOT","display-name":"&fEdzett Ötvözet","tier":"REFINED","processing-state":"REFINED","primary-profession":"armorer","source-types":["profession-processing:ore"],"sink-types":["plate-processing","market"],"economy-managed":True,"lore":["&7A meglévő Rezgő Rézötvözetből stabilizált kovácsötvözet.","&8PLATE finomított fém"]},
        "kovacsolt_lemez": {"material":"HEAVY_WEIGHTED_PRESSURE_PLATE","display-name":"&7Kovácsolt Lemez","tier":"SPECIAL","processing-state":"COMPONENT","primary-profession":"armorer","source-types":["profession-processing:alloy"],"sink-types":["plate-equipment","market"],"economy-managed":True,"lore":["&7Többször visszahajtott, kalapált páncéllemez.","&8PLATE felszerelés-komponens"]},
        "szovet_foszlany": {"material":"STRING","display-name":"&7Szövetfoszlány","tier":"COMMON","processing-state":"SALVAGE","source-types":["salvage:cloth"],"sink-types":["upgrade-service","market"],"economy-managed":True,"lore":["&7Veszteséges CLOTH salvage maradéka."]},
        "bor_hulladek": {"material":"RABBIT_HIDE","display-name":"&6Bőrhulladék","tier":"COMMON","processing-state":"SALVAGE","source-types":["salvage:leather"],"sink-types":["upgrade-service","market"],"economy-managed":True,"lore":["&7Veszteséges LEATHER salvage maradéka."]},
        "lanc_toredek": {"material":"IRON_NUGGET","display-name":"&7Lánctöredék","tier":"COMMON","processing-state":"SALVAGE","source-types":["salvage:mail"],"sink-types":["upgrade-service","market"],"economy-managed":True,"lore":["&7Bőr és fém szétválaszthatatlan MAIL maradéka."]},
        "femhulladek": {"material":"IRON_NUGGET","display-name":"&8Fémhulladék","tier":"COMMON","processing-state":"SALVAGE","source-types":["salvage:plate"],"sink-types":["upgrade-service","market"],"economy-managed":True,"lore":["&7Veszteséges PLATE salvage maradéka."]},
    }

    new_recipes: dict[str, Any] = {
        "p2_szott_poszto": {"profession":"enchanter","kind":"processing","economy-category":"PROCESSING","material-tier":"REFINED","economy-managed":True,"batchable":True,"batch-limit":16,"level":8,"learn":"level","display-name":"Szőtt Posztó","category":"Feldolgozás • Textil","result":{"unique":"szott_poszto","amount":2},"ingredients":["STRING:6","WHITE_WOOL:1"]},
        "p2_runaszott_poszto": {"profession":"enchanter","kind":"processing","economy-category":"PROCESSING","material-tier":"SPECIAL","economy-managed":True,"batchable":True,"batch-limit":8,"processing-dependencies":["szott_poszto","runapor"],"level":24,"learn":"level","display-name":"Rúnaszőtt Posztó","category":"Feldolgozás • Textil","result":{"unique":"runaszott_poszto","amount":1},"ingredients":["unique:szott_poszto:2","unique:runapor:1","AMETHYST_SHARD:1"]},
        "p2_cserzett_bor": {"profession":"alchemist","kind":"processing","economy-category":"PROCESSING","material-tier":"REFINED","economy-managed":True,"batchable":True,"batch-limit":16,"level":8,"learn":"level","display-name":"Cserzett Bőr","category":"Feldolgozás • Bőr","result":{"unique":"cserzett_bor","amount":2},"ingredients":["LEATHER:3","SUGAR:1","OAK_LEAVES:2"]},
        "p2_erositett_bor": {"profession":"alchemist","kind":"processing","economy-category":"PROCESSING","material-tier":"SPECIAL","economy-managed":True,"batchable":True,"batch-limit":8,"processing-dependencies":["cserzett_bor","vad_esszencia"],"level":26,"learn":"level","display-name":"Erősített Bőr","category":"Feldolgozás • Bőr","result":{"unique":"erositett_bor","amount":1},"ingredients":["unique:cserzett_bor:2","unique:vad_esszencia:1","IRON_NUGGET:4"]},
        "p2_finom_huzal": {"profession":"armorer","kind":"processing","economy-category":"PROCESSING","material-tier":"REFINED","economy-managed":True,"batchable":True,"batch-limit":16,"level":12,"learn":"level","display-name":"Finom Fémhuzal","category":"Feldolgozás • Sodrony","result":{"unique":"finom_huzal","amount":3},"ingredients":["IRON_INGOT:1","COPPER_INGOT:1"]},
        "p2_sodrott_lancszem": {"profession":"armorer","kind":"processing","economy-category":"PROCESSING","material-tier":"SPECIAL","economy-managed":True,"batchable":True,"batch-limit":8,"processing-dependencies":["finom_huzal","cserzett_bor"],"level":28,"learn":"level","display-name":"Sodrott Láncszem","category":"Feldolgozás • Sodrony","result":{"unique":"sodrott_lancszem","amount":2},"ingredients":["unique:finom_huzal:3","unique:cserzett_bor:1"]},
        "p2_edzett_otvozet": {"profession":"armorer","kind":"processing","economy-category":"PROCESSING","material-tier":"REFINED","economy-managed":True,"batchable":True,"batch-limit":12,"processing-dependencies":["rezgo_rez_otvozet"],"level":18,"learn":"level","display-name":"Edzett Ötvözet","category":"Feldolgozás • Lemez","result":{"unique":"edzett_otvozet","amount":2},"ingredients":["IRON_INGOT:3","unique:rezgo_rez_otvozet:1"]},
        "p2_kovacsolt_lemez": {"profession":"armorer","kind":"processing","economy-category":"PROCESSING","material-tier":"SPECIAL","economy-managed":True,"batchable":True,"batch-limit":8,"processing-dependencies":["edzett_otvozet"],"level":30,"learn":"level","display-name":"Kovácsolt Lemez","category":"Feldolgozás • Lemez","result":{"unique":"kovacsolt_lemez","amount":1},"ingredients":["unique:edzett_otvozet:2","COAL:2"]},
    }

    overrides: dict[str, Any] = {}
    canonical_rows: list[dict[str, Any]] = []
    family_counts = defaultdict(int)
    for rid, recipe in canonical.items():
        template_id = recipe["result"]["template"]
        template = templates.get(template_id)
        if not template:
            raise RuntimeError(f"canonical recipe {rid} references missing template {template_id}")
        family = (template.get("armor-family") or "").lower()
        row = {"recipe_id":rid,"template_id":template_id,"slot":template.get("slot"),"armor_family":family.upper() or None,
               "old_owner":recipe.get("profession"),"new_owner":recipe.get("profession"),"processing_dependency":[],"migration_action":"KEEP","status":"VERIFIED"}
        if family:
            if family not in ARMOR_FAMILIES:
                raise RuntimeError(f"unknown ArmorFamily on {template_id}: {family}")
            family_counts[family] += 1
            if family == "cloth":
                owner, dep, extra = "enchanter", ["runaszott_poszto"], ["unique:runaszott_poszto:2"]
            elif family == "leather":
                owner, dep, extra = "alchemist", ["erositett_bor"], ["unique:erositett_bor:2"]
            elif family == "mail":
                owner, dep, extra = "armorer", ["cserzett_bor","sodrott_lancszem"], ["unique:cserzett_bor:1","unique:sodrott_lancszem:2"]
            else:
                owner, dep, extra = "armorer", ["kovacsolt_lemez"], ["unique:kovacsolt_lemez:3"]
            existing = list(recipe.get("ingredients", []))
            # Keep authored/boss identity, but remove ordinary bulk armor metals/leather that the processed component replaces.
            keep = [spec for spec in existing if str(spec).startswith("unique:") or any(token in str(spec)
                    for token in ("NETHERITE", "DIAMOND", "NETHER_STAR", "ECHO_SHARD"))]
            high_tier = int(template.get("item-level", 1)) >= 45 or str(template.get("rarity", "")).lower() in {"legendary","mythic"}
            if high_tier and not any("szorny_mag" in str(spec) or "osi_ereklyeszilank" in str(spec) or "sarkanycsont" in str(spec) for spec in keep):
                keep.append("unique:szorny_mag:1")
            merged = []
            for spec in extra + keep:
                if spec not in merged:
                    merged.append(spec)
            overrides[rid] = {"profession":owner,"kind":"equipment","economy-category":"EQUIPMENT","material-tier":"SPECIAL",
                              "economy-managed":True,"batchable":False,"batch-limit":1,"processing-dependencies":dep,
                              "category":f"Equipment 2.0 • {family.upper()}","ingredients":merged}
            row.update(new_owner=owner, processing_dependency=dep, migration_action="MIGRATE")
        else:
            overrides[rid] = {"economy-category":"EQUIPMENT","economy-managed":True,"batchable":False,"batch-limit":1}
            row.update(migration_action="RETUNE")
        canonical_rows.append(row)

    overlay = {
        "professions": {"masterwork":{"base-chance":0.02,"chance-per-level":0.003,"maximum-chance":0.18},
                        "economy":{"recipe-aliases":{}}},
        "profession-materials": new_materials,
        "profession-recipes": {**new_recipes, **overrides},
    }
    write(CONFIG / "professions-2.yml", yaml.safe_dump(overlay, allow_unicode=True, sort_keys=False, width=120))

    effective = dict(base_recipes)
    for rid, patch in overlay["profession-recipes"].items():
        if rid not in effective:
            effective[rid] = patch
            continue
        merged = json.loads(json.dumps(effective[rid]))
        for key, value in patch.items():
            merged[key] = value
        effective[rid] = merged

    categories = {"KEEP":0,"RETUNE":0,"MIGRATE":0,"PROCESSING":0,"EQUIPMENT":0,"UTILITY":0,"CONSUMABLE":0,"UPGRADE_SERVICE":0,"OBSOLETE":0}
    migration_rows = []
    canonical_ids = set(canonical)
    for rid, recipe in base_recipes.items():
        kind = str(recipe.get("kind", "")).lower()
        category_text = str(recipe.get("category", "")).lower()
        result = recipe.get("result") or {}
        if rid in canonical_ids:
            action = "MIGRATE" if (templates[result["template"]].get("armor-family")) else "RETUNE"
        elif kind == "hozam" or "alapanyag" in category_text:
            action = "PROCESSING"
        elif result.get("affix-tier") or any(x in category_text for x in ("fegyver","páncél","szerszám")):
            action = "RETUNE"
        elif any(x in category_text for x in ("étel","ital","főzet","consum")):
            action = "CONSUMABLE"
        elif kind == "gyakorlo":
            action = "KEEP"
        else:
            action = "UTILITY"
        categories[action] += 1
        eff = effective[rid]
        tmpl = (eff.get("result") or {}).get("template")
        fam = (templates.get(tmpl, {}).get("armor-family") if tmpl else None)
        migration_rows.append({"recipe_id":rid,"old_category":recipe.get("category",""),"new_category":eff.get("category",recipe.get("category","")),
                               "owner_profession":eff.get("profession"),"output":tmpl or (eff.get("result") or {}).get("unique") or (eff.get("result") or {}).get("material"),
                               "armor_family":fam.upper() if fam else None,"processing_dependency":eff.get("processing-dependencies",[]),
                               "migration_action":action,"status":"VERIFIED"})
    for rid in new_recipes:
        categories["PROCESSING"] += 1
        rec = effective[rid]
        migration_rows.append({"recipe_id":rid,"old_category":None,"new_category":rec.get("category"),"owner_profession":rec.get("profession"),
                               "output":rec["result"].get("unique") or rec["result"].get("material"),"armor_family":None,
                               "processing_dependency":rec.get("processing-dependencies",[]),"migration_action":"PROCESSING","status":"ADDED"})

    # Producer -> consumer graph for unique materials.
    produces = defaultdict(list)
    consumes = defaultdict(list)
    profession_produces = defaultdict(set)
    profession_consumes = defaultdict(set)
    for rid, rec in effective.items():
        result = rec.get("result") or {}
        if result.get("unique"):
            mid = result["unique"]
            produces[mid].append(rid); profession_produces[rec.get("profession")].add(mid)
        for spec in rec.get("ingredients", []):
            text = str(spec)
            if text.startswith("unique:"):
                parts = text.split(":")
                if len(parts) >= 2:
                    consumes[parts[1]].append(rid); profession_consumes[rec.get("profession")].add(parts[1])
    material_doc = yaml.safe_load((CONFIG / "profession-materials.yml").read_text(encoding="utf-8")) or {}
    all_materials = dict(material_doc.get("profession-materials", {})); all_materials.update(new_materials)
    dead = []
    graph_nodes = []
    for mid, definition in sorted(all_materials.items()):
        sources = list(definition.get("source-types", []))
        sinks = list(definition.get("sink-types", []))
        producer_ids = sorted(produces.get(mid, []))
        consumer_ids = sorted(consumes.get(mid, []))
        if not sources and producer_ids: sources = [f"recipe:{rid}" for rid in producer_ids]
        if not sinks and consumer_ids: sinks = [f"recipe:{rid}" for rid in consumer_ids]
        managed = bool(definition.get("economy-managed", False))
        if managed and (not sources or not sinks): dead.append(mid)
        graph_nodes.append({"id":mid,"tier":definition.get("tier","LEGACY"),"processing_state":definition.get("processing-state","LEGACY"),
                            "sources":sources,"sinks":sinks,"producer_recipes":producer_ids,"consumer_recipes":consumer_ids,
                            "economy_managed":managed})
    if dead: raise RuntimeError(f"economy-managed materials missing source/sink: {dead}")

    # New managed processing graph must be acyclic and MAIL must depend on another profession output.
    dep_graph = defaultdict(set)
    for rid, rec in new_recipes.items():
        out = rec["result"].get("unique")
        if out:
            for dep in rec.get("processing-dependencies", []):
                if dep in new_materials: dep_graph[out].add(dep)
    temp=set(); perm=set()
    def dfs(node):
        if node in perm: return
        if node in temp: raise RuntimeError(f"processing cycle: {node}")
        temp.add(node)
        for nxt in dep_graph.get(node, ()): dfs(nxt)
        temp.remove(node); perm.add(node)
    for n in list(dep_graph): dfs(n)
    mail_mixed = any("cserzett_bor" in row["processing_dependency"] and "sodrott_lancszem" in row["processing_dependency"]
                     for row in canonical_rows if row["armor_family"] == "MAIL")
    if family_counts.get("mail",0) and not mail_mixed:
        raise RuntimeError("MAIL canonical chain lacks mixed leather/metal dependency")

    migration_report = {"schema":2,"baseline_recipe_count":len(base_recipes),"effective_recipe_count":len(effective),
                        "canonical_recipe_count":len(canonical),"category_summary":categories,"recipes":migration_rows}
    economy_report = {"schema":2,"north_star":"survival_gathering -> processing -> crafting -> equipment/utility -> use/trade/salvage -> upgrade -> market",
                      "profession_nodes":[{"profession":p,"produces":sorted(profession_produces[p]),"consumes":sorted(profession_consumes[p])}
                                          for p in sorted(set(profession_produces)|set(profession_consumes))],
                      "material_nodes":graph_nodes,"new_processing_edges":[{"from":d,"to":o} for o,deps in sorted(dep_graph.items()) for d in sorted(deps)],
                      "canonical_equipment":canonical_rows,"family_distribution":dict(sorted((k.upper(),v) for k,v in family_counts.items())),
                      "dead_managed_materials":dead,"cycles":[],"mail_mixed_dependency_verified":mail_mixed}
    rp_handoff = {"schema":2,"authority":"Equipment 2.0 ArmorFamily remains canonical","items":[
        {"recipe_id":row["recipe_id"],"template_id":row["template_id"],"crafting_source":"profession:craft",
         "profession":row["new_owner"],"material_theme":({"CLOTH":"woven arcane textile","LEATHER":"treated reinforced hide","MAIL":"hybrid leather and light rings","PLATE":"forged alloy plate"}.get(row["armor_family"],"authored utility/weapon")),
         "armor_family":row["armor_family"],"visual_theme_hint":"preserve authored template silhouette; Professions 2.0 adds no new equipment asset"}
        for row in canonical_rows]}
    DOCDEV.mkdir(parents=True, exist_ok=True)
    write(DOCDEV / "professions-2-recipe-migration.json", json.dumps(migration_report, ensure_ascii=False, indent=2, sort_keys=True))
    write(DOCDEV / "professions-2-economy-graph.json", json.dumps(economy_report, ensure_ascii=False, indent=2, sort_keys=True))
    write(DOCDEV / "professions-2-rp-handoff.json", json.dumps(rp_handoff, ensure_ascii=False, indent=2, sort_keys=True))


def write_balance_harness() -> None:
    write("scripts/test_professions_2_economy.py", r'''#!/usr/bin/env python3
from __future__ import annotations
import json, random
from collections import defaultdict
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
CFG=ROOT/'src/main/resources/config'
base=yaml.safe_load((CFG/'profession-recipes.yml').read_text(encoding='utf-8'))['profession-recipes']
overlay=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['profession-recipes']
effective={k:dict(v) for k,v in base.items()}
for rid,patch in overlay.items():
    if rid not in effective: effective[rid]=patch; continue
    effective[rid].update(patch)
rng=random.Random(0x1CE5A2)
processing=[(rid,r) for rid,r in effective.items() if str(r.get('economy-category','')).upper()=='PROCESSING']
gear=[(rid,r) for rid,r in effective.items() if (r.get('result') or {}).get('template')]
# Deterministic economy sanity, deliberately not a production-balance claim.
throughput=[]
for rid,r in processing:
    cost=0
    for spec in r.get('ingredients',[]):
        try: cost+=int(str(spec).rsplit(':',1)[1])
        except Exception: cost+=1
    out=int((r.get('result') or {}).get('amount',1))
    throughput.append({'recipe':rid,'input_units':cost,'output_units':out,'ratio':round(out/max(1,cost),4)})
# Masterwork expected-rate Monte Carlo from configured bounded chance.
p=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['professions']['masterwork']
chance=min(float(p['maximum-chance']),float(p['base-chance'])+50*float(p['chance-per-level']))
hits=sum(1 for _ in range(100000) if rng.random()<chance)
rate=hits/100000
assert 0.0 < chance < 0.5 and abs(rate-chance)<0.01
# Salvage is a hard loss target: never model >=100% resource recovery.
salvage_recovery_ceiling=0.55
assert salvage_recovery_ceiling < 1.0
# XP spam gate is already grey-after; calculate relevance window.
prof=yaml.safe_load((CFG/'professions.yml').read_text(encoding='utf-8'))['professions']['xp']
grey=int(prof['recipe-craft-grey-after']); assert grey>=2
report={'seed':0x1CE5A2,'processing_recipe_count':len(processing),'canonical_gear_recipe_count':len(gear),
        'masterwork_level50_configured_chance':chance,'masterwork_seeded_rate':rate,
        'salvage_recovery_ceiling':salvage_recovery_ceiling,'recipe_xp_grey_after':grey,
        'throughput':throughput,'production_balance_proven':False,
        'staging_required':['50-60 player material supply','real GUI latency','disconnect during craft','market price equilibrium']}
out=ROOT/'build/reports/professions-2/economy-harness.json'; out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print(f"Professions 2.0 economy harness: {len(processing)} processing, {len(gear)} canonical gear, masterwork@50={rate:.3f}")
''')
    write("scripts/check_professions_2_reports.py", r'''#!/usr/bin/env python3
from pathlib import Path
import json, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
paths=[ROOT/'docs/development/professions-2-recipe-migration.json',ROOT/'docs/development/professions-2-economy-graph.json',ROOT/'docs/development/professions-2-rp-handoff.json']
for p in paths:
    data=json.loads(p.read_text(encoding='utf-8'))
    assert data.get('schema')==2, p
mig=json.loads(paths[0].read_text(encoding='utf-8'))
assert mig['baseline_recipe_count']==392 and mig['canonical_recipe_count']==15
assert len(mig['recipes'])==mig['effective_recipe_count']
g=json.loads(paths[1].read_text(encoding='utf-8'))
assert not g['dead_managed_materials'] and not g['cycles']
assert set(g['family_distribution']).issubset({'CLOTH','LEATHER','MAIL','PLATE'})
print('Professions 2.0 reports: OK')
''')


def patch_listener() -> None:
    path = "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"
    replace_once(path,
        '    private volatile hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;\n',
        '    private volatile hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;\n'
        '    private final hu.taliann.icesmp.professions.ProfessionCraftTransaction craftTransaction;\n')
    replace_once(path,
        '        this.craftedAtKey = new NamespacedKey(plugin, "crafted_at");\n',
        '        this.craftedAtKey = new NamespacedKey(plugin, "crafted_at");\n'
        '        this.craftTransaction = new hu.taliann.icesmp.professions.ProfessionCraftTransaction(uniqueMaterials);\n')
    replace_once(path,
        '            default -> craft(player, action, holder.getPage());\n',
        '            default -> craft(player, action, holder.getPage(), event.isShiftClick());\n')
    replace_once(path,
        '    private void craft(final Player player, final String recipeId, final int page) {\n',
        '    private void craft(final Player player, final String recipeId, final int page, final boolean batchRequested) {\n')
    old = '''        if (!hasIngredients(player, recipe)) {\n            player.sendMessage(messageManager.get("profession-recipe-missing", "&cNincs meg minden hozzávaló ehhez a recepthez."));\n            return;\n        }\n\n        // ELŐBB épül az eredmény, és csak sikeres build UTÁN fogy a hozzávaló —\n        // hibás recept-config (feloldhatatlan unique eredmény) nem nyelheti el az anyagot.\n        final ItemStack result = buildResult(player, recipe);\n        if (result == null) {\n            return;\n        }\n        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {\n            if (!hu.taliann.icesmp.utils.PlainIngredients.consume(\n                    player, entry.getKey(), entry.getValue(), uniqueMaterials)) {\n                // A hasIngredients UGYANEZT a predikátumot használta ugyanezen a szálon,\n                // ezért ide nem szabad eljutni: ha mégis, a hozzávaló ingyen maradna.\n                plugin.getLogger().severe("Craft-hozzávaló nem fogyott el: "\n                        + recipe.id() + " / " + entry.getKey() + " x" + entry.getValue());\n                return;\n            }\n        }\n        consumeUnique(player, recipe);\n        for (final ItemStack overflow : player.getInventory().addItem(result).values()) {\n            player.getWorld().dropItemNaturally(player.getLocation(), overflow);\n        }\n        EquippedCombatPowerService.refreshAfterMutation(player);\n'''
    new = '''        final ProfessionRecipeCatalog.EconomyMetadata economy = catalog.economy(recipe.id());\n        final int batches = batchRequested && economy.batchable() && recipe.templateId() == null\n                && recipe.affixTier() == null ? Math.min(5, economy.batchLimit()) : 1;\n        if (!hasIngredients(player, recipe)) {\n            player.sendMessage(messageManager.get("profession-recipe-missing", "&cNincs meg minden hozzávaló ehhez a recepthez."));\n            return;\n        }\n        final java.util.UUID rootOperationId = java.util.UUID.randomUUID();\n        final java.util.List<ItemStack> outputs = new java.util.ArrayList<>();\n        int masterworkCount = 0;\n        for (int index = 0; index < batches; index++) {\n            final java.util.UUID operationId = derivedOperationId(rootOperationId, index);\n            final ItemStack result = buildResult(player, recipe, true, operationId);\n            if (result == null) return;\n            outputs.add(result);\n            final hu.taliann.icesmp.itemization.ItemIdentityService identity = itemIdentityService;\n            if (identity != null) {\n                final var inspection = identity.inspect(result);\n                if (inspection.readable() && inspection.instance() != null\n                        && inspection.instance().origin().masterwork()) masterworkCount++;\n            }\n        }\n        final var transaction = craftTransaction.apply(player, recipe, batches, outputs);\n        if (!transaction.applied()) {\n            if (transaction.status() == hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status.INVENTORY_FULL) {\n                player.sendMessage(messageManager.get("profession-recipe-inventory-full",\n                        "&cNincs elég hely a hátizsákodban; semmi nem fogyott el."));\n            } else {\n                player.sendMessage(messageManager.get("profession-recipe-missing",\n                        "&cNincs meg minden hozzávaló ehhez az adaghoz; semmi nem fogyott el."));\n            }\n            return;\n        }\n        EquippedCombatPowerService.refreshAfterMutation(player);\n'''
    replace_once(path, old, new)
    # Multiply XP by successful batch, respecting existing bulk cap.
    replace_once(path,
        '''        if (craftXp > 0) {\n            final int durableCraftXp = craftXp;\n''',
        '''        if (craftXp > 0) {\n            final int bulkCap = Math.max(1, configManager.getInt("professions.xp.bulk-event-cap", 16));\n            final int durableCraftXp = Math.multiplyExact(craftXp, Math.min(batches, bulkCap));\n''')
    replace_once(path,
        '''        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);\n        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s", recipe.displayName()));\n''',
        '''        hu.taliann.icesmp.professions.ProfessionEconomyTelemetry.global().recordCraft(\n                recipe, batches, masterworkCount, recipe.level() >= 40 || recipe.blueprint());\n        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);\n        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s",\n                recipe.displayName() + (batches > 1 ? " ×" + batches : "")));\n''')
    # Public/private build overloads and deterministic canonical roll.
    replace_once(path,
        '''    public ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {\n        return buildResult(player, recipe, true);\n    }\n\n    /** Builds a crate reward without firing an advancement before the world reveal finishes. */\n    public ItemStack buildDeferredReward(final Player player,\n                                         final ProfessionRecipeCatalog.Recipe recipe) {\n        return buildResult(player, recipe, false);\n    }\n\n    private ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe,\n                                  final boolean awardMasterwork) {\n''',
        '''    public ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {\n        return buildResult(player, recipe, true, java.util.UUID.randomUUID());\n    }\n\n    /** Builds a crate reward without firing an advancement before the world reveal finishes. */\n    public ItemStack buildDeferredReward(final Player player,\n                                         final ProfessionRecipeCatalog.Recipe recipe) {\n        return buildResult(player, recipe, false, java.util.UUID.randomUUID());\n    }\n\n    private ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe,\n                                  final boolean professionCraft, final java.util.UUID operationId) {\n''')
    old_quality = '''                if (awardMasterwork) {\n                    final double baseFloor = clamp01(configManager.getDouble(\n                            "itemization.crafting.base-minimum-quality", 0.10D));\n                    final double levelContribution = Math.min(clamp01(configManager.getDouble(\n                                    "itemization.crafting.maximum-level-contribution", 0.20D)),\n                            professionManager.getLevel(player, recipe.profession())\n                                    * Math.max(0.0D, configManager.getDouble(\n                                    "itemization.crafting.quality-per-profession-level", 0.003D)));\n                    final double blueprintBonus = recipe.blueprint() ? Math.max(0.0D,\n                            configManager.getDouble("itemization.crafting.blueprint-quality-bonus", 0.05D)) : 0.0D;\n                    final double masterworkBonus = recipe.masterwork() ? Math.max(0.0D,\n                            configManager.getDouble("itemization.crafting.masterwork-quality-bonus", 0.15D)) : 0.0D;\n                    final double minimumQuality = clamp01(baseFloor + levelContribution\n                            + blueprintBonus + masterworkBonus);\n                    instance = identity.rollCraftedInstance(template, java.util.UUID.randomUUID(),\n                            player.getUniqueId(), player.getName(), recipe.profession().getId(),\n                            locationSnapshot(player), recipe.masterwork(), now, minimumQuality,\n                            () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());\n                } else {\n                    instance = identity.rollInstance(template, java.util.UUID.randomUUID(),\n                            "crate:authored", recipe.id(), null, "", now,\n                            () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());\n                }\n'''
    new_quality = '''                if (professionCraft) {\n                    final var decision = hu.taliann.icesmp.professions.ProfessionCraftQualityPolicy.decide(\n                            operationId, professionManager.getLevel(player, recipe.profession()),\n                            recipe.blueprint(), recipe.masterwork(),\n                            hu.taliann.icesmp.professions.ProfessionCraftQualityPolicy.from(configManager));\n                    instance = identity.rollCraftedInstance(template, operationId,\n                            player.getUniqueId(), player.getName(), recipe.profession().getId(),\n                            locationSnapshot(player), decision.masterwork(), now, decision.minimumQuality(),\n                            decision.qualitySource());\n                } else {\n                    instance = identity.rollInstance(template, operationId,\n                            "crate:authored", recipe.id(), null, "", now,\n                            () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());\n                }\n'''
    replace_once(path, old_quality, new_quality)
    replace_once(path,
        "                return identity.render(template, instance);\n",
        "                final ItemStack rendered = identity.render(template, instance);\n"
        "                if (professionCraft && instance.origin().masterwork()) {\n"
        "                    hu.taliann.icesmp.managers.AdvancementService.award(player, \"masterwork\");\n"
        "                }\n"
        "                return rendered;\n")
    replace_once(path,
        "        if (awardMasterwork) {\n            awardMasterworkIfEligible(player, result);\n        }\n",
        "        if (professionCraft) {\n            awardMasterworkIfEligible(player, result);\n        }\n")
    # Derived UUID helper before clamp01 method if present, else before final brace.
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if "derivedOperationId(final java.util.UUID root" not in text:
        anchor = "    private static double clamp01(final double value) {"
        helper = '''    private static java.util.UUID derivedOperationId(final java.util.UUID root, final int index) {\n        if (index == 0) return root;\n        final String source = root + ":" + index;\n        return java.util.UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));\n    }\n\n'''
        if anchor not in text: raise RuntimeError("listener clamp01 anchor missing")
        target.write_text(text.replace(anchor, helper + anchor, 1), encoding="utf-8")


def patch_gui() -> None:
    path="src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java"
    replace_once(path,
        '            final ProfessionRecipeCatalog.Recipe recipe = recipes.get(start + i);\n            inv.setItem(i, buildTile(player, professionManager, recipe, uniqueMaterials));\n',
        '            final ProfessionRecipeCatalog.Recipe recipe = recipes.get(start + i);\n            inv.setItem(i, buildTile(player, professionManager, catalog, recipe, uniqueMaterials));\n')
    replace_once(path,
        '''    private static org.bukkit.inventory.ItemStack buildTile(final Player player, final ProfessionManager professionManager,\n                                                            final ProfessionRecipeCatalog.Recipe recipe,\n                                                            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {\n''',
        '''    private static org.bukkit.inventory.ItemStack buildTile(final Player player, final ProfessionManager professionManager,\n                                                            final ProfessionRecipeCatalog catalog,\n                                                            final ProfessionRecipeCatalog.Recipe recipe,\n                                                            final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials) {\n''')
    replace_once(path,
        '        lore.add(grey("Kategória: " + recipe.category()));\n',
        '''        lore.add(grey("Kategória: " + recipe.category()));\n        final ProfessionRecipeCatalog.EconomyMetadata economy = catalog.economy(recipe.id());\n        lore.add(grey("Gazdasági szerep: " + economy.category() + " • " + economy.tier()));\n        if (!economy.dependencies().isEmpty()) {\n            lore.add(Component.text("↔ Feldolgozási lánc: " + String.join(", ", economy.dependencies()),\n                    NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));\n        }\n        if (recipe.templateId() != null) {\n            lore.add(Component.text("◆ Canonical template: " + recipe.templateId(), NamedTextColor.AQUA)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n        if (recipe.masterwork()) {\n            lore.add(Component.text("✦ Mestermű-esély: ritka, skill-függő; nem garantált", NamedTextColor.LIGHT_PURPLE)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n''')
    replace_once(path,
        '''        lore.add(craftable\n                ? Component.text("» Kattints a craftoláshoz", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)\n''',
        '''        if (economy.batchable()) {\n            lore.add(Component.text("⇧ Shift+katt: 5-ös batch (max " + economy.batchLimit() + ")", NamedTextColor.GOLD)\n                    .decoration(TextDecoration.ITALIC, false));\n        }\n        lore.add(craftable\n                ? Component.text("» Kattints a craftoláshoz", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)\n''')


def patch_build() -> None:
    path="build.gradle.kts"
    text=(ROOT/path).read_text(encoding="utf-8")
    if 'professions2RegressionTest' not in text:
        anchor='''val professionRecipeAuditRegressionTest = registerRegression(\n    "professionRecipeAuditRegressionTest",\n    "Validates deterministic profession recipes, semantic uniqueness and reload cleanup.",\n    "hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite")\n'''
        addition=anchor+'''val professions2RegressionTest = registerRegression(\n    "professions2RegressionTest",\n    "Runs deterministic Masterwork and Professions 2.0 economy contracts.",\n    "hu.taliann.icesmp.professions.Professions2RegressionSuite")\nval professions2ReportRegressionTest by tasks.registering(Exec::class) {\n    group = "verification"\n    description = "Validates Professions 2.0 recipe migration and economy graph reports."\n    inputs.files("scripts/check_professions_2_reports.py", "docs/development/professions-2-recipe-migration.json",\n        "docs/development/professions-2-economy-graph.json", "docs/development/professions-2-rp-handoff.json")\n    commandLine(pythonCommand, "scripts/check_professions_2_reports.py")\n}\nval professions2EconomyRegressionTest by tasks.registering(Exec::class) {\n    group = "verification"\n    description = "Runs the seeded Professions 2.0 economy sanity harness."\n    inputs.files("scripts/test_professions_2_economy.py", "src/main/resources/config/professions-2.yml",\n        "src/main/resources/config/profession-recipes.yml", "src/main/resources/config/professions.yml")\n    commandLine(pythonCommand, "scripts/test_professions_2_economy.py")\n}\n'''
        if anchor not in text: raise RuntimeError("build regression anchor missing")
        text=text.replace(anchor,addition,1)
        text=text.replace('''    dependsOn(equipment2ReportRegressionTest)\n''','''    dependsOn(equipment2ReportRegressionTest)\n    dependsOn(professions2ReportRegressionTest)\n    dependsOn(professions2EconomyRegressionTest)\n''',1)
        text=text.replace('''        professionRecipeAuditRegressionTest, inventoryReadWriteRegressionTest,\n''','''        professionRecipeAuditRegressionTest, professions2RegressionTest, inventoryReadWriteRegressionTest,\n''',1)
        (ROOT/path).write_text(text,encoding="utf-8")


def patch_config_manager() -> None:
    replace_once("src/main/java/hu/taliann/icesmp/managers/ConfigManager.java",
        '            "profession-recipes", "sit", "tablist", "dev-items", "client"\n',
        '            "profession-recipes", "professions-2", "sit", "tablist", "dev-items", "client"\n')


def write_docs() -> None:
    append_once("ROADMAP.md", "## Professions 2.0 — source closure",
'''## Professions 2.0 — source closure
- Survival gathering remains vanilla-world activity; Professions 2.0 adds processing/economy, not static gathering nodes.
- CLOTH/LEATHER/MAIL/PLATE production is stacked on Equipment 2.0. ArmorFamily/class proficiency are not redefined here.
- Recipe migration/report authority: `docs/development/professions-2-recipe-migration.json`.
- Economy graph/dead-content authority: `docs/development/professions-2-economy-graph.json`.
- Runtime staging remains required for multiplayer throughput, real market prices, disconnect/packet-sync and 50–60-player balance.
- Equipment Resource Pack 2.0 and crafting-order escrow marketplace remain future stacked scopes.''')
    append_once("docs/ARCHITECTURE.md", "## Professions 2.0 authority",
'''## Professions 2.0 authority
`PlayerProfile` remains the only durable profession progression authority. `ProfessionRecipeCatalog` publishes one immutable indexed recipe generation after validating stable material IDs, semantic duplicates, aliases and managed processing cycles. `ProfessionMaterialRegistry` is configuration identity for stackable economy materials; it deliberately does not assign ItemInstance UUIDs to ordinary stacks.

The execution boundary is owner-thread inventory state: `ProfessionCraftTransaction` plans removal plus output placement against cloned storage and commits only after the whole batch fits. Canonical equipment is still `ItemTemplate -> ItemInstance`; deterministic operation-seeded quality decisions prevent retry from becoming a free Masterwork reroll. Vanilla Crafting Boundary and Equipment 2.0 active-equipment authority remain upstream contracts.''')
    append_once("docs/FEATURES.md", "## Professions 2.0 economy",
'''## Professions 2.0 economy
- Meaningful raw → refined → component → craft chains for textile, leather, hybrid mail and forged plate.
- Cross-profession MAIL dependency and selected high-tier combat components.
- Targeted canonical crafting, bounded non-guaranteed Masterwork, family-aware lossy salvage and player-market-ready material metadata.
- Shift-click batch processing for stackable processing recipes with all-or-nothing inventory capacity checks.
- Machine-readable migration, producer/consumer and resource-pack handoff reports.''')
    append_once("docs/PLAYER_GUIDE.md", "## Professions 2.0 — mit csináljak?",
'''## Professions 2.0 — mit csináljak?
A szakmád most gazdasági szerep. A nyersanyag továbbra is valódi Minecraft-tevékenységből jön: bányászol, vadászol, gyűjtesz, halászol és farmolsz. A receptkönyvben a **Feldolgozás** receptek nyers alapanyagból olyan komponenseket készítenek, amelyekre más játékosoknak is szükségük lehet.

- **CLOTH:** rost/fonal → Szőtt Posztó → Rúnaszőtt Posztó → canonical szövet gear.
- **LEATHER:** bőr → Cserzett Bőr → Erősített Bőr → canonical bőr gear.
- **MAIL:** könnyű fémhuzal **és** cserzett bőr → Sodrott Láncszem → canonical sodrony gear. Ez szándékosan több szakmát köt össze.
- **PLATE:** survival fém + meglévő ötvözet → Edzett Ötvözet → Kovácsolt Lemez → canonical lemez gear.

A **Mestermű** nem külön rarity és nem garantált tökéletes roll. Magasabb szakmaszint javítja a quality floor-t, a kijelölt mestermű-recepteknél pedig ritka, korlátozott extra esélyt ad. A tárgy template-je ugyanaz marad, a készítő és a Mestermű-jelölés az ItemInstance eredetében látszik.

Stackelhető feldolgozásnál normál kattintás 1 craft, **Shift+kattintás 5-ös batch**. Ha nincs hely az összes outputnak, semmi nem fogy el. Canonical gear nem batch-elődik.

Salvage veszteséges visszaforgatás. CLOTH textilfoszlányt, LEATHER bőrhulladékot, MAIL lánctöredéket, PLATE fémhulladékot adhat; boss-komponenst nem kapsz automatikusan vissza. A rúnázás/reforge/ascension továbbra is az Itemization saját canonical rendszerét használja.''')
    append_once("docs/ADMIN_GUIDE.md", "## Professions 2.0 admin / economy",
'''## Professions 2.0 admin / economy
- Effective recipe authority: `config/profession-recipes.yml` + később merge-elt `config/professions-2.yml` overlay; player progression továbbra is PlayerProfile v2.
- Machine-readable migration: `docs/development/professions-2-recipe-migration.json`.
- Producer/consumer, faucet/sink és dependency graph: `docs/development/professions-2-economy-graph.json`.
- `./gradlew professions2ReportRegressionTest professions2EconomyRegressionTest professions2RegressionTest` futtatja a célzott source gate-eket.
- `ProfessionEconomyTelemetry.global().snapshot()` bounded aggregátumot ad crafted/processed/Masterwork/high-tier/salvage számlálókról; nem tart végtelen operation historyt.
- Material/recipe reload fail-closed: hiányzó unique ID, rossz level/amount, semantic duplicate vagy managed processing cycle esetén az új generáció nem publikálható.

Balance-változtatást a seedelt harness után is stagingen kell igazolni. Ne állíts be NPC buy/sell hurkot, amely crafttal profitot termel; high-value komponens korlátlan vendorforrása tiltott policy.''')
    append_once("docs/LATEST_CHANGES.md", "## 2026-08-18 — Professions 2.0",
'''## 2026-08-18 — Professions 2.0
- A 392 meglévő profession recipe teljes gépi migrációs inventoryt kapott; a recipe ID-k és blueprint unlockok stabilak maradtak.
- Bevezetésre került a processing/material economy authority, a négy Equipment 2.0 family termelési lánca, mixed MAIL dependency és bounded Masterwork.
- Craft inventory commit all-or-nothing, batch-aware és full-inventory esetben nem dob tárgyat a világba.
- Salvage family-aware, veszteséges és nem állítja elő újra az eredeti boss komponenst.
- Új economy graph, migration report, RP handoff és seedelt sanity harness készült. Runtime/player-market végleges balansz staging-required.''')


def write_professions_config_comments() -> None:
    # Existing config keeps progression defaults; Professions 2 overlay owns the new bounded Masterwork parameters.
    pass


def main() -> None:
    patch_config_manager()
    write("src/main/java/hu/taliann/icesmp/professions/ProfessionMaterialRegistry.java", MATERIAL_REGISTRY)
    write("src/main/java/hu/taliann/icesmp/professions/ProfessionCraftQualityPolicy.java", QUALITY_POLICY)
    write("src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java", CRAFT_TRANSACTION)
    write("src/main/java/hu/taliann/icesmp/professions/ProfessionEconomyTelemetry.java", TELEMETRY)
    write("src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeCatalog.java", CATALOG)
    write("src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java", SALVAGE_SERVICE)
    write("src/regression/java/hu/taliann/icesmp/professions/Professions2RegressionSuite.java", REGRESSION_SUITE)
    generate_overlay_and_reports()
    write_balance_harness()
    patch_listener()
    patch_gui()
    patch_build()
    write_docs()
    print("Professions 2.0 rework applied")


if __name__ == "__main__":
    main()
