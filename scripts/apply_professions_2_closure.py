#!/usr/bin/env python3
"""Idempotent final Professions 2.0 closure applied after the base rework generators."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CRAFT_TRANSACTION = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.PlainIngredients;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

/**
 * Owner-thread inventory transaction for profession processing/crafting.
 * All input removal and output placement is planned against a clone first; full inventory or
 * missing input leaves the live inventory untouched. The committed snapshot is immediately
 * persisted through the player's canonical data store; persistence failure rolls the live
 * inventory back to the exact pre-craft snapshot. No fallback world-drop is used.
 */
public final class ProfessionCraftTransaction {

    public enum Status {
        APPLIED,
        MISSING_INGREDIENTS,
        INVENTORY_FULL,
        INVALID_BATCH,
        PERSISTENCE_FAILED
    }

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
        final ItemStack[] before = cloneContents(inventory.getStorageContents());
        final ItemStack[] working = cloneContents(before);

        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE
                    || !consumePlain(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE
                    || !consumeUnique(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final ItemStack raw : outputs) {
            if (raw == null || raw.getType().isAir() || raw.getAmount() <= 0) {
                return new Result(Status.INVALID_BATCH, 0);
            }
            if (!insert(working, raw.clone())) {
                return new Result(Status.INVENTORY_FULL, 0);
            }
        }

        try {
            inventory.setStorageContents(cloneContents(working));
            player.saveData();
            return new Result(Status.APPLIED, batches);
        } catch (final RuntimeException persistenceFailure) {
            try {
                inventory.setStorageContents(cloneContents(before));
                player.saveData();
            } catch (final RuntimeException rollbackFailure) {
                persistenceFailure.addSuppressed(rollbackFailure);
                throw new IllegalStateException(
                        "profession craft persistence rollback failed", persistenceFailure);
            }
            return new Result(Status.PERSISTENCE_FAILED, 0);
        }
    }

    private boolean consumePlain(final ItemStack[] contents, final Material material,
                                 final int amount) {
        int available = 0;
        for (final ItemStack item : contents) {
            if (PlainIngredients.matches(item, material, uniqueMaterials)) {
                available += item.getAmount();
            }
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
            if (item != null && id.equals(uniqueMaterials.idOf(item))) {
                available += item.getAmount();
            }
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

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] result = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            result[slot] = source[slot] == null ? null : source[slot].clone();
        }
        return result;
    }

    private static boolean insert(final ItemStack[] contents, final ItemStack output) {
        int remaining = output.getAmount();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack current = contents[slot];
            if (current == null || !current.isSimilar(output)) continue;
            final int room = Math.max(0,
                    Math.min(current.getMaxStackSize(), output.getMaxStackSize())
                            - current.getAmount());
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

SALVAGE_SERVICE = r'''package hu.taliann.icesmp.itemization;

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
                    || maximumDust < 0) {
                throw new IllegalArgumentException("negative salvage tuning");
            }
        }
    }

    public record Preview(Status status, UUID itemId, Map<String, Integer> outputs,
                          int estimatedInputValue, int estimatedOutputValue) {
        public Preview {
            outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
            if (estimatedInputValue < 0 || estimatedOutputValue < 0
                    || estimatedOutputValue > estimatedInputValue) {
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
        if (item.states().contains(ItemState.LEGACY)) {
            return denied(Status.LEGACY_DISABLED, item.itemId(), conservativeInputValue);
        }
        if (item.origin().sourceTag().startsWith("admin")
                || item.origin().sourceTag().startsWith("dev")) {
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
        final int familyScrap = family == null ? 0
                : Math.min(grossDust / 2, Math.max(1, grossDust / 3));
        final int genericDust = Math.max(0, grossDust - familyScrap);
        if (genericDust > 0) outputs.put("salvage_dust", genericDust);
        if (familyScrap > 0) outputs.put(familyScrapId(family), familyScrap);

        final int runeDust = (int) Math.min(tuning.maximumDust(),
                (long) item.runes().size() * tuning.runeDustPerRune());
        if (runeDust > 0) outputs.put("rune_dust", runeDust);
        final int signatureDust = Math.min(tuning.maximumDust(), tuning.signatureDust());
        if (template.salvagePolicy() == ItemTemplate.SalvagePolicy.SIGNATURE_MATERIALS
                && signatureDust > 0) {
            outputs.put("signature_dust", signatureDust);
        }

        final long outputValue = outputs.values().stream().mapToLong(Integer::longValue).sum();
        if (outputValue > conservativeInputValue) {
            throw new IllegalStateException(
                    "configured salvage yield exceeds conservative input value");
        }
        // Preview is deliberately side-effect free. Telemetry belongs to the durable commit path.
        return new Preview(Status.ALLOWED, item.itemId(), outputs,
                conservativeInputValue, (int) outputValue);
    }

    /** Stable economy material id used by the delivery map and regression gates. */
    public static String familyScrapId(final ArmorFamily family) {
        Objects.requireNonNull(family, "family");
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

REGRESSION = r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.itemization.ItemSalvageService;

import java.util.Map;
import java.util.UUID;

/** Deterministic Professions 2.0 contracts that need no live Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        masterworkEligibilityDoesNotChangeTemplateIdentity();
        familySalvageMaterialIdsAreStableAndDistinct();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
        final var first = ProfessionCraftQualityPolicy.decide(
                operation, 50, true, true, tuning);
        final var retry = ProfessionCraftQualityPolicy.decide(
                operation, 50, true, true, tuning);
        require(first.equals(retry), "same operation must preserve Masterwork decision");
        require(first.masterworkChance() <= 0.18D,
                "Masterwork cannot become guaranteed");
        require(Double.compare(first.qualitySource().getAsDouble(),
                        retry.qualitySource().getAsDouble()) == 0,
                "retry quality source must be deterministic");
        require(first.minimumQuality() < 1.0D,
                "profession skill must not guarantee perfect quality");
    }

    private static void masterworkEligibilityDoesNotChangeTemplateIdentity() {
        final UUID operation = UUID.fromString("3d46e53f-644d-409e-83d4-054d9479919d");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
        final var decision = ProfessionCraftQualityPolicy.decide(
                operation, 30, false, true, tuning);
        require(decision.operationId().equals(operation),
                "Masterwork is instance metadata on the same operation/item identity");
    }

    private static void familySalvageMaterialIdsAreStableAndDistinct() {
        final Map<ArmorFamily, String> expected = Map.of(
                ArmorFamily.CLOTH, "szovet_foszlany",
                ArmorFamily.LEATHER, "bor_hulladek",
                ArmorFamily.MAIL, "lanc_toredek",
                ArmorFamily.PLATE, "femhulladek");
        expected.forEach((family, id) -> require(
                id.equals(ItemSalvageService.familyScrapId(family)),
                "wrong family salvage material for " + family));
        require(expected.values().stream().distinct().count() == ArmorFamily.values().length,
                "each ArmorFamily must keep a distinct salvage material identity");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''

CHECKER = r'''#!/usr/bin/env python3
from pathlib import Path
import json
import yaml

ROOT = Path(__file__).resolve().parents[1]
paths = [
    ROOT / 'docs/development/professions-2-recipe-migration.json',
    ROOT / 'docs/development/professions-2-economy-graph.json',
    ROOT / 'docs/development/professions-2-rp-handoff.json',
]
for path in paths:
    assert json.loads(path.read_text(encoding='utf-8')).get('schema') == 2, path

migration = json.loads(paths[0].read_text(encoding='utf-8'))
assert migration['baseline_recipe_count'] == 392
assert migration['effective_recipe_count'] == 407
assert migration['canonical_recipe_count'] == 18
assert migration['category_summary']['EQUIPMENT'] == 18
assert len(migration['recipes']) == 407
assert all('economy_category' in row for row in migration['recipes'])

graph = json.loads(paths[1].read_text(encoding='utf-8'))
assert not graph['dead_managed_materials'] and not graph['cycles']
assert graph['mail_mixed_dependency_verified']
assert set(graph['family_distribution']) == {'CLOTH', 'LEATHER', 'MAIL', 'PLATE'}
assert all(graph['family_distribution'][family] > 0
           for family in graph['family_distribution'])
expected_scraps = {'szovet_foszlany', 'bor_hulladek', 'lanc_toredek', 'femhulladek'}
assert set(graph['salvage_reclamation_sinks_verified']) == expected_scraps
nodes = {node['id']: node for node in graph['material_nodes']}
for scrap in expected_scraps:
    assert nodes[scrap]['consumer_recipes'], scrap

listener = (ROOT / 'src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java').read_text(encoding='utf-8')
tx_index = listener.index('craftTransaction.apply(player, recipe, batches, outputs)')
award_index = listener.index('AdvancementService.award(player, "masterwork")')
assert award_index > tx_index
assert 'dropItemNaturally(player.getLocation(), overflow)' not in listener

transaction = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java').read_text(encoding='utf-8')
assert 'player.saveData();' in transaction
assert 'PERSISTENCE_FAILED' in transaction
assert 'inventory.setStorageContents(cloneContents(before));' in transaction
assert 'dropItemNaturally' not in transaction

salvage = (ROOT / 'src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java').read_text(encoding='utf-8')
assert 'familyScrapId' in salvage
assert 'ProfessionEconomyTelemetry.global().recordSalvage' not in salvage

root_config = yaml.safe_load((ROOT / 'src/main/resources/config.yml').read_text(encoding='utf-8')) or {}
delivery = (((root_config.get('itemization') or {}).get('salvage') or {}).get('output-map') or {})
for scrap in expected_scraps:
    assert delivery.get(scrap) == scrap, (scrap, delivery.get(scrap))

print('Professions 2.0 reports/hardening: OK')
'''


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.write_text(content.rstrip() + '\n', encoding='utf-8')


def patch_root_config() -> None:
    path = ROOT / 'src/main/resources/config.yml'
    text = path.read_text(encoding='utf-8')
    marker = '# ===== Professions 2.0 — family salvage delivery ====='
    if marker in text:
        return
    block = '''# ===== Professions 2.0 — family salvage delivery =====
# A salvage domain output-keyjei stabil profession material ID-k; nincs runapor fallback.
itemization:
  salvage:
    output-map:
      szovet_foszlany: szovet_foszlany
      bor_hulladek: bor_hulladek
      lanc_toredek: lanc_toredek
      femhulladek: femhulladek

'''
    anchor = '# ===== Season 0 / Prologue — Olethropyla ====='
    if anchor not in text:
        raise RuntimeError('config.yml insertion anchor missing')
    path.write_text(text.replace(anchor, block + anchor, 1), encoding='utf-8')


def main() -> None:
    write('src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java', CRAFT_TRANSACTION)
    write('src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java', SALVAGE_SERVICE)
    write('src/regression/java/hu/taliann/icesmp/professions/Professions2RegressionSuite.java', REGRESSION)
    write('scripts/check_professions_2_reports.py', CHECKER)
    patch_root_config()
    print('Professions 2.0 final closure applied: durable craft snapshot + exact family salvage delivery')


if __name__ == '__main__':
    main()
