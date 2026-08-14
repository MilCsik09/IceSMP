package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateSoundResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Small shared helpers for the character menu GUIs (filler panes, icon builders),
 * so the individual menu classes stay focused on layout.
 */
public final class GuiUtil {

    private GuiUtil() {
    }

    // ==================== Egységes GUI-hangnyelv ====================

    /** A közös GUI-hang-készlet: minden felület ugyanazt a hangnyelvet beszéli. */
    public enum GuiSound {
        /** Menü/almenü megnyitása. */
        OPEN("BLOCK_BARREL_OPEN", 0.4F, 1.3F),
        /** Lapozás / nézet-váltás. */
        PAGE("ITEM_BOOK_PAGE_TURN", 0.7F, 1.2F),
        /** Sima gomb-kattintás. */
        CLICK("UI_BUTTON_CLICK", 0.4F, 1.1F),
        /** Sikeres művelet (mentés, teljesítés, jelölés). */
        SUCCESS("ENTITY_EXPERIENCE_ORB_PICKUP", 0.6F, 1.5F),
        /** Hibás/elutasított művelet. */
        ERROR("BLOCK_NOTE_BLOCK_BASS", 0.6F, 0.7F),
        /** Vásárlás/tranzakció. */
        BUY("BLOCK_AMETHYST_BLOCK_CHIME", 0.7F, 1.4F);

        private final String defaultSound;
        private final float defaultVolume;
        private final float defaultPitch;

        GuiSound(final String defaultSound, final float defaultVolume, final float defaultPitch) {
            this.defaultSound = defaultSound;
            this.defaultVolume = defaultVolume;
            this.defaultPitch = defaultPitch;
        }
    }

    /** Config-forrás a hang-felülbírálásokhoz (gui.sounds.*) — az IceSMPCore enable()-je tölti fel. */
    private static volatile hu.taliann.icesmp.managers.ConfigManager soundConfig;

    public static void initSounds(final hu.taliann.icesmp.managers.ConfigManager configManager) {
        soundConfig = configManager;
    }

    /**
     * A közös hangnyelv lejátszása a játékosnak (a hívó a játékos saját régió-szálán van —
     * GUI-kattintás/nyitás mindig ott fut). Config-felülbírálás:
     * {@code gui.sounds.<open|page|click|success|error|buy>.sound/volume/pitch}.
     */
    public static void sound(final org.bukkit.entity.Player player, final GuiSound sound) {
        final hu.taliann.icesmp.managers.ConfigManager config = soundConfig;
        final String base = "gui.sounds." + sound.name().toLowerCase(java.util.Locale.ROOT);
        final String soundName = config == null ? sound.defaultSound
                : config.getString(base + ".sound", sound.defaultSound);
        final float volume = config == null ? sound.defaultVolume
                : (float) config.getDouble(base + ".volume", sound.defaultVolume);
        final float pitch = config == null ? sound.defaultPitch
                : (float) config.getDouble(base + ".pitch", sound.defaultPitch);
        final org.bukkit.Sound resolved = CrateSoundResolver.resolve(soundName);
        if (resolved != null) {
            player.playSound(player.getLocation(), resolved, volume, pitch);
        }
    }

    public static void fill(final Inventory inventory) {
        final ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    public static ItemStack filler() {
        final ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /**
     * Convenience builder for ampersand-coloured menu text. Kept here so config and utility
     * menus do not each duplicate legacy-component conversion and non-italic lore handling.
     */
    public static ItemStack item(final Material material, final String name,
                                 final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final var serializer = net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacyAmpersand();
            meta.displayName(serializer.deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(serializer.deserialize(line)
                        .colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * A "key: value" lore line: grey, non-italic key label followed by the value component.
     * Shared by the character-menu GUIs (Profile/Spec/Profession/Talent) and CommandMenus.
     */
    public static Component label(final String key, final Component value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(value);
    }

    /** Bold aqua, non-italic accent text (section headers / highlights in the menus). */
    public static Component accent(final String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
    }

    /** Plain grey, non-italic body text (lore detail lines in the menus). */
    public static Component grey(final String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack icon(final Material material, final Component name, final List<Component> lore) {
        return icon(material, name, lore, false);
    }

    public static ItemStack icon(final Material material, final Component name, final List<Component> lore,
                                 final boolean glow) {
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /**
     * A player-head icon carrying {@code owner}'s skin. Falls back to the plain
     * head when the owner is unknown (the skin resolves from the player cache).
     */
    public static ItemStack playerHead(final OfflinePlayer owner, final Component name, final List<Component> lore) {
        final ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = itemStack.getItemMeta();
        if (owner != null && meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
        }
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
