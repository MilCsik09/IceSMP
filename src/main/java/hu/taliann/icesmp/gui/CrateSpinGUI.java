package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateTaskLease;
import hu.taliann.icesmp.crates.CrateTaskSubmission;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.utils.TextUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Purely cosmetic, idempotently cleaned player-owned crate reveal. */
public final class CrateSpinGUI {

    private static final ConcurrentHashMap<UUID, CrateSpinHolder> ACTIVE = new ConcurrentHashMap<>();
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int SIZE = 27;
    private static final int[] REEL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int STATUS_SLOT = 4;
    private static final long[] SPIN_DELAYS = {2L, 2L, 2L, 3L, 3L, 4L, 4L, 5L, 5L, 6L, 7L, 8L, 9L, 10L};

    private CrateSpinGUI() {
    }

    public static void open(final JavaPlugin plugin, final Player player, final String crateDisplayName,
                            final List<CrateManager.RewardEntry> rewards,
                            final CrateManager.RewardEntry finalReward,
                            final Runnable onComplete) {
        if (rewards.isEmpty()) {
            onComplete.run();
            return;
        }
        final CrateSpinHolder holder = new CrateSpinHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("» " + crateDisplayName + " «", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);
        final CrateSpinHolder previous = ACTIVE.put(player.getUniqueId(), holder);
        if (previous != null) {
            previous.cancel();
        }
        GuiUtil.fill(inventory);
        inventory.setItem(STATUS_SLOT, statusIcon(crateDisplayName, false));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F);
        spinStep(plugin, player, holder, rewards, finalReward, crateDisplayName, onComplete, 0);
    }

    private static void spinStep(final JavaPlugin plugin, final Player player, final CrateSpinHolder holder,
                                 final List<CrateManager.RewardEntry> rewards,
                                 final CrateManager.RewardEntry finalReward,
                                 final String crateDisplayName, final Runnable onComplete, final int index) {
        if (holder.isCancelled() || ACTIVE.get(holder.getOwnerUuid()) != holder) {
            cleanup(holder);
            return;
        }
        if (index >= SPIN_DELAYS.length) {
            reveal(player, holder, finalReward, crateDisplayName, onComplete);
            return;
        }
        final Inventory inventory = holder.inventoryOrNull();
        if (inventory == null) {
            cleanup(holder);
            return;
        }
        for (final int slot : REEL_SLOTS) {
            inventory.setItem(slot, reelIcon(rewards));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.0F);

        final CrateTaskLease lease = new CrateTaskLease();
        if (!holder.replaceTaskLease(lease)) {
            cleanup(holder);
            return;
        }
        final ScheduledTask handle = CrateTaskSubmission.entityDelayed(plugin, player.getScheduler(),
                () -> spinStep(plugin, player, holder, rewards, finalReward,
                        crateDisplayName, onComplete, index + 1),
                () -> cleanup(holder), SPIN_DELAYS[index]);
        if (!lease.publish(handle)) {
            cleanup(holder);
        }
    }

    private static void reveal(final Player player, final CrateSpinHolder holder,
                               final CrateManager.RewardEntry finalReward,
                               final String crateDisplayName, final Runnable onComplete) {
        if (holder.isCancelled() || !ACTIVE.remove(holder.getOwnerUuid(), holder)) {
            cleanup(holder);
            return;
        }
        final Inventory inventory = holder.inventoryOrNull();
        if (inventory == null) {
            holder.cancel();
            return;
        }
        holder.complete(() -> {
            for (final int slot : REEL_SLOTS) {
                inventory.setItem(slot, slot == CENTER_SLOT ? iconFor(finalReward, true) : GuiUtil.filler());
            }
            inventory.setItem(CENTER_SLOT - 1, frameGlass());
            inventory.setItem(CENTER_SLOT + 1, frameGlass());
            inventory.setItem(STATUS_SLOT, statusIcon(crateDisplayName, true));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
            onComplete.run();
        });
    }

    private static void cleanup(final CrateSpinHolder holder) {
        ACTIVE.remove(holder.getOwnerUuid(), holder);
        holder.cancel();
    }

    private static ItemStack reelIcon(final List<CrateManager.RewardEntry> rewards) {
        return iconFor(rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())), false);
    }

    private static ItemStack iconFor(final CrateManager.RewardEntry reward, final boolean highlight) {
        final Component name = SERIALIZER.deserialize(TextUtil.color(CrateManager.describeReward(reward)))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, highlight);
        return GuiUtil.icon(reward.iconMaterial(), name, List.of(), highlight);
    }

    public static void cancel(final UUID playerId) {
        final CrateSpinHolder holder = ACTIVE.remove(playerId);
        if (holder != null) {
            holder.cancel();
        }
    }

    public static void cancelAll() {
        for (final UUID playerId : List.copyOf(ACTIVE.keySet())) {
            cancel(playerId);
        }
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    private static ItemStack frameGlass() {
        return GuiUtil.icon(Material.YELLOW_STAINED_GLASS_PANE,
                Component.text("★", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false), List.of());
    }

    private static ItemStack statusIcon(final String crateDisplayName, final boolean done) {
        final Component name = done
                ? Component.text("Nyeremény kihirdetve!", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                : Component.text("Pörgetés...", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false);
        return GuiUtil.icon(Material.NETHER_STAR, name,
                List.of(SERIALIZER.deserialize(TextUtil.color(crateDisplayName))
                        .decoration(TextDecoration.ITALIC, false)));
    }
}
