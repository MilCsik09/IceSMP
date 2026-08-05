#!/usr/bin/env python3
"""Make weekly profession rewards consume pending state only after profile CAS."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "src/main/java/hu/taliann/icesmp/managers/ProfessionWeeklyGoalManager.java"
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)


replace_once('''                if (online != null) {
                    // Folia: a jutalom a JÁTÉKOS saját régió-szálán íródik (PDC).
                    online.getScheduler().run(plugin, task -> {
                        professionManager.addXpFor(online, profession, rewardXp);
                        online.sendMessage(messageManager.getMessage("profession-weekly-reward",
                                "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                Map.of("xp", String.valueOf(rewardXp), "profession", profession.getId())));
                    }, null);
                } else {
''', '''                if (online != null) {
                    professionManager.addXp(online, profession, rewardXp)
                            .whenComplete((change, failure) -> professionManager.runOnOwnerThread(
                                    online, () -> {
                                        if (failure == null && change != null && change.changed()
                                                && online.isOnline()) {
                                            online.sendMessage(messageManager.getMessage(
                                                    "profession-weekly-reward",
                                                    "<gold>⚒ Szakma-céh jutalom: <white>+{xp} {profession} XP</white> a heti közös célért!</gold>",
                                                    Map.of("xp", String.valueOf(rewardXp),
                                                            "profession", profession.getId())));
                                        }
                                    }));
                } else {
''', "online weekly reward")

replace_once('''        final Map<String, Integer> pending = pendingRewards.remove(event.getPlayer().getUniqueId());
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
''', '''        final UUID playerId = event.getPlayer().getUniqueId();
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
''', "pending weekly reward")

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
    if text.count(anchor) != 1:
        raise RuntimeError("weekly load anchor mismatch")
    text = text.replace(anchor, helper + anchor, 1)

path.write_text(text, encoding="utf-8")
print("Profession weekly reward persistence patch applied.")
