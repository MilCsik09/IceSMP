package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.AdvancedConfigRuntimeBridge;
import hu.taliann.icesmp.core.ConfigRuntimeReloadBridge;
import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.gui.AdvancedConfigEntry;
import hu.taliann.icesmp.gui.AdvancedConfigEntryRenderer;
import hu.taliann.icesmp.gui.AdvancedConfigPolicy;
import hu.taliann.icesmp.gui.BlockRegenConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigChatInputGate;
import hu.taliann.icesmp.gui.ConfigMenuEntryRenderer;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuHolder;
import hu.taliann.icesmp.gui.ConfigMenuRootGUI;
import hu.taliann.icesmp.gui.CrateConfigMenuGUI;
import hu.taliann.icesmp.gui.CrateRewardEditor;
import hu.taliann.icesmp.gui.GuiUtil;
import hu.taliann.icesmp.gui.OperationalConfigMenuGUI;
import hu.taliann.icesmp.gui.OperationalConfigPolicy;
import hu.taliann.icesmp.gui.ServerWorldConfigMenuGUI;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Az admin config-menü kattintás-kezelője. Minden írás a config.yml override-rétegbe
 * kerül; a görgőkattintás (és survival-kompatibilis tartalékban a Q) ezt a konkrét
 * override-ot törli, ezért a subsystem YAML aktuális alapértéke lép vissza.
 */
public final class ConfigMenuGUIListener implements Listener {

    public static final String PERMISSION = "icesmp.admin.config";
    private static final long INPUT_TIMEOUT_MILLIS = 120_000L;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @FunctionalInterface
    private interface InputCommit {
        /** @return null on success, otherwise a player-facing error. */
        String commit(Player player, Object value);
    }

    private record InputSession(AdvancedConfigEntry entry, String returnCategory,
                                InputCommit commit, Consumer<Player> defaultAction,
                                long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, InputSession> inputSessions = new ConcurrentHashMap<>();
    private volatile Consumer<String> configChangeHook;

    public ConfigMenuGUIListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void setConfigChangeHook(final Consumer<String> configChangeHook) {
        this.configChangeHook = configChangeHook;
    }

    public void open(final Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(messageManager.get("no-permission", "&cNincs jogosultságod ehhez."));
            return;
        }
        ConfigMenuRootGUI.openRoot(player);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()
                || !player.hasPermission(PERMISSION)) {
            return;
        }

