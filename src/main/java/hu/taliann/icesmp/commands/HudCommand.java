package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.hud.HudComponent;
import hu.taliann.icesmp.hud.HudComponentLayout;
import hu.taliann.icesmp.hud.HudEditorAccessPolicy;
import hu.taliann.icesmp.hud.HudEditorStateMachine;
import hu.taliann.icesmp.hud.HudLayoutPreset;
import hu.taliann.icesmp.hud.HudLayoutSnapshot;
import hu.taliann.icesmp.hud.HudPreviewSelection;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Public HUD visibility controls plus the gated first-party layout editor. */
public final class HudCommand implements BasicCommand {

    private static final String TOGGLE = "toggle";
    private static final String EDIT = "edit";

    private final HudManager hudManager;
    private final MessageManager messageManager;

    public HudCommand(final HudManager hudManager, final MessageManager messageManager) {
        this.hudManager = hudManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (args.length > 0 && EDIT.equalsIgnoreCase(args[0])) {
            handleEditor(sender, args);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length == 0) {
            sendStatus(player);
        } else if (TOGGLE.equalsIgnoreCase(args[0])) {
            handleToggle(player, args);
        } else {
            sendStatus(player);
        }
    }

    private void handleEditor(final CommandSender sender, final String[] args) {
        final boolean playerSender = sender instanceof Player;
        final HudEditorStateMachine.Scope requestedScope = requestedScope(sender, args);
        final boolean globalScope = requestedScope == HudEditorStateMachine.Scope.GLOBAL;
        final HudEditorAccessPolicy.Decision decision = HudEditorAccessPolicy.decide(playerSender,
                globalScope, sender.hasPermission(Permissions.HUD_EDITOR), hudManager.hudEditorEnabled(),
                hudManager.personalHudEditorEnabled());
        if (decision != HudEditorAccessPolicy.Decision.ALLOWED) {
            if (sender instanceof Player player) {
                hudManager.hudEditorSession(player)
                        .filter(session -> session.scope() == requestedScope)
                        .ifPresent(session -> hudManager.cancelHudEditor(player));
            }
            final String message = switch (decision) {
                case PLAYER_ONLY -> "&cA HUD-editor élő előnézete csak játékosként használható.";
                case NO_PERMISSION -> "&cA szerveralap szerkesztéséhez szükséges: " + Permissions.HUD_EDITOR;
                case CONFIG_DISABLED -> "&cEz a HUD-editor mód jelenleg ki van kapcsolva.";
                case ALLOWED -> throw new IllegalStateException("unreachable");
            };
            sender.sendMessage(messageManager.get("hud-editor-denied", message));
            return;
        }
        final Player player = (Player) sender;
        if (args.length == 1 || "personal".equalsIgnoreCase(args[1])
                || "global".equalsIgnoreCase(args[1])) {
            hudManager.beginHudEditor(player, requestedScope);
            final boolean visible = hudManager.refreshHudEditorPreview(player);
            sendEditorPage(player, visible, EditorPage.OVERVIEW);
            return;
        }

        final HudEditorStateMachine.Session active = hudManager.hudEditorSession(player)
                .orElseGet(() -> hudManager.beginHudEditor(player, HudEditorStateMachine.Scope.PERSONAL));
        if (active.scope() == HudEditorStateMachine.Scope.GLOBAL
                && !player.hasPermission(Permissions.HUD_EDITOR)) {
            hudManager.cancelHudEditor(player);
            player.sendMessage(Component.text("[HUD] A szerveralap munkamenet jogosultsága megszűnt.",
                    NamedTextColor.RED));
            return;
        }

        if ("status".equalsIgnoreCase(args[1]) || "page".equalsIgnoreCase(args[1])) {
            final EditorPage page = "page".equalsIgnoreCase(args[1]) && args.length >= 3
                    ? EditorPage.find(args[2]) : EditorPage.OVERVIEW;
            final boolean visible = hudManager.refreshHudEditorPreview(player);
            sendEditorPage(player, visible, page);
            return;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        if ("cancel".equals(action)) {
            player.sendMessage(hudManager.cancelHudEditor(player)
                    ? Component.text("[HUD] A módosításokat elvetetted.", NamedTextColor.YELLOW)
                    : Component.text("[HUD] Nincs aktív editor-munkamenet.", NamedTextColor.GRAY));
            return;
        }
        if ("save".equals(action)) {
            saveEditor(player);
            return;
        }

        try {
            switch (action) {
                case "move" -> move(player, args);
                case "margin" -> margin(player, args);
                case "step" -> hudManager.setHudEditorStep(player, requireStep(args));
                case "scale" -> scale(player, args);
                case "set" -> setValue(player, args);
                case "select" -> select(player, args);
                case "previous" -> hudManager.cycleHudEditorComponent(player, -1);
                case "next" -> hudManager.cycleHudEditorComponent(player, 1);
                case "visibility" -> visibility(player);
                case "preset" -> preset(player, args);
                case "preview" -> preview(player, args);
                case "reset" -> reset(player, args);
                case "undo" -> hudManager.undoHudEditor(player);
                default -> throw new IllegalArgumentException("Ismeretlen editor-művelet: " + action);
            }
            final boolean visible = hudManager.refreshHudEditorPreview(player);
            sendEditorActionBar(player, visible);
        } catch (final IllegalArgumentException exception) {
            player.sendMessage(Component.text("[HUD] " + exception.getMessage(), NamedTextColor.RED));
        }
    }

    private HudEditorStateMachine.Scope requestedScope(final CommandSender sender, final String[] args) {
        if (args.length >= 2 && "global".equalsIgnoreCase(args[1])) {
            return HudEditorStateMachine.Scope.GLOBAL;
        }
        if (args.length >= 2 && "personal".equalsIgnoreCase(args[1])) {
            return HudEditorStateMachine.Scope.PERSONAL;
        }
        if (sender instanceof Player player) {
            return hudManager.hudEditorSession(player).map(HudEditorStateMachine.Session::scope)
                    .orElse(HudEditorStateMachine.Scope.PERSONAL);
        }
        return HudEditorStateMachine.Scope.PERSONAL;
    }

    private void saveEditor(final Player player) {
        player.sendActionBar(Component.text("HUD-beállítások mentése…", NamedTextColor.YELLOW));
        hudManager.saveHudEditor(player).whenComplete((result, failure) ->
                player.getScheduler().run(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(HudCommand.class),
                        task -> {
                            if (failure != null) {
                                player.sendMessage(Component.text("[HUD] A Profile v2 mentés sikertelen: "
                                        + safeFailure(failure), NamedTextColor.RED));
                                return;
                            }
                            switch (result.status()) {
                                case SAVED -> {
                                    hudManager.finishHudEditorSave(player, result);
                                    final String target = result.scope() == HudEditorStateMachine.Scope.PERSONAL
                                            ? "Saját HUD elmentve (" + result.personalOverrideCount()
                                            + " személyes eltérés)."
                                            : "A szerveralap elmentve és minden játékosnál frissítve.";
                                    player.sendMessage(Component.text("[HUD] " + target, NamedTextColor.GREEN));
                                }
                                case NO_CHANGES -> {
                                    hudManager.finishHudEditorSave(player, result);
                                    player.sendMessage(Component.text("[HUD] Nem volt mentendő változás.",
                                            NamedTextColor.GRAY));
                                }
                                case STALE -> player.sendMessage(Component.text(
                                        "[HUD] A globális config közben megváltozott; nyisd újra a szerveralapot.",
                                        NamedTextColor.RED));
                                case NO_SESSION -> player.sendMessage(Component.text(
                                        "[HUD] Nincs aktív editor-munkamenet.", NamedTextColor.GRAY));
                                case IN_PROGRESS -> player.sendMessage(Component.text(
                                        "[HUD] A mentés már folyamatban van.", NamedTextColor.YELLOW));
                            }
                        }, null));
    }

    private static String safeFailure(final Throwable failure) {
        final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        final String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private void move(final Player player, final String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Használat: /hud edit move <left|right|up|down>");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "left" -> hudManager.moveHudEditor(player, -1, 0);
            case "right" -> hudManager.moveHudEditor(player, 1, 0);
            case "up" -> hudManager.moveHudEditor(player, 0, -1);
            case "down" -> hudManager.moveHudEditor(player, 0, 1);
            default -> throw new IllegalArgumentException("Az irány: left, right, up vagy down lehet.");
        }
    }

    private void margin(final Player player, final String[] args) {
        if (hudManager.hudEditorSession(player).orElseThrow().selected() != HudComponent.GLOBAL) {
            throw new IllegalArgumentException("A biztonsági margó a teljes HUD célponton állítható.");
        }
        if (args.length < 3 || !("+".equals(args[2]) || "-".equals(args[2]))) {
            throw new IllegalArgumentException("Használat: /hud edit margin <+|->");
        }
        hudManager.changeHudEditorMargin(player, "+".equals(args[2]) ? 1 : -1);
    }

    private static int requireStep(final String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Használat: /hud edit step <1|5|10|15>");
        try {
            final int step = Integer.parseInt(args[2]);
            if (!List.of(1, 5, 10, 15).contains(step)) {
                throw new IllegalArgumentException("A lépésköz 1, 5, 10 vagy 15 lehet.");
            }
            return step;
        } catch (final NumberFormatException failure) {
            throw new IllegalArgumentException("A lépésköz 1, 5, 10 vagy 15 lehet.");
        }
    }

    private void scale(final Player player, final String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("Használat: /hud edit scale <fine|coarse> <up|down>");
        }
        final int amount = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "fine" -> 1;
            case "coarse" -> 2;
            default -> throw new IllegalArgumentException("A skálázás fine vagy coarse lehet.");
        };
        final int direction = switch (args[3].toLowerCase(Locale.ROOT)) {
            case "up" -> 1;
            case "down" -> -1;
            default -> throw new IllegalArgumentException("A skálázás iránya up vagy down lehet.");
        };
        hudManager.changeHudEditorScale(player, amount * direction);
    }

    private void setValue(final Player player, final String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("Használat: /hud edit set <x|y|scale> <érték>");
        }
        try {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "x" -> hudManager.setHudEditorX(player, Integer.parseInt(args[3]));
                case "y" -> hudManager.setHudEditorY(player, Integer.parseInt(args[3]));
                case "scale" -> hudManager.setHudEditorScale(player,
                        Double.parseDouble(args[3].replace(',', '.')));
                default -> throw new IllegalArgumentException("Az érték típusa x, y vagy scale lehet.");
            }
        } catch (final NumberFormatException failure) {
            throw new IllegalArgumentException("Az X/Y egész szám, a méret például 1.40 legyen.");
        }
    }

    private void select(final Player player, final String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Hiányzó HUD-komponens azonosító.");
        hudManager.selectHudEditorComponent(player, HudComponent.find(args[2])
                .orElseThrow(() -> new IllegalArgumentException("Ismeretlen HUD-komponens: " + args[2])));
    }

    private void visibility(final Player player) {
        if (hudManager.hudEditorSession(player).orElseThrow().selected() == HudComponent.GLOBAL) {
            throw new IllegalArgumentException("A teljes HUD láthatóságát a /hud toggle mind kezeli.");
        }
        hudManager.toggleHudEditorComponent(player);
    }

    private void reset(final Player player, final String[] args) {
        if (args.length >= 3 && "all".equalsIgnoreCase(args[2])) {
            hudManager.resetAllHudEditor(player);
        } else {
            hudManager.resetHudEditor(player);
        }
    }

    private void preset(final Player player, final String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Hiányzó felbontás/GUI-scale preset.");
        hudManager.useHudEditorPreset(player, HudLayoutPreset.find(args[2])
                .orElseThrow(() -> new IllegalArgumentException("Ismeretlen preset: " + args[2])));
    }

    private void preview(final Player player, final String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("Használat: /hud edit preview <faction|class|state> <érték>");
        }
        final HudPreviewSelection current = hudManager.hudEditorSession(player).orElseThrow().preview();
        final int direction = "previous".equalsIgnoreCase(args[3]) ? -1
                : "next".equalsIgnoreCase(args[3]) ? 1 : 0;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "faction" -> hudManager.previewHudFaction(player, direction == 0 ? args[3]
                    : cycle(HudPreviewSelection.FACTIONS, current.faction(), direction));
            case "class" -> hudManager.previewHudClass(player, direction == 0 ? args[3]
                    : cycle(HudPreviewSelection.CLASSES, current.playerClass(), direction));
            case "state" -> hudManager.previewHudState(player, direction == 0 ? args[3]
                    : cycle(HudPreviewSelection.STATES, current.state(), direction));
            default -> throw new IllegalArgumentException("A preview tengely faction, class vagy state lehet.");
        }
    }

    private static String cycle(final List<String> values, final String current, final int direction) {
        return values.get(Math.floorMod(values.indexOf(current) + Integer.signum(direction), values.size()));
    }

    private void sendEditorPage(final Player player, final boolean visible, final EditorPage page) {
        final HudEditorStateMachine.Session session = hudManager.hudEditorSession(player).orElseThrow();
        final HudLayoutSnapshot layout = session.working();
        final HudComponent selected = session.selected();
        final boolean personal = session.scope() == HudEditorStateMachine.Scope.PERSONAL;
        final String mode = personal ? "SAJÁT HUD" : "SZERVERALAP • ADMIN";
        player.sendMessage(Component.text("━━ HUD EDITOR • " + mode + " ━━", personal
                ? NamedTextColor.AQUA : NamedTextColor.GOLD));
        player.sendMessage(editorNavigation(page));
        player.sendMessage(Component.text("Kijelölve: ", NamedTextColor.GRAY)
                .append(Component.text(selected.displayName(), NamedTextColor.GOLD))
                .append(Component.text(" • " + editorValues(session), NamedTextColor.WHITE)));
        switch (page) {
            case OVERVIEW -> sendOverviewPage(player, session);
            case POSITION -> sendPositionPage(player, session);
            case APPEARANCE -> sendAppearancePage(player, session);
            case PREVIEW -> sendPreviewPage(player, session.preview());
            case PRESETS -> sendPresetPage(player, layout);
            case COMPONENTS -> sendComponentPage(player, session);
        }
        if (!visible) {
            player.sendMessage(Component.text("Az előnézethez a szerver resource packjének sikeresen be kell "
                    + "töltődnie; a natív fallback közben változatlanul aktív.", NamedTextColor.YELLOW));
        }
    }

    private Component editorNavigation(final EditorPage active) {
        Component result = Component.empty();
        for (final EditorPage page : EditorPage.values()) {
            result = result.append(button(page.label, "/hud edit page " + page.id, page == active))
                    .append(Component.space());
        }
        return result;
    }

    private void sendOverviewPage(final Player player, final HudEditorStateMachine.Session session) {
        Component modes = Component.text("Mód: ", NamedTextColor.GRAY)
                .append(button("saját", "/hud edit personal",
                        session.scope() == HudEditorStateMachine.Scope.PERSONAL));
        if (player.hasPermission(Permissions.HUD_EDITOR)) {
            modes = modes.append(Component.space()).append(button("szerveralap", "/hud edit global",
                    session.scope() == HudEditorStateMachine.Scope.GLOBAL));
        }
        if (session.scope() == HudEditorStateMachine.Scope.PERSONAL) {
            modes = modes.append(Component.text(hudManager.hasPersonalHudLayout(player)
                    ? "  • van mentett személyes eltérés" : "  • globális alapot örököl",
                    NamedTextColor.GRAY));
        }
        player.sendMessage(modes);
        player.sendMessage(button("↶ visszavonás", "/hud edit undo").append(Component.space())
                .append(button("kijelölt visszaállítása", "/hud edit reset")).append(Component.space())
                .append(button(session.scope() == HudEditorStateMachine.Scope.PERSONAL
                        ? "globális alap visszaállítása" : "gyári alap visszaállítása",
                        "/hud edit reset all")));
        player.sendMessage(button("✓ MENTÉS", "/hud edit save").append(Component.space())
                .append(button("✕ ELVETÉS", "/hud edit cancel")));
    }

    private void sendPositionPage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(Component.text("Mozgatás: ", NamedTextColor.GRAY)
                .append(button("←", "/hud edit move left")).append(Component.space())
                .append(button("→", "/hud edit move right")).append(Component.space())
                .append(button("↑", "/hud edit move up")).append(Component.space())
                .append(button("↓", "/hud edit move down")).append(Component.text("  lépés: ", NamedTextColor.GRAY))
                .append(button("1", "/hud edit step 1", session.step() == 1)).append(Component.space())
                .append(button("5", "/hud edit step 5", session.step() == 5)).append(Component.space())
                .append(button("10", "/hud edit step 10", session.step() == 10)).append(Component.space())
                .append(button("15", "/hud edit step 15", session.step() == 15)));
        Component direct = Component.text("Pontos érték: ", NamedTextColor.GRAY)
                .append(inputButton("X", "/hud edit set x ")).append(Component.space())
                .append(inputButton("Y", "/hud edit set y "));
        if (session.selected() == HudComponent.GLOBAL) {
            direct = direct.append(Component.text("  ")).append(button("margó −", "/hud edit margin -"))
                    .append(Component.space()).append(button("margó +", "/hud edit margin +"));
        }
        player.sendMessage(direct);
    }

    private void sendAppearancePage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(Component.text("Méret 0.75×–3.50×: ", NamedTextColor.GRAY)
                .append(button("finom −", "/hud edit scale fine down")).append(Component.space())
                .append(button("finom +", "/hud edit scale fine up")).append(Component.space())
                .append(button("durva −", "/hud edit scale coarse down")).append(Component.space())
                .append(button("durva +", "/hud edit scale coarse up")).append(Component.space())
                .append(inputButton("pontos méret", "/hud edit set scale ")));
        if (session.selected() != HudComponent.GLOBAL) {
            final boolean visible = session.working().componentLayout(session.selected()).visible();
            player.sendMessage(button(visible ? "kijelölt elrejtése" : "kijelölt megjelenítése",
                    "/hud edit visibility"));
        }
    }

    private void sendPreviewPage(final Player player, final HudPreviewSelection preview) {
        player.sendMessage(previewAxis("Frakció", preview.faction(), "faction"));
        player.sendMessage(previewAxis("Kaszt", preview.playerClass(), "class"));
        player.sendMessage(previewAxis("Állapot", preview.state(), "state"));
    }

    private Component previewAxis(final String label, final String value, final String axis) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(button("◀", "/hud edit preview " + axis + " previous")).append(Component.space())
                .append(Component.text(value, NamedTextColor.WHITE)).append(Component.space())
                .append(button("▶", "/hud edit preview " + axis + " next"));
    }

    private void sendPresetPage(final Player player, final HudLayoutSnapshot layout) {
        Component presets = Component.text("Felbontás / GUI scale: ", NamedTextColor.GRAY);
        for (final HudLayoutPreset preset : HudLayoutPreset.VALUES) {
            presets = presets.append(button(preset.resolution() + "/G" + preset.guiScale(),
                    "/hud edit preset " + preset.id(), presetMatches(layout, preset)))
                    .append(Component.space());
        }
        player.sendMessage(presets);
    }

    private void sendComponentPage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(button("◀ előző elem", "/hud edit previous").append(Component.space())
                .append(button("következő elem ▶", "/hud edit next")));
        final List<HudComponent> targets = HudComponent.editorTargets();
        for (int offset = 0; offset < targets.size(); offset += 6) {
            Component row = Component.empty();
            for (final HudComponent component : targets.subList(offset, Math.min(offset + 6, targets.size()))) {
                row = row.append(button(shortComponentName(component), "/hud edit select " + component.id(),
                        component == session.selected())).append(Component.space());
            }
            player.sendMessage(row);
        }
    }

    private static String shortComponentName(final HudComponent component) {
        return switch (component) {
            case GLOBAL -> "Teljes";
            case FRAME -> "Fő keret";
            case CLASS_ICON -> "Kasztikon";
            case CLASS_NAME -> "Kasztnév";
            case FACTION -> "Frakció";
            case LEVEL_ICON -> "Szintikon";
            case LEVEL_TEXT -> "Szint";
            case WALLET_FRAME -> "Valutakeret";
            case WALLET -> "Valuták";
            case RESOURCE_LABEL -> "Erőforrásnév";
            case RESOURCE_BAR -> "Erőforrássáv";
            case PRIMARY_MECHANIC -> "Fő mechanika";
            case SECONDARY_MECHANIC -> "Másodlagos";
            case CHARGES -> "Töltetek";
            case STATE_PROC -> "Proc/állapot";
            case DETAIL_FRAME -> "Részletkeret";
            case DETAIL_METRICS -> "Metrikák";
            case EVENT_ICON -> "Eseményikon";
            case EVENT_TEXT -> "Eseményszöveg";
        };
    }

    private static Component button(final String label, final String command) {
        return button(label, command, false);
    }

    private static Component button(final String label, final String command, final boolean active) {
        return Component.text("[" + label + "]", active ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)));
    }

    private static Component inputButton(final String label, final String commandPrefix) {
        return Component.text("[" + label + " = …]", NamedTextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(commandPrefix))
                .hoverEvent(HoverEvent.showText(Component.text("Kattints, írd be az értéket, majd Enter.",
                        NamedTextColor.GRAY)));
    }

    private void sendEditorActionBar(final Player player, final boolean visible) {
        final HudEditorStateMachine.Session session = hudManager.hudEditorSession(player).orElseThrow();
        final String mode = session.scope() == HudEditorStateMachine.Scope.PERSONAL ? "SAJÁT" : "SZERVERALAP";
        player.sendActionBar(Component.text(mode + " • " + session.selected().displayName() + " • "
                        + editorValues(session) + (visible ? "" : " • resource pack hiányzik"),
                visible ? NamedTextColor.AQUA : NamedTextColor.RED));
    }

    private static String editorValues(final HudEditorStateMachine.Session session) {
        final HudLayoutSnapshot layout = session.working();
        if (session.selected() == HudComponent.GLOBAL) {
            return "X " + layout.xOffsetPixels() + "  Y " + layout.yOffsetPixels()
                    + "  margó " + layout.safeMarginPixels() + "  méret "
                    + String.format(Locale.ROOT, "%.2f", layout.scale()) + "x  lépés " + session.step();
        }
        final HudComponentLayout element = layout.componentLayout(session.selected());
        return "relatív X " + element.xOffsetPixels() + "  Y " + element.yOffsetPixels()
                + "  méret " + String.format(Locale.ROOT, "%.2f", element.scale()) + "x  "
                + (element.visible() ? "látható" : "rejtett") + "  lépés " + session.step();
    }

    private static boolean presetMatches(final HudLayoutSnapshot layout, final HudLayoutPreset preset) {
        final HudLayoutSnapshot candidate = preset.layout();
        return layout.xOffsetPixels() == candidate.xOffsetPixels()
                && layout.yOffsetPixels() == candidate.yOffsetPixels()
                && layout.safeMarginPixels() == candidate.safeMarginPixels()
                && layout.scaleIndex() == candidate.scaleIndex();
    }

    private void handleToggle(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("hud-usage-toggle", "&cHasználat: /hud toggle <szekció|mind>"));
            return;
        }
        final String section = args[1].toLowerCase(Locale.ROOT);
        if (!HudManager.SECTION_ALL.equals(section) && !HudManager.SECTIONS.contains(section)) {
            player.sendMessage(messageManager.get("hud-unknown-section",
                    "&cIsmeretlen HUD-szekció: &f%s&c. Lehetséges értékek: &f%s&c, mind",
                    section, String.join(", ", HudManager.SECTIONS)));
            return;
        }
        hudManager.toggleSection(player, section).whenComplete((nowHidden, failure) ->
                player.getScheduler().run(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(HudCommand.class), task -> {
                    if (failure != null) {
                        player.sendMessage(messageManager.get("hud-toggle-storage-failed",
                                "&cA HUD-beállítás PlayerProfile mentése meghiúsult."));
                        return;
                    }
                    hudManager.update(player);
                    final String key = HudManager.SECTION_ALL.equals(section)
                            ? (Boolean.TRUE.equals(nowHidden) ? "hud-toggled-all-off" : "hud-toggled-all-on")
                            : (Boolean.TRUE.equals(nowHidden) ? "hud-toggled-off" : "hud-toggled-on");
                    final String fallback = Boolean.TRUE.equals(nowHidden)
                            ? "&b[HUD] &7%s &ckikapcsolva&7." : "&b[HUD] &7%s &abekapcsolva&7.";
                    player.sendMessage(messageManager.get(key, fallback, displayName(section)));
                }, null));
    }

    private void sendStatus(final Player player) {
        final Set<String> hidden = hudManager.hiddenSections(player);
        player.sendMessage(messageManager.get("hud-list-header", "&b[HUD] &7Szekciók (/hud toggle <szekció>):"));
        for (final String section : HudManager.SECTIONS) {
            player.sendMessage(messageManager.get("hud-list-entry", "&7- &f%s &8(%s)&7: %s",
                    displayName(section), section, hidden.contains(section) ? "&ckikapcsolva" : "&abekapcsolva"));
        }
        player.sendMessage(messageManager.get("hud-list-all", "&7- &fTeljes HUD &8(mind)&7: %s",
                hidden.contains(HudManager.SECTION_ALL) ? "&ckikapcsolva" : "&abekapcsolva"));
    }

    private static String displayName(final String section) {
        return switch (section) {
            case HudManager.SECTION_FACTION -> "Frakció";
            case HudManager.SECTION_CURRENCY -> "Valuta";
            case HudManager.SECTION_CLASS -> "Kaszt";
            case HudManager.SECTION_RESOURCE -> "Erőforrás-csík";
            case HudManager.SECTION_EVENT -> "Esemény sor";
            case HudManager.SECTION_PARTY -> "Csapat (party) keret";
            case HudManager.SECTION_ALL -> "Teljes HUD";
            default -> section;
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        final ArrayList<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.add(TOGGLE);
            options.add(EDIT);
        } else if (TOGGLE.equalsIgnoreCase(args[0]) && args.length == 2) {
            options.addAll(HudManager.SECTIONS);
            options.add(HudManager.SECTION_ALL);
        } else if (EDIT.equalsIgnoreCase(args[0])) {
            editorSuggestions(args, options, source.getSender().hasPermission(Permissions.HUD_EDITOR));
        }
        final String prefix = prefixAt(args, Math.max(0, args.length - 1));
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private static void editorSuggestions(final String[] args, final List<String> options,
                                          final boolean canEditGlobal) {
        if (args.length == 2) {
            options.addAll(List.of("personal", "status", "page", "select", "previous", "next", "move",
                "margin", "step", "scale", "set", "visibility", "preset", "preview", "undo", "reset",
                "save", "cancel"));
            if (canEditGlobal) options.add("global");
        } else if (args.length == 3 && "page".equalsIgnoreCase(args[1]))
            options.addAll(List.of("main", "position", "appearance", "preview", "presets", "components"));
        else if (args.length == 3 && "select".equalsIgnoreCase(args[1]))
            options.addAll(HudComponent.editorTargets().stream().map(HudComponent::id).toList());
        else if (args.length == 3 && "move".equalsIgnoreCase(args[1]))
            options.addAll(List.of("left", "right", "up", "down"));
        else if (args.length == 3 && "margin".equalsIgnoreCase(args[1])) options.addAll(List.of("+", "-"));
        else if (args.length == 3 && "step".equalsIgnoreCase(args[1]))
            options.addAll(List.of("1", "5", "10", "15"));
        else if (args.length == 3 && "scale".equalsIgnoreCase(args[1])) options.addAll(List.of("fine", "coarse"));
        else if (args.length == 4 && "scale".equalsIgnoreCase(args[1])) options.addAll(List.of("up", "down"));
        else if (args.length == 3 && "set".equalsIgnoreCase(args[1]))
            options.addAll(List.of("x", "y", "scale"));
        else if (args.length == 3 && "preset".equalsIgnoreCase(args[1]))
            options.addAll(HudLayoutPreset.VALUES.stream().map(HudLayoutPreset::id).toList());
        else if (args.length == 3 && "reset".equalsIgnoreCase(args[1])) options.add("all");
        else if (args.length == 3 && "preview".equalsIgnoreCase(args[1]))
            options.addAll(List.of("faction", "class", "state"));
        else if (args.length == 4 && "preview".equalsIgnoreCase(args[1])) {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "faction" -> {
                    options.addAll(List.of("previous", "next"));
                    options.addAll(HudPreviewSelection.FACTIONS);
                }
                case "class" -> {
                    options.addAll(List.of("previous", "next"));
                    options.addAll(HudPreviewSelection.CLASSES);
                }
                case "state" -> {
                    options.addAll(List.of("previous", "next"));
                    options.addAll(HudPreviewSelection.STATES);
                }
                default -> { }
            }
        }
    }

    private enum EditorPage {
        OVERVIEW("main", "Áttekintés"),
        POSITION("position", "Pozíció"),
        APPEARANCE("appearance", "Méret"),
        PREVIEW("preview", "Előnézet"),
        PRESETS("presets", "Presetek"),
        COMPONENTS("components", "Elemek");

        private final String id;
        private final String label;

        EditorPage(final String id, final String label) {
            this.id = id;
            this.label = label;
        }

        private static EditorPage find(final String raw) {
            if (raw == null) return OVERVIEW;
            final String value = raw.toLowerCase(Locale.ROOT);
            for (final EditorPage page : values()) {
                if (page.id.equals(value)) return page;
            }
            return switch (value) {
                case "overview", "attekintes", "áttekintés" -> OVERVIEW;
                case "pozicio", "pozíció" -> POSITION;
                case "meret", "méret" -> APPEARANCE;
                case "elonezet", "előnézet" -> PREVIEW;
                case "preset" -> PRESETS;
                case "elemek", "komponensek" -> COMPONENTS;
                default -> OVERVIEW;
            };
        }
    }
}
