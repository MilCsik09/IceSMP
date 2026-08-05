#!/usr/bin/env python3
"""Convert remaining synchronous profession call sites to CompletionStage handling."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_command() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/commands/ProfessionCommand.java"
    replace_once(path, '''        if (!professionManager.selectProfession(player, profession)) {
            sender.sendMessage(messageManager.get(
                    "profession-slot-taken",
                    "&cMár van %s szakmád. Szakmaváltást csak admin végezhet.",
                    profession.getCategory().getDisplayName().toLowerCase(Locale.ROOT)
            ));
            return;
        }

        player.sendMessage(messageManager.getMessage("profession-join-success", "&aSzakma kiválasztva:")
                .append(Component.space())
                .append(profession.getDisplayName()));
''', '''        professionManager.selectProfession(player, profession)
                .whenComplete((selected, failure) -> professionManager.runOnOwnerThread(player, () -> {
                    if (failure != null) {
                        player.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else if (!Boolean.TRUE.equals(selected)) {
                        player.sendMessage(messageManager.get(
                                "profession-slot-taken",
                                "&cMár van %s szakmád. Szakmaváltást csak admin végezhet.",
                                profession.getCategory().getDisplayName().toLowerCase(Locale.ROOT)));
                    } else {
                        player.sendMessage(messageManager.getMessage("profession-join-success",
                                        "&aSzakma kiválasztva:")
                                .append(Component.space()).append(profession.getDisplayName()));
                    }
                }));
''', "profession join async")

    replace_once(path, '''        target.getScheduler().run(plugin, task -> {
            professionManager.setProfession(target, profession);
            sender.sendMessage(messageManager.get("profession-set-success", "&aSzakma beállítva: &f%s &7-> &e%s", target.getName(), profession.getId()));
        }, null);
''', '''        professionManager.setProfession(target, profession)
                .whenComplete((changed, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else {
                        sender.sendMessage(messageManager.get("profession-set-success",
                                "&aSzakma beállítva: &f%s &7-> &e%s",
                                target.getName(), profession.getId()));
                    }
                }));
''', "profession admin set async")

    replace_once(path, '''        target.getScheduler().run(plugin, task -> {
            professionManager.clearProfession(target, category);
            sender.sendMessage(messageManager.get("profession-clear-success", "&aSzakma slot törölve: &f%s &7(%s)", target.getName(), category.getDisplayName()));
        }, null);
''', '''        professionManager.clearProfession(target, category)
                .whenComplete((changed, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else {
                        sender.sendMessage(messageManager.get("profession-clear-success",
                                "&aSzakma slot törölve: &f%s &7(%s)",
                                target.getName(), category.getDisplayName()));
                    }
                }));
''', "profession admin clear async")

    replace_once(path, '''        target.getScheduler().run(plugin, task -> {
            if (!professionManager.addXp(target, profession, amount)) {
                sender.sendMessage(messageManager.get("profession-addxp-failed", "&cNem sikerült XP-t adni (hibás összeg)."));
                return;
            }

            sender.sendMessage(messageManager.get(
                    "profession-addxp-success",
                    "&aXP hozzáadva: &f%s &7| %s +&f%s XP &7| Szint: &f%s",
                    target.getName(),
                    profession.getId(),
                    amount,
                    professionManager.getLevel(target, profession)
            ));
        }, null);
''', '''        professionManager.addXp(target, profession, amount)
                .whenComplete((change, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null || change == null || !change.changed()) {
                        sender.sendMessage(messageManager.get("profession-addxp-failed",
                                "&cNem sikerült XP-t adni (érvénytelen kérés vagy tárolási hiba)."));
                        return;
                    }
                    sender.sendMessage(messageManager.get(
                            "profession-addxp-success",
                            "&aXP hozzáadva: &f%s &7| %s +&f%s XP &7| Szint: &f%s",
                            target.getName(), profession.getId(), amount, change.level()));
                }));
''', "profession admin xp async")


def patch_xp_listener() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/ProfessionXpListener.java"
    replace_once(path, '''        if (!professionManager.addXpFor(player, profession, totalXp)) {
            return;
        }
        final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
        if (weeklyRef != null) {
            weeklyRef.add(player, profession, totalXp);
        }
''', '''        professionManager.addXpFor(player, profession, totalXp)
                .whenComplete((change, failure) -> {
                    if (failure != null || change == null || !change.changed()) {
                        return;
                    }
                    professionManager.runOnOwnerThread(player, () -> {
                        final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
                        if (weeklyRef != null && player.isOnline()) {
                            weeklyRef.add(player, profession, totalXp);
                        }
                    });
                });
''', "profession activity xp async")


def patch_character_gui() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java"
    replace_once(path, '''        if (ctx.professionManager().selectProfession(player, profession)) {
            success(player, ctx.messageManager().getMessage("profession-gui-learned", "&aSzakma megtanulva:")
                    .append(Component.space()).append(profession.getDisplayName()));
        } else {
            fail(player, ctx.messageManager().getComponent("profession-gui-cannot",
                    "&cEzt a szakmát most nem tanulhatod — ebben a kategóriában már betöltötted a helyed."));
        }
        ProfessionGUI.open(player, ctx);
''', '''        ctx.professionManager().selectProfession(player, profession)
                .whenComplete((selected, failure) -> ctx.professionManager().runOnOwnerThread(player, () -> {
                    if (failure != null) {
                        fail(player, ctx.messageManager().getComponent("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else if (Boolean.TRUE.equals(selected)) {
                        success(player, ctx.messageManager().getMessage("profession-gui-learned",
                                        "&aSzakma megtanulva:")
                                .append(Component.space()).append(profession.getDisplayName()));
                    } else {
                        fail(player, ctx.messageManager().getComponent("profession-gui-cannot",
                                "&cEzt a szakmát most nem tanulhatod — ebben a kategóriában már betöltötted a helyed."));
                    }
                    if (player.isOnline()) ProfessionGUI.open(player, ctx);
                }));
''', "profession GUI async")


def patch_blueprint() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/BlueprintUseListener.java"
    replace_once(path, '''        if (!professionManager.learnRecipe(player, recipeId)) {
            player.sendMessage(messageManager.get("blueprint-already-known", "&7Ezt a receptet már ismered."));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
        player.sendMessage(messageManager.get("blueprint-learned", "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)", recipe.displayName()));
''', '''        professionManager.learnRecipe(player, recipeId)
                .whenComplete((learned, failure) -> professionManager.runOnOwnerThread(player, () -> {
                    if (failure != null) {
                        player.sendMessage(messageManager.get("blueprint-storage-failed",
                                "&cA recept PlayerProfile mentése meghiúsult; a tervrajz nem fogyott el."));
                        return;
                    }
                    if (!Boolean.TRUE.equals(learned)) {
                        player.sendMessage(messageManager.get("blueprint-already-known",
                                "&7Ezt a receptet már ismered."));
                        return;
                    }
                    final ItemStack current = player.getInventory().getItemInMainHand();
                    if (!recipeId.equals(blueprintFactory.recipeIdOf(current)) || current.getAmount() <= 0) {
                        player.sendMessage(messageManager.get("blueprint-item-changed",
                                "&eA recept elmentve, de a kézben tartott tervrajz megváltozott, ezért nem fogyasztottunk itemet."));
                        return;
                    }
                    current.setAmount(current.getAmount() - 1);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
                    player.sendMessage(messageManager.get("blueprint-learned",
                            "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)",
                            recipe.displayName()));
                }));
''', "blueprint durable consume")


def main() -> int:
    patch_command()
    patch_xp_listener()
    patch_character_gui()
    patch_blueprint()
    print("PlayerProfile profession call-site wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
