package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Transactional views for crate definitions and complete staged reward-list overrides. */
public final class TransactionalCrateConfigMenuGUI {

    private static final int PAGE_SIZE = 45;
    private static final List<AdvancedConfigEntry> GLOBAL_ENTRIES = globalEntriesReflective();

    private TransactionalCrateConfigMenuGUI() { }

    @SuppressWarnings("unchecked")
    private static List<AdvancedConfigEntry> globalEntriesReflective() {
        try {
            final Field field = CrateConfigMenuGUI.class.getDeclaredField("GLOBAL_ENTRIES");
            field.setAccessible(true);
            return List.copyOf((List<AdvancedConfigEntry>) field.get(null));
        } catch (final ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public static List<AdvancedConfigEntry> globalEntries() { return GLOBAL_ENTRIES; }

    public static AdvancedConfigEntry findEntry(final String key, final ConfigManager configManager) {
        for (final AdvancedConfigEntry entry : GLOBAL_ENTRIES) if (entry.key().equals(key)) return entry;
        return CrateConfigMenuGUI.findEntry(key, configManager);
    }

    public static void openRoot(final Player player, final ConfigManager configManager,
                                final ConfigEditSession session, final int requestedPage) {
        final List<String> ids = CrateConfigMenuGUI.crateIds(configManager);
        final int pageCount = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                CrateConfigMenuGUI.ROOT_CATEGORY_PREFIX + page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Crate editor " + (page + 1) + "/" + pageCount,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        final int from = page * PAGE_SIZE;
        final int to = Math.min(ids.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            final String id = ids.get(index);
            final String root = "crates." + id + ".";
            final Material icon = material(stringValue(session, configManager,
                    root + "key-material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK);
            final String name = stringValue(session, configManager, root + "display-name", id);
            final boolean enabled = booleanValue(session, configManager, root + "enabled", true);
            final int rewards = stagedRewards(session, configManager, id).size();
            inventory.setItem(index - from, GuiUtil.item(icon, (enabled ? "&a" : "&c") + name,
                    List.of("&8" + id, "&7Rewardok: &f" + rewards,
                            session.hasPending(CrateRewardEditor.path(id))
                                    ? "&eNem mentett rewardlista" : "&7Rewardlista változatlan",
                            "&eKattints a staged szerkesztéshez")));
            holder.bind(index - from, CrateConfigMenuGUI.OPEN_ACTION_PREFIX + id);
        }
        if (page > 0) {
            inventory.setItem(46, GuiUtil.item(Material.ARROW, "&7Előző oldal", List.of()));
            holder.bind(46, CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX + (page - 1));
        }
        inventory.setItem(47, GuiUtil.item(Material.REDSTONE_TORCH, "&bGlobális crate-beállítások",
                List.of("&7Mesterkapcsoló és pörgős animáció")));
        holder.bind(47, CrateConfigMenuGUI.GLOBAL_ACTION);
        if (page + 1 < pageCount) {
            inventory.setItem(52, GuiUtil.item(Material.ARROW, "&7Következő oldal", List.of()));
            holder.bind(52, CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX + (page + 1));
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openGlobal(final Player player, final ConfigManager configManager,
                                  final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), CrateConfigMenuGUI.GLOBAL_CATEGORY);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Globális crate-beállítások", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        for (int index = 0; index < GLOBAL_ENTRIES.size(); index++) {
            final AdvancedConfigEntry entry = GLOBAL_ENTRIES.get(index);
            inventory.setItem(index, AdvancedConfigEntryRenderer.render(entry, configManager, session));
            holder.bind(index, CrateConfigMenuGUI.ENTRY_ACTION_PREFIX + entry.key());
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openCrate(final Player player, final ConfigManager configManager,
                                 final ConfigEditSession session, final String crateId) {
        if (!CrateConfigMenuGUI.crateIds(configManager).contains(crateId)) {
            openRoot(player, configManager, session, 0);
            return;
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                CrateConfigMenuGUI.CRATE_CATEGORY_PREFIX + crateId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Crate: " + crateId, NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final AdvancedConfigEntry entry : CrateConfigMenuGUI.entriesFor(crateId)) {
            inventory.setItem(slot, AdvancedConfigEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, CrateConfigMenuGUI.ENTRY_ACTION_PREFIX + entry.key());
            slot++;
        }
        inventory.setItem(44, GuiUtil.item(Material.CHEST, "&bStrukturált reward-editor",
                List.of("&7" + stagedRewards(session, configManager, crateId).size() + " reward",
                        "&7A teljes lista staged másolatként változik.")));
        holder.bind(44, CrateConfigMenuGUI.REWARDS_ACTION_PREFIX + crateId + ":0");
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openRewards(final Player player, final ConfigManager configManager,
                                   final ConfigEditSession session, final String crateId,
                                   final int requestedPage) {
        final List<Map<String, Object>> rewards = stagedRewards(session, configManager, crateId);
        final int pageCount = Math.max(1, (rewards.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                CrateConfigMenuGUI.REWARDS_CATEGORY_PREFIX + crateId + ":" + page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Rewardok: " + crateId + " " + (page + 1) + "/" + pageCount,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        final int from = page * PAGE_SIZE;
        final int to = Math.min(rewards.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            inventory.setItem(index - from, rewardTile(rewards.get(index), index));
            holder.bind(index - from, CrateConfigMenuGUI.REWARD_OPEN_ACTION_PREFIX + crateId + ":" + index);
        }
        if (page > 0) {
            inventory.setItem(46, GuiUtil.item(Material.ARROW, "&7Előző oldal", List.of()));
            holder.bind(46, CrateConfigMenuGUI.REWARDS_ACTION_PREFIX + crateId + ":" + (page - 1));
        }
        inventory.setItem(47, GuiUtil.item(Material.LIME_DYE, "&aÚj tárgyjutalom",
                List.of("&7Biztonságos STONE alap; staged listába kerül.")));
        holder.bind(47, CrateConfigMenuGUI.REWARD_ADD_ACTION_PREFIX + crateId);
        if (page + 1 < pageCount) {
            inventory.setItem(52, GuiUtil.item(Material.ARROW, "&7Következő oldal", List.of()));
            holder.bind(52, CrateConfigMenuGUI.REWARDS_ACTION_PREFIX + crateId + ":" + (page + 1));
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openReward(final Player player, final ConfigManager configManager,
                                  final ConfigEditSession session, final String crateId,
                                  final int index) {
        final Map<String, Object> reward = CrateRewardEditor.reward(
                session.value(CrateRewardEditor.path(crateId)), index);
        if (reward.isEmpty()) {
            openRewards(player, configManager, session, crateId, 0);
            return;
        }
        final String type = CrateRewardEditor.type(reward);
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                CrateConfigMenuGUI.REWARD_CATEGORY_PREFIX + crateId + ":" + index);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Reward #" + (index + 1) + " — " + type, NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        inventory.setItem(10, GuiUtil.item(typeIcon(type), "&bTípus: &f" + type,
                List.of("&7A típus read-only; nincs félkész típusváltás.")));
        inventory.setItem(11, numberTile("Súly", CrateRewardEditor.numericValue(reward, "weight", 1.0D)));
        holder.bind(11, CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX + crateId + ":" + index + ":weight");
        if (!"command".equals(type)) {
            inventory.setItem(12, numberTile("Mennyiség", CrateRewardEditor.numericValue(reward, "amount", 1.0D)));
            holder.bind(12, CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX + crateId + ":" + index + ":amount");
        }
        inventory.setItem(13, textTile("Leírás", String.valueOf(reward.getOrDefault("description", ""))));
        holder.bind(13, CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":description");
        if ("item".equals(type)) {
            inventory.setItem(14, textTile("Material", String.valueOf(reward.getOrDefault("material", "STONE"))));
            holder.bind(14, CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":material");
        } else if ("command".equals(type)) {
            inventory.setItem(14, textTile("Konzolparancs", String.valueOf(reward.getOrDefault("command", ""))));
            holder.bind(14, CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX + crateId + ":" + index + ":command");
        } else if ("currency".equals(type)) {
            inventory.setItem(14, GuiUtil.item(Material.EMERALD,
                    "&bValuta: &f" + reward.getOrDefault("currency", "NEUTRAL"),
                    List.of("&eKattintás: következő staged valuta")));
            holder.bind(14, CrateConfigMenuGUI.REWARD_CURRENCY_ACTION_PREFIX + crateId + ":" + index);
        }
        inventory.setItem(44, GuiUtil.item(Material.RED_DYE, "&cReward staged törlése",
                List.of("&7Legalább egy rewardnak meg kell maradnia.")));
        holder.bind(44, CrateConfigMenuGUI.REWARD_DELETE_ACTION_PREFIX + crateId + ":" + index);
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openBack(final Player player, final ConfigManager configManager,
                                final ConfigEditSession session, final String category) {
        if (category == null || category.startsWith(CrateConfigMenuGUI.ROOT_CATEGORY_PREFIX)) {
            ConfigMenuRootGUI.openRoot(player, session);
        } else if (category.equals(CrateConfigMenuGUI.GLOBAL_CATEGORY)
                || category.startsWith(CrateConfigMenuGUI.CRATE_CATEGORY_PREFIX)) {
            openRoot(player, configManager, session, 0);
        } else if (category.startsWith(CrateConfigMenuGUI.REWARDS_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(CrateConfigMenuGUI.REWARDS_CATEGORY_PREFIX.length()).split(":");
            openCrate(player, configManager, session, parts[0]);
        } else if (category.startsWith(CrateConfigMenuGUI.REWARD_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(CrateConfigMenuGUI.REWARD_CATEGORY_PREFIX.length()).split(":");
            openRewards(player, configManager, session, parts[0], Integer.parseInt(parts[1]) / PAGE_SIZE);
        }
    }

    public static void reopen(final Player player, final ConfigManager configManager,
                              final ConfigEditSession session, final String category) {
        if (category.startsWith(CrateConfigMenuGUI.ROOT_CATEGORY_PREFIX)) {
            openRoot(player, configManager, session, parseInt(category.substring(CrateConfigMenuGUI.ROOT_CATEGORY_PREFIX.length()), 0));
        } else if (category.equals(CrateConfigMenuGUI.GLOBAL_CATEGORY)) {
            openGlobal(player, configManager, session);
        } else if (category.startsWith(CrateConfigMenuGUI.CRATE_CATEGORY_PREFIX)) {
            openCrate(player, configManager, session, category.substring(CrateConfigMenuGUI.CRATE_CATEGORY_PREFIX.length()));
        } else if (category.startsWith(CrateConfigMenuGUI.REWARDS_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(CrateConfigMenuGUI.REWARDS_CATEGORY_PREFIX.length()).split(":");
            openRewards(player, configManager, session, parts[0], parseInt(parts[1], 0));
        } else if (category.startsWith(CrateConfigMenuGUI.REWARD_CATEGORY_PREFIX)) {
            final String[] parts = category.substring(CrateConfigMenuGUI.REWARD_CATEGORY_PREFIX.length()).split(":");
            openReward(player, configManager, session, parts[0], parseInt(parts[1], 0));
        }
    }

    public static List<Map<String, Object>> stagedRewards(final ConfigEditSession session,
                                                          final ConfigManager configManager,
                                                          final String crateId) {
        final String path = CrateRewardEditor.path(crateId);
        final Object raw = session == null ? configManager.getConfiguration().getList(path) : session.value(path);
        return CrateRewardEditor.rewards(raw);
    }

    private static String stringValue(final ConfigEditSession session, final ConfigManager config,
                                      final String key, final String fallback) {
        final Object value = session == null ? config.getConfiguration().get(key) : session.value(key);
        return value == null ? fallback : String.valueOf(value);
    }
    private static boolean booleanValue(final ConfigEditSession session, final ConfigManager config,
                                        final String key, final boolean fallback) {
        final Object value = session == null ? config.getConfiguration().get(key) : session.value(key);
        return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
    private static ItemStack rewardTile(final Map<String, Object> reward, final int index) {
        final String type = CrateRewardEditor.type(reward);
        return GuiUtil.item(typeIcon(type), "&b#" + (index + 1) + " &f" + type,
                List.of("&7Súly: &f" + format(CrateRewardEditor.numericValue(reward, "weight", 1.0D)),
                        "&7Mennyiség: &f" + format(CrateRewardEditor.numericValue(reward, "amount", 1.0D)),
                        "&eKattints a staged szerkesztéshez"));
    }
    private static ItemStack numberTile(final String label, final double value) {
        return GuiUtil.item(Material.PAPER, "&b" + label + ": &f" + format(value),
                List.of("&eBal katt: növelés", "&eJobb katt: csökkentés", "&7SHIFT = ötszörös lépés"));
    }
    private static ItemStack textTile(final String label, final String value) {
        final String compact = value.length() <= 48 ? value : value.substring(0, 47) + "…";
        return GuiUtil.item(Material.NAME_TAG, "&b" + label,
                List.of("&fJelenleg: &b" + compact, "&eKattintás: privát staged chat-bevitel"));
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
        return Math.rint(value) == value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.3f", value);
    }
    private static int parseInt(final String raw, final int fallback) {
        try { return Integer.parseInt(raw); } catch (final RuntimeException ignored) { return fallback; }
    }
}
