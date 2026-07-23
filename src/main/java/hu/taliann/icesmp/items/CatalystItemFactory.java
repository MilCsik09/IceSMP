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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for the class-themed Ability Catalyst items (the successor of the
 * universal spellbook). The item's material, name, item model and cycle sound
 * are themed to the player's primary class; identification uses the
 * 'is_ability_catalyst' PDC tag.
 */
public final class CatalystItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Per-class catalyst theme: item visuals plus the spell-cycle sound
     * (sound playback parameters are tuned so the feedback stays short).
     */
    private record CatalystTheme(
            Material material,
            String displayName,
            Sound cycleSound,
            float cycleVolume,
            float cyclePitch
    ) {
    }

    // Map.ofEntries (not Map.of) so the roster can grow past 10 classes.
    // Names follow the canon (docs/LORE.md VI — a kaszt forrása adja a Vezérlő Tárgy nevét).
    private static final Map<JobType, CatalystTheme> THEMES = new EnumMap<>(Map.ofEntries(
            Map.entry(JobType.WIZARD, new CatalystTheme(
                    Material.ENCHANTED_BOOK, "<light_purple><bold>Caldesterai Rúnakódex</bold></light_purple>",
                    Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.1F)),
            // Goat horn "call" sound at high pitch so the feedback stays short and non-intrusive.
            Map.entry(JobType.WARRIOR, new CatalystTheme(
                    Material.GOAT_HORN, "<red><bold>Sárkánykirály Kürtje</bold></red>",
                    Sound.ITEM_GOAT_HORN_SOUND_1, 0.35F, 2.0F)),
            Map.entry(JobType.ARCHER, new CatalystTheme(
                    Material.RABBIT_HIDE, "<green><bold>Soleil Vadásztarsolya</bold></green>",
                    Sound.ITEM_CROSSBOW_LOADING_START, 0.8F, 1.3F)),
            Map.entry(JobType.ASSASSIN, new CatalystTheme(
                    Material.FLINT, "<gray><bold>Homály-szilánk</bold></gray>",
                    Sound.BLOCK_CANDLE_EXTINGUISH, 0.9F, 0.8F)),
            Map.entry(JobType.DRUID, new CatalystTheme(
                    Material.OAK_SAPLING, "<dark_green><bold>Aetrinita Sarja</bold></dark_green>",
                    Sound.BLOCK_BELL_RESONATE, 0.7F, 1.5F)),
            Map.entry(JobType.PALADIN, new CatalystTheme(
                    Material.BELL, "<gold><bold>Hajnaltűz Harangja</bold></gold>",
                    Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.3F)),
            Map.entry(JobType.DEATH_KNIGHT, new CatalystTheme(
                    Material.WITHER_SKELETON_SKULL, "<dark_red><bold>Néma Rúnakoponya</bold></dark_red>",
                    Sound.ENTITY_WITHER_AMBIENT, 0.6F, 0.8F)),
            Map.entry(JobType.SHAMAN, new CatalystTheme(
                    Material.TOTEM_OF_UNDYING, "<aqua><bold>Ősvihar Totemje</bold></aqua>",
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.1F)),
            Map.entry(JobType.MONK, new CatalystTheme(
                    Material.BAMBOO, "<green><bold>Élet Ága</bold></green>",
                    Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 1.2F)),
            Map.entry(JobType.PRIEST, new CatalystTheme(
                    Material.WHITE_CANDLE, "<white><bold>Asterlayna Gyertyája</bold></white>",
                    Sound.BLOCK_BELL_RESONATE, 0.8F, 1.6F)),
            Map.entry(JobType.WARLOCK, new CatalystTheme(
                    Material.SOUL_LANTERN, "<dark_purple><bold>Kárhozat Lámpása</bold></dark_purple>",
                    Sound.ENTITY_WITHER_AMBIENT, 0.7F, 1.0F)),
            Map.entry(JobType.DEMON_HUNTER, new CatalystTheme(
                    Material.ENDER_EYE, "<light_purple><bold>Hasadék Szeme</bold></light_purple>",
                    Sound.ENTITY_PHANTOM_AMBIENT, 0.8F, 0.9F)),
            Map.entry(JobType.EVOKER, new CatalystTheme(
                    Material.DRAGON_BREATH, "<dark_aqua><bold>Sárkányvér-fiola</bold></dark_aqua>",
                    Sound.ENTITY_BREEZE_SHOOT, 0.8F, 1.0F))
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
                MINI_MESSAGE.deserialize("<dark_gray>Lélekkapocs — a Fa ajándéka</dark_gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<gray>Jobb katt: <white>képesség használata</white></gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<gray>Lopakodás + ütés: <white>képesség váltása</white></gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<dark_gray>Nem dobható el.</dark_gray>").decoration(TextDecoration.ITALIC, false)
        ));

        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(isCatalystKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        itemStack.setItemMeta(meta);
        // Data-komponensek a setItemMeta UTÁN: kaszt-alapú ITEM_MODEL + saját
        // cooldown-csoport (utóbbi: a cast-overlay NEM sötétíti a vele azonos Materialú vanília itemet).
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(itemStack,
                "icesmp:catalyst_" + (jobType == null ? "wizard" : jobType.getId()));
        hu.taliann.icesmp.items.ItemDataFactory.applyUseCooldownGroup(itemStack, "catalyst", 0.5F);
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
