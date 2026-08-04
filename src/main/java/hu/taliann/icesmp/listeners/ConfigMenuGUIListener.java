package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.BlockRegenConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuHolder;
import hu.taliann.icesmp.gui.ConfigMenuRootGUI;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Az admin config-menü kattintás-kezelője (jog: {@code icesmp.admin.config} — a menü
 * megnyitása és MINDEN kattintás is ehhez kötött). Az érték-írás a meglévő
 * override-mechanizmus: a data-folder config.yml-be kerül (a ConfigManager utolsóként
 * fésüli be), majd reload + validálás. A GUI a szokásos mintát követi: holder-szűrés,
 * minden cancel, drag is tiltva; minden művelet a kattintó játékos saját régió-szálán
 * fut (Folia-safe).
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

    /** A menü megnyitása (a parancs-oldal is ezen át hívja; jog-ellenőrzéssel). */
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

        final Object newValue;
        switch (entry.type()) {
            case TOGGLE -> newValue = !configManager.getBoolean(key, false);
            case CYCLE -> {
                final String current = configManager.getString(key,
                        entry.options().isEmpty() ? "" : entry.options().get(0))
                        .toLowerCase(Locale.ROOT);
                final int index = entry.options().indexOf(current);
                newValue = entry.options().get(
                        (index + 1) % Math.max(1, entry.options().size()));
            }
            default -> {
                final double step = entry.step()
                        * (event.isShiftClick() ? 5.0D : 1.0D)
                        * (event.isRightClick() ? -1.0D : 1.0D);
                final double next = Math.min(entry.max(),
                        Math.max(entry.min(), configManager.getDouble(key, 0.0D) + step));
                newValue = entry.type() == ConfigMenuGUI.EntryType.INTEGER
                        ? (Object) (int) Math.round(next) : (Object) next;
            }
        }
        applyOverride(player, key, newValue);
        if (BlockRegenConfigMenuGUI.CATEGORY_ID.equals(holder.getCategory())) {
            BlockRegenConfigMenuGUI.open(player, configManager);
        } else {
            ConfigMenuGUI.openCategory(player, holder.getCategory(), configManager);
        }
    }

    /** Az override kiírása + reload — ugyanaz a szerializált út, mint a /icesmp config set. */
    private void applyOverride(final Player player, final String key, final Object value) {
        configManager.applyOverride(key, value);
        messageManager.reload();
        ConfigValidator.validate(configManager, plugin.getLogger());
        final java.util.function.Consumer<String> hook = configChangeHook;
        if (hook != null) {
            hook.accept(key);
        }
        if (BlockRegenConfigMenuGUI.requiresRestart(key)) {
            player.sendMessage(messageManager.get(
                    "admin.icesmp.config.set-success-restart",
                    "&e⚙ &6%s &7= &f%s &7(a szerver újraindítása után lép életbe)",
                    key, String.valueOf(value)));
        } else {
            player.sendMessage(messageManager.get(
                    "admin.icesmp.config.set-success-short",
                    "&a⚙ &6%s &7= &f%s &7(azonnal él)",
                    key, String.valueOf(value)));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder) {
            event.setCancelled(true);
        }
    }
}
