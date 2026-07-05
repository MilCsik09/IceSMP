package hu.taliann.icesmp.integration;

import hu.taliann.icesmp.managers.QuestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Reflective FancyNpcs bridge for TALK_TO_NPC quest objectives: subscribes to
 * FancyNpcs' NpcInteractEvent without any compile-time dependency (mirroring
 * the DruidDisguise / PlaceholderAPI soft-dep pattern), and forwards the
 * interacting player + the NPC's internal name to the QuestManager. FancyNpcs
 * fires the event on the player's own region thread, so the quest-progress
 * PDC write is Folia-safe without a scheduler hop.
 */
public final class FancyNpcsQuestBridge {

    private FancyNpcsQuestBridge() {
    }

    /**
     * Registers the interact listener. Callers guard with
     * {@code isPluginEnabled("FancyNpcs")} and catch every throwable, so a
     * missing or incompatible FancyNpcs version degrades to a log line.
     *
     * @param plugin the IceSMP plugin
     * @param questManager the quest manager to progress
     * @throws ReflectiveOperationException if the FancyNpcs API is not on the classpath
     */
    @SuppressWarnings("unchecked")
    public static void register(final JavaPlugin plugin, final QuestManager questManager)
            throws ReflectiveOperationException {
        final Class<? extends Event> eventClass =
                (Class<? extends Event>) Class.forName("de.oliver.fancynpcs.api.events.NpcInteractEvent");
        final Method getPlayer = eventClass.getMethod("getPlayer");
        final Method getNpc = eventClass.getMethod("getNpc");

        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                new Listener() {
                },
                EventPriority.NORMAL,
                (listener, event) -> {
                    if (!eventClass.isInstance(event)) {
                        return;
                    }
                    try {
                        final Object playerObject = getPlayer.invoke(event);
                        final Object npc = getNpc.invoke(event);
                        if (!(playerObject instanceof Player player) || npc == null) {
                            return;
                        }
                        final Object data = npc.getClass().getMethod("getData").invoke(npc);
                        if (data == null) {
                            return;
                        }
                        final Object name = data.getClass().getMethod("getName").invoke(data);
                        if (name instanceof String npcName) {
                            questManager.handleNpcInteract(player, npcName);
                        }
                    } catch (final ReflectiveOperationException exception) {
                        plugin.getLogger().warning("FancyNpcs quest-bridge hiba: " + exception.getMessage());
                    }
                },
                plugin
        );
    }
}
