package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.QuestBuilderGUI;
import hu.taliann.icesmp.gui.QuestBuilderHolder;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Click handling and chat-input flow for the admin quest builder
 * (/quest admin builder). Field tiles close the inventory and capture the
 * admin's next chat line as the new value; toggles flip in place. The chat
 * capture runs on the async chat thread, so every write and GUI reopen is
 * hopped back onto the player's region thread first (Folia).
 */
public final class QuestBuilderListener implements Listener {

    /** What the captured chat line is for. */
    private enum PromptKind { FIELD, NEW_COUNT, NEW_NAME, ADD_COUNT }

    private record Prompt(PromptKind kind, String questId, String field, String objectiveType, int count) {
    }

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final MessageManager messageManager;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public QuestBuilderListener(final JavaPlugin plugin, final QuestManager questManager,
                                final MessageManager messageManager) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    /**
     * Entry point from /quest admin builder: opens the editor for an existing
     * custom quest, or the new-quest type picker for a fresh id.
     */
    public void openBuilder(final Player player, final String rawId) {
        final String questId = rawId.toLowerCase(Locale.ROOT);
        if (questManager.isCustomQuest(questId)) {
            QuestBuilderGUI.openEditor(player, questId, questManager);
            return;
        }
        if (questManager.getQuestIds().contains(questId)) {
            player.sendMessage(messageManager.get("quest-builder-config-quest",
                    "&cEz configból érkező küldetés — a builder csak admin-készítette (custom) questeket szerkeszt."));
            return;
        }
        if (!questId.matches("[a-z0-9_]+")) {
            player.sendMessage(messageManager.get("quest-admin-bad-id",
                    "&cAz azonosító csak kisbetűt, számot és aláhúzást tartalmazhat."));
            return;
        }
        QuestBuilderGUI.openTypePicker(player, null, questId);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestBuilderHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        final String action = holder.getAction(event.getSlot());
        if (action == null) {
            return;
        }

        if ("CLOSE".equals(action)) {
            player.closeInventory();
            return;
        }

        if (holder.getView() == QuestBuilderHolder.View.TYPE_PICKER && action.startsWith("TYPE:")) {
            handleTypeChosen(player, holder, action.substring("TYPE:".length()));
            return;
        }

        final String questId = holder.getQuestId();
        if (questId == null) {
            return;
        }

        if (action.startsWith("FIELD:")) {
            final String field = action.substring("FIELD:".length());
            prompts.put(player.getUniqueId(), new Prompt(PromptKind.FIELD, questId, field, null, 0));
            player.closeInventory();
            player.sendMessage(messageManager.get("quest-builder-prompt",
                    "&eÍrd be a chatbe az új értéket ehhez: &f%s &7(megszakítás: 'mégse')", field));
            return;
        }

        if (action.startsWith("TOGGLE:")) {
            // TOGGLE:<mező>:<új érték> — a renderelő már az átbillentett értéket kötötte be.
            final String[] parts = action.split(":", 3);
            applyField(player, questId, parts[1], parts[2]);
            QuestBuilderGUI.openEditor(player, questId, questManager);
            return;
        }

        if ("MODE".equals(action)) {
            final var quest = questManager.getQuestSection(questId);
            final String current = quest == null ? "ALL" : quest.getString("objectives-mode", "ALL");
            applyField(player, questId, "objectives-mode", "ALL".equalsIgnoreCase(current) ? "SEQUENCE" : "ALL");
            QuestBuilderGUI.openEditor(player, questId, questManager);
            return;
        }

        if ("NEWOBJ".equals(action)) {
            QuestBuilderGUI.openTypePicker(player, questId, null);
            return;
        }

        if ("DELETE".equals(action)) {
            if (!holder.isDeleteArmed()) {
                holder.setDeleteArmed(true);
                player.sendMessage(messageManager.get("quest-builder-delete-confirm",
                        "&cMég egy kattintás, és a(z) &f%s &cküldetés végleg törlődik!", questId));
                return;
            }
            questManager.deleteCustomQuest(questId);
            player.closeInventory();
            player.sendMessage(messageManager.get("quest-admin-delete-success", "&aKüldetés törölve: &e%s", questId));
        }
    }

