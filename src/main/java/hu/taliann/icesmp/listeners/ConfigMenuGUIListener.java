package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.AdvancedConfigRuntimeBridge;
import hu.taliann.icesmp.core.ConfigRuntimeReloadBridge;
import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.gui.AdvancedConfigEntry;
import hu.taliann.icesmp.gui.AdvancedConfigEntryRenderer;
import hu.taliann.icesmp.gui.AdvancedConfigPolicy;
import hu.taliann.icesmp.gui.BlockRegenConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigChatInputGate;
import hu.taliann.icesmp.gui.ConfigEditSession;
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
import hu.taliann.icesmp.gui.TransactionalConfigMenuGUI;
import hu.taliann.icesmp.gui.TransactionalCrateConfigMenuGUI;
import hu.taliann.icesmp.gui.TransactionalOperationalConfigMenuGUI;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Transactional, Folia-safe admin config GUI including text/list and structured crate editors. */
public final class ConfigMenuGUIListener implements Listener {
    public static final String PERMISSION = "icesmp.admin.config";
    private static final long INPUT_TIMEOUT_MILLIS = 120_000L;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @FunctionalInterface
    private interface InputCommit {
        /** @return null on success, otherwise a player-facing error. */
        String commit(Object value);
    }

    private record InputSession(AdvancedConfigEntry entry, String returnCategory,
                                InputCommit commit, Runnable defaultAction, long expiresAt) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, ConfigEditSession> sessions = new ConcurrentHashMap<>();
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
        final ConfigEditSession session = captureSession();
        sessions.put(player.getUniqueId(), session);
        ConfigMenuRootGUI.openRoot(player, session);
    }

    private ConfigEditSession captureSession() {
        final ConfigManager.ConfigSnapshot snapshot = configManager.snapshot();
        final Set<String> keys = new LinkedHashSet<>();
        ConfigMenuGUI.CATEGORIES.values().forEach(category ->
                category.entries().forEach(entry -> keys.add(entry.key())));
        BlockRegenConfigMenuGUI.entries().forEach(entry -> keys.add(entry.key()));
        TransactionalOperationalConfigMenuGUI.entries().forEach(entry -> keys.add(entry.key()));
        ServerWorldConfigMenuGUI.entries().forEach(entry -> keys.add(entry.key()));
        TransactionalCrateConfigMenuGUI.globalEntries().forEach(entry -> keys.add(entry.key()));
        for (final String crateId : CrateConfigMenuGUI.crateIds(configManager)) {
            CrateConfigMenuGUI.entriesFor(crateId).forEach(entry -> keys.add(entry.key()));
            keys.add(CrateRewardEditor.path(crateId));
        }
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, Object> defaults = new LinkedHashMap<>();
        for (final String key : keys) {
            values.put(key, snapshot.configuration() == null ? null : snapshot.configuration().get(key));
            defaults.put(key, snapshot.baseValue(key));
        }
        return new ConfigEditSession(snapshot.generation(), snapshot.sourceFingerprint(), values, defaults);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()
                || !player.hasPermission(PERMISSION)) return;
        final ConfigEditSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        final String action = holder.actionAt(event.getSlot());
        if (action == null) return;

        switch (action) {
            case "CLOSE", "CANCEL" -> {
                discard(player, session);
                return;
            }
            case "SAVE" -> {
                save(player, session);
                return;
            }
            case "BACK" -> {
                openBack(player, holder.getCategory(), session);
                return;
            }
            default -> { }
        }
        if (BlockRegenConfigMenuGUI.ROOT_ACTION.equals(action)) {
            BlockRegenConfigMenuGUI.open(player, configManager, session); return;
        }
        if (OperationalConfigMenuGUI.ROOT_ACTION.equals(action)) {
            TransactionalOperationalConfigMenuGUI.openRoot(player, session); return;
        }
        if (ServerWorldConfigMenuGUI.ROOT_ACTION.equals(action)) {
            ServerWorldConfigMenuGUI.open(player, configManager, session); return;
        }
        if (CrateConfigMenuGUI.ROOT_ACTION.equals(action)) {
            TransactionalCrateConfigMenuGUI.openRoot(player, configManager, session, 0); return;
        }
        if (action.startsWith(OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX)) {
            TransactionalOperationalConfigMenuGUI.openCategory(player,
                    action.substring(OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX.length()),
                    configManager, session); return;
        }
        if (action.startsWith("CAT:")) {
            TransactionalConfigMenuGUI.openCategory(player, action.substring(4), configManager, session); return;
        }
        if (handleCrateAction(player, holder, session, action, event)) return;
        if (action.startsWith(ServerWorldConfigMenuGUI.ENTRY_ACTION_PREFIX)
                || action.startsWith(CrateConfigMenuGUI.ENTRY_ACTION_PREFIX)) {
            handleAdvancedEntry(player, holder, session,
                    action.substring(action.indexOf(':') + 1), event); return;
        }

        final String key = action.substring(action.indexOf(':') + 1);
        ConfigMenuGUI.Entry entry = ConfigMenuGUI.findEntry(key);
        if (entry == null) entry = BlockRegenConfigMenuGUI.findEntry(key);
        if (entry == null) entry = TransactionalOperationalConfigMenuGUI.findEntry(key);
        if (entry == null) return;
        if (event.isMiddleClick() || event.getClick() == ClickType.DROP) {
            session.reset(key);
        } else {
            final Object next = nextScalar(entry, session.value(key), event.isShiftClick(), event.isRightClick());
            final String problem = OperationalConfigPolicy.validate(key, next, configManager);
            if (problem != null) { reject(player, problem); return; }
            session.stage(key, next);
        }
        reopenView(player, holder.getCategory(), session);
        GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
    }

    private static Object nextScalar(final ConfigMenuGUI.Entry entry, final Object currentValue,
                                     final boolean shift, final boolean rightClick) {
        return switch (entry.type()) {
            case TOGGLE -> !(currentValue instanceof Boolean value && value);
            case CYCLE -> {
                if (entry.options().isEmpty()) yield "";
                final String current = String.valueOf(currentValue).toLowerCase(Locale.ROOT);
                int index = -1;
                for (int i = 0; i < entry.options().size(); i++) {
                    if (entry.options().get(i).toLowerCase(Locale.ROOT).equals(current)) { index = i; break; }
                }
                yield entry.options().get((index + 1) % entry.options().size());
            }
            default -> {
                final double current = currentValue instanceof Number number ? number.doubleValue() : entry.min();
                final double step = entry.step() * (shift ? 5.0D : 1.0D) * (rightClick ? -1.0D : 1.0D);
                final double next = Math.min(entry.max(), Math.max(entry.min(), current + step));
                yield entry.type() == ConfigMenuGUI.EntryType.INTEGER ? (int) Math.round(next) : next;
            }
        };
    }

    private void handleAdvancedEntry(final Player player, final ConfigMenuHolder holder,
                                     final ConfigEditSession session, final String key,
                                     final InventoryClickEvent event) {
        AdvancedConfigEntry entry = ServerWorldConfigMenuGUI.findEntry(key);
        if (entry == null) entry = TransactionalCrateConfigMenuGUI.findEntry(key, configManager);
        if (entry == null) return;
        if (event.isMiddleClick() || event.getClick() == ClickType.DROP) {
            session.reset(key);
            reopenView(player, holder.getCategory(), session);
            return;
        }
        if (entry.type() == AdvancedConfigEntry.Type.TEXT
                || entry.type() == AdvancedConfigEntry.Type.STRING_LIST) {
            beginAdvancedInput(player, session, entry, holder.getCategory());
            return;
        }
        final Object current = session.value(key);
        final Object next = switch (entry.type()) {
            case TOGGLE -> !(current instanceof Boolean value && value);
            case CYCLE -> {
                int index = entry.options().indexOf(String.valueOf(current));
                yield entry.options().get((index + 1) % entry.options().size());
            }
            case NUMBER, INTEGER -> {
                final double number = current instanceof Number value ? value.doubleValue() : entry.min();
                final double delta = entry.step() * (event.isShiftClick() ? 5.0D : 1.0D)
                        * (event.isRightClick() ? -1.0D : 1.0D);
                final double bounded = Math.max(entry.min(), Math.min(entry.max(), number + delta));
                yield entry.type() == AdvancedConfigEntry.Type.INTEGER ? (int) Math.round(bounded) : bounded;
            }
            default -> throw new IllegalStateException("Text input reached scalar branch");
        };
        final String problem = AdvancedConfigPolicy.validate(entry, next, configManager);
        if (problem != null) { reject(player, problem); return; }
        session.stage(key, next);
        reopenView(player, holder.getCategory(), session);
    }

    private void beginAdvancedInput(final Player player, final ConfigEditSession session,
                                    final AdvancedConfigEntry entry, final String returnCategory) {
        beginInput(player, entry, returnCategory,
                value -> {
                    final String problem = AdvancedConfigPolicy.validate(entry, value, configManager);
                    if (problem != null) return problem;
                    session.stage(entry.key(), value);
                    return null;
                }, () -> session.reset(entry.key()));
        player.sendMessage(messageManager.get("admin.icesmp.config.input-current",
                "&7Jelenlegi staged érték: &f%s", String.valueOf(session.value(entry.key()))));
    }

    private boolean handleCrateAction(final Player player, final ConfigMenuHolder holder,
                                      final ConfigEditSession session, final String action,
                                      final InventoryClickEvent event) {
        if (CrateConfigMenuGUI.GLOBAL_ACTION.equals(action)) {
            TransactionalCrateConfigMenuGUI.openGlobal(player, configManager, session); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX)) {
            TransactionalCrateConfigMenuGUI.openRoot(player, configManager, session,
                    parseInt(action.substring(CrateConfigMenuGUI.ROOT_PAGE_ACTION_PREFIX.length()), 0)); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.OPEN_ACTION_PREFIX)) {
            TransactionalCrateConfigMenuGUI.openCrate(player, configManager, session,
                    action.substring(CrateConfigMenuGUI.OPEN_ACTION_PREFIX.length())); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARDS_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARDS_ACTION_PREFIX.length()).split(":");
            TransactionalCrateConfigMenuGUI.openRewards(player, configManager, session, parts[0],
                    parts.length > 1 ? parseInt(parts[1], 0) : 0); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_OPEN_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_OPEN_ACTION_PREFIX.length()).split(":");
            TransactionalCrateConfigMenuGUI.openReward(player, configManager, session, parts[0], parseInt(parts[1], 0)); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_ADD_ACTION_PREFIX)) {
            final String crateId = action.substring(CrateConfigMenuGUI.REWARD_ADD_ACTION_PREFIX.length());
            final CrateRewardEditor.Mutation mutation = CrateRewardEditor.addItem(session.value(CrateRewardEditor.path(crateId)));
            if (!stageRewardMutation(player, session, crateId, mutation)) return true;
            TransactionalCrateConfigMenuGUI.openReward(player, configManager, session, crateId, mutation.rewards().size() - 1); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_DELETE_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_DELETE_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0]; final int index = parseInt(parts[1], -1);
            if (stageRewardMutation(player, session, crateId,
                    CrateRewardEditor.delete(session.value(CrateRewardEditor.path(crateId)), index))) {
                TransactionalCrateConfigMenuGUI.openRewards(player, configManager, session, crateId, Math.max(0, index / 45));
            }
            return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_NUMBER_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0]; final int index = parseInt(parts[1], -1); final String field = parts[2];
            final Object raw = session.value(CrateRewardEditor.path(crateId));
            final Map<String, Object> reward = CrateRewardEditor.reward(raw, index);
            final String type = CrateRewardEditor.type(reward);
            final double current = CrateRewardEditor.numericValue(reward, field, 1.0D);
            final double unit = "weight".equals(field) ? 1.0D : "currency".equals(type) ? 25.0D : 1.0D;
            final double delta = unit * (event.isShiftClick() ? 5.0D : 1.0D) * (event.isRightClick() ? -1.0D : 1.0D);
            final double minimum = "weight".equals(field) || "currency".equals(type) ? 0.01D : 1.0D;
            final double maximum = "weight".equals(field) ? CrateRules.MAX_WEIGHT
                    : "currency".equals(type) ? CrateRules.MAX_CURRENCY_REWARD : CrateRules.MAX_REWARD_ITEM_AMOUNT;
            stageRewardMutation(player, session, crateId,
                    CrateRewardEditor.setNumber(raw, index, field, Math.max(minimum, Math.min(maximum, current + delta))));
            TransactionalCrateConfigMenuGUI.openReward(player, configManager, session, crateId, index); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_CURRENCY_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_CURRENCY_ACTION_PREFIX.length()).split(":");
            final String crateId = parts[0]; final int index = parseInt(parts[1], -1);
            stageRewardMutation(player, session, crateId,
                    CrateRewardEditor.cycleCurrency(session.value(CrateRewardEditor.path(crateId)), index));
            TransactionalCrateConfigMenuGUI.openReward(player, configManager, session, crateId, index); return true;
        }
        if (action.startsWith(CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX)) {
            final String[] parts = action.substring(CrateConfigMenuGUI.REWARD_TEXT_ACTION_PREFIX.length()).split(":");
            beginRewardTextInput(player, session, holder.getCategory(), parts[0], parseInt(parts[1], -1), parts[2]); return true;
        }
        return false;
    }

    private boolean stageRewardMutation(final Player player, final ConfigEditSession session,
                                        final String crateId, final CrateRewardEditor.Mutation mutation) {
        if (!mutation.successful()) { reject(player, mutation.error()); return false; }
        session.stage(CrateRewardEditor.path(crateId), mutation.rewards());
        GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
        return true;
    }

    private void beginRewardTextInput(final Player player, final ConfigEditSession session,
                                      final String returnCategory, final String crateId,
                                      final int index, final String field) {
        final Map<String, Object> reward = CrateRewardEditor.reward(session.value(CrateRewardEditor.path(crateId)), index);
        final int maxLength = "command".equals(field) ? CrateRules.MAX_COMMAND_LENGTH : "material".equals(field) ? 64 : 512;
        final AdvancedConfigEntry entry = AdvancedConfigEntry.text("crate-reward." + crateId + "." + index + "." + field,
                "Reward " + field, maxLength, "description".equals(field),
                "material".equals(field) ? "[A-Za-z0-9_:.-]+" : "",
                "A reward strukturált " + field + " mezőjének staged szerkesztése.");
        beginInput(player, entry, returnCategory,
                value -> {
                    final String generic = AdvancedConfigPolicy.validate(entry, value, configManager);
                    if (generic != null) return generic;
                    final CrateRewardEditor.Mutation mutation = CrateRewardEditor.setText(
                            session.value(CrateRewardEditor.path(crateId)), index, field, String.valueOf(value));
                    if (!mutation.successful()) return mutation.error();
                    session.stage(CrateRewardEditor.path(crateId), mutation.rewards());
                    return null;
                }, null);
        player.sendMessage(messageManager.get("admin.icesmp.config.input-current",
                "&7Jelenlegi staged érték: &f%s", String.valueOf(reward.getOrDefault(field, ""))));
    }

    private void beginInput(final Player player, final AdvancedConfigEntry entry,
                            final String returnCategory, final InputCommit commit,
                            final Runnable defaultAction) {
        inputSessions.put(player.getUniqueId(), new InputSession(entry, returnCategory, commit,
                defaultAction, System.currentTimeMillis() + INPUT_TIMEOUT_MILLIS));
        ConfigChatInputGate.open(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(messageManager.get("admin.icesmp.config.input-start",
                "&b✎ %s&7 staged szerkesztése. %sÍrd be az új értéket a chatbe.", entry.label(),
                entry.type() == AdvancedConfigEntry.Type.STRING_LIST ? "A listaelemeket ';;' jellel válaszd el. " : ""));
        player.sendMessage(messageManager.get("admin.icesmp.config.input-controls",
                "&7Vezérlés: &f!cancel &7= mégse, &f!default &7= staged reset, &f!empty &7= üres érték/lista. Időkorlát: 120 mp."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final InputSession input = inputSessions.get(player.getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        final String raw = PLAIN.serialize(event.message()).strip();
        if (System.currentTimeMillis() > input.expiresAt()) {
            closeInput(player.getUniqueId(), input);
            schedulePlayer(player, () -> { player.sendMessage("§cA config-beviteli munkamenet lejárt."); reopenView(player, input.returnCategory(), sessions.get(player.getUniqueId())); });
            return;
        }
        if (raw.equalsIgnoreCase("!cancel")) {
            closeInput(player.getUniqueId(), input);
            schedulePlayer(player, () -> reopenView(player, input.returnCategory(), sessions.get(player.getUniqueId())));
            return;
        }
        if (raw.equalsIgnoreCase("!default")) {
            if (input.defaultAction() == null) { inputError(player, "Ehhez a strukturált mezőhöz nincs külön reset."); return; }
            input.defaultAction().run(); closeInput(player.getUniqueId(), input);
            schedulePlayer(player, () -> reopenView(player, input.returnCategory(), sessions.get(player.getUniqueId())));
            return;
        }
        final Object parsed;
        if (input.entry().type() == AdvancedConfigEntry.Type.STRING_LIST) {
            if (raw.equalsIgnoreCase("!empty")) parsed = List.of();
            else {
                final LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (final String part : raw.split("\\s*;;\\s*")) if (!part.strip().isEmpty()) unique.add(part.strip());
                parsed = List.copyOf(unique);
            }
        } else parsed = raw.equalsIgnoreCase("!empty") ? "" : raw;
        final String generic = AdvancedConfigPolicy.validate(input.entry(), parsed, configManager);
        if (generic != null) { inputError(player, generic); return; }
        final String error = input.commit().commit(parsed);
        if (error != null) { inputError(player, error); return; }
        closeInput(player.getUniqueId(), input);
        schedulePlayer(player, () -> reopenView(player, input.returnCategory(), sessions.get(player.getUniqueId())));
    }

    private void inputError(final Player player, final String error) {
        schedulePlayer(player, () -> {
            player.sendMessage(messageManager.get("admin.icesmp.config.input-invalid", "&c⚠ %s", error));
            player.sendMessage("§7A staged munkamenet aktív maradt; írd be újra, vagy !cancel.");
            GuiUtil.sound(player, GuiUtil.GuiSound.ERROR);
        });
    }

    private void closeInput(final UUID id, final InputSession session) {
        inputSessions.remove(id, session);
        ConfigChatInputGate.close(id);
    }

    private void save(final Player player, final ConfigEditSession session) {
        if (!session.dirty()) {
            sessions.remove(player.getUniqueId(), session); player.closeInventory();
            player.sendMessage(messageManager.get("admin.icesmp.config.no-changes", "&7Nincs mentendő config módosítás."));
            return;
        }
        final Map<String, Object> changes = session.pendingChanges();
        sessions.remove(player.getUniqueId(), session);
        ConfigChatInputGate.close(player.getUniqueId());
        player.closeInventory();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            final ConfigManager.BatchApplyResult result = configManager.applyOverridesIfUnchanged(
                    session.expectedGeneration(), session.expectedFingerprint(), changes);
            plugin.getServer().getGlobalRegionScheduler().run(plugin, global -> {
                if (result == ConfigManager.BatchApplyResult.APPLIED) applyHooks(changes.keySet());
                player.getScheduler().run(plugin, playerTask -> finishSave(player, result, changes.size()), null);
            });
        });
    }

    private void applyHooks(final Set<String> changedKeys) {
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        for (final String key : changedKeys) {
            final Consumer<String> hook = configChangeHook;
            if (hook != null) hook.accept(key);
            if (key.startsWith("spell-vfx.")) {
                hu.taliann.icesmp.utils.SpellVfx.configure(configManager.getBoolean("spell-vfx.enabled", true),
                        configManager.getInt("spell-vfx.max-points", 48));
            }
            ConfigRuntimeReloadBridge.apply(plugin, configManager, key);
            AdvancedConfigRuntimeBridge.apply(plugin, configManager, key);
        }
    }

    private void finishSave(final Player player, final ConfigManager.BatchApplyResult result, final int count) {
        switch (result) {
            case APPLIED -> player.sendMessage(messageManager.get("admin.icesmp.config.batch-success",
                    "&a⚙ &f%s &akulcs mentve egy tranzakcióban.", count));
            case NO_CHANGES -> player.sendMessage("§7Nincs ténylegesen megváltozott config érték.");
            case STALE -> player.sendMessage("§cA config közben megváltozott. Nyisd meg újra; semmi nem lett felülírva.");
        }
    }

    private void discard(final Player player, final ConfigEditSession session) {
        sessions.remove(player.getUniqueId(), session);
        final InputSession input = inputSessions.remove(player.getUniqueId());
        if (input != null) ConfigChatInputGate.close(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(messageManager.get("admin.icesmp.config.cancelled", "&7Config módosítások elvetve."));
    }

    private void reject(final Player player, final String problem) {
        player.sendMessage(messageManager.get("admin.icesmp.config.invalid-combination", "&c⚠ %s", problem));
        GuiUtil.sound(player, GuiUtil.GuiSound.ERROR);
    }

    private void openBack(final Player player, final String category, final ConfigEditSession session) {
        if (CrateConfigMenuGUI.isCrateCategory(category)) {
            TransactionalCrateConfigMenuGUI.openBack(player, configManager, session, category);
        } else if (ServerWorldConfigMenuGUI.CATEGORY_ID.equals(category)) {
            ConfigMenuRootGUI.openRoot(player, session);
        } else if (OperationalConfigMenuGUI.isOperationalCategory(category)) {
            TransactionalOperationalConfigMenuGUI.openRoot(player, session);
        } else ConfigMenuRootGUI.openRoot(player, session);
    }

    private void reopenView(final Player player, final String category, final ConfigEditSession session) {
        if (session == null) return;
        if (category == null) ConfigMenuRootGUI.openRoot(player, session);
        else if (BlockRegenConfigMenuGUI.CATEGORY_ID.equals(category)) BlockRegenConfigMenuGUI.open(player, configManager, session);
        else if (ServerWorldConfigMenuGUI.CATEGORY_ID.equals(category)) ServerWorldConfigMenuGUI.open(player, configManager, session);
        else if (CrateConfigMenuGUI.isCrateCategory(category)) TransactionalCrateConfigMenuGUI.reopen(player, configManager, session, category);
        else if (OperationalConfigMenuGUI.isOperationalCategory(category)) TransactionalOperationalConfigMenuGUI.openCategory(player,
                OperationalConfigMenuGUI.categoryIdFromHolder(category), configManager, session);
        else TransactionalConfigMenuGUI.openCategory(player, category, configManager, session);
    }

    private void schedulePlayer(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, task -> { if (player.isOnline()) action.run(); }, null);
    }

    private static int parseInt(final String raw, final int fallback) {
        try { return Integer.parseInt(raw); } catch (final RuntimeException ignored) { return fallback; }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfigMenuHolder)
                || !(event.getPlayer() instanceof Player player)) return;
        final UUID id = player.getUniqueId();
        player.getScheduler().runDelayed(plugin, task -> {
            if (!ConfigChatInputGate.isOpen(id)
                    && !(player.getOpenInventory().getTopInventory().getHolder() instanceof ConfigMenuHolder)) {
                sessions.remove(id);
            }
        }, null, 1L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id); inputSessions.remove(id); ConfigChatInputGate.close(id);
    }
}
