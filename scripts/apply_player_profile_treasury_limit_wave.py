#!/usr/bin/env python3
"""Move per-player treasury withdrawal limits from PDC to PlayerProfile faction state."""
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


def write_store() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileTreasuryWithdrawalStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed per-player treasury withdrawal allowance; the treasury itself remains global. */
public final class PlayerProfileTreasuryWithdrawalStore {
    private static final String DAY_KEY = "treasury.withdraw.day";
    private static final String AMOUNT_KEY = "treasury.withdraw.milli";
    private static final long SCALE = 1_000L;

    public CompletionStage<Reservation> reserve(final UUID playerId, final long day,
                                                final double amount, final double cap) {
        final long requested = toMilli(amount, "amount");
        final long maximum = cap <= 0.0D ? Long.MAX_VALUE : toMilli(cap, "cap");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final long storedDay = number(current.extensions().get(DAY_KEY), -1L);
                    final long previous = storedDay == day
                            ? number(current.extensions().get(AMOUNT_KEY), 0L) : 0L;
                    if (requested > maximum || previous > maximum - requested) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Reservation(false, day, previous, requested));
                    }
                    FactionSection next = PlayerProfileSectionExtensions.put(current, DAY_KEY, day);
                    next = PlayerProfileSectionExtensions.put(next, AMOUNT_KEY,
                            Math.addExact(previous, requested));
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new Reservation(true, day, previous, requested));
                });
    }

    /** Compensates only the exact reservation produced by this call; concurrent use fails closed. */
    public CompletionStage<Boolean> rollback(final UUID playerId, final Reservation reservation) {
        if (reservation == null || !reservation.allowed()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final long storedDay = number(current.extensions().get(DAY_KEY), -1L);
                    final long stored = number(current.extensions().get(AMOUNT_KEY), 0L);
                    final long expected = Math.addExact(reservation.previousMilli(),
                            reservation.reservedMilli());
                    if (storedDay != reservation.day() || stored != expected) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final FactionSection next = PlayerProfileSectionExtensions.put(
                            current, AMOUNT_KEY, reservation.previousMilli());
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public double usedToday(final UUID playerId, final long day) {
        final FactionSection current = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.FACTION, FactionSection.class);
        if (number(current.extensions().get(DAY_KEY), -1L) != day) return 0.0D;
        return number(current.extensions().get(AMOUNT_KEY), 0L) / (double) SCALE;
    }

    private static long toMilli(final double amount, final String label) {
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        final double scaled = amount * SCALE;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " exceeds storage range");
        }
        return Math.round(scaled);
    }

    private static long number(final Object raw, final long fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) {
            throw new IllegalStateException("Invalid treasury allowance extension type");
        }
        final long value = number.longValue();
        if (value < 0L) throw new IllegalStateException("Negative treasury allowance extension");
        return value;
    }

    public record Reservation(boolean allowed, long day, long previousMilli,
                              long reservedMilli) {
        public Reservation {
            if (day < 0L || previousMilli < 0L || reservedMilli <= 0L) {
                throw new IllegalArgumentException("Invalid treasury allowance reservation");
            }
        }
    }
}
''', encoding="utf-8")


def patch_subcommand() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/commands/faction/FactionTreasurySubcommand.java"
    text = path.read_text(encoding="utf-8")
    text = text.replace('''    private final org.bukkit.NamespacedKey withdrawDayKey =
            org.bukkit.NamespacedKey.fromString("icesmp:treasury_withdraw_day");
    private final org.bukkit.NamespacedKey withdrawSumKey =
            org.bukkit.NamespacedKey.fromString("icesmp:treasury_withdraw_sum");
''', '''    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileTreasuryWithdrawalStore
            withdrawalStore = new hu.taliann.icesmp.playerprofile.application.PlayerProfileTreasuryWithdrawalStore();
    private final org.bukkit.plugin.java.JavaPlugin plugin =
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(FactionTreasurySubcommand.class);
''')

    start = text.index('        final long today = System.currentTimeMillis() / 86_400_000L;')
    end = text.index('\n        return true;\n    }', start)
    replacement = '''        final long today = System.currentTimeMillis() / 86_400_000L;
        if (councilPath) {
            final double[] shared = councilWithdrawnToday.get(faction);
            final double takenToday = shared != null && (long) shared[0] == today ? shared[1] : 0.0D;
            if (dailyCap > 0.0D && takenToday + amount > dailyCap) {
                player.sendMessage(messageManager.get(
                        "messages.faction-treasury-daily-cap",
                        "&cA mai kassza-kivét keret elfogyott (&f%s&c/nap). Holnap folytathatod.",
                        currencyManager.formatBalance(dailyCap)));
                return true;
            }
            if (!treasuryManager.withdraw(faction, amount)) {
                player.sendMessage(messageManager.get("messages.faction-treasury-insufficient",
                        "&cNincs ennyi a frakciókasszában."));
                return true;
            }
            councilWithdrawnToday.compute(faction, (key, old) ->
                    old == null || (long) old[0] != today
                            ? new double[]{today, amount} : new double[]{today, old[1] + amount});
            finishWithdrawal(player, faction, amount);
            return true;
        }

        withdrawalStore.reserve(player.getUniqueId(), today, amount, dailyCap)
                .whenComplete((reservation, reserveFailure) -> {
                    if (reserveFailure != null) {
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-profile-failed",
                                "&cA napi kivételi keret PlayerProfile mentése meghiúsult; a kassza nem változott.")));
                        return;
                    }
                    if (reservation == null || !reservation.allowed()) {
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-daily-cap",
                                "&cA mai kassza-kivét keret elfogyott (&f%s&c/nap). Holnap folytathatod.",
                                currencyManager.formatBalance(dailyCap))));
                        return;
                    }
                    final boolean withdrawn;
                    try {
                        withdrawn = treasuryManager.withdraw(faction, amount);
                    } catch (final RuntimeException failure) {
                        withdrawalStore.rollback(player.getUniqueId(), reservation);
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-persistence-failed",
                                "&cA frakciókassza tartós kivéte meghiúsult; a napi keret kompenzálása elindult.")));
                        return;
                    }
                    if (!withdrawn) {
                        withdrawalStore.rollback(player.getUniqueId(), reservation)
                                .whenComplete((rolledBack, rollbackFailure) -> runOnOwner(player, () ->
                                        player.sendMessage(messageManager.get(
                                                "messages.faction-treasury-insufficient",
                                                "&cNincs ennyi a frakciókasszában."))));
                        return;
                    }
                    runOnOwner(player, () -> finishWithdrawal(player, faction, amount));
                });
        return true;
    }

    private void finishWithdrawal(final Player player, final FactionType faction,
                                  final double amount) {
        currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction),
                (long) Math.floor(amount));
        player.sendMessage(messageManager.get(
                "messages.faction-treasury-withdraw-success",
                "&aKivét a kasszából: &f%s %s &7(veretben, a kezedbe).",
                currencyManager.formatBalance(amount), faction.getDisplayName()));
    }

    private void runOnOwner(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
'''
    text = text[:start] + replacement + text[end:]
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_subcommand()
    print("PlayerProfile treasury withdrawal authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
