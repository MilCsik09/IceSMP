#!/usr/bin/env python3
"""Convert talent callers to durable asynchronous PlayerProfile transactions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TALENT_COMMAND = ROOT / "src/main/java/hu/taliann/icesmp/commands/TalentCommand.java"
MEMORY_COMMAND = ROOT / "src/main/java/hu/taliann/icesmp/commands/MemoryCommand.java"
CHARACTER = ROOT / "src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java"
RESPEC = ROOT / "src/main/java/hu/taliann/icesmp/managers/RespecService.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_talent_command() -> None:
    text = TALENT_COMMAND.read_text(encoding="utf-8")
    old = '''        if (!talentManager.spendPoint(player, classPool, args[2])) {
            player.sendMessage(messageManager.get(
                    "talent-spend-failed",
                    "&cNem sikerült a pont elköltése (nincs pont, ismeretlen talent vagy max szint)."
            ));
            return;
        }

        player.sendMessage(messageManager.getMessage(
                "talent-spend-success",
                "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                Map.of(
                        "talent", args[2].toLowerCase(Locale.ROOT),
                        "rank", String.valueOf(talentManager.getRank(player, classPool, args[2])),
                        "points", String.valueOf(talentManager.getAvailablePoints(player, classPool))
                )
        ));
'''
    new = '''        talentManager.spendPoint(player, classPool, args[2]).whenComplete((spent, failure) ->
                talentManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(messageManager.get(
                                "talent-storage-failed",
                                "&cA talent tartós mentése sikertelen; az állapot nem változott."));
                        return;
                    }
                    if (!Boolean.TRUE.equals(spent)) {
                        player.sendMessage(messageManager.get(
                                "talent-spend-failed",
                                "&cNem sikerült a pont elköltése (nincs pont, ismeretlen talent vagy max szint)."));
                        return;
                    }
                    player.sendMessage(messageManager.getMessage(
                            "talent-spend-success",
                            "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                            Map.of(
                                    "talent", args[2].toLowerCase(Locale.ROOT),
                                    "rank", String.valueOf(talentManager.getRank(player, classPool, args[2])),
                                    "points", String.valueOf(talentManager.getAvailablePoints(player, classPool))
                            )));
                }));
'''
    TALENT_COMMAND.write_text(replace_once(text, old, new, "talent command spend"), encoding="utf-8")


def patch_memory_command() -> None:
    text = MEMORY_COMMAND.read_text(encoding="utf-8")
    old = '''        talentManager.grantBonusPoints(player, true, 1);
        remembered(player, messageManager.get("memory-redeemed-talent",
                "&d✦ Egy régi lecke tér vissza — &f+1 kaszt-talentpont&d (&7/talent&d)."));
'''
    new = '''        talentManager.grantBonusPoints(player, true, 1).whenComplete((bonus, failure) ->
                talentManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        refundShards(player, cost);
                        player.sendMessage(messageManager.get("memory-talent-failed",
                                "&cA talentpont jóváírása meghiúsult; az Emlékszilánkokat visszakaptad."));
                        return;
                    }
                    remembered(player, messageManager.get("memory-redeemed-talent",
                            "&d✦ Egy régi lecke tér vissza — &f+1 kaszt-talentpont&d (&7/talent&d)."));
                }));
'''
    MEMORY_COMMAND.write_text(replace_once(text, old, new, "memory talent redemption"), encoding="utf-8")


def patch_character() -> None:
    text = CHARACTER.read_text(encoding="utf-8")
    old = '''        if (ctx.talentManager().spendPoint(player, node.classPool(), node.id())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
            player.sendMessage(ctx.messageManager().getMessage(
                    "talent-spend-success",
                    "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                    Map.of(
                            "talent", talentDisplayName(ctx, node),
                            "rank", String.valueOf(ctx.talentManager().getRank(player, node.classPool(), node.id())),
                            "points", String.valueOf(ctx.talentManager().getAvailablePoints(player, node.classPool()))
                    )));
        } else {
            fail(player, ctx.messageManager().getComponent("talent-spend-failed",
                    "&cNem sikerült a pont elköltése (zárolt talent, nincs pont, vagy max rang)."));
        }
        TalentGUI.open(player, ctx);
'''
    new = '''        ctx.talentManager().spendPoint(player, node.classPool(), node.id())
                .whenComplete((spent, failure) -> ctx.talentManager().runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        fail(player, ctx.messageManager().getComponent("talent-storage-failed",
                                "&cA talent tartós mentése sikertelen; az állapot nem változott."));
                    } else if (Boolean.TRUE.equals(spent)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
                        player.sendMessage(ctx.messageManager().getMessage(
                                "talent-spend-success",
                                "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                                Map.of(
                                        "talent", talentDisplayName(ctx, node),
                                        "rank", String.valueOf(ctx.talentManager().getRank(
                                                player, node.classPool(), node.id())),
                                        "points", String.valueOf(ctx.talentManager().getAvailablePoints(
                                                player, node.classPool()))
                                )));
                    } else {
                        fail(player, ctx.messageManager().getComponent("talent-spend-failed",
                                "&cNem sikerült a pont elköltése (zárolt talent, nincs pont, vagy max rang)."));
                    }
                    TalentGUI.open(player, ctx);
                }));
'''
    CHARACTER.write_text(replace_once(text, old, new, "talent GUI spend"), encoding="utf-8")


def patch_respec() -> None:
    text = RESPEC.read_text(encoding="utf-8")
    old = '''    private CompletionStage<Integer> completePlayerEffects(final Player player, final UUID sessionToken) {
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
            if (!gateway.isCurrentSession(player.getUniqueId(), sessionToken)) {
                result.completeExceptionally(new IllegalStateException("stale respec session completion"));
                return;
            }
            try { result.complete(talentManager.refundUnavailableTalents(player, true)); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException("Player scheduler rejected respec completion")));
        return result;
    }
'''
    new = '''    private CompletionStage<Integer> completePlayerEffects(final Player player, final UUID sessionToken) {
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
            if (!gateway.isCurrentSession(player.getUniqueId(), sessionToken)) {
                gate.completeExceptionally(new IllegalStateException("stale respec session completion"));
                return;
            }
            gate.complete(null);
        }, () -> gate.completeExceptionally(
                new IllegalStateException("Player scheduler rejected respec completion")));
        return gate.thenCompose(ignored -> talentManager.refundUnavailableTalents(player, true));
    }
'''
    RESPEC.write_text(replace_once(text, old, new, "respec talent refund"), encoding="utf-8")


def main() -> int:
    patch_talent_command()
    patch_memory_command()
    patch_character()
    patch_respec()
    print("Talent async call sites applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
