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
            final String key = switch (decision) {
                case PLAYER_ONLY -> "hud-editor-player-only";
                case NO_PERMISSION -> "hud-editor-no-permission";
                case CONFIG_DISABLED -> "hud-editor-config-disabled";
                case ALLOWED -> throw new IllegalStateException("unreachable");
            };
            sender.sendMessage(messageManager.requiredComponent(key));
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
            player.sendMessage(messageManager.requiredComponent("hud-editor-global-session-permission-lost"));
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
            player.sendMessage(messageManager.requiredComponent(hudManager.cancelHudEditor(player)
                    ? "hud-editor-cancelled" : "hud-editor-no-session"));
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
                default -> throw error("hud-editor-error-unknown-action", action);
            }
            final boolean visible = hudManager.refreshHudEditorPreview(player);
            sendEditorActionBar(player, visible);
        } catch (final HudCommandException exception) {
            player.sendMessage(messageManager.requiredComponent(exception.key(), exception.args()));
        } catch (final IllegalArgumentException exception) {
            player.sendMessage(messageManager.requiredComponent("hud-editor-error-invalid-change"));
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
        player.sendActionBar(messageManager.requiredComponent("hud-editor-saving"));
        hudManager.saveHudEditor(player).whenComplete((result, failure) ->
                player.getScheduler().run(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(HudCommand.class),
                        task -> {
                            if (failure != null) {
                                player.sendMessage(messageManager.requiredComponent(
                                        "hud-editor-save-failed", safeFailure(failure)));
                                return;
                            }
                            switch (result.status()) {
                                case SAVED -> {
                                    hudManager.finishHudEditorSave(player, result);
                                    player.sendMessage(messageManager.requiredComponent(
                                            result.scope() == HudEditorStateMachine.Scope.PERSONAL
                                                    ? "hud-editor-save-personal"
                                                    : "hud-editor-save-success",
                                            result.personalOverrideCount()));
                                }
                                case NO_CHANGES -> {
                                    hudManager.finishHudEditorSave(player, result);
                                    player.sendMessage(messageManager.requiredComponent("hud-editor-save-no-changes"));
                                }
                                case STALE -> player.sendMessage(
                                        messageManager.requiredComponent("hud-editor-save-stale"));
                                case NO_SESSION -> player.sendMessage(
                                        messageManager.requiredComponent("hud-editor-no-session"));
                                case IN_PROGRESS -> player.sendMessage(
                                        messageManager.requiredComponent("hud-editor-save-in-progress"));
                            }
                        }, null));
    }

    private static String safeFailure(final Throwable failure) {
        final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        final String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private void move(final Player player, final String[] args) {
        if (args.length < 3) throw error("hud-editor-error-usage-move");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "left" -> hudManager.moveHudEditor(player, -1, 0);
            case "right" -> hudManager.moveHudEditor(player, 1, 0);
            case "up" -> hudManager.moveHudEditor(player, 0, -1);
            case "down" -> hudManager.moveHudEditor(player, 0, 1);
            default -> throw error("hud-editor-error-direction");
        }
    }

    private void margin(final Player player, final String[] args) {
        if (hudManager.hudEditorSession(player).orElseThrow().selected() != HudComponent.GLOBAL) {
            throw error("hud-editor-error-margin-global");
        }
        if (args.length < 3 || !("+".equals(args[2]) || "-".equals(args[2]))) {
            throw error("hud-editor-error-usage-margin");
        }
        hudManager.changeHudEditorMargin(player, "+".equals(args[2]) ? 1 : -1);
    }

    private static int requireStep(final String[] args) {
        if (args.length < 3) throw error("hud-editor-error-usage-step");
        try {
            final int step = Integer.parseInt(args[2]);
            if (!List.of(1, 5, 10, 15).contains(step)) throw error("hud-editor-error-step");
            return step;
        } catch (final NumberFormatException failure) {
            throw error("hud-editor-error-step");
        }
    }

    private void scale(final Player player, final String[] args) {
        if (args.length < 4) {
            throw error("hud-editor-error-usage-scale");
        }
        final int amount = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "fine" -> 1;
            case "coarse" -> 2;
            default -> throw error("hud-editor-error-scale-mode");
        };
        final int direction = switch (args[3].toLowerCase(Locale.ROOT)) {
            case "up" -> 1;
            case "down" -> -1;
            default -> throw error("hud-editor-error-scale-direction");
        };
        hudManager.changeHudEditorScale(player, amount * direction);
    }

    private void setValue(final Player player, final String[] args) {
        if (args.length < 4) {
            throw error("hud-editor-error-usage-set");
        }
        try {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "x" -> hudManager.setHudEditorX(player, Integer.parseInt(args[3]));
                case "y" -> hudManager.setHudEditorY(player, Integer.parseInt(args[3]));
                case "scale" -> hudManager.setHudEditorScale(player,
                        Double.parseDouble(args[3].replace(',', '.')));
                default -> throw error("hud-editor-error-set-field");
            }
        } catch (final NumberFormatException failure) {
            throw error("hud-editor-error-set-value");
        }
    }

    private void select(final Player player, final String[] args) {
        if (args.length < 3) throw error("hud-editor-error-missing-component");
        hudManager.selectHudEditorComponent(player, HudComponent.find(args[2])
                .orElseThrow(() -> error("hud-editor-error-unknown-component", args[2])));
    }

    private void visibility(final Player player) {
        final HudComponent selected = hudManager.hudEditorSession(player).orElseThrow().selected();
        if (selected == HudComponent.GLOBAL) {
            throw error("hud-editor-error-global-visibility");
        }
        if (!selected.hideable()) {
            throw error("hud-editor-error-protected-visibility");
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
        if (args.length < 3) throw error("hud-editor-error-missing-preset");
        hudManager.useHudEditorPreset(player, HudLayoutPreset.find(args[2])
                .orElseThrow(() -> error("hud-editor-error-unknown-preset", args[2])));
    }

    private void preview(final Player player, final String[] args) {
        if (args.length < 4) {
            throw error("hud-editor-error-usage-preview");
        }
        final HudPreviewSelection current = hudManager.hudEditorSession(player).orElseThrow().preview();
        final int direction = "previous".equalsIgnoreCase(args[3]) ? -1
                : "next".equalsIgnoreCase(args[3]) ? 1 : 0;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "faction" -> hudManager.previewHudFaction(player, previewValue(
                    HudPreviewSelection.FACTIONS, current.faction(), args[3], direction));
            case "class" -> hudManager.previewHudClass(player, previewValue(
                    HudPreviewSelection.CLASSES, current.playerClass(), args[3], direction));
            case "state" -> hudManager.previewHudState(player, previewValue(
                    HudPreviewSelection.STATES, current.state(), args[3], direction));
            default -> throw error("hud-editor-error-preview-axis");
        }
    }

    private static String previewValue(final List<String> values, final String current,
                                       final String requested, final int direction) {
        if (direction != 0) {
            return values.get(Math.floorMod(values.indexOf(current) + Integer.signum(direction), values.size()));
        }
        if (!values.contains(requested)) {
            throw error("hud-editor-error-preview-value", requested);
        }
        return requested;
    }

    private void sendEditorPage(final Player player, final boolean visible, final EditorPage page) {
        final HudEditorStateMachine.Session session = hudManager.hudEditorSession(player).orElseThrow();
        final HudLayoutSnapshot layout = session.working();
        final HudComponent selected = session.selected();
        final boolean personal = session.scope() == HudEditorStateMachine.Scope.PERSONAL;
        player.sendMessage(messageManager.requiredComponent(personal
                ? "hud-editor-header-personal" : "hud-editor-header-global"));
        player.sendMessage(editorNavigation(page));
        player.sendMessage(messageManager.requiredComponent("hud-editor-panel",
                componentLabel(selected), editorValues(session)));
        switch (page) {
            case OVERVIEW -> sendOverviewPage(player, session);
            case POSITION -> sendPositionPage(player, session);
            case APPEARANCE -> sendAppearancePage(player, session);
            case PREVIEW -> sendPreviewPage(player, session.preview());
            case PRESETS -> sendPresetPage(player, layout);
            case COMPONENTS -> sendComponentPage(player, session);
        }
        if (!visible) {
            player.sendMessage(messageManager.requiredComponent("hud-editor-pack-required"));
        }
    }

    private Component editorNavigation(final EditorPage active) {
        Component result = Component.empty();
        for (final EditorPage page : EditorPage.values()) {
            result = result.append(button(messageManager.required(page.labelKey),
                            "/hud edit page " + page.id, page == active))
                    .append(Component.space());
        }
        return result;
    }

    private void sendOverviewPage(final Player player, final HudEditorStateMachine.Session session) {
        Component modes = messageManager.requiredComponent("hud-editor-mode-prefix")
                .append(button(messageManager.required("hud-editor-mode-personal"), "/hud edit personal",
                        session.scope() == HudEditorStateMachine.Scope.PERSONAL));
        if (player.hasPermission(Permissions.HUD_EDITOR)) {
            modes = modes.append(Component.space()).append(button(
                    messageManager.required("hud-editor-mode-global"), "/hud edit global",
                    session.scope() == HudEditorStateMachine.Scope.GLOBAL));
        }
        if (session.scope() == HudEditorStateMachine.Scope.PERSONAL) {
            modes = modes.append(messageManager.requiredComponent(hudManager.hasPersonalHudLayout(player)
                    ? "hud-editor-personal-overrides" : "hud-editor-global-inherited"));
        }
        player.sendMessage(modes);
        player.sendMessage(button(messageManager.required("hud-editor-button-undo"), "/hud edit undo")
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-reset"), "/hud edit reset"))
                .append(Component.space())
                .append(button(messageManager.required(session.scope() == HudEditorStateMachine.Scope.PERSONAL
                        ? "hud-editor-button-reset-global" : "hud-editor-button-reset-factory"),
                        "/hud edit reset all")));
        player.sendMessage(button(messageManager.required("hud-editor-button-save"), "/hud edit save")
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-cancel"), "/hud edit cancel")));
    }

    private void sendPositionPage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(messageManager.requiredComponent("hud-editor-position-prefix")
                .append(button(messageManager.required("hud-editor-button-left"), "/hud edit move left"))
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-right"), "/hud edit move right"))
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-up"), "/hud edit move up"))
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-down"), "/hud edit move down"))
                .append(messageManager.requiredComponent("hud-editor-step-prefix"))
                .append(button(messageManager.required("hud-editor-button-step-one"),
                        "/hud edit step 1", session.step() == 1)).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-step-five"),
                        "/hud edit step 5", session.step() == 5)).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-step-ten"),
                        "/hud edit step 10", session.step() == 10)).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-step-fifteen"),
                        "/hud edit step 15", session.step() == 15)));
        Component direct = messageManager.requiredComponent("hud-editor-direct-prefix")
                .append(inputButton("X", "/hud edit set x ")).append(Component.space())
                .append(inputButton("Y", "/hud edit set y "));
        if (session.selected() == HudComponent.GLOBAL) {
            direct = direct.append(Component.space()).append(button(
                            messageManager.required("hud-editor-button-margin-down"), "/hud edit margin -"))
                    .append(Component.space()).append(button(
                            messageManager.required("hud-editor-button-margin-up"), "/hud edit margin +"));
        }
        player.sendMessage(direct);
    }

    private void sendAppearancePage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(messageManager.requiredComponent("hud-editor-scale-prefix")
                .append(button(messageManager.required("hud-editor-button-fine-down"),
                        "/hud edit scale fine down")).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-fine-up"),
                        "/hud edit scale fine up")).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-coarse-down"),
                        "/hud edit scale coarse down")).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-coarse-up"),
                        "/hud edit scale coarse up")).append(Component.space())
                .append(inputButton(messageManager.required("hud-editor-input-scale"),
                        "/hud edit set scale ")));
        if (session.selected() != HudComponent.GLOBAL && session.selected().hideable()) {
            final boolean visible = session.working().componentLayout(session.selected()).visible();
            player.sendMessage(button(messageManager.required(visible
                            ? "hud-editor-button-hide" : "hud-editor-button-show"),
                    "/hud edit visibility"));
        }
    }

    private void sendPreviewPage(final Player player, final HudPreviewSelection preview) {
        player.sendMessage(messageManager.requiredComponent("hud-editor-preview",
                preview.faction(), preview.playerClass(), preview.state()));
        player.sendMessage(previewAxis("hud-editor-axis-faction", preview.faction(), "faction"));
        player.sendMessage(previewAxis("hud-editor-axis-class", preview.playerClass(), "class"));
        player.sendMessage(previewAxis("hud-editor-axis-state", preview.state(), "state"));
    }

    private Component previewAxis(final String labelKey, final String value, final String axis) {
        return messageManager.requiredComponent("hud-editor-preview-axis",
                        messageManager.required(labelKey), value)
                .append(button(messageManager.required("hud-editor-button-preview-previous"),
                        "/hud edit preview " + axis + " previous")).append(Component.space())
                .append(button(messageManager.required("hud-editor-button-preview-next"),
                        "/hud edit preview " + axis + " next"));
    }

    private void sendPresetPage(final Player player, final HudLayoutSnapshot layout) {
        Component presets = messageManager.requiredComponent("hud-editor-presets-prefix");
        for (final HudLayoutPreset preset : HudLayoutPreset.VALUES) {
            presets = presets.append(button(preset.resolution() + "/G" + preset.guiScale(),
                    "/hud edit preset " + preset.id(), presetMatches(layout, preset)))
                    .append(Component.space());
        }
        player.sendMessage(presets);
    }

    private void sendComponentPage(final Player player, final HudEditorStateMachine.Session session) {
        player.sendMessage(button(messageManager.required("hud-editor-button-previous"), "/hud edit previous")
                .append(Component.space())
                .append(button(messageManager.required("hud-editor-button-next"), "/hud edit next")));
        final List<HudComponent> all = HudComponent.editorTargets();
        final List<List<HudComponent>> categories = List.of(
                all.stream().filter(component -> component == HudComponent.GLOBAL
                        || component.parentGroup() == null && !component.isGroup()
                        && component != HudComponent.DK_RUNES).toList(),
                List.of(HudComponent.DK_RUNES),
                all.stream().filter(component -> component == HudComponent.PLAYER_GROUP
                        || component.parentGroup() == HudComponent.PLAYER_GROUP).toList(),
                all.stream().filter(component -> component == HudComponent.TARGET_GROUP
                        || component.parentGroup() == HudComponent.TARGET_GROUP).toList(),
                all.stream().filter(component -> component == HudComponent.PARTY_GROUP
                        || component.parentGroup() == HudComponent.PARTY_GROUP).toList());
        final List<String> labels = List.of("class", "dk", "player", "target", "party");
        for (int category = 0; category < categories.size(); category++) {
            player.sendMessage(messageManager.requiredComponent(
                    "hud-editor-category-" + labels.get(category)));
            final List<HudComponent> targets = categories.get(category);
            for (int offset = 0; offset < targets.size(); offset += 5) {
                Component row = Component.empty();
                for (final HudComponent component : targets.subList(
                        offset, Math.min(offset + 5, targets.size()))) {
                    row = row.append(button(componentLabel(component),
                            "/hud edit select " + component.id(),
                            component == session.selected())).append(Component.space());
                }
                player.sendMessage(row);
            }
        }
    }

    private Component button(final String label, final String command) {
        return button(label, command, false);
    }

    private Component button(final String label, final String command, final boolean active) {
        return messageManager.requiredComponent(active ? "hud-editor-button-active" : "hud-editor-button", label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(messageManager.requiredComponent(
                        "hud-editor-button-hover", command)));
    }

    private Component inputButton(final String label, final String commandPrefix) {
        return messageManager.requiredComponent("hud-editor-input-button", label)
                .clickEvent(ClickEvent.suggestCommand(commandPrefix))
                .hoverEvent(HoverEvent.showText(
                        messageManager.requiredComponent("hud-editor-input-hover")));
    }

    private void sendEditorActionBar(final Player player, final boolean visible) {
        final HudEditorStateMachine.Session session = hudManager.hudEditorSession(player).orElseThrow();
        player.sendActionBar(messageManager.requiredComponent(visible
                        ? "hud-editor-actionbar" : "hud-editor-actionbar-pack-missing",
                messageManager.required(session.scope() == HudEditorStateMachine.Scope.PERSONAL
                        ? "hud-editor-mode-personal" : "hud-editor-mode-global"),
                componentLabel(session.selected()),
                editorValues(session)));
    }

    private String componentLabel(final HudComponent component) {
        final String id = component == null ? "global" : component.id();
        final StringBuilder fallback = new StringBuilder();
        for (final String word : id.split("-")) {
            if (!fallback.isEmpty()) fallback.append(' ');
            fallback.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return messageManager.get("hud-component-" + id, fallback.toString());
    }

    private String editorValues(final HudEditorStateMachine.Session session) {
        final HudLayoutSnapshot layout = session.working();
        if (session.selected() == HudComponent.GLOBAL) {
            return messageManager.required("hud-editor-values-global", layout.xOffsetPixels(),
                    layout.yOffsetPixels(), layout.safeMarginPixels(), layout.scale(), session.step());
        }
        final HudComponentLayout element = layout.componentLayout(session.selected());
        return messageManager.required("hud-editor-values-component", element.xOffsetPixels(),
                element.yOffsetPixels(), element.scale(), messageManager.required(element.visible()
                        ? "hud-editor-state-visible" : "hud-editor-state-hidden"), session.step());
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

    private String displayName(final String section) {
        return switch (section) {
            case HudManager.SECTION_FACTION -> messageManager.get("hud-section-faction", "Frakció");
            case HudManager.SECTION_CURRENCY -> messageManager.get("hud-section-currency", "Valuta");
            case HudManager.SECTION_CLASS -> messageManager.get("hud-section-class", "Kaszt");
            case HudManager.SECTION_RESOURCE -> messageManager.get("hud-section-resource", "Erőforrás-csík");
            case HudManager.SECTION_EVENT -> messageManager.get("hud-section-event", "Esemény sor");
            case HudManager.SECTION_PARTY -> messageManager.get("hud-section-party", "Csapat (party) keret");
            case HudManager.SECTION_ALL -> messageManager.get("hud-section-all", "Teljes HUD");
            default -> section;
        };
    }

    private static HudCommandException error(final String key, final Object... args) {
        return new HudCommandException(key, args);
    }

    private static final class HudCommandException extends IllegalArgumentException {
        private final String key;
        private final Object[] args;

        private HudCommandException(final String key, final Object[] args) {
            this.key = key;
            this.args = args.clone();
        }

        private String key() {
            return key;
        }

        private Object[] args() {
            return args.clone();
        }
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
        OVERVIEW("main", "hud-editor-page-overview"),
        POSITION("position", "hud-editor-page-position"),
        APPEARANCE("appearance", "hud-editor-page-appearance"),
        PREVIEW("preview", "hud-editor-page-preview"),
        PRESETS("presets", "hud-editor-page-presets"),
        COMPONENTS("components", "hud-editor-page-components");

        private final String id;
        private final String labelKey;

        EditorPage(final String id, final String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
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
