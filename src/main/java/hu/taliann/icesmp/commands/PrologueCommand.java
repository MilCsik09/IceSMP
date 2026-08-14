package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.prologue.BreachSeverity;
import hu.taliann.icesmp.prologue.PrologueFinalePhase;
import hu.taliann.icesmp.prologue.PrologueRuntime;
import hu.taliann.icesmp.prologue.PrologueStage;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /prologue — explicit, audited Season 0 live-ops surface. */
public final class PrologueCommand implements BasicCommand {
    private final PrologueRuntime runtime;

    public PrologueCommand(final PrologueRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack stack, final @NonNull String[] args) {
        final CommandSender sender = stack.getSender();
        if (!sender.hasPermission(Permissions.PROLOGUE)) {
            sender.sendMessage("§cNincs jogosultságod ehhez a parancshoz.");
            return;
        }
        try {
            if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
                sender.sendMessage(runtime.statusLine());
                sender.sendMessage(runtime.commitChainLine());
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start" -> start(sender);
                case "reset" -> reset(sender, args);
                case "advance" -> advance(sender, args);
                case "stage" -> stage(sender, args);
                case "stability" -> stability(sender, args);
                case "breach" -> breach(sender, args);
                case "finale" -> finale(sender, args);
                case "gate" -> gate(sender, args);
                default -> usage(sender);
            }
        } catch (final IllegalArgumentException | IllegalStateException failure) {
            sender.sendMessage("§cPrologue: " + failure.getMessage());
        }
    }

    private void start(final CommandSender sender) {
        sender.sendMessage(runtime.manager().arm(sender.getName())
                ? "§aA Prologue élesítve: Olethropyla eszkalációja mostantól számol."
                : "§eA Prologue már fut vagy lezárult; az élesítés nem szükséges.");
    }

    private void reset(final CommandSender sender, final String[] args) {
        if (args.length < 2 || !"--force".equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("Veszélyes teszt-visszatekerés: /prologue reset --force");
        }
        final boolean seasonRolledBack = runtime.manager().seasonOneStarted();
        runtime.resetForTesting(sender.getName());
        sender.sendMessage("§cA Prologue visszatekerve DORMANT állapotba. Indítás: §f/prologue start");
        if (seasonRolledBack) {
            sender.sendMessage("§eA Season 1 átbillenés is visszavonva; a szezon friss timestamppel indul újra.");
        }
        sender.sendMessage("§7Nem áll vissza: a már kiosztott Founder/finálé achievementek, "
                + "a krónika-bejegyzés és az emlékmű sora.");
    }

    /** Futó tartós finálé alatt fázist léptet, egyébként eszkalációs stage-et — mindkettő csak előre. */
    private void advance(final CommandSender sender, final String[] args) {
        final PrologueFinalePhase phase = runtime.manager().finalePhase();
        if (runtime.manager().finaleId() != null && phase.running()) {
            final PrologueFinalePhase next = args.length >= 2
                    ? PrologueFinalePhase.valueOf(args[1].toUpperCase(Locale.ROOT))
                    : nextPhase(phase);
            if (next == PrologueFinalePhase.COMPLETED || next == PrologueFinalePhase.ABORTED) {
                throw new IllegalArgumentException(
                        "A lezárás és a megszakítás saját úton történik: /prologue finale abort");
            }
            runtime.manager().checkpoint(next, sender.getName());
            sender.sendMessage("§aFinálé fázis: §f" + next);
            if (next.irreversibleVictoryPath()) {
                sender.sendMessage("§eGyőzelmi ág: a Kapu ettől még csak tényleges boss-győzelemmel nyílik meg.");
            }
            return;
        }
        final PrologueStage current = runtime.manager().stage();
        if (current == PrologueStage.COLLAPSE) {
            throw new IllegalStateException("A Kapu már az Összeomlás állapotában van.");
        }
        final PrologueStage next = args.length >= 2
                ? PrologueStage.valueOf(args[1].toUpperCase(Locale.ROOT))
                : PrologueStage.values()[current.ordinal() + 1];
        runtime.manager().setStage(next, sender.getName());
        sender.sendMessage("§aPrologue stage: §f" + next.displayName());
    }

    private static PrologueFinalePhase nextPhase(final PrologueFinalePhase current) {
        final PrologueFinalePhase next = PrologueFinalePhase.values()[current.ordinal() + 1];
        if (next == PrologueFinalePhase.COMPLETED) {
            throw new IllegalStateException("A finálé az utolsó kézzel léptethető fázisban van.");
        }
        return next;
    }

    private void stage(final CommandSender sender, final String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("/prologue stage <SILENCE|CRACKS|LEAK|COLLAPSE>");
        final PrologueStage stage = PrologueStage.valueOf(args[1].toUpperCase(Locale.ROOT));
        runtime.manager().setStage(stage, sender.getName());
        sender.sendMessage("§aPrologue stage: §f" + stage.displayName());
    }

    private void stability(final CommandSender sender, final String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("/prologue stability <0-100>");
        final int value = Integer.parseInt(args[1]);
        if (value < 0 || value > 100) throw new IllegalArgumentException("A stabilitás 0 és 100 közötti szám.");
        runtime.manager().setStability(value, sender.getName());
        sender.sendMessage("§aKapustabilitás: §f" + value + "%");
    }

    private void breach(final CommandSender sender, final String[] args) {
        if (args.length < 2 || !"start".equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("/prologue breach start [MINOR|MAJOR|CRITICAL]");
        }
        final BreachSeverity severity = args.length >= 3
                ? BreachSeverity.valueOf(args[2].toUpperCase(Locale.ROOT)) : BreachSeverity.MINOR;
        runtime.startAdminBreach(severity, sender);
    }

    private void finale(final CommandSender sender, final String[] args) {
        if (args.length < 2) throw new IllegalArgumentException(
                "/prologue finale <start [--rehearsal]|pause|resume|abort>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                final boolean rehearsal = args.length >= 3 && "--rehearsal".equalsIgnoreCase(args[2]);
                sender.sendMessage(runtime.finale().start(rehearsal, sender.getName())
                        ? rehearsal ? "§eA Prologue rehearsal elindult." : "§aA Kárhozat Éjszakája elindult."
                        : "§cA finálé nem indítható: már fut esemény vagy más major event aktív.");
            }
            case "pause" -> {
                runtime.finale().pause(sender.getName());
                sender.sendMessage("§eA Prologue finálé szünetel.");
            }
            case "resume" -> {
                runtime.finale().resume(sender.getName());
                sender.sendMessage("§aA Prologue finálé folytatódik.");
            }
            case "abort" -> {
                runtime.finale().abort(sender.getName());
                sender.sendMessage("§eA Prologue finálé megszakítva; a normál Doom Gate szabályok visszaálltak.");
            }
            default -> throw new IllegalArgumentException(
                    "/prologue finale <start [--rehearsal]|pause|resume|abort>");
        }
    }

    private void gate(final CommandSender sender, final String[] args) {
        if (args.length < 3 || !"--force".equalsIgnoreCase(args[2])) {
            throw new IllegalArgumentException("Veszélyes override: /prologue gate <open|close> --force");
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                runtime.manager().forceGateOpen(sender.getName());
                sender.sendMessage("§cFIGYELEM: a Kárhozat Kapuja admin override-dal megnyílt. A Prologue ettől még nem lezárt.");
            }
            case "close" -> {
                runtime.manager().closeGate(sender.getName());
                sender.sendMessage("§eA Kárhozat Kapuja visszazárt; a Prologue az eszkalációs szakaszba tért vissza.");
            }
            default -> throw new IllegalArgumentException("Veszélyes override: /prologue gate <open|close> --force");
        }
    }

    private static void usage(final CommandSender sender) {
        sender.sendMessage("§7/prologue status | start | advance [cél] | stage <...> | stability <0-100>");
        sender.sendMessage("§7/prologue breach start [severity] | finale <start [--rehearsal]|pause|resume|abort>");
        sender.sendMessage("§7/prologue gate <open|close> --force | reset --force");
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack stack,
                                                final @NonNull String[] args) {
        if (!stack.getSender().hasPermission(Permissions.PROLOGUE)) return List.of();
        if (args.length <= 1) return prefix(args, List.of("status", "start", "advance", "stage", "stability",
                "breach", "finale", "gate", "reset"));
        if (args.length == 2 && "reset".equalsIgnoreCase(args[0])) return prefix(args, List.of("--force"));
        if (args.length == 2 && "advance".equalsIgnoreCase(args[0])) {
            final PrologueFinalePhase phase = runtime.manager().finalePhase();
            return prefix(args, runtime.manager().finaleId() != null && phase.running()
                    ? List.of("BREACH_1", "BREACH_2", "ELITE_WAVE", "BOSS_INTRO", "BOSS_FIGHT",
                            "FALSE_END", "GATE_AWAKENING", "EPILOGUE")
                    : List.of("CRACKS", "LEAK", "COLLAPSE"));
        }
        if (args.length == 2 && "stage".equalsIgnoreCase(args[0]))
            return prefix(args, List.of("SILENCE", "CRACKS", "LEAK", "COLLAPSE"));
        if (args.length == 2 && "breach".equalsIgnoreCase(args[0])) return prefix(args, List.of("start"));
        if (args.length == 3 && "breach".equalsIgnoreCase(args[0]))
            return prefix(args, List.of("MINOR", "MAJOR", "CRITICAL"));
        if (args.length == 2 && "finale".equalsIgnoreCase(args[0]))
            return prefix(args, List.of("start", "pause", "resume", "abort"));
        if (args.length == 3 && "finale".equalsIgnoreCase(args[0]) && "start".equalsIgnoreCase(args[1]))
            return prefix(args, List.of("--rehearsal"));
        if (args.length == 2 && "gate".equalsIgnoreCase(args[0])) return prefix(args, List.of("open", "close"));
        if (args.length == 3 && "gate".equalsIgnoreCase(args[0])) return prefix(args, List.of("--force"));
        return List.of();
    }

    private static List<String> prefix(final String[] args, final List<String> values) {
        final String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
