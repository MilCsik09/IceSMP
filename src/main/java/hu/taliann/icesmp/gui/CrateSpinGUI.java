package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.utils.TextUtil;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Purely cosmetic reward-reveal animation for native crates. By the time this
 * GUI opens, {@link CrateManager#open} has ALREADY rolled and credited the reward — this
 * class never decides or grants anything, it only "spins" the middle row of a 27-slot
 * inventory through random reward icons for a few seconds (slowing down as it goes) before
 * landing on the actual reward in the center slot. {@code onComplete} is invoked at the end
 * so the caller can fire its normal feedback (sound/particle/chat message) once the reveal
 * lands, instead of upfront spoiling the animation.
 *
 * <p>Folia: the whole step chain runs on the OPENING PLAYER'S OWN entity scheduler — each
 * step schedules the next one with {@code runDelayed} (a growing delay, so the chain itself
 * produces the slow-down) instead of a single {@code runAtFixedRate}. Every step only reads
 * or writes the player's own open inventory, so no cross-region hop is ever needed. Closing
 * the inventory (or logging out) flips {@link CrateSpinHolder#cancel()} via
 * {@link hu.taliann.icesmp.listeners.CrateSpinGUIListener}, which the next step checks
 * before doing anything else.
 */
public final class CrateSpinGUI {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private static final int SIZE = 27;
    private static final int[] REEL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int STATUS_SLOT = 4;
    /** Growing per-step delays (ticks): the chain naturally slows down as it nears the end (~3.5s total). */
    private static final long[] SPIN_DELAYS = {2L, 2L, 2L, 3L, 3L, 4L, 4L, 5L, 5L, 6L, 7L, 8L, 9L, 10L};

    private CrateSpinGUI() {
    }

    /**
     * Opens the spin GUI for {@code player} and starts the reveal chain.
     *
     * @param plugin the plugin (for the entity-scheduler chain)
     * @param player the player who is opening the crate
     * @param crateDisplayName the crate's configured display name (GUI title + status tile)
     * @param rewards the crate's full loot table — only used to draw random reel icons
     * @param finalReward the ALREADY-rolled reward the reel lands on
     * @param onComplete run once the reel lands on the final reward (sound/particle/chat)
     */
    public static void open(final JavaPlugin plugin, final Player player, final String crateDisplayName,
                            final List<CrateManager.RewardEntry> rewards, final CrateManager.RewardEntry finalReward,
                            final Runnable onComplete) {
        if (rewards.isEmpty()) {
            onComplete.run();
            return;
        }

        final CrateSpinHolder holder = new CrateSpinHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("» " + crateDisplayName + " «", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);
        inventory.setItem(STATUS_SLOT, statusIcon(crateDisplayName, false));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F);

        spinStep(plugin, player, holder, rewards, finalReward, crateDisplayName, onComplete, 0);
    }

    private static void spinStep(final JavaPlugin plugin, final Player player, final CrateSpinHolder holder,
                                 final List<CrateManager.RewardEntry> rewards, final CrateManager.RewardEntry finalReward,
                                 final String crateDisplayName, final Runnable onComplete, final int index) {
        if (holder.isCancelled()) {
            return;
        }
        if (index >= SPIN_DELAYS.length) {
            reveal(player, holder, finalReward, crateDisplayName, onComplete);
            return;
        }

        final Inventory inventory = holder.getInventory();
        for (final int slot : REEL_SLOTS) {
            inventory.setItem(slot, reelIcon(rewards));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.0F);

        // Chained runDelayed (not runAtFixedRate) so the growing SPIN_DELAYS entries give the
        // reel its slow-down naturally; the retired callback covers the player logging out
        // mid-spin (their entity scheduler is retired before the delayed step can fire).
        player.getScheduler().runDelayed(plugin,
                task -> spinStep(plugin, player, holder, rewards, finalReward, crateDisplayName, onComplete, index + 1),
                holder::cancel, SPIN_DELAYS[index]);
    }

    private static void reveal(final Player player, final CrateSpinHolder holder, final CrateManager.RewardEntry finalReward,
                               final String crateDisplayName, final Runnable onComplete) {
        if (holder.isCancelled()) {
            return;
        }
        holder.cancel();

        final Inventory inventory = holder.getInventory();
        for (final int slot : REEL_SLOTS) {
            inventory.setItem(slot, slot == CENTER_SLOT ? iconFor(finalReward, true) : GuiUtil.filler());
        }
        inventory.setItem(CENTER_SLOT - 1, frameGlass());
        inventory.setItem(CENTER_SLOT + 1, frameGlass());
        inventory.setItem(STATUS_SLOT, statusIcon(crateDisplayName, true));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        onComplete.run();
    }

    private static ItemStack reelIcon(final List<CrateManager.RewardEntry> rewards) {
        return iconFor(rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())), false);
    }

    private static ItemStack iconFor(final CrateManager.RewardEntry reward, final boolean highlight) {
        final Material material = reward.material() != null ? reward.material() : Material.PAPER;
        final Component name = SERIALIZER.deserialize(TextUtil.color(CrateManager.describeReward(reward)))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, highlight);
        return GuiUtil.icon(material, name, List.of(), highlight);
    }

    private static ItemStack frameGlass() {
        return GuiUtil.icon(Material.YELLOW_STAINED_GLASS_PANE,
                Component.text("★", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false), List.of());
    }

    private static ItemStack statusIcon(final String crateDisplayName, final boolean done) {
        final Component name = done
                ? Component.text("Nyeremény kihirdetve!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("Pörgetés...", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
        return GuiUtil.icon(Material.NETHER_STAR, name,
                List.of(SERIALIZER.deserialize(TextUtil.color(crateDisplayName)).decoration(TextDecoration.ITALIC, false)));
    }
}