        final String action = holder.actionAt(event.getSlot());
        if (action == null) {
            return;
        }
        if ("CLOSE".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("BACK".equals(action)) {
            openBack(player, holder.getCategory());
            return;
        }
        if (BlockRegenConfigMenuGUI.ROOT_ACTION.equals(action)) {
            BlockRegenConfigMenuGUI.open(player, configManager);
            return;
        }
        if (OperationalConfigMenuGUI.ROOT_ACTION.equals(action)) {
            OperationalConfigMenuGUI.openRoot(player);
            return;
        }
        if (ServerWorldConfigMenuGUI.ROOT_ACTION.equals(action)) {
            ServerWorldConfigMenuGUI.open(player, configManager);
            return;
        }
        if (CrateConfigMenuGUI.ROOT_ACTION.equals(action)) {
            CrateConfigMenuGUI.openRoot(player, configManager, 0);
            return;
        }
        if (action.startsWith(OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX)) {
            OperationalConfigMenuGUI.openCategory(player,
                    action.substring(OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX.length()),
                    configManager);
            return;
        }
        if (action.startsWith("CAT:")) {
            ConfigMenuGUI.openCategory(player, action.substring(4), configManager);
            return;
        }

        if (handleCrateAction(player, holder, action, event)) {
            return;
        }
        if (action.startsWith(ServerWorldConfigMenuGUI.ENTRY_ACTION_PREFIX)
                || action.startsWith(CrateConfigMenuGUI.ENTRY_ACTION_PREFIX)) {
            final String key = action.substring(action.indexOf(':') + 1);
            handleAdvancedEntry(player, holder, key, event);
            return;
        }

        final String key = action.substring(action.indexOf(':') + 1);
        ConfigMenuGUI.Entry entry = ConfigMenuGUI.findEntry(key);
        if (entry == null) {
            entry = BlockRegenConfigMenuGUI.findEntry(key);
        }
        if (entry == null) {
            entry = OperationalConfigMenuGUI.findEntry(key);
        }
        if (entry == null) {
            return;
        }

        if (event.isMiddleClick() || event.getClick() == ClickType.DROP) {
            resetOverride(player, key, entry);
            reopenView(player, holder.getCategory());
            return;
        }

        final Object current = ConfigMenuEntryRenderer.currentValue(entry, configManager);
        final Object newValue;
        switch (entry.type()) {
            case TOGGLE -> newValue = !Boolean.TRUE.equals(current);
            case CYCLE -> {
                final String currentOption = String.valueOf(current).toLowerCase(Locale.ROOT);
                final int index = entry.options().indexOf(currentOption);
                newValue = entry.options().get(
                        (index + 1) % Math.max(1, entry.options().size()));
            }
            default -> {
                final double step = entry.step()
                        * (event.isShiftClick() ? 5.0D : 1.0D)
                        * (event.isRightClick() ? -1.0D : 1.0D);
                final double next = Math.min(entry.max(), Math.max(entry.min(),
                        ConfigMenuEntryRenderer.currentDouble(entry, configManager) + step));
                newValue = entry.type() == ConfigMenuGUI.EntryType.INTEGER
                        ? (Object) (int) Math.round(next) : (Object) next;
            }
        }

        final String validationProblem =
                OperationalConfigPolicy.validate(key, newValue, configManager);
        if (validationProblem != null) {
            rejectInvalidCombination(player, validationProblem);
            reopenView(player, holder.getCategory());
            return;
        }

        applyOverride(player, key, newValue);
        reopenView(player, holder.getCategory());
    }

    private void handleAdvancedEntry(final Player player, final ConfigMenuHolder holder,
                                     final String key, final InventoryClickEvent event) {
        AdvancedConfigEntry entry = ServerWorldConfigMenuGUI.findEntry(key);
        if (entry == null) {
            entry = CrateConfigMenuGUI.findEntry(key, configManager);
        }
        if (entry == null) {
            return;
        }

        if (event.isMiddleClick() || event.getClick() == ClickType.DROP) {
            resetAdvancedOverride(player, entry);
            reopenView(player, holder.getCategory());
            return;
        }
        if (entry.type() == AdvancedConfigEntry.Type.TEXT
                || entry.type() == AdvancedConfigEntry.Type.STRING_LIST) {
            beginAdvancedInput(player, entry, holder.getCategory());
            return;
        }

        final Object current = AdvancedConfigEntryRenderer.currentValue(entry, configManager);
        final Object next;
        switch (entry.type()) {
            case TOGGLE -> next = !Boolean.TRUE.equals(current);
            case CYCLE -> {
                final int index = entry.options().indexOf(String.valueOf(current));
                next = entry.options().get((index + 1) % entry.options().size());
            }
            case NUMBER, INTEGER -> {
                final double delta = entry.step()
                        * (event.isShiftClick() ? 5.0D : 1.0D)
                        * (event.isRightClick() ? -1.0D : 1.0D);
                final double bounded = Math.max(entry.min(), Math.min(entry.max(),
                        AdvancedConfigEntryRenderer.currentDouble(entry, configManager) + delta));
                next = entry.type() == AdvancedConfigEntry.Type.INTEGER
                        ? (Object) (int) Math.round(bounded) : bounded;
            }
            default -> throw new IllegalStateException("Szöveges entry nem kerülhet scalar ágba.");
        }
        final String problem = AdvancedConfigPolicy.validate(entry, next, configManager);
        if (problem != null) {
            rejectInvalidCombination(player, problem);
            reopenView(player, holder.getCategory());
            return;
        }
        applyAdvancedOverride(player, entry.key(), next, entry.label());
        reopenView(player, holder.getCategory());
    }

