package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
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

public final class ArmamentSpell extends BaseSpell {

    private static final long DURATION_TICKS = 20L * 120L;

    private static final Map<UUID, Long> ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static NamespacedKey staticArmamentTag;

    private final JavaPlugin plugin;
    private final NamespacedKey armamentTag;

    public ArmamentSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "armament", "Fegyverzet", 300, SpellCostType.XP, 352);
        this.plugin = plugin;
        this.armamentTag = new NamespacedKey(plugin, "armament_item");
        staticArmamentTag = this.armamentTag;
    }

    @Override
    public boolean canCast(final Player player) {
        if (ACTIVE_UNTIL.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            return false;
        }

        for (final ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece != null && !armorPiece.getType().isAir()) {
                return false;
            }
        }

        return true;
    }


    @Override
    public void execute(final Player player) {
        final ItemStack helmet = createLockedItem(Material.CHAINMAIL_HELMET);
        final ItemStack chestplate = createLockedItem(Material.IRON_CHESTPLATE);
        final ItemStack leggings = createLockedItem(Material.LEATHER_LEGGINGS);
        final ItemStack boots = createLockedItem(Material.GOLDEN_BOOTS);
        final ItemStack sword = createLockedItem(Material.IRON_SWORD);

        player.getInventory().setArmorContents(new ItemStack[]{
                boots,
                leggings,
                chestplate,
                helmet
        });
        player.getInventory().addItem(sword);
        final UUID playerId = player.getUniqueId();
        final long durationTicks = balanceInt("duration-ticks", (int) DURATION_TICKS);
        ACTIVE_UNTIL.put(playerId, System.currentTimeMillis() + (durationTicks * 50L));

        // Folia: per-player region scheduler instead of the unsupported Bukkit scheduler.
        player.getScheduler().runDelayed(plugin, task -> {
            final Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null) {
                removeTaggedItems(onlinePlayer);
            }
            ACTIVE_UNTIL.remove(playerId);
        }, () -> ACTIVE_UNTIL.remove(playerId), durationTicks);
    }

    private ItemStack createLockedItem(final Material material) {
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(armamentTag, PersistentDataType.BOOLEAN, true);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void removeTaggedItems(final Player player) {
        final ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (isLockedItem(armor[i])) {
                armor[i] = null;
            }
        }
        player.getInventory().setArmorContents(armor);

        final List<Integer> toClear = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack itemStack = player.getInventory().getItem(slot);
            if (isLockedItem(itemStack)) {
                toClear.add(slot);
            }
        }

        for (final Integer slot : toClear) {
            player.getInventory().setItem(slot, null);
        }
    }

    private boolean isLockedItem(final ItemStack itemStack) {
        return itemStack != null
                && itemStack.hasItemMeta()
                && Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(armamentTag, PersistentDataType.BOOLEAN));
    }


    public static void cleanup(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        ACTIVE_UNTIL.remove(playerId);
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

    public static void cleanup(final Player player) {
        if (player == null) {
            return;
        }

        cleanup(player.getUniqueId());
        if (staticArmamentTag == null) {
            return;
        }

        final ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (hasArmamentTag(armor[i], staticArmamentTag)) {
                armor[i] = null;
            }
        }
        player.getInventory().setArmorContents(armor);

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (hasArmamentTag(stack, staticArmamentTag)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    public static boolean hasArmamentTag(final ItemStack itemStack, final NamespacedKey armamentTag) {
        return itemStack != null
                && itemStack.hasItemMeta()
                && Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(armamentTag, PersistentDataType.BOOLEAN));
    }
}

