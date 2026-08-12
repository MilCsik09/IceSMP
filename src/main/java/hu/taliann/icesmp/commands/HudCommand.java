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
import hu.taliann.icesmp.managers.ConfigManager;
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
        final HudEditorAccessPolicy.Decision decision = HudEditorAccessPolicy.decide(playerSender,
                sender.hasPermission(Permissions.HUD_EDITOR), hudManager.hudEditorEnabled());
        if (decision != HudEditorAccessPolicy.Decision.ALLOWED) {
            final String message = switch (decision) {
                case PLAYER_ONLY -> "&cA HUD-editor élő előnézete csak játékosként használható.";
                case NO_PERMISSION -> "&cNincs jogosultságod: " + Permissions.HUD_EDITOR;
                case CONFIG_DISABLED -> "&cA HUD-editor production configkapuja ki van kapcsolva: "
                        + "hud.icesmp-hud.editor.enabled";
                case ALLOWED -> throw new IllegalStateException("unreachable");
            };
            sender.sendMessage(messageManager.get("hud-editor-denied", message));
            return;
        }
        final Player player = (Player) sender;
        if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
            hudManager.beginHudEditor(player);
            final boolean visible = hudManager.refreshHudEditorPreview(player);
            sendEditorPanel(player, visible);
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
            if (hudManager.hudEditorSession(player).isEmpty()) {
                player.sendMessage(Component.text("[HUD] Nincs aktív editor-munkamenet.", NamedTextColor.GRAY));
                return;
            }
            final ConfigManager.BatchApplyResult result = hudManager.saveHudEditor(player);
            if (result == ConfigManager.BatchApplyResult.STALE) {
                player.sendMessage(Component.text("[HUD] A config közben megváltozott; a mentést elutasítottam. "
                        + "Elvetés után nyisd meg újra az editort.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("[HUD] A validált layout elmentve és élőben alkalmazva.",
                        NamedTextColor.GREEN));
            }
            return;
        }

        hudManager.beginHudEditor(player);
        try {
            switch (action) {
                case "move" -> move(player, args);
                case "margin" -> margin(player, args);
                case "step" -> hudManager.setHudEditorStep(player, requireStep(args));
                case "scale" -> scale(player, args);
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
            sendEditorPanel(player, visible);
        } catch (final IllegalArgumentException exception) {
            player.sendMessage(Component.text("[HUD] " + exception.getMessage(), NamedTextColor.RED));
        }
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
        if (args.length < 3) throw new IllegalArgumentException("Használat: /hud edit step <1|5|10>");
        try {
            return Integer.parseInt(args[2]);
        } catch (final NumberFormatException failure) {
            throw new IllegalArgumentException("A lépésköz 1, 5 vagy 10 lehet.");
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
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "faction" -> hudManager.previewHudFaction(player, args[3]);
            case "class" -> hudManager.previewHudClass(player, args[3]);
            case "state" -> hudManager.previewHudState(player, args[3]);
            default -> throw new IllegalArgumentException("A preview tengely faction, class vagy state lehet.");
        }
    }

    private void sendEditorPanel(final Player player, final boolean visible) {
        final HudEditorStateMachine.Session session = hudManager.hudEditorSession(player).orElseThrow();
        final HudLayoutSnapshot layout = session.working();
        final HudComponent selected = session.selected();
        final String values;
        if (selected == HudComponent.GLOBAL) {
            values = "X " + layout.xOffsetPixels() + "  Y " + layout.yOffsetPixels()
                    + "  margó " + layout.safeMarginPixels() + "  méret "
                    + String.format(Locale.ROOT, "%.2f", layout.scale()) + "x";
        } else {
            final HudComponentLayout element = layout.componentLayout(selected);
            values = "relatív X " + element.xOffsetPixels() + "  Y " + element.yOffsetPixels()
                    + "  méret " + String.format(Locale.ROOT, "%.2f", element.scale()) + "x  "
                    + (element.visible() ? "látható" : "rejtett");
        }
        player.sendMessage(Component.text("[HUD editor] ", NamedTextColor.AQUA)
                .append(Component.text(selected.displayName() + " (" + selected.id() + ") • "
                        + values + " • lépés " + session.step(), NamedTextColor.WHITE)));
        Component targetControls = button("◀ elem", "/hud edit previous").append(Component.space())
                .append(button("elem ▶", "/hud edit next"));
        if (selected != HudComponent.GLOBAL) {
            targetControls = targetControls.append(Component.space())
                    .append(button(layout.componentLayout(selected).visible() ? "elrejt" : "megjelenít",
                            "/hud edit visibility"));
        }
        player.sendMessage(targetControls);
        player.sendMessage(button("←", "/hud edit move left").append(Component.space())
                .append(button("→", "/hud edit move right")).append(Component.space())
                .append(button("↑", "/hud edit move up")).append(Component.space())
                .append(button("↓", "/hud edit move down")));
        if (selected == HudComponent.GLOBAL) {
            player.sendMessage(button("margó −", "/hud edit margin -").append(Component.space())
                    .append(button("margó +", "/hud edit margin +")));
        }
        player.sendMessage(button("1px", "/hud edit step 1").append(Component.space())
                .append(button("5px", "/hud edit step 5")).append(Component.space())
                .append(button("10px", "/hud edit step 10")).append(Component.space())
                .append(button("finom −", "/hud edit scale fine down")).append(Component.space())
                .append(button("finom +", "/hud edit scale fine up")).append(Component.space())
                .append(button("durva −", "/hud edit scale coarse down")).append(Component.space())
                .append(button("durva +", "/hud edit scale coarse up")));
        player.sendMessage(Component.text("Preview: " + session.preview().faction() + " / "
                + session.preview().playerClass() + " / " + session.preview().state(), NamedTextColor.GRAY));
        player.sendMessage(button("visszavonás", "/hud edit undo").append(Component.space())
                .append(button("elem reset", "/hud edit reset")).append(Component.space())
                .append(button("minden reset", "/hud edit reset all")).append(Component.space())
                .append(button("MENTÉS", "/hud edit save")).append(Component.space())
                .append(button("ELVETÉS", "/hud edit cancel")));
        if (!visible) {
            player.sendMessage(Component.text("Az előnézethez a szerver resource packjének sikeresen be kell "
                    + "töltődnie; a natív fallback közben változatlanul aktív.", NamedTextColor.YELLOW));
        }
    }

    private static Component button(final String label, final String command) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)));
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
            if (source.getSender().hasPermission(Permissions.HUD_EDITOR)) options.add(EDIT);
        } else if (TOGGLE.equalsIgnoreCase(args[0]) && args.length == 2) {
            options.addAll(HudManager.SECTIONS);
            options.add(HudManager.SECTION_ALL);
        } else if (EDIT.equalsIgnoreCase(args[0])) {
            editorSuggestions(args, options);
        }
        final String prefix = prefixAt(args, Math.max(0, args.length - 1));
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private static void editorSuggestions(final String[] args, final List<String> options) {
        if (args.length == 2) options.addAll(List.of("status", "select", "previous", "next", "move",
                "margin", "step", "scale", "visibility", "preset", "preview", "undo", "reset",
                "save", "cancel"));
        else if (args.length == 3 && "select".equalsIgnoreCase(args[1]))
            options.addAll(HudComponent.editorTargets().stream().map(HudComponent::id).toList());
        else if (args.length == 3 && "move".equalsIgnoreCase(args[1]))
            options.addAll(List.of("left", "right", "up", "down"));
        else if (args.length == 3 && "margin".equalsIgnoreCase(args[1])) options.addAll(List.of("+", "-"));
        else if (args.length == 3 && "step".equalsIgnoreCase(args[1])) options.addAll(List.of("1", "5", "10"));
        else if (args.length == 3 && "scale".equalsIgnoreCase(args[1])) options.addAll(List.of("fine", "coarse"));
        else if (args.length == 4 && "scale".equalsIgnoreCase(args[1])) options.addAll(List.of("up", "down"));
        else if (args.length == 3 && "preset".equalsIgnoreCase(args[1]))
            options.addAll(HudLayoutPreset.VALUES.stream().map(HudLayoutPreset::id).toList());
        else if (args.length == 3 && "reset".equalsIgnoreCase(args[1])) options.add("all");
        else if (args.length == 3 && "preview".equalsIgnoreCase(args[1]))
            options.addAll(List.of("faction", "class", "state"));
        else if (args.length == 4 && "preview".equalsIgnoreCase(args[1])) {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "faction" -> options.addAll(HudPreviewSelection.FACTIONS);
                case "class" -> options.addAll(HudPreviewSelection.CLASSES);
                case "state" -> options.addAll(HudPreviewSelection.STATES);
                default -> { }
            }
        }
    }
}