    private void beginAdvancedInput(final Player player, final AdvancedConfigEntry entry,
                                    final String returnCategory) {
        final Object current = AdvancedConfigEntryRenderer.currentValue(entry, configManager);
        beginInput(player, entry, returnCategory,
                (target, value) -> {
                    final String problem = AdvancedConfigPolicy.validate(entry, value, configManager);
                    if (problem != null) {
                        return problem;
                    }
                    applyAdvancedOverride(target, entry.key(), value, entry.label());
                    return null;
                }, target -> resetAdvancedOverride(target, entry));
        player.sendMessage(messageManager.get("admin.icesmp.config.input-current",
                "&7Jelenlegi érték: &f%s", String.valueOf(current)));
    }

    private boolean handleCrateAction(final Player player, final ConfigMenuHolder holder,
                                      final String action, final InventoryClickEvent event) {
        if (CrateConfigMenuGUI.GLOBAL_ACTION.equals(action)) {
            CrateConfigMenuGUI.openGlobal(player, configManager);
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX)) {
            CrateConfigMenuGUI.openRoot(player, configManager,
                    parseInt(action.substring(CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX.length()), 0));
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.OPEN_ACTION_PREFIX)) {
            CrateConfigMenuGUI.openCrate(player, configManager,
                    action.substring(CrateConfigMenuGUI.OPEN_ACTION_PREFIX.length()));
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARDS_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARDS_ACTION_PREFIX.length()).split(":");
            CrateConfigMenuGUI.openRewards(player, configManager, parts[0],
                    parts.length > 1 ? parseInt(parts[1], 0) : 0);
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_OPEN_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_OPEN_ACTION_PREFIX.length()).split(":");
            CrateConfigMenuGUI.openReward(player, configManager, parts[0], parseInt(parts[1], 0));
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_ADD_ACTION_PREFIX)) {
            final String crateId = action.substring(CrateConfigMenuGUI.REWARD_ADD_ACTION_PREFIX.length());
            final CrateRewardEditor.Mutation mutation = CrateRewardEditor.addItem(configManager, crateId);
            if (!applyRewardMutation(player, crateId, mutation, "Új tárgyjutalom hozzáadva")) {
                reopenView(player, holder.getCategory());
                return true;
            }
            final int index = CrateRewardEditor.rewards(configManager, crateId).size() - 1;
            CrateConfigMenuGUI.openReward(player, configManager, crateId, Math.max(0, index));
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_DELETE_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_DELETE_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0];
            final int index = parseInt(parts[1], -1);
            final CrateRewardEditor.Mutation mutation = CrateRewardEditor.delete(configManager, crateId, index);
            if (applyRewardMutation(player, crateId, mutation, "Reward törölve")) {
                CrateConfigMenuGUI.openRewards(player, configManager, crateId, Math.max(0, index / 45));
            } else {
                reopenView(player, holder.getCategory());
            }
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0];
            final int index = parseInt(parts[1], -1);
            final String field = parts[2];
            final Map<String, Object> reward = CrateRewardEditor.reward(configManager, crateId, index);
            final String type = CrateRewardEditor.type(reward);
            final double current = CrateRewardEditor.numericValue(reward, field, 1.0D);
            final double unit = "weight".equals(field) ? 1.0D
                    : "currency".equals(type) ? 25.0D : 1.0D;
            final double delta = unit * (event.isShiftClick() ? 5.0D : 1.0D)
                    * (event.isRightClick() ? -1.0D : 1.0D);
            final double minimum = "weight".equals(field) || "currency".equals(type)
                    ? Math.nextUp(0.0D) : 1.0D;
            final double maximum = "weight".equals(field) ? CrateRules.MAX_WEIGHT
                    : "currency".equals(type) ? CrateRules.MAX_CURRENCY_REWARD
                    : CrateRules.MAX_REWARD_ITEM_AMOUNT;
            final CrateRewardEditor.Mutation mutation = CrateRewardEditor.setNumber(
                    configManager, crateId, index, field,
                    Math.max(minimum, Math.min(maximum, current + delta)));
            applyRewardMutation(player, crateId, mutation, "Reward számérték frissítve");
            CrateConfigMenuGUI.openReward(player, configManager, crateId, index);
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_CURRENCY_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_CURRENCY_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0];
            final int index = parseInt(parts[1], -1);
            applyRewardMutation(player, crateId,
                    CrateRewardEditor.cycleCurrency(configManager, crateId, index),
                    "Reward valuta frissítve");
            CrateConfigMenuGUI.openReward(player, configManager, crateId, index);
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0];
            final int index = parseInt(parts[1], -1);
            final String field = parts[2];
            beginRewardTextInput(player, holder.getCategory(), crateId, index, field);
            return true;
        }
        return false;
    }

    private void beginRewardTextInput(final Player player, final String returnCategory,
                                      final String crateId, final int index, final String field) {
        final Map<String, Object> reward = CrateRewardEditor.reward(configManager, crateId, index);
        final int maxLength = "command".equals(field) ? CrateRules.MAX_COMMAND_LENGTH
                : "material".equals(field) ? 64 : 512;
        final boolean allowBlank = "description".equals(field);
        final String pattern = "material".equals(field) ? "[A-Za-z0-9_:.-]+" : "";
        final AdvancedConfigEntry entry = AdvancedConfigEntry.text(
                "crate-reward." + crateId + "." + index + "." + field,
                "Reward " + field, maxLength, allowBlank, pattern,
                "A reward strukturált " + field + " mezőjének szerkesztése.");
        beginInput(player, entry, returnCategory,
                (target, value) -> {
                    final String generic = AdvancedConfigPolicy.validate(entry, value, configManager);
                    if (generic != null) {
                        return generic;
                    }
                    final CrateRewardEditor.Mutation mutation = CrateRewardEditor.setText(
                            configManager, crateId, index, field, String.valueOf(value));
                    if (!mutation.successful()) {
                        return mutation.error();
                    }
                    applyRawOverride(target, CrateRewardEditor.path(crateId), mutation.rewards(),
                            "Reward " + field + " frissítve");
                    return null;
                }, null);
        player.sendMessage(messageManager.get("admin.icesmp.config.input-current",
                "&7Jelenlegi érték: &f%s", String.valueOf(reward.getOrDefault(field, ""))));
    }

    private void beginInput(final Player player, final AdvancedConfigEntry entry,
                            final String returnCategory, final InputCommit commit,
                            final Consumer<Player> defaultAction) {
        inputSessions.put(player.getUniqueId(), new InputSession(entry, returnCategory,
                commit, defaultAction, System.currentTimeMillis() + INPUT_TIMEOUT_MILLIS));
        ConfigChatInputGate.open(player.getUniqueId());
        player.closeInventory();
        final String mode = entry.type() == AdvancedConfigEntry.Type.STRING_LIST
                ? "A listaelemeket ';;' jellel válaszd el. " : "";
        player.sendMessage(messageManager.get("admin.icesmp.config.input-start",
                "&b✎ %s&7 szerkesztése. %sÍrd be az új értéket a chatbe.",
                entry.label(), mode));
        player.sendMessage(messageManager.get("admin.icesmp.config.input-controls",
                "&7Vezérlés: &f!cancel &7= mégse, &f!default &7= alapérték, "
                        + "&f!empty &7= üres érték/lista. Időkorlát: 120 mp."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final InputSession session = inputSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        final String raw = PLAIN.serialize(event.message()).strip();
        if (System.currentTimeMillis() > session.expiresAt()) {
            inputSessions.remove(player.getUniqueId(), session);
            ConfigChatInputGate.close(player.getUniqueId());
            schedulePlayer(player, () -> {
                player.sendMessage(messageManager.get("admin.icesmp.config.input-expired",
                        "&cA config-beviteli munkamenet lejárt."));
                reopenView(player, session.returnCategory());
            });
            return;
        }
        if (raw.equalsIgnoreCase("!cancel")) {
            inputSessions.remove(player.getUniqueId(), session);
            ConfigChatInputGate.close(player.getUniqueId());
            schedulePlayer(player, () -> reopenView(player, session.returnCategory()));
            return;
        }
        if (raw.equalsIgnoreCase("!default")) {
            if (session.defaultAction() == null) {
                notifyInputError(player, session,
                        "Ehhez a strukturált mezőhöz nincs külön alaphelyzet parancs.");
                return;
            }
            inputSessions.remove(player.getUniqueId(), session);
            ConfigChatInputGate.close(player.getUniqueId());
            schedulePlayer(player, () -> {
                session.defaultAction().accept(player);
                reopenView(player, session.returnCategory());
            });
            return;
        }

        final Object parsed;
        if (session.entry().type() == AdvancedConfigEntry.Type.STRING_LIST) {
            if (raw.equalsIgnoreCase("!empty")) {
                parsed = List.of();
            } else {
                final LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (final String item : raw.split("\\s*;;\\s*")) {
                    final String value = item.strip();
                    if (!value.isEmpty()) {
                        unique.add(value);
                    }
                }
                parsed = List.copyOf(unique);
            }
        } else {
            parsed = raw.equalsIgnoreCase("!empty") ? "" : raw;
        }

        final String generic = AdvancedConfigPolicy.validate(session.entry(), parsed, configManager);
        if (generic != null) {
            notifyInputError(player, session, generic);
            return;
        }

        inputSessions.remove(player.getUniqueId(), session);
        ConfigChatInputGate.close(player.getUniqueId());
        schedulePlayer(player, () -> {
            final String error = session.commit().commit(player, parsed);
            if (error != null) {
                inputSessions.put(player.getUniqueId(), session);
                ConfigChatInputGate.open(player.getUniqueId());
                player.sendMessage(messageManager.get("admin.icesmp.config.input-invalid",
                        "&c⚠ %s", error));
                player.sendMessage(messageManager.get("admin.icesmp.config.input-retry",
                        "&7A munkamenet aktív maradt; írd be újra, vagy használd a !cancel parancsot."));
                GuiUtil.sound(player, GuiUtil.GuiSound.ERROR);
                return;
            }
            reopenView(player, session.returnCategory());
        });
    }

    private void notifyInputError(final Player player, final InputSession session,
                                  final String error) {
        schedulePlayer(player, () -> {
            player.sendMessage(messageManager.get("admin.icesmp.config.input-invalid",
                    "&c⚠ %s", error));
            player.sendMessage(messageManager.get("admin.icesmp.config.input-retry",
                    "&7A munkamenet aktív maradt; írd be újra, vagy használd a !cancel parancsot."));
            GuiUtil.sound(player, GuiUtil.GuiSound.ERROR);
        });
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        inputSessions.remove(event.getPlayer().getUniqueId());
        ConfigChatInputGate.close(event.getPlayer().getUniqueId());
    }

    private boolean applyRewardMutation(final Player player, final String crateId,
                                        final CrateRewardEditor.Mutation mutation,
                                        final String label) {
        if (!mutation.successful()) {
            rejectInvalidCombination(player, mutation.error());
            return false;
        }
        applyRawOverride(player, CrateRewardEditor.path(crateId), mutation.rewards(), label);
        return true;
    }

    private void resetOverride(final Player player, final String key,
                               final ConfigMenuGUI.Entry entry) {
        final Object fallback = ConfigMenuEntryRenderer.defaultValue(entry, configManager);
        final String validationProblem =
                OperationalConfigPolicy.validate(key, fallback, configManager);
        if (validationProblem != null) {
            rejectInvalidCombination(player, validationProblem);
            return;
        }

        final boolean changed = configManager.resetOverride(key);
        afterConfigMutation(key);
        final String resolved = ConfigMenuEntryRenderer.formatCurrent(entry, configManager);
        sendResetResult(player, key, resolved, changed);
    }

    private void resetAdvancedOverride(final Player player, final AdvancedConfigEntry entry) {
        final Object fallback = AdvancedConfigEntryRenderer.defaultValue(entry, configManager);
        final String problem = AdvancedConfigPolicy.validate(entry, fallback, configManager);
        if (problem != null) {
            rejectInvalidCombination(player, problem);
            return;
        }
        final boolean changed = configManager.resetOverride(entry.key());
        afterConfigMutation(entry.key());
        sendResetResult(player, entry.key(),
                AdvancedConfigEntryRenderer.formatCurrent(entry, configManager), changed);
    }

    private void sendResetResult(final Player player, final String key,
                                 final String resolved, final boolean changed) {
        if (changed) {
            player.sendMessage(messageManager.get("admin.icesmp.config.reset-success",
                    "&a↺ &6%s &7visszaállítva az alapértékre: &f%s &7(azonnal él)",
                    key, resolved));
            GuiUtil.sound(player, GuiUtil.GuiSound.SUCCESS);
        } else {
            player.sendMessage(messageManager.get("admin.icesmp.config.reset-not-overridden",
                    "&7↺ &6%s &7már az alapkonfigurációt használja: &f%s", key, resolved));
            GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
        }
    }

    private void rejectInvalidCombination(final Player player, final String problem) {
        player.sendMessage(messageManager.get("admin.icesmp.config.invalid-combination",
                "&c⚠ %s", problem));
        GuiUtil.sound(player, GuiUtil.GuiSound.ERROR);
    }

    private void applyOverride(final Player player, final String key, final Object value) {
        applyRawOverride(player, key, value, key);
    }

    private void applyAdvancedOverride(final Player player, final String key,
                                       final Object value, final String label) {
        applyRawOverride(player, key, value, label);
    }

    private void applyRawOverride(final Player player, final String key,
                                  final Object value, final String label) {
        configManager.applyOverride(key, value);
        afterConfigMutation(key);
        player.sendMessage(messageManager.get("admin.icesmp.config.set-success-short",
                "&a⚙ &6%s &7= &f%s &7(azonnal él)", label, compactValue(value)));
        GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
    }

    private void afterConfigMutation(final String key) {
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        applyLiveHooks(key);
    }

    private void applyLiveHooks(final String key) {
        final Consumer<String> hook = configChangeHook;
        if (hook != null) {
            hook.accept(key);
        }
        if (key.startsWith("spell-vfx.")) {
            hu.taliann.icesmp.utils.SpellVfx.configure(
                    configManager.getBoolean("spell-vfx.enabled", true),
                    configManager.getInt("spell-vfx.max-points", 48));
        }
        ConfigRuntimeReloadBridge.apply(plugin, configManager, key);
        AdvancedConfigRuntimeBridge.apply(plugin, configManager, key);
    }

    private void openBack(final Player player, final String category) {
        if (CrateConfigMenuGUI.isCrateCategory(category)) {
            CrateConfigMenuGUI.openBack(player, configManager, category);
        } else if (ServerWorldConfigMenuGUI.CATEGORY_ID.equals(category)) {
            ConfigMenuRootGUI.openRoot(player);
        } else if (OperationalConfigMenuGUI.isOperationalCategory(category)) {
            OperationalConfigMenuGUI.openRoot(player);
        } else {
            ConfigMenuRootGUI.openRoot(player);
        }
    }

    private void reopenView(final Player player, final String category) {
        if (category == null) {
            ConfigMenuRootGUI.openRoot(player);
            return;
        }
        if (BlockRegenConfigMenuGUI.CATEGORY_ID.equals(category)) {
            BlockRegenConfigMenuGUI.open(player, configManager);
            return;
        }
        if (ServerWorldConfigMenuGUI.CATEGORY_ID.equals(category)) {
            ServerWorldConfigMenuGUI.open(player, configManager);
            return;
        }
        if (CrateConfigMenuGUI.isCrateCategory(category)) {
            CrateConfigMenuGUI.reopen(player, configManager, category);
            return;
        }
        if (OperationalConfigMenuGUI.isOperationalCategory(category)) {
            OperationalConfigMenuGUI.openCategory(player,
                    OperationalConfigMenuGUI.categoryIdFromHolder(category), configManager);
            return;
        }
        ConfigMenuGUI.openCategory(player, category, configManager);
    }

    private void schedulePlayer(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.run();
            }
        }, null);
    }

    private static String compactValue(final Object value) {
        final String raw;
        if (value instanceof List<?> list) {
            raw = list.isEmpty() ? "[]" : String.join(" | ", list.stream().map(String::valueOf).toList());
        } else {
            raw = String.valueOf(value);
        }
        return raw.length() <= 96 ? raw : raw.substring(0, 95) + "…";
    }

    private static int parseInt(final String raw, final int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (final RuntimeException ignored) {
            return fallback;
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder) {
            event.setCancelled(true);
        }
    }
}
