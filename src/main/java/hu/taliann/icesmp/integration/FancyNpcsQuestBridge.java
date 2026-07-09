package hu.taliann.icesmp.integration;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reflective FancyNpcs bridge — no compile-time dependency (mirroring the
 * DruidDisguise / PlaceholderAPI soft-dep pattern). Three duties:
 *
 * <ul>
 *   <li><b>TALK_TO_NPC objectives:</b> forwards NPC interactions to the
 *       QuestManager (the event fires on the player's own region thread, so
 *       the PDC write is Folia-safe without a hop).</li>
 *   <li><b>Quest-giver NPCs:</b> after the talk objectives ran, the NPC hands
 *       out its first acceptable {@code giver-npc} quest.</li>
 *   <li><b>Per-player markers:</b> {@link #tickMarkers()} shows a particle
 *       aura above quest NPCs — gold for "has a quest for YOU", green for
 *       "your active quest wants you to talk to them". Particles are sent
 *       with {@code player.spawnParticle}, so ONLY the eligible player sees
 *       them.</li>
 * </ul>
 *
 * <p>Every FancyNpcs method is resolved from the public API classes
 * ({@code FancyNpcsPlugin}, {@code NpcManager}, {@code Npc}, {@code NpcData}),
 * never from runtime impl classes — reflection on non-public impl classes
 * would fail with IllegalAccessException.</p>
 */
public final class FancyNpcsQuestBridge {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final Object npcManager;
    private final Method getNpcByName;
    private final Method npcGetData;
    private final Method dataGetName;
    private final Method dataGetLocation;
    private java.util.function.BiConsumer<Player, String> interactHook;

    /** Registers an extra (player, npcName) consumer fired on every NPC interaction (e.g. shops). */
    public void setInteractHook(final java.util.function.BiConsumer<Player, String> hook) {
        this.interactHook = hook;
    }

    private FancyNpcsQuestBridge(final JavaPlugin plugin, final ConfigManager configManager,
                                 final QuestManager questManager) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questManager = questManager;

        // Every method is resolved from public API types (declared return types where
        // possible), never from runtime impl classes or hardcoded impl-package names.
        final Class<?> apiClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
        final Object api = apiClass.getMethod("get").invoke(null);
        final Method getNpcManager = apiClass.getMethod("getNpcManager");
        this.npcManager = getNpcManager.invoke(api);
        this.getNpcByName = getNpcManager.getReturnType().getMethod("getNpc", String.class);
        final Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc");
        this.npcGetData = npcClass.getMethod("getData");
        final Class<?> dataClass = npcGetData.getReturnType();
        this.dataGetName = dataClass.getMethod("getName");
        this.dataGetLocation = dataClass.getMethod("getLocation");
    }

    /**
     * Creates the bridge and registers the interact listener. Callers guard
     * with {@code isPluginEnabled("FancyNpcs")} and catch every throwable, so
     * a missing or incompatible FancyNpcs version degrades to a log line.
     *
     * @param plugin the IceSMP plugin
     * @param configManager the config manager (marker settings)
     * @param questManager the quest manager to progress
     * @return the registered bridge
     * @throws ReflectiveOperationException if the FancyNpcs API is not on the classpath
     */
    public static FancyNpcsQuestBridge register(final JavaPlugin plugin, final ConfigManager configManager,
                                                final QuestManager questManager) throws ReflectiveOperationException {
        final FancyNpcsQuestBridge bridge = new FancyNpcsQuestBridge(plugin, configManager, questManager);
        bridge.registerInteractListener();
        return bridge;
    }

    @SuppressWarnings("unchecked")
    private void registerInteractListener() throws ReflectiveOperationException {
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
                        final Object data = npcGetData.invoke(npc);
                        if (data == null) {
                            return;
                        }
                        if (dataGetName.invoke(data) instanceof String npcName) {
                            // Talk objectives first, then the NPC hands out its next quest —
                            // so a master completes "talk to me" and gives the trial in one click.
                            questManager.handleNpcInteract(player, npcName);
                            questManager.acceptFromNpc(player, npcName);
                            // Extra interaction consumers (e.g. faction shops) run last; the event
                            // is already on the player's region thread, so this is Folia-safe.
                            if (interactHook != null) {
                                interactHook.accept(player, npcName);
                            }
                        }
                    } catch (final ReflectiveOperationException exception) {
                        plugin.getLogger().warning("FancyNpcs quest-bridge hiba: " + exception.getMessage());
                    }
                },
                plugin
        );
    }

    /**
     * Resolves a FancyNpcs NPC's stored location by internal name.
     *
     * @param name the NPC's internal name
     * @return a defensive copy of the location, or null if unknown
     */
    public Location locateNpc(final String name) {
        try {
            final Object npc = getNpcByName.invoke(npcManager, name);
            if (npc == null) {
                return null;
            }
            final Object data = npcGetData.invoke(npc);
            if (data == null) {
                return null;
            }
            return dataGetLocation.invoke(data) instanceof Location location ? location.clone() : null;
        } catch (final ReflectiveOperationException exception) {
            return null;
        }
    }

    /**
     * Per-player quest markers above NPCs. Runs on the global scheduler: NPC
     * locations are FancyNpcs config data (not entity state), then each
     * player's eligibility (PDC reads) and the particle packet run on that
     * player's own region thread (Folia rule).
     */
    public void tickMarkers() {
        if (!configManager.getBoolean("quest-npc-markers.enabled", true)) {
            return;
        }

        final Set<String> npcNames = questManager.getQuestNpcNames();
        if (npcNames.isEmpty()) {
            return;
        }

        final Map<String, Location> npcLocations = new HashMap<>();
        for (final String name : npcNames) {
            final Location location = locateNpc(name);
            if (location != null && location.getWorld() != null) {
                npcLocations.put(name, location);
            }
        }
        if (npcLocations.isEmpty()) {
            return;
        }

        final double range = Math.max(8.0D, configManager.getDouble("quest-npc-markers.range", 48.0D));
        final double rangeSquared = range * range;

        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                for (final Map.Entry<String, Location> entry : npcLocations.entrySet()) {
                    final Location npcLocation = entry.getValue();
                    if (!player.getWorld().equals(npcLocation.getWorld())
                            || player.getLocation().distanceSquared(npcLocation) > rangeSquared) {
                        continue;
                    }

                    // Gold aura: this NPC has a quest THIS player can take right now.
                    // Green aura: an active quest of THIS player wants a word with the NPC.
                    if (questManager.hasAcceptableQuestFrom(player, entry.getKey())) {
                        spawnMarker(player, npcLocation, Color.fromRGB(255, 170, 0));
                    } else if (questManager.hasTalkObjectiveAt(player, entry.getKey())) {
                        spawnMarker(player, npcLocation, Color.fromRGB(80, 255, 80));
                    }
                }
            }, null);
        }
    }

    /** Sends the marker particles to a single player only (client-side packet). */
    private void spawnMarker(final Player player, final Location npcLocation, final Color color) {
        player.spawnParticle(
                Particle.DUST,
                npcLocation.clone().add(0.0D, 2.4D, 0.0D),
                8,
                0.12D, 0.3D, 0.12D,
                0.0D,
                new Particle.DustOptions(color, 1.6F)
        );
    }
}
