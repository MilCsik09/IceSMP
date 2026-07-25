package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.InvseeGUI;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /invsee <név>} — read-only pillanatkép-betekintés egy ONLINE játékos fő
 * inventoryjáról, páncéljáról, off-handjéről és ender-ládájáról; az InvSee++ kiváltásának első
 * lépcsője. A snapshot ({@link ItemStack#clone()} minden slotra) a CÉLPONT saját régió-szálán
 * készül (Folia — idegen entitás inventoryját csak a saját szálán szabad olvasni), utána a NÉZŐ
 * szálán nyílik meg az {@link InvseeGUI}. NEM élő nézet: a GUI bezárása után a snapshot eldobódik,
 * a célpont további tárgymozgásai nem tükröződnek benne.
 */
public final class InvseeCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.inspect";

    private final JavaPlugin plugin;
    private final MessageManager messageManager;

    public InvseeCommand(final JavaPlugin plugin, final MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 1 || args[0].isBlank()) {
            sender.sendMessage(messageManager.get("admin.icesmp.invsee.usage", "&cHasználat: /invsee <név>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("admin.icesmp.invsee.offline",
                    "&cA céljátékos nem érhető el online (a betekintés csak élő pillanatképet tud készíteni)."));
            return;
        }

        target.getScheduler().run(plugin, task -> {
            final ItemStack[] mainSnapshot = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                final ItemStack item = target.getInventory().getItem(i);
                mainSnapshot[i] = item == null ? null : item.clone();
            }

            final ItemStack[] armorSource = target.getInventory().getArmorContents();
            final ItemStack[] armorSnapshot = new ItemStack[armorSource.length];
            for (int i = 0; i < armorSource.length; i++) {
                armorSnapshot[i] = armorSource[i] == null ? null : armorSource[i].clone();
            }

            final ItemStack offHandSource = target.getInventory().getItemInOffHand();
            final ItemStack offHandSnapshot = offHandSource.clone();

            final ItemStack[] enderSource = target.getEnderChest().getContents();
            final ItemStack[] enderSnapshot = new ItemStack[enderSource.length];
            for (int i = 0; i < enderSource.length; i++) {
                enderSnapshot[i] = enderSource[i] == null ? null : enderSource[i].clone();
            }

            final String targetName = target.getName();
            final long snapshotAt = System.currentTimeMillis();
            viewer.getScheduler().run(plugin, viewerTask -> InvseeGUI.openMain(
                    viewer, targetName, mainSnapshot, armorSnapshot, offHandSnapshot, enderSnapshot,
                    snapshotAt, messageManager
            ), null);
        }, null);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION) || args.length > 1) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
