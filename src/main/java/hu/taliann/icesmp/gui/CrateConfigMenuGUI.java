package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dynamic admin editor for native crate definitions and structured reward lists. */
public final class CrateConfigMenuGUI {

    public static final String ROOT_ACTION = "CRATE_CONFIG";
    public static final String ENTRY_ACTION_PREFIX = "ADV:";
    public static final String ROOT_PAGE_ACTION_PREFIX = "CRATE_ROOT_PAGE:";
    public static final String OPEN_ACTION_PREFIX = "CRATE_OPEN:";
    public static final String GLOBAL_ACTION = "CRATE_GLOBAL";
    public static final String REWARDS_ACTION_PREFIX = "CRATE_REWARDS:";
    public static final String REWARD_OPEN_ACTION_PREFIX = "CRATE_REWARD_OPEN:";
    public static final String REWARD_ADD_ACTION_PREFIX = "CRATE_REWARD_ADD:";
    public static final String REWARD_DELETE_ACTION_PREFIX = "CRATE_REWARD_DELETE:";
    public static final String REWARD_NUMBER_ACTION_PREFIX = "CRW_NUM:";
    public static final String REWARD_TEXT_ACTION_PREFIX = "CRW_TEXT:";
    public static final String REWARD_CURRENCY_ACTION_PREFIX = "CRW_CURRENCY:";

    public static final String ROOT_CATEGORY_PREFIX = "crate-root:";
    public static final String GLOBAL_CATEGORY = "crate-global";
    public static final String CRATE_CATEGORY_PREFIX = "crate:";
    public static final String REWARDS_CATEGORY_PREFIX = "crate-rewards:";
    public static final String REWARD_CATEGORY_PREFIX = "crate-reward:";

    private static final int PAGE_SIZE = 45;

    private static final List<AdvancedConfigEntry> GLOBAL_ENTRIES = List.of(
            AdvancedConfigEntry.toggle("crates-settings.enabled", "Natív crate-rendszer",
                    "A teljes natív crate-rendszer főkapcsolója. Kikapcsolva egyetlen crate sem nyitható, a definíciók és statisztikák megmaradnak.")
    );

    private CrateConfigMenuGUI() {
    }

    public static List<String> crateIds(final ConfigManager configManager) {
        final ConfigurationSection configuration = configManager.getConfiguration();
        final ConfigurationSection crates = configuration == null ? null
                : configuration.getConfigurationSection("crates");
        return crates == null ? List.of() : List.copyOf(crates.getKeys(false));
    }

