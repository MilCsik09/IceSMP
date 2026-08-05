#!/usr/bin/env python3
"""Apply the PlayerProfile spell-mastery integration to call sites and lifecycle."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURRENCY = ROOT / "src/main/java/hu/taliann/icesmp/managers/CurrencyManager.java"
SPELL_COMMAND = ROOT / "src/main/java/hu/taliann/icesmp/commands/SpellCommand.java"
CORE = ROOT / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_currency() -> None:
    text = CURRENCY.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import java.util.Locale;\nimport java.util.Optional;\n",
        "import java.util.List;\nimport java.util.Locale;\nimport java.util.Optional;\n",
        "CurrencyManager List import",
    )
    anchor = '''    public DurableWalletOperation commitOperation(final String operationId) {
'''
    block = '''    /** Immutable deterministic snapshot for restart recovery of one operation namespace. */
    public List<DurableWalletOperation> durableOperationsByPrefix(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("operation prefix cannot be blank");
        }
        synchronized (saveLock) {
            return durableWalletOperations.values().stream()
                    .filter(operation -> operation.operationId().startsWith(prefix))
                    .sorted(java.util.Comparator
                            .comparingLong(DurableWalletOperation::createdAtEpochMillis)
                            .thenComparing(DurableWalletOperation::operationId))
                    .toList();
        }
    }

'''
    text = replace_once(text, anchor, block + anchor, "durable operation snapshot")
    CURRENCY.write_text(text, encoding="utf-8")


def patch_spell_command() -> None:
    text = SPELL_COMMAND.read_text(encoding="utf-8")
    old = '''        final long cost = masteryManager.getUpgradeCost(player, spellId);
        final SpellMasteryManager.UpgradeResult result = masteryManager.upgrade(player, spellId);
        switch (result) {
            case SUCCESS -> player.sendMessage(messageManager.getMessage(
                    "spell-mastery-upgraded",
                    "&aMesterség fejlesztve: &e{spell} &7(rang &f{rank}&7, ár: &f{cost}&7)",
                    Map.of(
                            "spell", spellRegistry.getById(spellId).getName(),
                            "rank", String.valueOf(masteryManager.getRank(player, spellId)),
                            "cost", String.valueOf(cost)
                    )));
            case MAX_RANK -> player.sendMessage(messageManager.get("spell-mastery-max", "&7Ez a képesség már maximális mesterségű."));
            case INSUFFICIENT_FUNDS -> player.sendMessage(messageManager.get("spell-mastery-poor", "&cNincs elég frakcióvalutád (&f%s&c kellene).", cost));
        }
'''
    new = '''        final long cost = masteryManager.getUpgradeCost(player, spellId);
        masteryManager.upgrade(player, spellId).whenComplete((result, failure) ->
                masteryManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(messageManager.get(
                                "spell-mastery-storage-failed",
                                "&cA mesterség tartós mentése sikertelen; a wallet művelet helyreállításra került."));
                        return;
                    }
                    switch (result) {
                        case SUCCESS -> player.sendMessage(messageManager.getMessage(
                                "spell-mastery-upgraded",
                                "&aMesterség fejlesztve: &e{spell} &7(rang &f{rank}&7, ár: &f{cost}&7)",
                                Map.of(
                                        "spell", spellRegistry.getById(spellId).getName(),
                                        "rank", String.valueOf(masteryManager.getRank(player, spellId)),
                                        "cost", String.valueOf(cost)
                                )));
                        case MAX_RANK -> player.sendMessage(messageManager.get(
                                "spell-mastery-max",
                                "&7Ez a képesség már maximális mesterségű."));
                        case INSUFFICIENT_FUNDS -> player.sendMessage(messageManager.get(
                                "spell-mastery-poor",
                                "&cNincs elég frakcióvalutád (&f%s&c kellene).", cost));
                    }
                }));
'''
    text = replace_once(text, old, new, "SpellCommand async upgrade")
    SPELL_COMMAND.write_text(text, encoding="utf-8")


def patch_core() -> None:
    text = CORE.read_text(encoding="utf-8")
    old = '''        storeCoordinator.loadAll();
        siegeWeaponFactory.registerRecipe();
'''
    new = '''        storeCoordinator.loadAll();
        // Exact-once mastery wallet witnesses are reconciled against PlayerProfile receipts
        // before listeners or commands can admit new gameplay mutations.
        spellMasteryManager.recoverPendingOperations().toCompletableFuture().join();
        siegeWeaponFactory.registerRecipe();
'''
    text = replace_once(text, old, new, "mastery startup recovery")
    CORE.write_text(text, encoding="utf-8")


def main() -> int:
    patch_currency()
    patch_spell_command()
    patch_core()
    print("PlayerProfile spell mastery wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
