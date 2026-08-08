package hu.taliann.icesmp.integration;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.NpcBindingManager;
import hu.taliann.icesmp.managers.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reflective FancyNpcs bridge — no compile-time dependency (mirroring the
 * DruidDisguise / PlaceholderAPI soft-dep pattern). Three duties:
 *
 * <ul>
 *   <li><b>TALK_TO_NPC objectives:</b> forwards NPC interactions to the
 *       QuestManager (the event fires on the player's own region thread, so
 *       the PDC write is Folia-safe without a hop).</li>
 *   <li><b>Quest-giver NPCs:</b> after the talk objectives ran, the NPC hands
 *       out its first acceptable {@code giver-npc} quest — unless an explicit
 *       {@code /npcbind} binding exists for it, in which case the binding wins.</li>
 *   <li><b>Per-player markers:</b> {@link #tickMarkers()} shows a particle
 *       aura above quest NPCs — gold for an available quest, green for an
 *       active talk objective.</li>
 * </ul>
 *
 * <p>Every FancyNpcs method is resolved from the public API classes, never
 * from runtime implementation classes.</p>
 */
public final class FancyNpcsQuestBridge {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final NpcBindingManager npcBindingManager;
    private final Object npcManager;
    private final Method getNpcByName;
    private final Method getAllNpcs;
    private final Method npcGetData;
    private final Method dataGetName;
    private final Method dataGetLocation;
    private java.util.function.BiConsumer<Player, String> interactHook;
    private Consumer<Player> bankOpenHook;
    private Consumer<Player> factionMenuHook;

    public void setInteractHook(final java.util.function.BiConsumer<Player, String> hook) {
        this.interactHook = hook;
    }

    public void setBankOpenHook(final Consumer<Player> hook) {
        this.bankOpenHook = hook;
    }

    public void setFactionMenuHook(final Consumer<Player> hook) {
        this.factionMenuHook = hook;
    }

    private FancyNpcsQuestBridge(final JavaPlugin plugin, final ConfigManager configManager,
                                 final QuestManager questManager,
                                 final NpcBindingManager npcBindingManager) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questManager = questManager;
        this.npcBindingManager = npcBindingManager;

        final Class<?> apiClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
        final Object api = apiClass.getMethod("get").invoke(null);
        final Method getNpcManager = apiClass.getMethod("getNpcManager");
        this.npcManager = getNpcManager.invoke(api);
        this.getNpcByName = getNpcManager.getReturnType().getMethod("getNpc", String.class);
        this.getAllNpcs = getNpcManager.getReturnType().getMethod("getAllNpcs");
        final Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc");
        this.npcGetData = npcClass.getMethod("getData");
        final Class<?> dataClass = npcGetData.getReturnType();
        this.dataGetName = dataClass.getMethod("getName");
        this.dataGetLocation = dataClass.getMethod("getLocation");
    }

    public static FancyNpcsQuestBridge register(final JavaPlugin plugin, final ConfigManager configManager,
                                                final QuestManager questManager,
                                                final NpcBindingManager npcBindingManager) throws ReflectiveOperationException {
        final FancyNpcsQuestBridge bridge = new FancyNpcsQuestBridge(
                plugin, configManager, questManager, npcBindingManager);
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
                            // A híd CSAK adapter: hitelesített forrás-kontextust szállít, a
                            // felvétel/leadás/haladás jogosultságát a QuestManager központi
                            // authority-útvonala dönti el. Az NPC-hez kötött QUEST binding is
                            // csak UI-mutató — a quest saját start-policyje (start.npc) számít.
                            // Nem-quest bindingnél a leadás/haladás akkor is fut, csak az
                            // új-quest kínálat marad el (a binding az elsődleges funkció).
                            final NpcBindingManager.Binding binding =
                                    npcBindingManager == null ? null : npcBindingManager.get(npcName);
                            final boolean questSurface = binding == null
                                    || "QUEST".equals(binding.type().name());
                            questManager.handleAuthorizedNpcInteract(player, npcName, questSurface);
                            if (binding == null) {
                                if (interactHook != null) {
                                    interactHook.accept(player, npcName);
                                }
                            } else {
                                switch (binding.type()) {
                                    case QUEST -> {
                                    }
                                    case SHOP -> {
                                        if (interactHook != null) {
                                            interactHook.accept(player, binding.value());
                                        }
                                    }
                                    case BANK, EXCHANGE -> {
                                        if (bankOpenHook != null) {
                                            bankOpenHook.accept(player);
                                        }
                                    }
                                    case FACTION -> {
                                        if (factionMenuHook != null) {
                                            factionMenuHook.accept(player);
                                        }
                                    }
                                    case COMMAND -> player.performCommand(binding.value());
                                }
                            }
                        }
                    } catch (final Throwable exception) {
                        plugin.getLogger().warning("FancyNpcs quest-bridge hiba: " + exception.getMessage());
                    }
                },
                plugin
        );
    }

    public record MissingQuestNpc(
            String expectedName,
            String caseInsensitiveMatch,
            List<String> references
    ) {
        public MissingQuestNpc {
            references = List.copyOf(references);
        }
    }

    public record QuestNpcValidationReport(
            int requiredCount,
            int availableCount,
            List<MissingQuestNpc> missing,
            List<String> lookupErrors
    ) {
        public QuestNpcValidationReport {
            missing = List.copyOf(missing);
            lookupErrors = List.copyOf(lookupErrors);
        }

        public boolean healthy() {
            return missing.isEmpty() && lookupErrors.isEmpty();
        }
    }

    /**
     * Startup/deployment validation for exact FancyNpcs internal names. It reports case-only
     * mismatches and every packaged quest config path that references the missing name, but never
     * invents a world or coordinate. The existing delayed core callback invokes this after
     * FancyNpcs has loaded its snapshot.
     */
    public QuestNpcValidationReport validateNpcs(final Set<String> questNpcNames) {
        final Map<String, String> availableNames = new LinkedHashMap<>();
        final List<String> lookupErrors = new ArrayList<>();
        try {
            final Object allNpcs = getAllNpcs.invoke(npcManager);
            if (allNpcs instanceof Collection<?> collection) {
                for (final Object npc : collection) {
                    if (npc == null) {
                        continue;
                    }
                    final Object data = npcGetData.invoke(npc);
                    if (data != null && dataGetName.invoke(data) instanceof String name && !name.isBlank()) {
                        availableNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                    }
                }
            } else {
                lookupErrors.add("FancyNpcs getAllNpcs() nem Collection eredményt adott.");
            }
        } catch (final ReflectiveOperationException exception) {
            lookupErrors.add("FancyNpcs NPC-lista nem olvasható: " + safeMessage(exception));
        }

        final List<MissingQuestNpc> missing = new ArrayList<>();
        for (final String expected : questNpcNames) {
            try {
                final Object resolved = getNpcByName.invoke(npcManager, expected);
                final Object data = resolved == null ? null : npcGetData.invoke(resolved);
                final String resolvedName = data != null && dataGetName.invoke(data) instanceof String name
                        ? name
                        : null;
                if (expected.equals(resolvedName)) {
                    continue;
                }
                final String enumeratedMatch = availableNames.get(expected.toLowerCase(Locale.ROOT));
                final String caseMatch = resolvedName != null && expected.equalsIgnoreCase(resolvedName)
                        ? resolvedName
                        : enumeratedMatch;
                missing.add(new MissingQuestNpc(
                        expected,
                        expected.equals(caseMatch) ? null : caseMatch,
                        referencesFor(expected)
                ));
            } catch (final ReflectiveOperationException exception) {
                lookupErrors.add(expected + ": " + safeMessage(exception));
            }
        }

        final QuestNpcValidationReport report = new QuestNpcValidationReport(
                questNpcNames.size(), availableNames.size(), missing, lookupErrors);
        logValidationReport(report);
        return report;
    }

    private List<String> referencesFor(final String expectedName) {
        final ConfigurationSection root = configManager.getConfiguration() == null
                ? null
                : configManager.getConfiguration().getConfigurationSection("quests");
        if (root == null) {
            return List.of("runtime-name-only");
        }
        final List<String> references = new ArrayList<>();
        for (final String questId : root.getKeys(false)) {
            final ConfigurationSection quest = root.getConfigurationSection(questId);
            if (quest == null) {
                continue;
            }
            for (final Map.Entry<String, Object> entry : quest.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof String value) || !expectedName.equals(value)) {
                    continue;
                }
                final String path = entry.getKey();
                if (path.equals("giver-npc") || path.endsWith(".npc")) {
                    references.add(questId + "@quests." + questId + "." + path);
                }
            }
        }
        return references.isEmpty() ? List.of("runtime-name-only") : List.copyOf(references);
    }

    private void logValidationReport(final QuestNpcValidationReport report) {
        if (report.healthy()) {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Quest-NPC ellenőrzés: mind a(z) "
                    + report.requiredCount() + " kötelező NPC pontos belső névvel elérhető.");
            return;
        }
        plugin.getLogger().warning("Quest-NPC ellenőrzés: required=" + report.requiredCount()
                + ", available=" + report.availableCount()
                + ", missingOrMismatched=" + report.missing().size()
                + ", lookupErrors=" + report.lookupErrors().size() + ".");
        for (final MissingQuestNpc issue : report.missing()) {
            final String match = issue.caseInsensitiveMatch() == null
                    ? ""
                    : "; caseInsensitiveMatch=" + issue.caseInsensitiveMatch();
            plugin.getLogger().warning("Quest-NPC hiányzik vagy rossz a belső neve: expected="
                    + issue.expectedName() + match + "; references="
                    + String.join(",", issue.references()));
        }
        for (final String error : report.lookupErrors()) {
            plugin.getLogger().warning("Quest-NPC lookup hiba: " + error);
        }
        plugin.getLogger().warning("A koordináta és világ nem következtethető biztonságosan. "
                + "Hozd létre vagy importáld a szükséges NPC-ket pontos belső névvel. "
                + "Átmeneti áthidalásra az admin /quest talk parancs használható.");
    }

    private static String safeMessage(final ReflectiveOperationException exception) {
        final Throwable cause = exception.getCause();
        final String message = cause == null ? exception.getMessage() : cause.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replaceAll("[\r\n]+", " ");
    }

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
                    // A jelentés→szín döntés a központi palettában él (leadható > elérhető
                    // kategória-színnel > folyamatban); a híd csak megjelenít.
                    final var markerState = questManager.getNpcMarkerState(player, entry.getKey());
                    if (markerState != null) {
                        spawnMarker(player, npcLocation,
                                hu.taliann.icesmp.quest.QuestMarkerPalette.color(markerState));
                    }
                }
            }, null);
        }
    }

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