    public static AdvancedConfigEntry findEntry(final String key,
                                                final ConfigManager configManager) {
        for (final AdvancedConfigEntry entry : GLOBAL_ENTRIES) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        if (key == null || !key.startsWith("crates.")) {
            return null;
        }
        for (final String crateId : crateIds(configManager)) {
            for (final AdvancedConfigEntry entry : entriesFor(crateId)) {
                if (entry.key().equals(key)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static List<AdvancedConfigEntry> entriesFor(final String crateId) {
        final String root = "crates." + crateId + ".";
        return List.of(
                AdvancedConfigEntry.toggle(root + "enabled", "Crate engedélyezve",
                        "Külön kapcsolja ezt a crate-et. Kikapcsolva a ládablokk, kulcsvásárlás és nyitás megtagadásra kerül."),
                AdvancedConfigEntry.text(root + "display-name", "Crate megjelenített neve",
                        128, false, "", "A crate játékosoknak megjelenő, legacy & színkódokat is támogató neve."),
                AdvancedConfigEntry.text(root + "key-material", "Kulcs Bukkit materialja",
                        64, false, "[A-Za-z0-9_:.-]+", "A kulcstárgy alap Bukkit materialja. Létező, nem AIR material szükséges."),
                AdvancedConfigEntry.text(root + "key-name", "Kulcs megjelenített neve",
                        128, false, "", "A crate kulcsának játékosoknak megjelenő, legacy & színkódokat támogató neve."),
                AdvancedConfigEntry.text(root + "key-item-model", "Kulcs item-model azonosító",
                        128, true, "[A-Za-z0-9_:./-]*", "Az 1.21-es item model azonosítója, például icesmp:crate_key_common. !empty értékkel törölhető."),
                AdvancedConfigEntry.cycle(root + "key-price.currency", "Kulcs ára — valuta",
                        List.of("RED", "BLUE", "NEUTRAL", "DARK"), "A kulcsvásárláskor levont valuta típusa."),
                AdvancedConfigEntry.number(root + "key-price.amount", "Kulcs ára — összeg",
                        25.0D, 0.0D, 1_000_000_000.0D, "Egy crate kulcs vásárlási ára a kiválasztott valutában."),
                AdvancedConfigEntry.text(root + "permission", "Egyedi használati permission",
                        96, true, "[A-Za-z0-9_.-]*", "Üresen csak az általános crate permission kell. Egyedi érték kizárólag icesmp.* névtérben adható meg."),
                AdvancedConfigEntry.stringList(root + "worlds", "Engedélyezett világok",
                        32, 64, true, "[^|\\r\\n]+", "Üres lista esetén minden betöltött világ engedett. Egyébként csak a pontosan felsorolt, jelenleg ismert világokban nyitható."),
                AdvancedConfigEntry.integer(root + "required-key-count", "Kulcsok nyitásonként",
                        1, 1, CrateRules.MAX_REQUIRED_KEYS, "Egy nyitási egységhez elfogyasztott kulcsok száma."),
                AdvancedConfigEntry.integer(root + "cooldown-seconds", "Játékos cooldown (mp)",
                        5, 0, (int) (CrateRules.MAX_COOLDOWN_MILLIS / 1000L), "Két nyitás között játékosonként kötelező várakozás. 0 esetén nincs cooldown."),
                AdvancedConfigEntry.toggle(root + "mass-open.enabled", "Többszörös nyitás",
                        "Engedélyezi, hogy a játékos egy művelettel több nyitást kérjen és összesített elszámolást kapjon."),
                AdvancedConfigEntry.integer(root + "mass-open.max-openings", "Max. többszörös nyitás",
                        1, 1, CrateRules.MAX_MASS_OPEN, "Egyetlen többszörös nyitási kérés felső korlátja."),
                AdvancedConfigEntry.text(root + "opening-sound.sound", "Nyitási hang",
                        128, false, "[A-Za-z0-9_:.-]+", "A sikeres nyitáskor lejátszott Minecraft vagy Bukkit hangazonosító."),
                AdvancedConfigEntry.number(root + "opening-sound.volume", "Nyitási hangerő",
                        0.1D, 0.0D, 10.0D, "A crate nyitási hangjának hangerőszorzója."),
                AdvancedConfigEntry.number(root + "opening-sound.pitch", "Nyitási hangmagasság",
                        0.1D, 0.0D, 2.0D, "A crate nyitási hangjának pitch értéke."),
                AdvancedConfigEntry.toggle(root + "broadcast.enabled", "Nyitási broadcast",
                        "Sikeres nyitáskor a konfigurált broadcast üzenetet elküldi a szervernek."),
                AdvancedConfigEntry.text(root + "broadcast.message", "Broadcast üzenet",
                        512, false, "", "A crate nyitási szerverüzenete. Használható tokenek: {player}, {crate}, {amount}.")
        );
    }

    public static void openRoot(final Player player, final ConfigManager configManager,
                                final int requestedPage) {
        final List<String> ids = crateIds(configManager);
        final int pageCount = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                ROOT_CATEGORY_PREFIX + page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Crate editor " + (page + 1) + "/" + pageCount,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        final int from = page * PAGE_SIZE;
        final int to = Math.min(ids.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            final String id = ids.get(index);
            final String root = "crates." + id + ".";
            final Material icon = material(configManager.getString(root + "key-material", "TRIPWIRE_HOOK"),
                    Material.TRIPWIRE_HOOK);
            final String name = configManager.getString(root + "display-name", id);
            final boolean enabled = configManager.getBoolean(root + "enabled", true);
            final int rewards = CrateRewardEditor.rewards(configManager, id).size();
            inventory.setItem(index - from, GuiUtil.item(icon,
                    (enabled ? "&a" : "&c") + name,
                    List.of("&8" + id,
                            "&7Rewardok: &f" + rewards,
                            "&7Állapot: " + (enabled ? "&aengedélyezve" : "&ctiltva"),
                            "&eKattints a szerkesztéshez")));
            holder.bind(index - from, OPEN_ACTION_PREFIX + id);
        }

        if (page > 0) {
            inventory.setItem(45, GuiUtil.item(Material.ARROW, "&7Előző oldal", List.of()));
            holder.bind(45, ROOT_PAGE_ACTION_PREFIX + (page - 1));
        }
        inventory.setItem(47, GuiUtil.item(Material.REDSTONE_TORCH,
                "&bGlobális crate-beállítások",
                List.of("&7Mesterkapcsoló; a reveal a világban fut")));
        holder.bind(47, GLOBAL_ACTION);
        inventory.setItem(49, GuiUtil.item(Material.ARROW, "&7Vissza a config főmenübe", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(52, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(52, "CLOSE");
        if (page + 1 < pageCount) {
            inventory.setItem(53, GuiUtil.item(Material.ARROW, "&7Következő oldal", List.of()));
            holder.bind(53, ROOT_PAGE_ACTION_PREFIX + (page + 1));
        }
        player.openInventory(inventory);
    }

    public static void openGlobal(final Player player, final ConfigManager configManager) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), GLOBAL_CATEGORY);
        final Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("⚙ Globális crate-beállítások", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        for (int index = 0; index < GLOBAL_ENTRIES.size(); index++) {
            final AdvancedConfigEntry entry = GLOBAL_ENTRIES.get(index);
            inventory.setItem(10 + index, AdvancedConfigEntryRenderer.render(entry, configManager));
            holder.bind(10 + index, ENTRY_ACTION_PREFIX + entry.key());
        }
        inventory.setItem(22, GuiUtil.item(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(22, "BACK");
        inventory.setItem(26, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(26, "CLOSE");
        player.openInventory(inventory);
    }

    public static void openCrate(final Player player, final ConfigManager configManager,
                                 final String crateId) {
        if (!crateIds(configManager).contains(crateId)) {
            openRoot(player, configManager, 0);
            return;
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                CRATE_CATEGORY_PREFIX + crateId);
        final String name = configManager.getString("crates." + crateId + ".display-name", crateId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Crate: " + stripLegacy(name), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final AdvancedConfigEntry entry : entriesFor(crateId)) {
            inventory.setItem(slot, AdvancedConfigEntryRenderer.render(entry, configManager));
            holder.bind(slot, ENTRY_ACTION_PREFIX + entry.key());
            slot++;
        }
        inventory.setItem(45, GuiUtil.item(Material.CHEST,
                "&bStrukturált reward-editor",
                List.of("&7" + CrateRewardEditor.rewards(configManager, crateId).size() + " reward",
                        "&7Súly, mennyiség, leírás és típusfüggő mezők",
                        "&eKattints a jutalmakhoz")));
        holder.bind(45, REWARDS_ACTION_PREFIX + crateId + ":0");
        inventory.setItem(49, GuiUtil.item(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(53, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    public static void openRewards(final Player player, final ConfigManager configManager,
                                   final String crateId, final int requestedPage) {
        final List<Map<String, Object>> rewards = CrateRewardEditor.rewards(configManager, crateId);
        final int pageCount = Math.max(1, (rewards.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                REWARDS_CATEGORY_PREFIX + crateId + ":" + page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Rewardok: " + crateId + " " + (page + 1) + "/" + pageCount,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        final int from = page * PAGE_SIZE;
        final int to = Math.min(rewards.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            inventory.setItem(index - from, rewardTile(rewards.get(index), index));
            holder.bind(index - from, REWARD_OPEN_ACTION_PREFIX + crateId + ":" + index);
        }
        if (page > 0) {
            inventory.setItem(45, GuiUtil.item(Material.ARROW, "&7Előző oldal", List.of()));
            holder.bind(45, REWARDS_ACTION_PREFIX + crateId + ":" + (page - 1));
        }
        inventory.setItem(47, GuiUtil.item(Material.LIME_DYE,
                "&aÚj tárgyjutalom",
                List.of("&7Biztonságos alap: STONE, 1 db, 1.0 súly",
                        "&7Utána külön szerkeszthető.")));
        holder.bind(47, REWARD_ADD_ACTION_PREFIX + crateId);
        inventory.setItem(49, GuiUtil.item(Material.ARROW, "&7Vissza a crate-hez", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(52, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(52, "CLOSE");
        if (page + 1 < pageCount) {
            inventory.setItem(53, GuiUtil.item(Material.ARROW, "&7Következő oldal", List.of()));
            holder.bind(53, REWARDS_ACTION_PREFIX + crateId + ":" + (page + 1));
        }
        player.openInventory(inventory);
    }

    public static void openReward(final Player player, final ConfigManager configManager,
                                  final String crateId, final int index) {
        final List<Map<String, Object>> rewards = CrateRewardEditor.rewards(configManager, crateId);
        final Map<String, Object> reward = index >= 0 && index < rewards.size()
                ? rewards.get(index) : Map.of();
        if (reward.isEmpty()) {
            openRewards(player, configManager, crateId, 0);
            return;
        }
        final String type = CrateRewardEditor.type(reward);
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                REWARD_CATEGORY_PREFIX + crateId + ":" + index);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Reward #" + (index + 1) + " — " + type,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        inventory.setItem(10, GuiUtil.item(typeIcon(type), "&bTípus: &f" + type,
                List.of("&7A típus rögzített, így nincs félkész",
                        "&7típusváltási állapot. Új rewardként",
                        "&7biztonságos item típus adható hozzá.")));
        inventory.setItem(11, numberTile("Súly", CrateRewardEditor.numericValue(reward, "weight", 1.0D),
                "A relatív sorsolási súly; az esélyt a teljes súlyösszeghez viszonyítja."));
        holder.bind(11, REWARD_NUMBER_ACTION_PREFIX + crateId + ":" + index + ":weight");

        if (!"command".equals(type)) {
            inventory.setItem(12, numberTile("Mennyiség",
                    CrateRewardEditor.numericValue(reward, "amount", 1.0D),
                    "A kiosztott tárgy-, kulcs- vagy valuta-mennyiség."));
            holder.bind(12, REWARD_NUMBER_ACTION_PREFIX + crateId + ":" + index + ":amount");
        }

        inventory.setItem(13, textTile("Leírás", String.valueOf(reward.getOrDefault("description", "")),
                "A jutalom esélylistában megjelenő leírás."));
        holder.bind(13, REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":description");

        switch (type) {
            case "item" -> {
                inventory.setItem(14, textTile("Material",
                        String.valueOf(reward.getOrDefault("material", "STONE")),
                        "A kiosztott vanilla Bukkit material."));
                holder.bind(14, REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":material");
            }
            case "command" -> {
                inventory.setItem(14, textTile("Konzolparancs",
                        String.valueOf(reward.getOrDefault("command", "")),
                        "Kezdő / nélküli parancs; csak {player}, {uuid}, {crate}, {amount} token engedett."));
                holder.bind(14, REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":command");
            }
            case "currency" -> {
                inventory.setItem(14, GuiUtil.item(Material.EMERALD,
                        "&bValuta: &f" + reward.getOrDefault("currency", "NEUTRAL"),
                        List.of("&7Kattintás: következő valuta")));
                holder.bind(14, REWARD_CURRENCY_ACTION_PREFIX + crateId + ":" + index);
            }
            default -> inventory.setItem(14, GuiUtil.item(Material.BOOK,
                    "&bHivatkozás: &f" + reward.getOrDefault("id", ""),
                    List.of("&7A unique/recept/blueprint/crate cél",
                            "&7ebben a biztonsági körben csak olvasható.")));
        }

        inventory.setItem(45, GuiUtil.item(Material.RED_DYE, "&cReward törlése",
                List.of("&7Legalább egy rewardnak meg kell maradnia.")));
        holder.bind(45, REWARD_DELETE_ACTION_PREFIX + crateId + ":" + index);
        inventory.setItem(49, GuiUtil.item(Material.ARROW, "&7Vissza a rewardlistához", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(53, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    public static boolean isCrateCategory(final String category) {
        return category != null && (category.equals(GLOBAL_CATEGORY)
                || category.startsWith(ROOT_CATEGORY_PREFIX)
                || category.startsWith(CRATE_CATEGORY_PREFIX)
                || category.startsWith(REWARDS_CATEGORY_PREFIX)
                || category.startsWith(REWARD_CATEGORY_PREFIX));
    }

    public static void openBack(final Player player, final ConfigManager configManager,
                                final String category) {
        if (category == null || category.startsWith(ROOT_CATEGORY_PREFIX)) {
            ConfigMenuRootGUI.openRoot(player);
        } else if (category.equals(GLOBAL_CATEGORY)) {
            openRoot(player, configManager, 0);
        } else if (category.startsWith(CRATE_CATEGORY_PREFIX)) {
            openRoot(player, configManager, 0);
        } else if (category.startsWith(REWARDS_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(REWARDS_CATEGORY_PREFIX.length()).split(":");
            openCrate(player, configManager, parts[0]);
        } else if (category.startsWith(REWARD_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(REWARD_CATEGORY_PREFIX.length()).split(":");
            openRewards(player, configManager, parts[0], Integer.parseInt(parts[1]) / PAGE_SIZE);
        }
    }

    public static void reopen(final Player player, final ConfigManager configManager,
                              final String category) {
        if (category.startsWith(ROOT_CATEGORY_PREFIX)) {
            openRoot(player, configManager, parseTailInt(category, ROOT_CATEGORY_PREFIX, 0));
        } else if (category.equals(GLOBAL_CATEGORY)) {
            openGlobal(player, configManager);
        } else if (category.startsWith(CRATE_CATEGORY_PREFIX)) {
            openCrate(player, configManager, category.substring(CRATE_CATEGORY_PREFIX.length()));
        } else if (category.startsWith(REWARDS_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(REWARDS_CATEGORY_PREFIX.length()).split(":");
            openRewards(player, configManager, parts[0], Integer.parseInt(parts[1]));
        } else if (category.startsWith(REWARD_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(REWARD_CATEGORY_PREFIX.length()).split(":");
            openReward(player, configManager, parts[0], Integer.parseInt(parts[1]));
        }
    }

    private static ItemStack rewardTile(final Map<String, Object> reward, final int index) {
        final String type = CrateRewardEditor.type(reward);
        final double weight = CrateRewardEditor.numericValue(reward, "weight", 1.0D);
        final String description = String.valueOf(reward.getOrDefault("description", ""));
        return GuiUtil.item(typeIcon(type), "&b#" + (index + 1) + " &f" + type,
                List.of("&7Súly: &f" + format(weight),
                        "&7Mennyiség: &f" + format(CrateRewardEditor.numericValue(reward, "amount", 1.0D)),
                        description.isBlank() ? "&8Nincs leírás" : "&7" + description,
                        "&eKattints a szerkesztéshez"));
    }

    private static ItemStack numberTile(final String label, final double value,
                                        final String description) {
        return GuiUtil.item(Material.PAPER, "&b" + label + ": &f" + format(value),
                List.of("&7" + description, "", "&eBal katt: növelés",
                        "&eJobb katt: csökkentés", "&7SHIFT = ötszörös lépés"));
    }

    private static ItemStack textTile(final String label, final String value,
                                      final String description) {
        final String compact = value.length() <= 48 ? value : value.substring(0, 47) + "…";
        return GuiUtil.item(Material.NAME_TAG, "&b" + label,
                List.of("&7" + description, "", "&fJelenleg: &b" + compact,
                        "&eKattintás: biztonságos chat-bevitel"));
    }

    private static Material typeIcon(final String type) {
        return switch (type) {
            case "item" -> Material.CHEST;
            case "command" -> Material.COMMAND_BLOCK;
            case "currency" -> Material.EMERALD;
            case "unique-item" -> Material.NETHER_STAR;
            case "recipe-item" -> Material.CRAFTING_TABLE;
            case "blueprint" -> Material.KNOWLEDGE_BOOK;
            case "crate-key" -> Material.TRIPWIRE_HOOK;
            default -> Material.BARRIER;
        };
    }

    private static Material material(final String raw, final Material fallback) {
        final Material value = Material.matchMaterial(raw == null ? "" : raw);
        return value == null || value.isAir() ? fallback : value;
    }

    private static String format(final double value) {
        return Math.rint(value) == value ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String stripLegacy(final String raw) {
        return raw == null ? "crate" : raw.replaceAll("(?i)&[0-9A-FK-ORX]", "");
    }

    private static int parseTailInt(final String category, final String prefix,
                                    final int fallback) {
        try {
            return Integer.parseInt(category.substring(prefix.length()));
        } catch (final RuntimeException ignored) {
            return fallback;
        }
    }
}
