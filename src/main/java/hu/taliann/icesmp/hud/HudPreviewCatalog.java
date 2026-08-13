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
        final List<HudManager.HudCurrency> wallets = "wallet".equals(state)
                ? List.of(
                        new HudManager.HudCurrency(faction.currency, "Elsődleges", "12.8k", true),
                        new HudManager.HudCurrency("red", "Parázsló Parals", "840", false),
                        new HudManager.HudCurrency("blue", "Hópihér-veret", "315", false),
                        new HudManager.HudCurrency("dark", "Csontveret", "64", false))
                : List.of(new HudManager.HudCurrency(faction.currency, "Elsődleges", "12.8k", true));
        return new IceSmpHudModel(faction.name, faction.theme, faction.accent,
                playerClass.name, 42, "12.8k", true, resource, 100, resource,
                playerClass.resource, event, wallets, classState(safe, playerClass));
    }

    private static ClassHudState classState(final HudPreviewSelection selection,
                                            final ClassFixture playerClass) {
        if ("dk-runes".equals(selection.state()) || "death_knight".equals(selection.playerClass())) {
            return new ClassHudState("death_knight", "frost", "Fagyhozó", "Rúnák V2 F2 H2",
                    "Fagyjel 3/5", "harc", "Dérrobbanás kész", 4, 6,
                    List.of("Rúnák", "Fagyjel"),
                    List.of(ClassHudMetric.value("frost_marks", "Fagyjel", "3/5", 3, 5, "ready")),
                    List.of(new ClassHudSlot("r1", "blood", "ready", 100, "Vér"),
                            new ClassHudSlot("r2", "blood", "spent", 0, "Vér"),
                            new ClassHudSlot("r3", "frost", "regenerating", 55, "Fagy"),
                            new ClassHudSlot("r4", "frost", "ready", 100, "Fagy"),
                            new ClassHudSlot("r5", "death", "locked", 0, "Halál"),
                            new ClassHudSlot("r6", "death", "ready", 100, "Halál")));
        }
        if ("wizard-attunement".equals(selection.state()) || "wizard".equals(selection.playerClass())) {
            return new ClassHudState("wizard", "elementalist", "Elementalista", "Rúnaszövés 4",
                    "Tűz hangolás", "Tűz > Fagy > Vihar", "Elemi túltöltés", 3, 5,
                    List.of("Rúnaszövés", "Hangolás"),
                    List.of(ClassHudMetric.value("attunement", "Hangolás", "TŰZ 72%", 72, 100, "ready"),
                            ClassHudMetric.value("runewaving", "Rúnák", "4/5", 4, 5, "active"),
                            ClassHudMetric.text("court", "Udvar", "3 aktív", "active")),
                    ClassHudSlot.charges("rune", "runewaving", "Rúna", 3, 5));
        }
        final boolean noSpec = "spec".equals(selection.state());
        final String proc = "proc".equals(selection.state()) ? "PROC: tökéletes időzítés" : "Aktív";
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
