package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.client.protocol.HudStatePayload;
import hu.taliann.icesmp.managers.HudManager;

import java.util.List;

/**
 * A meglévő {@code HudManager.HudSnapshot} display-only projekció leképezése a
 * HUD_STATE vezeték-DTO-ra. Tiszta függvény: nem olvas élő player-állapotot, ezért
 * bármely szálról hívható, és dependency-free regresszió fedheti.
 *
 * <p>A lista-mezők a protokoll-limit fölött csonkolódnak (nem hibáznak): a HUD
 * megjelenítési adat, ahol a levágott farok elfogadhatóbb, mint az egész state-üzenet
 * eldobása. A resourceBar szándékosan nem utazik — az a szöveges HUD-backend
 * render-műterméke, a natív kliens a resource/resourceMax párból rajzol.</p>
 */
public final class ClientHudProjector {

    private static final int LIST_LIMIT = hu.taliann.icesmp.client.protocol.ClientProtocol.MAX_LIST_ELEMENTS;

    private ClientHudProjector() {
    }

    public static HudStatePayload project(final HudManager.HudSnapshot snapshot) {
        final ClassHudState classHud = snapshot.classHud();
        return new HudStatePayload(
                snapshot.faction(), snapshot.factionId(), snapshot.factionTheme(),
                snapshot.factionAccent(), snapshot.factionAccentSoft(),
                snapshot.className(), snapshot.classLevel(), snapshot.balance(), snapshot.hasClass(),
                snapshot.resource(), snapshot.resourceMax(), snapshot.resourcePercent(),
                snapshot.resourceName(), snapshot.event(),
                bounded(snapshot.partyLines()),
                bounded(snapshot.currencies()).stream()
                        .map(currency -> new HudStatePayload.Currency(currency.id(),
                                currency.displayName(), currency.amount(), currency.primary()))
                        .toList(),
                classHud == null ? HudStatePayload.ClassHud.empty() : project(classHud));
    }

    private static HudStatePayload.ClassHud project(final ClassHudState state) {
        return new HudStatePayload.ClassHud(
                state.classId(), state.specId(), state.specName(),
                state.mechanicPrimary(), state.mechanicSecondary(),
                state.state(), state.proc(), state.charges(), state.chargesMax(),
                bounded(state.mechanics()),
                bounded(state.metrics()).stream()
                        .map(metric -> new HudStatePayload.Metric(metric.id(), metric.label(),
                                metric.text(), metric.value(), metric.maximum(), metric.state()))
                        .toList(),
                bounded(state.slots()).stream()
                        .map(slot -> new HudStatePayload.Slot(slot.id(), slot.kind(), slot.state(),
                                slot.progress(), slot.label()))
                        .toList());
    }

    private static <T> List<T> bounded(final List<T> values) {
        return values.size() <= LIST_LIMIT ? values : values.subList(0, LIST_LIMIT);
    }
}
