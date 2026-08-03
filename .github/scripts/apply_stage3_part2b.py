#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

# ---------------- Config GUI listener: stage, save/cancel/reset, stale-write rejection, async I/O ----------------
write('src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java', r'''package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ConfigEditSession;
import hu.taliann.icesmp.gui.ConfigMenuGUI;
import hu.taliann.icesmp.gui.ConfigMenuHolder;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ConfigValidator;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Transactional, Folia-safe admin config GUI. */
public final class ConfigMenuGUIListener implements Listener {
    public static final String PERMISSION = "icesmp.admin.config";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<UUID, ConfigEditSession> sessions = new ConcurrentHashMap<>();
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
        final ConfigEditSession session = captureSession();
        sessions.put(player.getUniqueId(), session);
        ConfigMenuGUI.openRoot(player, session);
    }

    private ConfigEditSession captureSession() {
        final ConfigManager.ConfigSnapshot snapshot = configManager.snapshot();
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, Object> defaults = new LinkedHashMap<>();
        for (final ConfigMenuGUI.Entry entry : ConfigMenuGUI.allEntries()) {
            values.put(entry.key(), snapshot.configuration() == null ? null : snapshot.configuration().get(entry.key()));
            defaults.put(entry.key(), snapshot.baseValue(entry.key()));
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
                sessions.remove(player.getUniqueId(), session);
                player.closeInventory();
                player.sendMessage(messageManager.get("admin.icesmp.config.cancelled", "&7Config módosítások elvetve."));
                return;
            }
            case "SAVE" -> {
                save(player, session);
                return;
            }
            case "BACK" -> {
                ConfigMenuGUI.openRoot(player, session);
                return;
            }
            default -> { }
        }
        if (action.startsWith("CAT:")) {
            ConfigMenuGUI.openCategory(player, action.substring(4), configManager, session);
            return;
        }

        final String key = action.substring(action.indexOf(':') + 1);
        final ConfigMenuGUI.Entry entry = ConfigMenuGUI.findEntry(key);
        if (entry == null) return;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
            session.reset(key);
        } else {
            session.stage(key, nextValue(entry, session.value(key), event.isShiftClick(), event.isRightClick()));
        }
        ConfigMenuGUI.openCategory(player, holder.getCategory(), configManager, session);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
    }

    private static Object nextValue(final ConfigMenuGUI.Entry entry, final Object currentValue,
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
                final double current = currentValue instanceof Number number ? number.doubleValue() : 0.0D;
                final double step = entry.step() * (shift ? 5.0D : 1.0D) * (rightClick ? -1.0D : 1.0D);
                final double next = Math.min(entry.max(), Math.max(entry.min(), current + step));
                yield entry.type() == ConfigMenuGUI.EntryType.INTEGER ? (int) Math.round(next) : next;
            }
        };
    }

    private void save(final Player player, final ConfigEditSession session) {
        if (!session.dirty()) {
            sessions.remove(player.getUniqueId(), session);
            player.closeInventory();
            player.sendMessage(messageManager.get("admin.icesmp.config.no-changes", "&7Nincs mentendő config módosítás."));
            return;
        }
        final Map<String, Object> changes = session.pendingChanges();
        player.closeInventory();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            final ConfigManager.BatchApplyResult result = configManager.applyOverridesIfUnchanged(
                    session.expectedGeneration(), session.expectedFingerprint(), changes);
            plugin.getServer().getGlobalRegionScheduler().run(plugin, globalTask -> {
                if (result == ConfigManager.BatchApplyResult.APPLIED) {
                    messageManager.reload();
                    ConfigValidator.validate(configManager, plugin.getLogger());
                    final java.util.function.Consumer<String> hook = configChangeHook;
                    if (hook != null) changes.keySet().forEach(hook);
                }
                player.getScheduler().run(plugin, playerTask -> finishSave(player, session, result, changes),
                        () -> sessions.remove(player.getUniqueId(), session));
            });
        });
    }

    private void finishSave(final Player player, final ConfigEditSession session,
                            final ConfigManager.BatchApplyResult result, final Map<String, Object> changes) {
        sessions.remove(player.getUniqueId(), session);
        switch (result) {
            case APPLIED -> player.sendMessage(messageManager.get("admin.icesmp.config.batch-success",
                    "&a⚙ &f%s &akulcs mentve egy tranzakcióban.", changes.size()));
            case NO_CHANGES -> player.sendMessage(messageManager.get("admin.icesmp.config.no-changes",
                    "&7Nincs ténylegesen megváltozott config érték."));
            case STALE -> player.sendMessage(messageManager.get("admin.icesmp.config.stale",
                    "&cA config közben megváltozott (másik admin vagy fájlszerkesztés). Nyisd meg újra; semmi nem lett felülírva."));
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfigMenuHolder) || !(event.getPlayer() instanceof Player player)) return;
        final UUID id = player.getUniqueId();
        player.getScheduler().runDelayed(plugin, task -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ConfigMenuHolder)) {
                sessions.remove(id);
            }
        }, null, 1L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
''')

print('stage3 part 2b applied')
