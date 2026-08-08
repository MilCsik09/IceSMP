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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical Lélekkapocs/spellbook factory.
 *
 * <p>The ItemStack is never class/spec authority. Owner/class/spec/evolution tags are
 * rebuildable physical mirrors used only to reject foreign/corrupt copies and to render
 * the personal artifact. Profile v2 remains authoritative for class/spec/progression.</p>
 */
public final class CatalystItemFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static volatile CatalystItemFactory installed;

    private record CatalystTheme(Material material, String displayName,
                                 Sound cycleSound, float cycleVolume, float cyclePitch) {
    }

    private static final Map<JobType, CatalystTheme> THEMES = new EnumMap<>(Map.ofEntries(
            Map.entry(JobType.WIZARD, new CatalystTheme(
                    Material.ENCHANTED_BOOK, "<light_purple><bold>Caldesterai Rúnakódex</bold></light_purple>",
                    Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.1F)),
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
    private final NamespacedKey ownerKey;
    private final NamespacedKey classKey;
    private final NamespacedKey signatureKey;
    private final NamespacedKey activeSpecKey;
    private final NamespacedKey evolutionKey;

    public CatalystItemFactory(final JavaPlugin plugin) {
        isCatalystKey = new NamespacedKey(plugin, "is_ability_catalyst");
        uniqueIdKey = new NamespacedKey(plugin, "unique_id");
        ownerKey = new NamespacedKey(plugin, "soulbond_owner");
        classKey = new NamespacedKey(plugin, "soulbond_class");
        signatureKey = new NamespacedKey(plugin, "soulbond_signature");
        activeSpecKey = new NamespacedKey(plugin, "soulbond_active_spec");
        evolutionKey = new NamespacedKey(plugin, "soulbond_evolution");
        installed = this;
    }

    /** Single plugin-local presentation factory; not class/spec authority. */
    public static Optional<CatalystItemFactory> installed() {
        return Optional.ofNullable(installed);
    }

    /** Creates the one recoverable personal artifact mirror for owner+class. */
    public ItemStack createCatalyst(final JobType jobType, final UUID ownerId) {
        if (jobType == null || ownerId == null) {
            throw new IllegalArgumentException("A personal Lélekkapocs requires owner and class");
        }
        final CatalystTheme theme = resolveTheme(jobType);
        final ItemStack item = new ItemStack(theme.material());
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MINI_MESSAGE.deserialize(theme.displayName())
                .decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(isCatalystKey, PersistentDataType.BOOLEAN, true);
        pdc.set(ownerKey, PersistentDataType.STRING, ownerId.toString());
        pdc.set(classKey, PersistentDataType.STRING, jobType.getId());
        final String signature = signature(ownerId, jobType);
        pdc.set(signatureKey, PersistentDataType.STRING, signature);
        pdc.set(uniqueIdKey, PersistentDataType.STRING, signature);
        item.setItemMeta(meta);
        ItemDataFactory.applyItemModel(item, "icesmp:catalyst_" + jobType.getId());
        ItemDataFactory.applyUseCooldownGroup(item, "catalyst", 0.5F);
        refreshPresentation(item, ownerId, jobType, "", 1, 0, Map.of());
        return item;
    }

    /**
     * Legacy source-compatible factory for preview/test callers. The returned item is deliberately
     * unbound and therefore cannot be used as a spellcasting Lélekkapocs.
     */
    public ItemStack createCatalyst(final JobType jobType) {
        final CatalystTheme theme = resolveTheme(jobType);
        final ItemStack item = new ItemStack(theme.material());
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(theme.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(
                    isCatalystKey, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        ItemDataFactory.applyItemModel(item,
                "icesmp:catalyst_" + (jobType == null ? "wizard" : jobType.getId()));
        ItemDataFactory.applyUseCooldownGroup(item, "catalyst", 0.5F);
        return item;
    }

    public boolean isCatalyst(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer()
                .get(isCatalystKey, PersistentDataType.BOOLEAN));
    }

    public Optional<UUID> ownerOf(final ItemStack item) {
        if (!isCatalyst(item)) return Optional.empty();
        final String raw = item.getItemMeta().getPersistentDataContainer()
                .get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (final IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public boolean isOwnedBy(final ItemStack item, final UUID ownerId) {
        return ownerId != null && ownerOf(item).filter(ownerId::equals).isPresent();
    }

    /** Fail-closed physical mirror validation. Profile v2 still decides gameplay authority. */
    public boolean isUsableBy(final ItemStack item, final UUID ownerId, final JobType jobType) {
        if (!isCatalyst(item) || ownerId == null || jobType == null) return false;
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return ownerId.toString().equals(pdc.get(ownerKey, PersistentDataType.STRING))
                && jobType.getId().equalsIgnoreCase(
                pdc.getOrDefault(classKey, PersistentDataType.STRING, ""))
                && signature(ownerId, jobType).equals(
                pdc.getOrDefault(signatureKey, PersistentDataType.STRING, ""));
    }

    /** Same logical artifact identity; useful for deterministic duplicate cleanup. */
    public boolean isPersonalCopyFor(final ItemStack item, final UUID ownerId,
                                     final JobType jobType) {
        return isUsableBy(item, ownerId, jobType);
    }

    /**
     * Refreshes only presentation/mirror fields. No class/spec/mastery decision is ever read back
     * from these tags for authority.
     */
    public void refreshPresentation(final ItemStack item, final UUID ownerId,
                                    final JobType jobType, final String activeSpec,
                                    final int classLevel, final int masteryRank,
                                    final Map<String, String> doctrineChoices) {
        if (!isUsableBy(item, ownerId, jobType)) return;
        final String spec = activeSpec == null ? "" : activeSpec.trim().toLowerCase(Locale.ROOT);
        final String evolution = evolution(classLevel);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(MINI_MESSAGE.deserialize(resolveTheme(jobType).displayName())
                .decoration(TextDecoration.ITALIC, false));

        final List<Component> lore = new ArrayList<>();
        lore.add(line("<dark_gray>Lélekkapocs — személyes class spellbook</dark_gray>"));
        lore.add(line("<gray>Állapot: <white>" + evolution + "</white></gray>"));
        if (!spec.isBlank()) {
            lore.add(line("<gray>Aktív út: <white>" + specDisplay(spec) + "</white></gray>"));
        } else {
            lore.add(line("<gray>Aktív út: <dark_gray>még nincs specializáció</dark_gray></gray>"));
        }
        if (classLevel >= 35 && !spec.isBlank()) {
            if (jobType == JobType.WARRIOR) {
                addWarriorEvolutionLore(lore, spec, doctrineChoices == null ? Map.of() : doctrineChoices);
            } else if (jobType == JobType.EVOKER) {
                addEvokerEvolutionLore(lore, spec, doctrineChoices == null ? Map.of() : doctrineChoices);
            }
        }
        if (masteryRank >= 5) {
            lore.add(line(masteryRank >= 10
                    ? "<gold>✦ Mesterség X — Kallan emlékezete visszhangzik.</gold>"
                    : "<aqua>✧ Mesterség V — a tárgy formája kiforrott.</aqua>"));
        }
        lore.add(Component.empty());
        lore.add(line("<gray>Jobb katt: <white>aktív spell</white></gray>"));
        lore.add(line("<gray>Shift + jobb katt: <white>spellbook</white></gray>"));
        lore.add(line("<gray>Shift + görgetés/ütés: <white>spellváltás</white></gray>"));
        lore.add(line("<dark_gray>Nem cserélhető • elvesztéskor újragenerálható.</dark_gray>"));
        meta.lore(lore);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(activeSpecKey, PersistentDataType.STRING, spec);
        pdc.set(evolutionKey, PersistentDataType.STRING, evolution.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        ItemDataFactory.applyItemModel(item, "icesmp:catalyst_" + jobType.getId());
        ItemDataFactory.applyUseCooldownGroup(item, "catalyst", 0.5F);
    }

    public Component getDisplayName(final JobType jobType) {
        return MINI_MESSAGE.deserialize(resolveTheme(jobType).displayName());
    }

    public String getDisplayNamePlain(final JobType jobType) {
        return PlainTextComponentSerializer.plainText().serialize(getDisplayName(jobType));
    }

    public Material getMaterial(final JobType jobType) {
        return resolveTheme(jobType).material();
    }

    public void playCycleSound(final org.bukkit.entity.Player player, final JobType jobType) {
        final CatalystTheme theme = resolveTheme(jobType);
        player.playSound(player.getLocation(), theme.cycleSound(),
                theme.cycleVolume(), theme.cyclePitch());
    }

    private void addWarriorEvolutionLore(final List<Component> lore, final String spec,
                                         final Map<String, String> doctrines) {
        if ("berserker".equals(spec)) {
            final String first = doctrines.getOrDefault("level_30", "nincs billog");
            final String second = doctrines.getOrDefault("level_40", "nincs billog");
            lore.add(line("<dark_red>Harci Billog I: <white>" + doctrineDisplay(first) + "</white></dark_red>"));
            lore.add(line("<red>Harci Billog II: <white>" + doctrineDisplay(second) + "</white></red>"));
        } else if ("guardian".equals(spec)) {
            final String design = doctrines.getOrDefault("level_30", "bastion");
            final String command = doctrines.getOrDefault("level_40", "még kialakulatlan");
            lore.add(line("<gold>Sárkánypajzs: <white>" + doctrineDisplay(design) + "</white></gold>"));
            lore.add(line("<yellow>Parancsnoki jel: <white>" + doctrineDisplay(command) + "</white></yellow>"));
        }
    }

    private void addEvokerEvolutionLore(final List<Component> lore, final String spec,
                                        final Map<String, String> doctrines) {
        if ("devastation".equals(spec)) {
            final String first = doctrines.getOrDefault("level_30", "még kialakulatlan");
            final String second = doctrines.getOrDefault("level_40", "még kialakulatlan");
            lore.add(line("<gold>Izzó véna I: <white>" + doctrineDisplay(first) + "</white></gold>"));
            lore.add(line("<red>Izzó véna II: <white>" + doctrineDisplay(second) + "</white></red>"));
        } else if ("preservation".equals(spec)) {
            final String first = doctrines.getOrDefault("level_30", "még kialakulatlan");
            final String second = doctrines.getOrDefault("level_40", "még kialakulatlan");
            lore.add(line("<aqua>Smaragd-csepp I: <white>" + doctrineDisplay(first) + "</white></aqua>"));
            lore.add(line("<green>Smaragd-csepp II: <white>" + doctrineDisplay(second) + "</white></green>"));
        }
    }

    private static String evolution(final int level) {
        if (level >= 50) return "Beteljesedés";
        if (level >= 35) return "Mélyülés";
        if (level >= 25) return "Elköteleződés";
        return "Ébredés";
    }

    private static String signature(final UUID ownerId, final JobType jobType) {
        return UUID.nameUUIDFromBytes(("icesmp:soulbond:" + ownerId + ':' + jobType.getId())
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Component line(final String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static String specDisplay(final String id) {
        return switch (id) {
            case "berserker" -> "Berserker";
            case "guardian" -> "Védelmező";
            case "devastation" -> "Perzselés";
            case "preservation" -> "Megőrzés";
            default -> id;
        };
    }

    private static String doctrineDisplay(final String id) {
        return switch (id) {
            case "bloodlust" -> "Vérszomj";
            case "titan" -> "Titán";
            case "whirlwind" -> "Forgószél";
            case "executioner" -> "Hóhér";
            case "defiant" -> "Dacoló";
            case "kallan_wrath" -> "Kallan Haragja";
            case "bastion" -> "Bástya";
            case "vanguard" -> "Előőrs";
            case "shield_wall" -> "Pajzsfal";
            case "war_signal" -> "Hadijel";
            case "for_one" -> "Egy emberért";
            case "for_all" -> "Mindenkiért";
            case "gyujtopont" -> "Gyújtópont";
            case "hosszu_lelegzet" -> "Hosszú Lélegzet";
            case "iker_aram" -> "Ikeráram";
            case "tulhevites" -> "Túlhevítés";
            case "orok_izzas" -> "Örök Izzás";
            case "kettos_szikra" -> "Kettős Szikra";
            case "hosszu_visszhang" -> "Hosszú Visszhang";
            case "melyebb_visszhang" -> "Mélyebb Visszhang";
            case "idofonal" -> "Időfonál";
            case "gyors_lenyomat" -> "Gyors Lenyomat";
            case "orzo_pajzs" -> "Őrző Pajzs";
            case "tiszta_ido" -> "Tiszta Idő";
            default -> id.replace('_', ' ');
        };
    }

    private CatalystTheme resolveTheme(final JobType jobType) {
        final CatalystTheme theme = jobType == null ? null : THEMES.get(jobType);
        return theme == null ? THEMES.get(JobType.WIZARD) : theme;
    }
}
