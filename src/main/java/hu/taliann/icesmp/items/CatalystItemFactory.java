package hu.taliann.icesmp.items;

import hu.taliann.icesmp.data.JobType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for the class-themed Ability Catalyst items (the successor of the
 * universal spellbook). The item's material, name, model data and cycle sound
 * are themed to the player's primary class; identification uses the
 * 'is_ability_catalyst' PDC tag.
 */
@SuppressWarnings("deprecation")
public final class CatalystItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Per-class catalyst theme: item visuals plus the spell-cycle sound
     * (sound playback parameters are tuned so the feedback stays short).
     */
    private record CatalystTheme(
            Material material,
            String displayName,
            int customModelData,
            Sound cycleSound,
            float cycleVolume,
            float cyclePitch
    ) {
    }

    private static final Map<JobType, CatalystTheme> THEMES = new EnumMap<>(Map.of(
            JobType.WIZARD, new CatalystTheme(
                    Material.ENCHANTED_BOOK, "<light_purple><bold>Mágikus Kódex</bold></light_purple>",
                    5201, Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.1F),
            // Goat horn "call" sound at high pitch so the feedback stays short and non-intrusive.
            JobType.WARRIOR, new CatalystTheme(
                    Material.GOAT_HORN, "<red><bold>Harci Kürt</bold></red>",
                    5202, Sound.ITEM_GOAT_HORN_SOUND_1, 0.35F, 2.0F),
            JobType.ARCHER, new CatalystTheme(
                    Material.RABBIT_HIDE, "<green><bold>Vadásztarsoly</bold></green>",
                    5203, Sound.ITEM_CROSSBOW_LOADING_START, 0.8F, 1.3F),
            JobType.ASSASSIN, new CatalystTheme(
                    Material.FLINT, "<gray><bold>Árnyékamulett</bold></gray>",
                    5204, Sound.BLOCK_CANDLE_EXTINGUISH, 0.9F, 0.8F),
            JobType.DRUID, new CatalystTheme(
                    Material.OAK_SAPLING, "<dark_green><bold>Vadon Talizmánja</bold></dark_green>",
                    5205, Sound.BLOCK_BELL_RESONATE, 0.7F, 1.5F)
    ));

    private final NamespacedKey isCatalystKey;
    private final NamespacedKey uniqueIdKey;

    public CatalystItemFactory(final JavaPlugin plugin) {
        this.isCatalystKey = new NamespacedKey(plugin, "is_ability_catalyst");
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
    }

    /**
     * Creates the themed Ability Catalyst for a class.
     *
     * @param jobType the owner's primary class (null falls back to the wizard theme)
     * @return the catalyst item
     */
    public ItemStack createCatalyst(final JobType jobType) {
        final CatalystTheme theme = resolveTheme(jobType);
        final ItemStack itemStack = new ItemStack(theme.material());
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        meta.displayName(MINI_MESSAGE.deserialize(theme.displayName()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MINI_MESSAGE.deserialize("<dark_gray>Képesség Katalizátor</dark_gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<gray>Jobb katt: <white>képesség használata</white></gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<gray>Lopakodás + ütés: <white>képesség váltása</white></gray>").decoration(TextDecoration.ITALIC, false)
        ));

        final CustomModelDataComponent customModelDataComponent = meta.getCustomModelDataComponent();
        customModelDataComponent.setFloats(List.of((float) theme.customModelData()));
        meta.setCustomModelDataComponent(customModelDataComponent);

        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(isCatalystKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isCatalyst(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }

        return Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(isCatalystKey, PersistentDataType.BOOLEAN));
    }

    public Component getDisplayName(final JobType jobType) {
        return MINI_MESSAGE.deserialize(resolveTheme(jobType).displayName());
    }

    /**
     * Gets the catalyst's display name as plain text for chat message placeholders.
     *
     * @param jobType the class
     * @return the themed item name without formatting
     */
    public String getDisplayNamePlain(final JobType jobType) {
        return PlainTextComponentSerializer.plainText().serialize(getDisplayName(jobType));
    }

    public Material getMaterial(final JobType jobType) {
        return resolveTheme(jobType).material();
    }

    /**
     * Plays the class-themed spell-cycle sound to the player.
     *
     * @param player the player cycling spells
     * @param jobType the player's primary class
     */
    public void playCycleSound(final org.bukkit.entity.Player player, final JobType jobType) {
        final CatalystTheme theme = resolveTheme(jobType);
        player.playSound(player.getLocation(), theme.cycleSound(), theme.cycleVolume(), theme.cyclePitch());
    }

    private CatalystTheme resolveTheme(final JobType jobType) {
        final CatalystTheme theme = jobType == null ? null : THEMES.get(jobType);
        return theme == null ? THEMES.get(JobType.WIZARD) : theme;
    }
}