    private void handleTypeChosen(final Player player, final QuestBuilderHolder holder, final String type) {
        player.closeInventory();
        if (holder.getNewQuestId() != null) {
            prompts.put(player.getUniqueId(),
                    new Prompt(PromptKind.NEW_COUNT, holder.getNewQuestId(), null, type, 0));
            player.sendMessage(messageManager.get("quest-builder-prompt-count",
                    "&eÍrd be a chatbe a(z) &f%s &eobjektíva darabszámát &7(megszakítás: 'mégse')",
                    QuestBuilderGUI.typeName(type)));
            return;
        }
        prompts.put(player.getUniqueId(),
                new Prompt(PromptKind.ADD_COUNT, holder.getQuestId(), null, type, 0));
        player.sendMessage(messageManager.get("quest-builder-prompt-count",
                "&eÍrd be a chatbe a(z) &f%s &eobjektíva darabszámát &7(megszakítás: 'mégse')",
                QuestBuilderGUI.typeName(type)));
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestBuilderHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Captures the next chat line of an admin with a pending prompt. Runs on the
     * async chat thread: the prompt is consumed here (so the message never
     * reaches the chat), but all quest writes and the GUI reopen hop to the
     * player's region thread.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);

        final String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        player.getScheduler().run(plugin, task -> handlePromptInput(player, prompt, input), null);
    }

    private void handlePromptInput(final Player player, final Prompt prompt, final String input) {
        if (input.equalsIgnoreCase("mégse") || input.equalsIgnoreCase("megse") || input.equalsIgnoreCase("cancel")) {
            player.sendMessage(messageManager.get("quest-builder-cancelled", "&7Szerkesztés megszakítva."));
            reopenEditorIfExists(player, prompt.questId());
            return;
        }

        switch (prompt.kind()) {
            case FIELD -> {
                applyField(player, prompt.questId(), prompt.field(), input);
                reopenEditorIfExists(player, prompt.questId());
            }
            case NEW_COUNT, ADD_COUNT -> {
                final int count = parseCount(input);
                if (count < 1) {
                    player.sendMessage(messageManager.get("quest-builder-bad-number",
                            "&cPozitív egész számot írj be. Szerkesztés megszakítva."));
                    reopenEditorIfExists(player, prompt.questId());
                    return;
                }
                if (prompt.kind() == PromptKind.ADD_COUNT) {
                    final String error = questManager.addObjective(
                            prompt.questId(), prompt.objectiveType(), count, null, new int[1]);
                    if (error != null) {
                        player.sendMessage(messageManager.get(error, "&cA művelet nem sikerült."));
                    }
                    reopenEditorIfExists(player, prompt.questId());
                    return;
                }
                prompts.put(player.getUniqueId(), new Prompt(PromptKind.NEW_NAME,
                        prompt.questId(), null, prompt.objectiveType(), count));
                player.sendMessage(messageManager.get("quest-builder-prompt-name",
                        "&eÍrd be a chatbe a küldetés megjelenő nevét &7(megszakítás: 'mégse')"));
            }
            case NEW_NAME -> {
                final String error = questManager.createCustomQuest(
                        prompt.questId(), prompt.objectiveType(), prompt.count(), input);
                if (error != null) {
                    player.sendMessage(messageManager.get(error, "&cA küldetés létrehozása nem sikerült."));
                    return;
                }
                player.sendMessage(messageManager.get("quest-builder-created",
                        "&aKüldetés létrehozva: &e%s&a. A többi mezőt a builderben állíthatod.", prompt.questId()));
                QuestBuilderGUI.openEditor(player, prompt.questId(), questManager);
            }
        }
    }

    private void applyField(final Player player, final String questId, final String field, final String value) {
        final String error = questManager.setCustomQuestField(questId, field, value);
        if (error != null) {
            player.sendMessage(messageManager.get(error, "&cA mező beállítása nem sikerült."));
        }
    }

    private void reopenEditorIfExists(final Player player, final String questId) {
        if (questId != null && questManager.isCustomQuest(questId)) {
            QuestBuilderGUI.openEditor(player, questId, questManager);
        }
    }

    private static int parseCount(final String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (final NumberFormatException exception) {
            return -1;
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }
}
