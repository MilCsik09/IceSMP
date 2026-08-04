package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.core.ConfigRuntimeReloadBridge;
import hu.taliann.icesmp.gui.BlockRegenConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuEntryRenderer;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuHolder;
import hu.taliann.icesmp.gui.ConfigMenuRootGUI;
import hu.taliann.icesmp.gui.GuiUtil;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Az admin config-menü kattintás-kezelője. Minden írás a config.yml override-rétegbe
 * kerül; a görgőkattintás (és survival-kompatibilis tartalékban a Q) ezt a konkrét
 * override-ot törli, ezért a subsystem YAML aktuális alapértéke lép vissza.
 */
public final class ConfigMenuGUIListener implements Listener {

    public static final String PERMISSION = "icesmp.admin.config";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private volatile java.util.function.Consumer<String> configChangeHook;

    public ConfigMenuGUIListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void setConfigChangeHook(final java.util.function.Consumer<String> configChangeHook) {
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
            ConfigMenuRootGUI.openRoot(player);
            return;
        }
        if (BlockRegenConfigMenuGUI.ROOT_ACTION.equals(action)) {
            BlockRegenConfigMenuGUI.open(player, configManager);
            return;
        }
        if (action.startsWith("CAT:")) {
            ConfigMenuGUI.openCategory(player, action.substring(4), configManager);
            return;
        }

        final String key = action.substring(action.indexOf(':') + 1);
        ConfigMenuGUI.Entry entry = ConfigMenuGUI.findEntry(key);
        if (entry == null) {
            entry = BlockRegenConfigMenuGUI.findEntry(key);
        }
        if (entry == null) {
            return;
        }

        if (event.isMiddleClick() || event.getClick() == ClickType.DROP) {
            resetOverride(player, key, entry);
            reopen(player, holder);
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
        applyOverride(player, key, newValue);
        reopen(player, holder);
    }

    private void resetOverride(final Player player, final String key,
                               final ConfigMenuGUI.Entry entry) {
        final boolean changed = configManager.resetOverride(key);
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        applyLiveHooks(key);
        final String resolved = ConfigMenuEntryRenderer.formatCurrent(entry, configManager);
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

    private void applyOverride(final Player player, final String key, final Object value) {
        configManager.applyOverride(key, value);
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        applyLiveHooks(key);
        player.sendMessage(messageManager.get("admin.icesmp.config.set-success-short",
                "&a⚙ &6%s &7= &f%s &7(azonnal él)", key, String.valueOf(value)));
        GuiUtil.sound(player, GuiUtil.GuiSound.CLICK);
    }

    private void applyLiveHooks(final String key) {
        final java.util.function.Consumer<String> hook = configChangeHook;
        if (hook != null) {
            hook.accept(key);
        }
        if (key.startsWith("spell-vfx.")) {
            hu.taliann.icesmp.utils.SpellVfx.configure(
                    configManager.getBoolean("spell-vfx.enabled", true),
                    configManager.getInt("spell-vfx.max-points", 48));
        }
        ConfigRuntimeReloadBridge.apply(plugin, configManager, key);
    }

    private void reopen(final Player player, final ConfigMenuHolder holder) {
        if (BlockRegenConfigMenuGUI.CATEGORY_ID.equals(holder.getCategory())) {
            BlockRegenConfigMenuGUI.open(player, configManager);
        } else {
            ConfigMenuGUI.openCategory(player, holder.getCategory(), configManager);
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder) {
            event.setCancelled(true);
        }
    }
}
