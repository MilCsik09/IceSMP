#!/usr/bin/env python3
"""Finish profession async UI, blueprint and weekly-reward integration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHARACTER = ROOT / "src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java"
BLUEPRINT = ROOT / "src/main/java/hu/taliann/icesmp/listeners/BlueprintUseListener.java"
WEEKLY = ROOT / "src/main/java/hu/taliann/icesmp/managers/ProfessionWeeklyGoalManager.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_character() -> None:
    text = CHARACTER.read_text(encoding="utf-8")
    old = '''        if (ctx.professionManager().selectProfession(player, profession)) {
            success(player, ctx.messageManager().getMessage("profession-gui-learned", "&aSzakma megtanulva:")
                    .append(Component.space()).append(profession.getDisplayName()));
        } else {
            fail(player, ctx.messageManager().getComponent("profession-gui-cannot",
                    "&cEzt a szakmát most nem tanulhatod — ebben a kategóriában már betöltötted a helyed."));
        }
        ProfessionGUI.open(player, ctx);
'''
    new = '''        ctx.professionManager().selectProfession(player, profession).whenComplete((selected, failure) ->
                ctx.professionManager().runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure == null && Boolean.TRUE.equals(selected)) {
                        success(player, ctx.messageManager().getMessage(
                                        "profession-gui-learned", "&aSzakma megtanulva:")
                                .append(Component.space()).append(profession.getDisplayName()));
                    } else if (failure == null) {
                        fail(player, ctx.messageManager().getComponent("profession-gui-cannot",
                                "&cEzt a szakmát most nem tanulhatod — ebben a kategóriában már betöltötted a helyed."));
                    } else {
                        fail(player, ctx.messageManager().getComponent("profession-storage-failed",
                                "&cA szakma tartós mentése sikertelen; az állapot nem változott."));
                    }
                    ProfessionGUI.open(player, ctx);
                }));
'''
    CHARACTER.write_text(replace_once(text, old, new, "profession GUI select"), encoding="utf-8")


def patch_blueprint() -> None:
    text = BLUEPRINT.read_text(encoding="utf-8")
    old = '''        if (!professionManager.learnRecipe(player, recipeId)) {
            player.sendMessage(messageManager.get("blueprint-already-known", "&7Ezt a receptet már ismered."));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
        player.sendMessage(messageManager.get("blueprint-learned", "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)", recipe.displayName()));
'''
    new = '''        professionManager.learnRecipe(player, recipeId).whenComplete((learned, failure) ->
                professionManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA tervrajz tartós mentése sikertelen; a tárgy nem fogyott el."));
                        return;
                    }
                    if (!Boolean.TRUE.equals(learned)) {
                        player.sendMessage(messageManager.get("blueprint-already-known",
                                "&7Ezt a receptet már ismered."));
                        return;
                    }
                    // Consumption happens only after the recipe CAS commits. Re-read the hand:
                    // another plugin/event may have replaced the original stack while I/O ran.
                    final ItemStack hand = player.getInventory().getItemInMainHand();
                    if (!recipeId.equals(blueprintFactory.recipeIdOf(hand))) {
                        player.sendMessage(messageManager.get("blueprint-consume-missing",
                                "&eA receptet megtanultad, de a tervrajz már nem volt a kezedben."));
                        return;
                    }
                    hand.setAmount(hand.getAmount() - 1);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
                    player.sendMessage(messageManager.get("blueprint-learned",
                            "&aÚj receptet tanultál: &e%s&a! (Recept-könyv: /profession recipes)",
                            recipe.displayName()));
                }));
'''
    BLUEPRINT.write_text(replace_once(text, old, new, "blueprint durable learn"), encoding="utf-8")


def patch_weekly() -> None:
    text = WEEKLY.read_text(encoding="utf-8")
    old_online = '''                if (online != null) {
                    // Folia: a jutalom a JÁTÉKOS saját régió-szálán íródik (PDC).
                    online.getScheduler().run(plugin, task -> {
                        professionManager.addXpFor(online, profession, rewardXp);
                        online.sendMessage(messageManager.getMessage("profession-weekly-reward",
                                "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                Map.of("xp", String.valueOf(rewardXp), "profession", profession.getId())));
                    }, null);
                } else {
'''
    new_online = '''                if (online != null) {
                    professionManager.addXp(online, profession, rewardXp).whenComplete((change, failure) ->
                            professionManager.runOnOwnerThread(online, () -> {
                                if (failure == null && change != null && change.changed() && online.isOnline()) {
                                    online.sendMessage(messageManager.getMessage("profession-weekly-reward",
                                            "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                            Map.of("xp", String.valueOf(rewardXp),
                                                    "profession", profession.getId())));
                                }
                            }));
                } else {
'''
    text = replace_once(text, old_online, new_online, "online weekly reward")
    old_join = '''        final Map<String, Integer> pending = pendingRewards.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        for (final Map.Entry<String, Integer> entry : pending.entrySet()) {
            final ProfessionType profession = ProfessionType.fromId(entry.getKey());
            if (profession != null) {
                professionManager.addXpFor(event.getPlayer(), profession, entry.getValue());
                event.getPlayer().sendMessage(messageManager.getMessage("profession-weekly-reward",
                        "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                        Map.of("xp", String.valueOf(entry.getValue()), "profession", entry.getKey())));
            }
        }
        save();
'''
    new_join = '''        final UUID playerId = event.getPlayer().getUniqueId();
        final Map<String, Integer> pending = pendingRewards.get(playerId);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (final Map.Entry<String, Integer> entry : Map.copyOf(pending).entrySet()) {
            final ProfessionType profession = ProfessionType.fromId(entry.getKey());
            if (profession == null) {
                continue;
            }
            professionManager.addXp(event.getPlayer(), profession, entry.getValue())
                    .whenComplete((change, failure) -> professionManager.runOnOwnerThread(
                            event.getPlayer(), () -> {
                                if (failure != null || change == null || !change.changed()
                                        || !event.getPlayer().isOnline()) {
                                    return;
                                }
                                consumePendingReward(playerId, entry.getKey(), entry.getValue());
                                event.getPlayer().sendMessage(messageManager.getMessage(
                                        "profession-weekly-reward",
                                        "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                        Map.of("xp", String.valueOf(entry.getValue()),
                                                "profession", entry.getKey())));
                            }));
        }
'''
    text = replace_once(text, old_join, new_join, "pending weekly reward")
    anchor = '''    @Override
    public synchronized void load() {
'''
    helper = '''    private synchronized void consumePendingReward(final UUID playerId,
                                                   final String professionId,
                                                   final int expectedAmount) {
        final Map<String, Integer> pending = pendingRewards.get(playerId);
        if (pending == null || !java.util.Objects.equals(
                pending.get(professionId), expectedAmount)) {
            return;
        }
        pending.remove(professionId);
        if (pending.isEmpty()) {
            pendingRewards.remove(playerId);
        }
        save();
    }

'''
    if helper not in text:
        text = replace_once(text, anchor, helper + anchor, "weekly consume helper")
    WEEKLY.write_text(text, encoding="utf-8")


def main() -> int:
    patch_character()
    patch_blueprint()
    patch_weekly()
    print("Profession async UI and weekly reward integration applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
