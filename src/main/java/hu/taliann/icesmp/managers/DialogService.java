package hu.taliann.icesmp.managers;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * P4d — natív, szerver-oldali párbeszéd-ablakok (Mojang Dialog API, 1.21.6+). Resource pack
 * NEM kell: a szerver a klienssel szinkronizálja a dialógust, a válasz a szerverhez fut vissza.
 *
 * <p>Újrahasználható belépők:
 * <ul>
 *   <li>{@link #showNotice} — egyszerű értesítő-ablak egy „rendben" gombbal;</li>
 *   <li>{@link #showConfirm} — megerősítő-ablak (igen/nem), a döntés szerver-oldali callbackkel.</li>
 * </ul>
 *
 * <p>Folia: a {@code player.showDialog(...)} a játékos entitását érinti — a hívó felelőssége,
 * hogy a JÁTÉKOS régió-szálán fusson (a parancsok Brigadier-kontextusa ott van). A megerősítő
 * callback az Audience-en (a kattintó játékoson) fut; ha ott másik entitást érint, hopoljon.
 */
public final class DialogService {

    private DialogService() {
    }

    /** Egyszerű értesítő-ablak: cím + több sor szöveg + alapértelmezett „rendben" gomb. */
    @SuppressWarnings("UnstableApiUsage")
    public static void showNotice(final Player player, final Component title, final List<Component> lines) {
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(toBody(lines))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.notice()));
        player.showDialog(dialog);
    }

    /**
     * Megerősítő-ablak: cím + szöveg + IGEN/NEM gomb. Az IGEN a {@code onConfirm}-ot futtatja
     * (a kattintó játékos szálán); a NEM egyszerűen bezár. A callback egyszer-használatos.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void showConfirm(final Player player, final Component title, final List<Component> lines,
                                   final Component yesLabel, final Component noLabel, final Runnable onConfirm) {
        final ClickCallback.Options options = ClickCallback.Options.builder().build();
        final ActionButton yes = ActionButton.builder(yesLabel)
                .action(DialogAction.customClick((view, audience) -> onConfirm.run(), options))
                .build();
        final ActionButton no = ActionButton.builder(noLabel)
                .action(DialogAction.customClick((view, audience) -> { }, options))
                .build();
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(toBody(lines))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(yes, no)));
        player.showDialog(dialog);
    }

    private static List<DialogBody> toBody(final List<Component> lines) {
        return lines.stream().map(line -> (DialogBody) DialogBody.plainMessage(line)).toList();
    }
}
