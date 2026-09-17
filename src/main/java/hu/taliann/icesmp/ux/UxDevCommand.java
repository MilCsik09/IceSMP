package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.trash.TooltipDevProjection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Hidden, developer-authority-only live acceptance surface for the immersive UX foundation.
 *
 * <p>This is deliberately presentation-only: it creates no durable state, grants no gameplay
 * reward, and the held-item tooltip preview is a client-only packet projection of a clone.</p>
 */
public final class UxDevCommand {

    private static final String AMBIENT_CONTEXT = "dev.ux.ambient";
    private static final String BOSS_CONTEXT = "dev.ux.boss";
    private static final long TOOLTIP_PREVIEW_TICKS = 200L;

    private final JavaPlugin plugin;
    private final DialogueEngine dialogue;
    private final MusicDirector music;
    private final UnifiedGuiManager gui;
    private final AtomicLong generations = new AtomicLong();
    private final ConcurrentMap<UUID, Long> tooltipPreviews = new ConcurrentHashMap<>();

    public UxDevCommand(final JavaPlugin plugin, final DialogueEngine dialogue,
                        final MusicDirector music, final UnifiedGuiManager gui) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.music = Objects.requireNonNull(music, "music");
        this.gui = Objects.requireNonNull(gui, "gui");
    }

    public void execute(final Player player, final String[] args) {
        if (player == null || !HiddenDevAuthority.mayUseHiddenContent(player)) return;
        runOwned(player, () -> executeOwned(player, args == null ? new String[0] : args));
    }

    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) tooltipPreviews.remove(playerId);
    }

    public void shutdown() {
        tooltipPreviews.clear();
    }

    private void executeOwned(final Player player, final String[] args) {
        if (args.length == 0 || "open".equalsIgnoreCase(args[0])
                || "gui".equalsIgnoreCase(args[0])) {
            openHubOwned(player);
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "dialogue" -> handleDialogueOwned(player, args);
            case "music" -> handleMusicOwned(player, args);
            case "tooltip" -> handleTooltipOwned(player, args);
            case "clear", "reset" -> {
                clearAllOwned(player, true);
                player.sendMessage(Component.text("UX DEV állapot törölve.", NamedTextColor.GREEN));
            }
            case "help" -> sendUsage(player);
            default -> sendUsage(player);
        }
    }

    private void handleDialogueOwned(final Player player, final String[] args) {
        final String action = args.length < 2 ? "start" : args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "start" -> startDialogueOwned(player);
            case "skip", "next" -> {
                dialogue.skip(player);
                player.sendMessage(Component.text("UX DEV: következő dialógus-node kérése.",
                        NamedTextColor.GRAY));
            }
            case "replace" -> replaceDialogueOwned(player);
            case "cancel" -> {
                dialogue.cancel(player.getUniqueId());
                player.sendMessage(Component.text("UX DEV dialógus megszakítva.", NamedTextColor.YELLOW));
            }
            case "status" -> player.sendMessage(Component.text(
                    "UX DEV dialogue active=" + dialogue.active(player.getUniqueId()),
                    NamedTextColor.GRAY));
            default -> sendUsage(player);
        }
    }

    private void handleMusicOwned(final Player player, final String[] args) {
        final String action = args.length < 2 ? "status" : args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "ambient" -> startAmbientOwned(player);
            case "boss" -> startBossOwned(player);
            case "remove-boss", "fallback" -> {
                music.remove(player, BOSS_CONTEXT);
                player.sendMessage(Component.text(
                        "UX DEV: boss context eltávolítva; az ambientnek vissza kell térnie.",
                        NamedTextColor.GREEN));
            }
            case "cue", "oneshot" -> {
                music.push(player, new MusicDirector.MusicContext(
                        "dev.ux.cue", MusicDirector.Type.EVENT, 100,
                        "minecraft:block.note_block.pling", 1.0F, 1.2F, false));
                player.sendMessage(Component.text(
                        "UX DEV: one-shot cue lejátszva; nem válhat aktív persistent contextté.",
                        NamedTextColor.GREEN));
            }
            case "clear", "stop" -> {
                music.clear(player);
                player.sendMessage(Component.text("UX DEV zene törölve.", NamedTextColor.YELLOW));
            }
            case "status" -> sendMusicStatus(player);
            default -> sendUsage(player);
        }
    }

    private void handleTooltipOwned(final Player player, final String[] args) {
        final String action = args.length < 2 ? "show" : args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "show", "preview" -> {
                if (gui.active(player.getUniqueId()) != null) gui.clear(player);
                showTooltipPreviewOwned(player);
            }
            case "restore", "clear" -> {
                restoreTooltipOwned(player);
                player.sendMessage(Component.text("UX DEV tooltip-projekció visszaállítva.",
                        NamedTextColor.GREEN));
            }
            default -> sendUsage(player);
        }
    }

    private void openHubOwned(final Player player) {
        final List<GuiComponent> components = new ArrayList<>();
        components.add(new Button(4,
                session -> item(Material.NETHER_STAR,
                        Component.text("Immersive UX DEV", NamedTextColor.LIGHT_PURPLE),
                        List.of(
                                Component.text("Rejtett, presentation-only acceptance felület.", NamedTextColor.GRAY),
                                Component.text("Dialogue: " + status(dialogue.active(player.getUniqueId())), NamedTextColor.GRAY),
                                Component.text("Music: " + activeMusicId(player), NamedTextColor.GRAY),
                                Component.text("GUI: " + session.guiId(), NamedTextColor.GRAY),
                                Component.text("A tooltip preview nem ír inventory-state-et.", NamedTextColor.DARK_GRAY))),
                null));

        components.add(simpleButton(10, Material.WRITABLE_BOOK, "Dialogue: start",
                "3 sor, 30 tickes ritmus + dialogue-owned music context.",
                (session, event) -> startDialogueOwned(player)));
        components.add(simpleButton(11, Material.FEATHER, "Dialogue: next/skip",
                "A jelenlegi node-ot fejezi be és a következőre lép.",
                (session, event) -> dialogue.skip(player)));
        components.add(simpleButton(12, Material.ENDER_PEARL, "Dialogue: replace",
                "Új sequence lecseréli a futó sessiont; stale callback nem élhet tovább.",
                (session, event) -> replaceDialogueOwned(player)));
        components.add(simpleButton(13, Material.BARRIER, "Dialogue: cancel",
                "Session + pending callback + dialogue music teardown.",
                (session, event) -> dialogue.cancel(player.getUniqueId())));

        components.add(new Button(14,
                session -> tooltipPreviewItem(player),
                (session, event) -> nextTick(player, () -> {
                    if (gui.active(player.getUniqueId()) == session) gui.clear(player);
                    showTooltipPreviewOwned(player);
                })));

        components.add(new Button(15,
                session -> {
                    final Integer clicks = session.state("probe-clicks", Integer.class);
                    final String last = session.state("probe-last", String.class);
                    return item(Material.COMPARATOR,
                            Component.text("Unified GUI input probe", NamedTextColor.AQUA),
                            List.of(
                                    Component.text("Kattintások: " + (clicks == null ? 0 : clicks), NamedTextColor.GRAY),
                                    Component.text("Utolsó: " + (last == null ? "-" : last), NamedTextColor.GRAY),
                                    Component.empty(),
                                    Component.text("Próbáld: bal/jobb/shift/number-key/double click.", NamedTextColor.YELLOW),
                                    Component.text("Az item nem kerülhet ki a managed top inventoryból.", NamedTextColor.DARK_GRAY)));
                },
                (session, event) -> {
                    final Integer current = session.state("probe-clicks", Integer.class);
                    session.putState("probe-clicks", current == null ? 1 : current + 1);
                    session.putState("probe-last", event.getClick().name() + "/" + event.getAction().name());
                    nextTick(player, () -> {
                        if (gui.active(player.getUniqueId()) == session && !session.closed()) {
                            gui.rerender(player);
                        }
                    });
                }));

        components.add(simpleButton(16, Material.ENDER_CHEST, "GUI session replacement",
                "Következő tickben új managed session nyílik ugyanennek a játékosnak.",
                (session, event) -> nextTick(player, () -> openReplacementOwned(player))));

        components.add(simpleButton(19, Material.MUSIC_DISC_CAT, "Music: ambient",
                "Alacsony prioritású persistent context: music_disc.cat.",
                (session, event) -> startAmbientOwned(player)));
        components.add(simpleButton(20, Material.MUSIC_DISC_PIGSTEP, "Music: boss override",
                "Magas prioritású contextnek le kell váltania az ambientet.",
                (session, event) -> startBossOwned(player)));
        components.add(simpleButton(21, Material.SHEARS, "Music: remove boss",
                "A boss context eltűnik; az ambientnek újra meg kell szólalnia.",
                (session, event) -> music.remove(player, BOSS_CONTEXT)));
        components.add(simpleButton(22, Material.NOTE_BLOCK, "Music: one-shot cue",
                "Pling cue; nem maradhat bent persistent arbitration state-ként.",
                (session, event) -> music.push(player, new MusicDirector.MusicContext(
                        "dev.ux.cue", MusicDirector.Type.EVENT, 100,
                        "minecraft:block.note_block.pling", 1.0F, 1.2F, false))));
        components.add(simpleButton(23, Material.REDSTONE_TORCH, "Music: clear",
                "Minden UX DEV music context leállítása.",
                (session, event) -> music.clear(player)));
        components.add(new Button(25,
                session -> item(Material.CLOCK,
                        Component.text("Runtime status", NamedTextColor.GOLD),
                        List.of(
                                Component.text("Dialogue active: " + dialogue.active(player.getUniqueId()), NamedTextColor.GRAY),
                                Component.text("Music active: " + activeMusicId(player), NamedTextColor.GRAY),
                                Component.text("Session: " + session.guiId(), NamedTextColor.GRAY),
                                Component.text("Page: " + session.currentPage(), NamedTextColor.GRAY))),
                (session, event) -> sendRuntimeStatus(player, session)));

        components.add(simpleButton(31, Material.LAVA_BUCKET, "Mindent töröl",
                "Dialogue + music + tooltip preview + managed GUI teardown.",
                (session, event) -> nextTick(player, () -> clearAllOwned(player, true))));

        gui.open(player, "dev.ux.hub",
                Component.text("IceSMP • UX DEV", NamedTextColor.DARK_PURPLE), 4, components);
    }

    private void openReplacementOwned(final Player player) {
        final List<GuiComponent> components = new ArrayList<>();
        components.add(new Button(4,
                session -> item(Material.ENDER_EYE,
                        Component.text("Replacement session aktív", NamedTextColor.LIGHT_PURPLE),
                        List.of(
                                Component.text("GUI ID: " + session.guiId(), NamedTextColor.GRAY),
                                Component.text("Az előző session holderének stale-nek kell lennie.", NamedTextColor.DARK_GRAY))),
                null));
        components.add(new Button(13,
                session -> {
                    final Integer clicks = session.state("replacement-clicks", Integer.class);
                    return item(Material.REPEATER,
                            Component.text("Replacement state probe", NamedTextColor.AQUA),
                            List.of(Component.text("Kattintások: " + (clicks == null ? 0 : clicks), NamedTextColor.GRAY)));
                },
                (session, event) -> {
                    final Integer clicks = session.state("replacement-clicks", Integer.class);
                    session.putState("replacement-clicks", clicks == null ? 1 : clicks + 1);
                    nextTick(player, () -> {
                        if (gui.active(player.getUniqueId()) == session && !session.closed()) gui.rerender(player);
                    });
                }));
        components.add(simpleButton(22, Material.ARROW, "Vissza a UX DEV hubra",
                "Újabb session replacementtel visszanyitja a fő tesztfelületet.",
                (session, event) -> nextTick(player, () -> openHubOwned(player))));
        gui.open(player, "dev.ux.replacement",
                Component.text("IceSMP • UX DEV • Replacement", NamedTextColor.DARK_PURPLE),
                3, components);
    }

    private void startDialogueOwned(final Player player) {
        final List<DialogueEngine.DialogueNode> nodes = List.of(
                new DialogueEngine.DialogueNode("dev.ux.dialogue.1", "UX DEV",
                        Component.text("Első sor — azonnal.", NamedTextColor.WHITE),
                        0L, 0L, true, ignored -> true, null, null),
                new DialogueEngine.DialogueNode("dev.ux.dialogue.2", "UX DEV",
                        Component.text("Második sor — 30 tickkel később.", NamedTextColor.WHITE),
                        30L, 0L, true, ignored -> true, null, null),
                new DialogueEngine.DialogueNode("dev.ux.dialogue.3", "UX DEV",
                        Component.text("Harmadik sor — újabb 30 tick; utána completion.", NamedTextColor.WHITE),
                        30L, 30L, true, ignored -> true, null, null));
        final MusicDirector.MusicContext context = new MusicDirector.MusicContext(
                "dev.ux.dialogue.music", MusicDirector.Type.CINEMATIC, 40,
                "minecraft:music_disc.chirp", 0.35F, 1.0F, true);
        dialogue.play(player, new DialogueEngine.DialogueSequence(
                "dev.ux.dialogue", nodes, context,
                () -> player.sendMessage(Component.text(
                        "UX DEV dialogue completion — a sessionnek most már inaktívnak kell lennie.",
                        NamedTextColor.GREEN))));
    }

    private void replaceDialogueOwned(final Player player) {
        final List<DialogueEngine.DialogueNode> nodes = List.of(
                new DialogueEngine.DialogueNode("dev.ux.replace.1", "UX REPLACE",
                        Component.text("Az előző session most le lett cserélve.", NamedTextColor.YELLOW),
                        0L, 0L, true, ignored -> true, null, null),
                new DialogueEngine.DialogueNode("dev.ux.replace.2", "UX REPLACE",
                        Component.text("Ha régi sor ezután visszajön, stale callback hiba van.", NamedTextColor.YELLOW),
                        10L, 10L, true, ignored -> true, null, null));
        dialogue.play(player, new DialogueEngine.DialogueSequence(
                "dev.ux.replacement-dialogue", nodes, null,
                () -> player.sendMessage(Component.text("UX DEV replacement sequence kész.",
                        NamedTextColor.GREEN))));
    }

    private void startAmbientOwned(final Player player) {
        music.push(player, new MusicDirector.MusicContext(
                AMBIENT_CONTEXT, MusicDirector.Type.AMBIENT, 10,
                "minecraft:music_disc.cat", 0.65F, 1.0F, true));
        player.sendMessage(Component.text(
                "UX DEV ambient context aktív. Következő teszt: boss override.",
                NamedTextColor.GREEN));
    }

    private void startBossOwned(final Player player) {
        music.push(player, new MusicDirector.MusicContext(
                BOSS_CONTEXT, MusicDirector.Type.BOSS, 80,
                "minecraft:music_disc.pigstep", 0.75F, 1.0F, true));
        player.sendMessage(Component.text(
                "UX DEV boss context aktív. A cat hangnak le kellett állnia.",
                NamedTextColor.GREEN));
    }

    private void showTooltipPreviewOwned(final Player player) {
        final ItemStack canonical = player.getInventory().getItemInMainHand();
        final boolean synthetic = canonical == null || canonical.getType().isAir();
        final ItemStack source = synthetic ? new ItemStack(Material.NETHER_STAR) : canonical.clone();
        final ItemStack display = source.clone();
        final ItemMeta meta = display.getItemMeta();
        if (synthetic) {
            meta.displayName(Component.text("UX Tooltip Preview", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(tooltipLines(player, source));
        display.setItemMeta(meta);

        if (!TooltipDevProjection.projectMainHand(player, display)) {
            player.sendMessage(Component.text(
                    "UX DEV tooltip packet projection nem elérhető ezen a runtime-on.",
                    NamedTextColor.RED));
            return;
        }

        final UUID playerId = player.getUniqueId();
        final long generation = generations.incrementAndGet();
        tooltipPreviews.put(playerId, generation);
        player.sendMessage(Component.text(
                "UX DEV tooltip preview aktív 10 másodpercig a kijelölt hotbar sloton. "
                        + "A szerver inventoryja nem változott.", NamedTextColor.GOLD));
        player.getScheduler().runDelayed(plugin, task -> {
            if (tooltipPreviews.remove(playerId, generation) && player.isOnline()) {
                player.updateInventory();
            }
        }, () -> tooltipPreviews.remove(playerId, generation), TOOLTIP_PREVIEW_TICKS);
    }

    private void restoreTooltipOwned(final Player player) {
        tooltipPreviews.remove(player.getUniqueId());
        player.updateInventory();
    }

    private ItemStack tooltipPreviewItem(final Player player) {
        final ItemStack held = player.getInventory().getItemInMainHand();
        final ItemStack contextItem = held == null || held.getType().isAir()
                ? new ItemStack(Material.NETHER_STAR) : held.clone();
        final ItemStack display = new ItemStack(Material.PAPER);
        final ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text("Tooltip Engine live preview", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(tooltipLines(player, contextItem));
        display.setItemMeta(meta);
        return display;
    }

    private List<Component> tooltipLines(final Player player, final ItemStack canonical) {
        final TooltipEngine.Context context = new TooltipEngine.Context(
                player, canonical, Map.of("dev-preview", true));
        final List<TooltipEngine.SectionRenderer> renderers = List.of(
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.HEADER, 0, List.of(
                        Component.text("ICE SMP • UX TOOLTIP", NamedTextColor.LIGHT_PURPLE),
                        Component.empty(),
                        Component.empty())),
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.TYPE, 10, List.of(
                        Component.text("Típus: " + canonical.getType().name(), NamedTextColor.GRAY),
                        Component.text("Presentation-only DEV projection", NamedTextColor.DARK_GRAY))),
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.PRIMARY_STATS, 20, List.of(
                        Component.text("⚔ Minta sebzés: 42–58", NamedTextColor.AQUA),
                        Component.text("✦ Tesztérték: +12%", NamedTextColor.GREEN))),
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.REQUIREMENTS, 40, List.of(
                        Component.text("✓ Hidden DEV authority", NamedTextColor.GREEN),
                        Component.text("✓ Canonical item érintetlen", NamedTextColor.GREEN))),
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.ARCHAEOLOGY, 70, List.of(
                        Component.text("⌕ Megfigyelés: a semantic section él.", NamedTextColor.GRAY))),
                ignored -> TooltipEngine.generated(TooltipEngine.SectionId.FLAVOR, 90, List.of(
                        Component.text("„Ha ezt látod, a renderer tényleg a kliensig jutott.”",
                                        NamedTextColor.DARK_PURPLE)
                                .decoration(TextDecoration.ITALIC, true))));
        return TooltipEngine.render(context, renderers).stream()
                .map(line -> line.decoration(TextDecoration.ITALIC,
                        line.decoration(TextDecoration.ITALIC)))
                .toList();
    }

    private void clearAllOwned(final Player player, final boolean closeGui) {
        dialogue.cancel(player.getUniqueId());
        music.clear(player);
        restoreTooltipOwned(player);
        if (closeGui && gui.active(player.getUniqueId()) != null) gui.clear(player);
    }

    private void sendMusicStatus(final Player player) {
        player.sendMessage(Component.text("UX DEV music active=" + activeMusicId(player),
                NamedTextColor.GRAY));
    }

    private void sendRuntimeStatus(final Player player, final GuiSession session) {
        player.sendMessage(Component.text(
                "UX DEV | dialogue=" + dialogue.active(player.getUniqueId())
                        + " | music=" + activeMusicId(player)
                        + " | gui=" + session.guiId(), NamedTextColor.GRAY));
    }

    private String activeMusicId(final Player player) {
        final MusicDirector.MusicContext active = music.active(player.getUniqueId());
        return active == null ? "-" : active.id() + " (p=" + active.priority() + ")";
    }

    private static String status(final boolean value) {
        return value ? "aktív" : "inaktív";
    }

    private GuiComponent simpleButton(final int slot, final Material material, final String name,
                                      final String description,
                                      final BiConsumer<GuiSession, InventoryClickEvent> action) {
        return new Button(slot,
                session -> item(material, Component.text(name, NamedTextColor.GOLD),
                        List.of(Component.text(description, NamedTextColor.GRAY))), action);
    }

    private static ItemStack item(final Material material, final Component name,
                                  final List<Component> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void nextTick(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) action.run();
        }, null);
    }

    private void runOwned(final Player player, final Runnable action) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) action.run();
        }, null);
    }

    private void sendUsage(final Player player) {
        player.sendMessage(Component.text("/icesmp dev ux", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" — teszt GUI", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(
                "/icesmp dev ux dialogue <start|skip|replace|cancel|status>", NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "/icesmp dev ux music <ambient|boss|remove-boss|cue|clear|status>", NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "/icesmp dev ux tooltip <show|restore> | /icesmp dev ux clear",
                NamedTextColor.GRAY));
    }

    private static final class Button implements GuiComponent {
        private final int slot;
        private final Function<GuiSession, ItemStack> renderer;
        private final BiConsumer<GuiSession, InventoryClickEvent> action;

        private Button(final int slot, final Function<GuiSession, ItemStack> renderer,
                       final BiConsumer<GuiSession, InventoryClickEvent> action) {
            this.slot = slot;
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            this.action = action;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public boolean visible(final GuiSession session) {
            return true;
        }

        @Override
        public ItemStack render(final GuiSession session) {
            return renderer.apply(session);
        }

        @Override
        public void onInteraction(final GuiSession session, final InventoryClickEvent event) {
            if (action != null) action.accept(session, event);
        }
    }
}
