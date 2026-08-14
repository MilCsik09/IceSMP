package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.classspec.integration.ClassHudMetric;
import hu.taliann.icesmp.classspec.integration.ClassHudSlot;
import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.managers.HudManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creates immutable display fixtures without reading or writing a live player. */
public final class HudPreviewCatalog {

    private record FactionFixture(String name, String theme, String accent, String currency) { }
    private record ClassFixture(String name, String spec, String resource, String primary, String secondary) { }

    private static final Map<String, FactionFixture> FACTIONS = Map.of(
            "guest", new FactionFixture("Menedék vendége", "ice", "8BE9FD", "neutral"),
            "red", new FactionFixture("Láng", "ember", "E7683F", "red"),
            "blue", new FactionFixture("Fagy", "frost", "8BE9FD", "blue"),
            "neutral", new FactionFixture("Menedék", "guild", "D6A74B", "neutral"),
            "dark", new FactionFixture("Kitaszított", "lich", "62D7CE", "dark"));

    private static final Map<String, ClassFixture> CLASSES = Map.ofEntries(
            entry("warrior", "Harcos", "Berserker", "Düh", "battle_tempo", "blood_frenzy"),
            entry("evoker", "Evoker", "Augmentáló", "Esszencia", "empower", "resonance"),
            entry("archer", "Íjász", "Mesterlövész", "Fókusz", "wind_read", "precision_chain"),
            entry("shaman", "Sámán", "Elemista", "Maelstrom", "totem_wheel", "maelstrom"),
            entry("monk", "Szerzetes", "Széljáró", "Csi", "flow", "combo_chain"),
            entry("paladin", "Paladin", "Védelem", "Szent Erő", "conviction", "shield_charge"),
            entry("demon_hunter", "Démonvadász", "Pusztítás", "Fájdalom", "load", "pain"),
            entry("druid", "Druida", "Vad", "Természeti Erő", "harmony", "combo"),
            entry("priest", "Pap", "Fegyelem", "Hit", "litany", "shield_web"),
            entry("death_knight", "Halállovag", "Fagyhozó", "Runikus Erő", "rune_wheel", "frost_marks"),
            entry("assassin", "Orgyilkos", "Méregkeverő", "Energia", "opening", "toxin"),
            entry("warlock", "Boszorkánymester", "Démonológus", "Lélekerő", "soul_debt", "demons"),
            entry("wizard", "Varázsló", "Elementalista", "Mana", "runewaving", "attunement"));

    private HudPreviewCatalog() {
    }

    public static IceSmpHudModel model(final HudPreviewSelection selection) {
        final HudPreviewSelection safe = selection == null ? HudPreviewSelection.defaults() : selection;
        final FactionFixture faction = FACTIONS.get(safe.faction());
        final ClassFixture playerClass = CLASSES.get(safe.playerClass());
        final String state = safe.state();
        final int resource = "resource".equals(state) ? 38 : 82;
        final String event = "event".equals(state) ? "Vérhold • RAID • Világboss" : "Vérhold 04:12";
        final List<HudManager.HudCurrency> wallets = List.of(
                currency("red", "Parázsló Parals", "840", faction.currency),
                currency("blue", "Hópihér-veret", "315", faction.currency),
                currency("neutral", "Creutzér", "12.8k", faction.currency),
                currency("dark", "Csontveret", "64", faction.currency));
        return new IceSmpHudModel(faction.name, faction.theme, faction.accent,
                playerClass.name, 42, "12.8k", true, resource, 100, resource,
                playerClass.resource, event, wallets, classState(safe, playerClass));
    }

    private static HudManager.HudCurrency currency(final String id, final String name,
                                                   final String amount, final String primaryId) {
        return new HudManager.HudCurrency(id, name, amount, id.equals(primaryId));
    }

    private static ClassHudState classState(final HudPreviewSelection selection,
                                            final ClassFixture playerClass) {
        if ("dk-runes".equals(selection.state()) || "death_knight".equals(selection.playerClass())) {
            final ClassHudMetric runes = ClassHudMetric.value(
                    "rune_wheel", "Rúnakör", "Rúnák", 4, 6, "active");
            final ClassHudMetric frostMarks = ClassHudMetric.value(
                    "frost_marks", "Fagyjel", "Fagyjel 3", 3, 5, "ready");
            return new ClassHudState("death_knight", "frost", "Fagyhozó", "Rúnák",
                    "Fagyjel 3", "harc", "Dérrobbanás kész", 4, 6,
                    List.of("Rúnák", "Fagyjel"), List.of(runes, frostMarks),
                    List.of(new ClassHudSlot("r1", "blood", "ready", 100, "Vér"),
                            new ClassHudSlot("r2", "blood", "spent", 0, "Vér"),
                            new ClassHudSlot("r3", "frost", "regenerating", 55, "Fagy"),
                            new ClassHudSlot("r4", "frost", "ready", 100, "Fagy"),
                            new ClassHudSlot("r5", "death", "locked", 0, "Halál"),
                            new ClassHudSlot("r6", "death", "ready", 100, "Halál")));
        }
        if ("wizard-attunement".equals(selection.state()) || "wizard".equals(selection.playerClass())) {
            final ClassHudMetric runewaving = ClassHudMetric.value(
                    "runewaving", "Rúnaszövés", "Rúnaszövés 4", 4, 5, "active");
            final ClassHudMetric attunement = ClassHudMetric.value(
                    "attunement", "Hangolás", "Tűz 72", 72, 100, "ready");
            return new ClassHudState("wizard", "elementalist", "Elementalista", "Rúnaszövés 4",
                    "Tűz 72", "Tűz > Fagy > Vihar", "Elemi túltöltés", 3, 5,
                    List.of("Rúnaszövés", "Hangolás"),
                    List.of(runewaving, attunement,
                            ClassHudMetric.value("attunement_fire", "Tűz", "72", 72, 100, "fire"),
                            ClassHudMetric.value("attunement_frost", "Fagy", "48", 48, 100, "frost"),
                            ClassHudMetric.value("attunement_arcane", "Arkán", "31", 31, 100, "arcane")),
                    ClassHudSlot.charges("rune", "runewaving", "Rúna", 3, 5));
        }
        final boolean noSpec = "spec".equals(selection.state());
        final String proc = "proc".equals(selection.state()) ? "PROC: tökéletes" : "Aktív";
        final int charges = "charges".equals(selection.state()) ? 2 : 4;
        final ArrayList<ClassHudMetric> metrics = new ArrayList<>();
        metrics.add(ClassHudMetric.value(playerClass.primary, "Fő mechanika", "72/100", 72, 100, "ready"));
        metrics.add(ClassHudMetric.value(playerClass.secondary, "Spec mechanika", "45/100", 45, 100, "active"));
        return new ClassHudState(selection.playerClass(), noSpec ? "" : "preview",
                noSpec ? "" : playerClass.spec, "Fő mechanika 72", noSpec ? "" : "Spec mechanika 45",
                "harc", proc, charges, 5, List.of(playerClass.primary, playerClass.secondary),
                List.copyOf(metrics), ClassHudSlot.charges("charge", playerClass.primary,
                "Töltet", charges, 5));
    }

    private static Map.Entry<String, ClassFixture> entry(final String id, final String name,
                                                         final String spec, final String resource,
                                                         final String primary, final String secondary) {
        return Map.entry(id, new ClassFixture(name, spec, resource, primary, secondary));
    }
}
