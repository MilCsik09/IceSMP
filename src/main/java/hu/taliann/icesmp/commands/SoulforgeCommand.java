package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.SoulShardManager;
import hu.taliann.icesmp.managers.SoulforgeManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E1 — /soulforge (alias /lelekkovacs): a Nekromanta lélek-kovács műhelye.
 * Alparancs nélkül áttekintés (rangok + következő árak + szilánk-egyenleg);
 * "fejleszt <elet|sebzes|letszam>" költi a lélekszilánkot.
 */
public final class SoulforgeCommand implements BasicCommand {

    private final SoulforgeManager soulforgeManager;
    private final SoulShardManager soulShardManager;
    private final MessageManager messageManager;

    public SoulforgeCommand(final SoulforgeManager soulforgeManager,
                            final SoulShardManager soulShardManager,
                            final MessageManager messageManager) {
        this.soulforgeManager = soulforgeManager;
        this.soulShardManager = soulShardManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (args.length >= 2 && "fejleszt".equalsIgnoreCase(args[0])) {
            final SoulforgeManager.Branch branch = parseBranch(args[1]);
            if (branch == null) {
                player.sendMessage(messageManager.get("soulforge-bad-branch",
                        "&cÁgak: elet | sebzes | letszam"));
                return;
            }
            final String error = soulforgeManager.upgrade(player, branch);
            if (error == null) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_SOUL_SAND_PLACE, 1.0F, 0.7F);
                player.sendMessage(messageManager.getMessage("soulforge-upgraded",
                        "<dark_purple>☠ A kovácstűz lelket nyelt: <white>{branch}</white> ág — <white>{rank}. rang</white>. A sereged érzi.</dark_purple>",
                        Map.of("branch", branchName(branch),
                                "rank", String.valueOf(soulforgeManager.getRank(player, branch)))));
            } else {
                player.sendMessage(messageManager.get("soulforge-error." + error, switch (error) {
                    case "soulforge-max" -> "&cEz az ág már a csúcson jár (5. rang).";
                    case "soulforge-poor" -> "&cNincs elég lélekszilánkod.";
                    default -> "&cA lélek-kovácsolás most nem elérhető.";
                }));
            }
            return;
        }
        player.sendMessage(messageManager.get("soulforge-header",
                "&5☠ Lélek-kovács &7— szilánkjaid: &f%s", soulShardManager.getShards(player)));
        for (final SoulforgeManager.Branch branch : SoulforgeManager.Branch.values()) {
            final int rank = soulforgeManager.getRank(player, branch);
            final int cost = soulforgeManager.nextCost(player, branch);
            player.sendMessage(messageManager.get("soulforge-row",
                    "&7- &f%s&7: &f%s/5&7 rang %s",
                    branchName(branch), rank,
                    cost < 0 ? "&8(MAX)" : "&7(következő: &f" + cost + "&7 szilánk — /soulforge fejleszt " + branch.name().toLowerCase(Locale.ROOT) + ")"));
        }
        player.sendMessage(messageManager.get("soulforge-hint",
                "&8Élet: +8%%/rang minion-HP • Sebzés: +6%%/rang • Létszám: extra idézés-slot (max +3)"));
    }

    private static SoulforgeManager.Branch parseBranch(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "elet", "élet" -> SoulforgeManager.Branch.ELET;
            case "sebzes", "sebzés" -> SoulforgeManager.Branch.SEBZES;
            case "letszam", "létszám" -> SoulforgeManager.Branch.LETSZAM;
            default -> null;
        };
    }

    private static String branchName(final SoulforgeManager.Branch branch) {
        return switch (branch) {
            case ELET -> "Élet";
            case SEBZES -> "Sebzés";
            case LETSZAM -> "Létszám";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("fejleszt");
        }
        if (args.length == 2) {
            return List.of("elet", "sebzes", "letszam");
        }
        return List.of();
    }
}
