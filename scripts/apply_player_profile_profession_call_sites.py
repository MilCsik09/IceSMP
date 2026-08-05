#!/usr/bin/env python3
"""Convert profession command/listener call sites to durable async PlayerProfile mutations."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMAND = ROOT / "src/main/java/hu/taliann/icesmp/commands/ProfessionCommand.java"
XP_LISTENER = ROOT / "src/main/java/hu/taliann/icesmp/listeners/ProfessionXpListener.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_command() -> None:
    text = COMMAND.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        if (!professionManager.selectProfession(player, profession)) {
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
''',
        '''        professionManager.selectProfession(player, profession).whenComplete((selected, failure) ->
                professionManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(messageManager.get(
                                "profession-storage-failed",
                                "&cA szakma tartós mentése sikertelen; az állapot nem változott."));
                        return;
                    }
                    if (!selected) {
                        player.sendMessage(messageManager.get(
                                "profession-slot-taken",
                                "&cMár van %s szakmád. Szakmaváltást csak admin végezhet.",
                                profession.getCategory().getDisplayName().toLowerCase(Locale.ROOT)
                        ));
                        return;
                    }
                    player.sendMessage(messageManager.getMessage(
                                    "profession-join-success", "&aSzakma kiválasztva:")
                            .append(Component.space())
                            .append(profession.getDisplayName()));
                }));
''',
        "profession join",
    )
    text = replace_once(
        text,
        '''        target.getScheduler().run(plugin, task -> {
            professionManager.setProfession(target, profession);
            sender.sendMessage(messageManager.get("profession-set-success", "&aSzakma beállítva: &f%s &7-> &e%s", target.getName(), profession.getId()));
        }, null);
''',
        '''        professionManager.setProfession(target, profession).whenComplete((changed, failure) ->
                professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get(
                                "profession-storage-failed",
                                "&cA szakma tartós mentése sikertelen; az állapot nem változott."));
                        return;
                    }
                    sender.sendMessage(messageManager.get("profession-set-success",
                            "&aSzakma beállítva: &f%s &7-> &e%s",
                            target.getName(), profession.getId()));
                }));
''',
        "profession admin set",
    )
    text = replace_once(
        text,
        '''        target.getScheduler().run(plugin, task -> {
            professionManager.clearProfession(target, category);
            sender.sendMessage(messageManager.get("profession-clear-success", "&aSzakma slot törölve: &f%s &7(%s)", target.getName(), category.getDisplayName()));
        }, null);
''',
        '''        professionManager.clearProfession(target, category).whenComplete((changed, failure) ->
                professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get(
                                "profession-storage-failed",
                                "&cA szakma tartós mentése sikertelen; az állapot nem változott."));
                        return;
                    }
                    sender.sendMessage(messageManager.get("profession-clear-success",
                            "&aSzakma slot törölve: &f%s &7(%s)",
                            target.getName(), category.getDisplayName()));
                }));
''',
        "profession admin clear",
    )
    text = replace_once(
        text,
        '''        target.getScheduler().run(plugin, task -> {
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
''',
        '''        professionManager.addXp(target, profession, amount).whenComplete((change, failure) ->
                professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null || change == null || !change.changed()) {
                        sender.sendMessage(messageManager.get(
                                "profession-addxp-failed",
                                "&cNem sikerült XP-t adni vagy tartósan menteni."));
                        return;
                    }
                    sender.sendMessage(messageManager.get(
                            "profession-addxp-success",
                            "&aXP hozzáadva: &f%s &7| %s +&f%s XP &7| Szint: &f%s",
                            target.getName(), profession.getId(), amount, change.level()));
                }));
''',
        "profession admin add xp",
    )
    COMMAND.write_text(text, encoding="utf-8")


def patch_xp_listener() -> None:
    text = XP_LISTENER.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        // Az addXpFor false-t ad, ha a játékos NEM gyakorolja ezt a szakmát (nem kapott XP-t).
        // A heti közös cél csak valódi jóváírásra tölthető, különben egy nem-bányász
        // ércbontása is töltötte a Bányász-céh heti célját (szakma-identitás nélküli farm).
        if (!professionManager.addXpFor(player, profession, totalXp)) {
            return;
        }
        final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
        if (weeklyRef != null) {
            weeklyRef.add(player, profession, totalXp);
        }
''',
        '''        // The weekly shared aggregate advances only after the PlayerProfile profession
        // section CAS durably commits the activity XP.
        professionManager.addXpFor(player, profession, totalXp).whenComplete((change, failure) ->
                professionManager.runOnOwnerThread(player, () -> {
                    if (failure != null || change == null || !change.changed() || !player.isOnline()) {
                        return;
                    }
                    final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
                    if (weeklyRef != null) {
                        weeklyRef.add(player, profession, totalXp);
                    }
                }));
''',
        "profession activity xp",
    )
    XP_LISTENER.write_text(text, encoding="utf-8")


def main() -> int:
    patch_command()
    patch_xp_listener()
    print("Profession async call sites applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
