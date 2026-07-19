package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Ártalom Kiűzése" — a kaszter kap egy PDC-tagelt, Visszalökés III bűbájjal
 * ellátott, törhetetlen "Kiűzés Botja" nevű botot 5 másodpercre, ami azután
 * magától eltűnik (ArmamentSpell-mintát követő retired-callback + kilépéskori
 * takarítás).
 */
public final class ExpelHarmSpell extends BaseSpell {

    private static final int DURATION_TICKS = 5 * 20;

    private static final Map<UUID, Long> ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static NamespacedKey staticStickTag;

    private final JavaPlugin plugin;
    private final NamespacedKey stickTag;

    public ExpelHarmSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "expel_harm", "Ártalom Kiűzése", 90, SpellCostType.XP, 45);
        this.plugin = plugin;
        this.stickTag = new NamespacedKey(plugin, "expel_harm_stick");
        staticStickTag = this.stickTag;
    }

    @Override
    public void execute(final Player player) {
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(createStick());
        for (final ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }

        final UUID playerId = player.getUniqueId();
        final int durationTicks = balanceInt("duration-ticks", DURATION_TICKS);
        ACTIVE_UNTIL.put(playerId, System.currentTimeMillis() + (durationTicks * 50L));

        // Folia: per-player region scheduler; the retired-callback still wipes the map entry
        // if the task can't fire (e.g. the player already logged out).
        player.getScheduler().runDelayed(plugin, task -> {
            final Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                removeTaggedItems(online);
            }
            ACTIVE_UNTIL.remove(playerId);
        }, () -> ACTIVE_UNTIL.remove(playerId), durationTicks);

        player.sendMessage(resolveMessage("spell.expel_harm.cast", "<yellow>A Kiűzés Botja megjelenik a kezedben.</yellow>"));
    }

    private ItemStack createStick() {
        final ItemStack stack = new ItemStack(Material.STICK);
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.displayName(Component.text("Kiűzés Botja", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        // Resource pack-hez: egyedi CMD (RESOURCE_PACK_CMD.md leltár; balansz-kulccsal felülbírálható).
        final org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(java.util.List.of((float) balanceInt("custom-model-data", 6104)));
        meta.setCustomModelDataComponent(cmd);
        meta.getPersistentDataContainer().set(stickTag, PersistentDataType.BOOLEAN, true);
        stack.setItemMeta(meta);
        return stack;
    }

    private void removeTaggedItems(final Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (hasStickTag(player.getInventory().getItem(slot), stickTag)) {
                player.getInventory().setItem(slot, null);
            }
        }

        final List<Integer> toClearArmor = new ArrayList<>();
        final ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (hasStickTag(armor[i], stickTag)) {
                toClearArmor.add(i);
            }
        }
        for (final Integer index : toClearArmor) {
            armor[index] = null;
        }
        player.getInventory().setArmorContents(armor);

        if (hasStickTag(player.getInventory().getItemInOffHand(), stickTag)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public static boolean hasStickTag(final ItemStack itemStack, final NamespacedKey stickTag) {
        return itemStack != null
                && itemStack.hasItemMeta()
                && Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(stickTag, PersistentDataType.BOOLEAN));
    }

    public static void cleanup(final UUID playerId) {
        if (playerId != null) {
            ACTIVE_UNTIL.remove(playerId);
        }
    }

    public static void cleanup(final Player player) {
        if (player == null) {
            return;
        }

        cleanup(player.getUniqueId());
        if (staticStickTag == null) {
            return;
        }

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (hasStickTag(player.getInventory().getItem(slot), staticStickTag)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            cleanup(player);
        } else {
            cleanup(playerId);
        }
    }
}
