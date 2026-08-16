package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.hud.HudComponent;
import hu.taliann.icesmp.hud.HudComponentLayout;
import hu.taliann.icesmp.hud.HudLayoutSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sparse Profile v2 HUD-layout inheritance and malformed-data regressions. */
public final class PlayerProfileHudLayoutRegressionSuite {
    private PlayerProfileHudLayoutRegressionSuite() { }

    public static void main(final String[] args) {
        unchangedFieldsFollowTheGlobalBase();
        onlyPersonalDifferencesArePersisted();
        malformedFieldsFallBackIndependently();
        legacyLayoutKeysAreIgnored();
        System.out.println("PlayerProfile HUD-layout regression suite passed.");
    }

    private static void unchangedFieldsFollowTheGlobalBase() {
        final HudLayoutSnapshot oldGlobal = new HudLayoutSnapshot(10, 16, 20, 4)
                .withComponent(HudComponent.WALLET, new HudComponentLayout(0, 0, 2, true));
        final HudLayoutSnapshot personal = oldGlobal.setX(HudComponent.GLOBAL, 44)
                .setY(HudComponent.WALLET, -7);
        final Map<String, String> saved = PlayerProfileHudPreferenceStore.encodeLayoutOverrides(
                personal, oldGlobal);
        final HudLayoutSnapshot newGlobal = new HudLayoutSnapshot(-12, 31, 28, 9)
                .withComponent(HudComponent.WALLET, new HudComponentLayout(8, 6, 5, true));
        final HudLayoutSnapshot merged = PlayerProfileHudPreferenceStore.applyLayoutOverrides(
                newGlobal, saved);

        check(merged.xOffsetPixels() == 44,
                "a személyesen felülírt globális X-nek stabilnak kell maradnia");
        check(merged.yOffsetPixels() == 31 && merged.safeMarginPixels() == 28
                        && merged.scaleIndex() == 9,
                "a nem módosított globális mezőknek követniük kell az új szerveralapot");
        check(merged.componentLayout(HudComponent.WALLET).xOffsetPixels() == 8
                        && merged.componentLayout(HudComponent.WALLET).yOffsetPixels() == -7
                        && merged.componentLayout(HudComponent.WALLET).scaleIndex() == 5,
                "a komponensenkénti öröklésnek is mezőszintűnek kell maradnia");
    }

    private static void onlyPersonalDifferencesArePersisted() {
        final HudLayoutSnapshot global = HudLayoutSnapshot.defaults();
        final HudLayoutSnapshot personal = global.setScale(HudComponent.GLOBAL, 3.50D)
                .withComponent(HudComponent.EVENT_TEXT,
                        new HudComponentLayout(15, -10, 15, false));
        final Map<String, String> saved = PlayerProfileHudPreferenceStore.encodeLayoutOverrides(
                personal, global);

        check(saved.size() == 5
                        && "3500".equals(saved.get("hud.layout-v2.scale"))
                        && "15".equals(saved.get("hud.layout-v2.event-text.x"))
                        && "-10".equals(saved.get("hud.layout-v2.event-text.y"))
                        && "3500".equals(saved.get("hud.layout-v2.event-text.scale"))
                        && "false".equals(saved.get("hud.layout-v2.event-text.visible")),
                "a Profile v2 csak az öt tényleges személyes eltérést tárolhatja");
        check(PlayerProfileHudPreferenceStore.applyLayoutOverrides(global, saved).equals(personal),
                "a ritka felülírásoknak veszteség nélkül kell visszaállítaniuk az effektív layoutot");
        check(PlayerProfileHudPreferenceStore.encodeLayoutOverrides(global, global).isEmpty(),
                "globális alapra reset után nem maradhat személyes layout-adat");
    }

    private static void malformedFieldsFallBackIndependently() {
        final HudLayoutSnapshot global = new HudLayoutSnapshot(7, 9, 19, 6)
                .withComponent(HudComponent.WALLET, new HudComponentLayout(3, 4, 5, true));
        final Map<String, String> malformed = new LinkedHashMap<>();
        malformed.put("hud.layout-v2.x", "999999");
        malformed.put("hud.layout-v2.y", "-23");
        malformed.put("hud.layout-v2.scale", "3333");
        malformed.put("hud.layout-v2.wallet.x", "nem-szám");
        malformed.put("hud.layout-v2.wallet.y", "11");
        malformed.put("hud.layout-v2.wallet.scale", "3500");
        malformed.put("hud.layout-v2.wallet.visible", "talán");

        final HudLayoutSnapshot merged = PlayerProfileHudPreferenceStore.applyLayoutOverrides(
                global, malformed);
        check(merged.xOffsetPixels() == 7 && merged.yOffsetPixels() == -23
                        && merged.scaleIndex() == 6,
                "hibás globális személyes mező csak önmagában eshet vissza az alapra");
        final HudComponentLayout wallet = merged.componentLayout(HudComponent.WALLET);
        check(wallet.xOffsetPixels() == 3 && wallet.yOffsetPixels() == 11
                        && wallet.scaleIndex() == 15 && wallet.visible(),
                "hibás komponensmező csak önmagában eshet vissza az alapra");
    }

    private static void legacyLayoutKeysAreIgnored() {
        final HudLayoutSnapshot global = HudLayoutSnapshot.defaults();
        final Map<String, String> legacy = Map.of("hud.layout.level-text.x", "-16");
        check(PlayerProfileHudPreferenceStore.applyLayoutOverrides(global, legacy).equals(global),
                "a v2 HUD nem migrálhat vagy olvashat régi layout-kulcsokat");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
