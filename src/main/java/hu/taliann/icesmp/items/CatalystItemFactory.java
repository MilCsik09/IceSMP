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

    // Map.ofEntries (not Map.of) so the roster can grow past 10 classes.
    private static final Map<JobType, CatalystTheme> THEMES = new EnumMap<>(Map.ofEntries(
            Map.entry(JobType.WIZARD, new CatalystTheme(
                    Material.ENCHANTED_BOOK, "<light_purple><bold>Mágikus Kódex</bold></light_purple>",
                    5201, Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.1F)),
            // Goat horn "call" sound at high pitch so the feedback stays short and non-intrusive.
            Map.entry(JobType.WARRIOR, new CatalystTheme(
                    Material.GOAT_HORN, "<red><bold>Harci Kürt</bold></red>",
                    5202, Sound.ITEM_GOAT_HORN_SOUND_1, 0.35F, 2.0F)),
            Map.entry(JobType.ARCHER, new CatalystTheme(
                    Material.RABBIT_HIDE, "<green><bold>Vadásztarsoly</bold></green>",
                    5203, Sound.ITEM_CROSSBOW_LOADING_START, 0.8F, 1.3F)),
            Map.entry(JobType.ASSASSIN, new CatalystTheme(
                    Material.FLINT, "<gray><bold>Árnyékamulett</bold></gray>",
                    5204, Sound.BLOCK_CANDLE_EXTINGUISH, 0.9F, 0.8F)),
            Map.entry(JobType.DRUID, new CatalystTheme(
                    Material.OAK_SAPLING, "<dark_green><bold>Vadon Talizmánja</bold></dark_green>",
                    5205, Sound.BLOCK_BELL_RESONATE, 0.7F, 1.5F)),
            Map.entry(JobType.PALADIN, new CatalystTheme(
                    Material.BELL, "<gold><bold>Szent Harang</bold></gold>",
                    5206, Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.3F)),
            Map.entry(JobType.DEATH_KNIGHT, new CatalystTheme(
                    Material.WITHER_SKELETON_SKULL, "<dark_red><bold>Rúnakovácsolt Koponya</bold></dark_red>",
                    5207, Sound.ENTITY_WITHER_AMBIENT, 0.6F, 0.8F)),
            Map.entry(JobType.SHAMAN, new CatalystTheme(
                    Material.TOTEM_OF_UNDYING, "<aqua><bold>Ősök Totemje</bold></aqua>",
                    5208, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.1F)),
            Map.entry(JobType.MONK, new CatalystTheme(
                    Material.BAMBOO, "<green><bold>Jáde Bot</bold></green>",
                    5209, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 1.2F)),
            Map.entry(JobType.PRIEST, new CatalystTheme(
                    Material.WHITE_CANDLE, "<white><bold>Szent Gyertya</bold></white>",
                    5210, Sound.BLOCK_BELL_RESONATE, 0.8F, 1.6F)),
            Map.entry(JobType.WARLOCK, new CatalystTheme(
                    Material.SOUL_LANTERN, "<dark_purple><bold>Lélek Lámpás</bold></dark_purple>",
                    5211, Sound.ENTITY_WITHER_AMBIENT, 0.7F, 1.0F)),
            Map.entry(JobType.DEMON_HUNTER, new CatalystTheme(
                    Material.ENDER_EYE, "<light_purple><bold>Démonszem</bold></light_purple>",
                    5212, Sound.ENTITY_PHANTOM_AMBIENT, 0.8F, 0.9F)),
            Map.entry(JobType.EVOKER, new CatalystTheme(
                    Material.DRAGON_BREATH, "<dark_aqua><bold>Sárkány Esszencia</bold></dark_aqua>",
                    5213, Sound.ENTITY_BREEZE_SHOOT, 0.8F, 1.0F))
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

    /** The catalyst's display name as plain text for chat message placeholders. */
    public String getDisplayNamePlain(final JobType jobType) {
        return PlainTextComponentSerializer.plainText().serialize(getDisplayName(jobType));
    }

    public Material getMaterial(final JobType jobType) {
        return resolveTheme(jobType).material();
    }

    public void playCycleSound(final org.bukkit.entity.Player player, final JobType jobType) {
        final CatalystTheme theme = resolveTheme(jobType);
        player.playSound(player.getLocation(), theme.cycleSound(), theme.cycleVolume(), theme.cyclePitch());
    }

    private CatalystTheme resolveTheme(final JobType jobType) {
        final CatalystTheme theme = jobType == null ? null : THEMES.get(jobType);
        return theme == null ? THEMES.get(JobType.WIZARD) : theme;
    }
}
