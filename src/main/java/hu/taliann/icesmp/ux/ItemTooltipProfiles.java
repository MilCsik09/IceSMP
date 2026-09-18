package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.dev.artifact.DevArtifactDefinition;
import hu.taliann.icesmp.dev.artifact.DevArtifactPolicy;
import hu.taliann.icesmp.dev.artifact.DevArtifactPresentation;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.relics.RelicDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Semantic presentation profiles for non-equipment IceSMP items.
 *
 * <p>The factories remain canonical identity/behavior authorities. This class only turns their
 * already-authored metadata into a consistent tooltip hierarchy.</p>
 */
public final class ItemTooltipProfiles {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    public enum Profile {
        BLUEPRINT("blueprint", "TERVRAJZ", TooltipPresentation.Glyph.BLUEPRINT, NamedTextColor.AQUA),
        PROFESSION("profession", "SZAKMAI TÁRGY", TooltipPresentation.Glyph.PROFESSION, NamedTextColor.GOLD),
        CURRENCY("currency", "VALUTA", TooltipPresentation.Glyph.CURRENCY, NamedTextColor.GOLD),
        RELIC("relic", "RELIKVIA", TooltipPresentation.Glyph.RELIC, NamedTextColor.LIGHT_PURPLE),
        DEVELOPER_ARTIFACT("developer_artifact", "FEJLESZTŐI EREKLYE",
                TooltipPresentation.Glyph.DEVELOPER, TextColor.color(255, 85, 220)),
        KEY("key", "KULCS", TooltipPresentation.Glyph.KEY, NamedTextColor.YELLOW);

        private final String styleId;
        private final String label;
        private final TooltipPresentation.Glyph glyph;
        private final TextColor accent;

        Profile(final String styleId, final String label, final TooltipPresentation.Glyph glyph,
                final TextColor accent) {
            this.styleId = styleId;
            this.label = label;
            this.glyph = glyph;
            this.accent = accent;
        }

        public String styleId() { return styleId; }
        public String label() { return label; }
        public TooltipPresentation.Glyph glyph() { return glyph; }
        public TextColor accent() { return accent; }
    }

    private ItemTooltipProfiles() {
    }

    public static String styleId(final Profile profile) {
        return "icesmp:" + Objects.requireNonNull(profile, "profile").styleId();
    }

    public static List<Component> compose(final Profile profile, final String detail,
                                          final List<Component> facts,
                                          final List<Component> usage,
                                          final List<Component> story) {
        Objects.requireNonNull(profile, "profile");
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        final String suffix = detail == null || detail.isBlank() ? "" : "  •  " + detail.trim();
        sections.add(TooltipEngine.generated(TooltipEngine.SectionId.TYPE, 0, List.of(
                TooltipPresentation.withIcon(profile.glyph(),
                        TooltipPresentation.line(profile.label() + suffix, profile.accent())
                                .decoration(TextDecoration.BOLD, true)))));

        addSection(sections, TooltipEngine.SectionId.REQUIREMENTS, 30,
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.REQUIREMENTS, "Adatok", profile.accent()), facts);
        addSection(sections, TooltipEngine.SectionId.EFFECTS, 50,
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Használat", profile.accent()), usage);
        addSection(sections, TooltipEngine.SectionId.STORY, 80,
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.STORY, "Leírás", profile.accent()), story);
        return TooltipEngine.render(sections);
    }

    public static Component fact(final String label, final String value, final TextColor valueColor) {
        if (value == null || value.isBlank()) return Component.empty();
        return TooltipPresentation.line(label + "  ", NamedTextColor.GRAY)
                .append(TooltipPresentation.line(value, valueColor));
    }

    public static Component fact(final String label, final Component value) {
        if (value == null) return Component.empty();
        return TooltipPresentation.line(label + "  ", NamedTextColor.GRAY)
                .append(value.decoration(TextDecoration.ITALIC, false));
    }

    public static List<Component> blueprint(final ProfessionRecipeCatalog.Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        final List<Component> facts = new ArrayList<>();
        facts.add(fact("Szakma", recipe.profession().getDisplayName()));
        facts.add(fact("Szakmaszint", Integer.toString(recipe.level()), NamedTextColor.YELLOW));
        facts.add(fact("Kategória", recipe.category(), NamedTextColor.WHITE));
        if (recipe.faction() != null) {
            facts.add(fact("Frakció", recipe.faction().getFullName(), NamedTextColor.WHITE));
        }
        if (recipe.job() != null && !recipe.job().isBlank()) {
            facts.add(fact("Kaszt", humanize(recipe.job()), NamedTextColor.WHITE));
        }
        if (recipe.lootOnly()) {
            facts.add(fact("Hozzáférés", "ritka zsákmány / jutalom", NamedTextColor.LIGHT_PURPLE));
        }

        final List<Component> usage = List.of(
                TooltipPresentation.line("Jobb katt  •  megtanulod a receptet.", NamedTextColor.AQUA),
                TooltipPresentation.line("Sikeres tanuláskor a tervrajz elfogy.", NamedTextColor.DARK_GRAY));
        return compose(Profile.BLUEPRINT, recipe.displayName(), facts, usage, legacyLines(recipe.lore()));
    }

    public static List<Component> professionMaterial(final ConfigurationSection definition,
                                                      final List<String> sourceHints) {
        Objects.requireNonNull(definition, "definition");
        final List<Component> facts = new ArrayList<>();
        final String tier = definition.getString("tier", "");
        final String state = definition.getString("processing-state", "");
        final String profession = definition.getString("primary-profession", "");
        if (!tier.isBlank()) facts.add(fact("Minőség", humanize(tier), NamedTextColor.GOLD));
        if (!state.isBlank()) facts.add(fact("Állapot", humanize(state), NamedTextColor.WHITE));
        if (!profession.isBlank()) facts.add(fact("Szakma", humanize(profession), NamedTextColor.GOLD));

        final List<Component> usage = new ArrayList<>();
        if (sourceHints != null) {
            for (final String hint : sourceHints) {
                if (hint != null && !hint.isBlank()) {
                    usage.add(TooltipPresentation.line(hint, NamedTextColor.GRAY));
                }
            }
        }
        return compose(Profile.PROFESSION, "ALAPANYAG", facts, usage,
                legacyLines(definition.getStringList("lore")));
    }

    public static Profile professionProfile(final ProfessionRecipeCatalog.Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        final String category = recipe.category() == null ? "" : recipe.category().toLowerCase(Locale.ROOT);
        return recipe.result() == Material.TRIAL_KEY || category.contains("kulcs")
                ? Profile.KEY : Profile.PROFESSION;
    }

    public static List<Component> professionResult(final ProfessionRecipeCatalog.Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        final Profile profile = professionProfile(recipe);
        final List<Component> facts = new ArrayList<>();
        facts.add(fact("Szakma", recipe.profession().getDisplayName()));
        facts.add(fact("Szakmaszint", Integer.toString(recipe.level()), NamedTextColor.YELLOW));
        facts.add(fact("Kategória", recipe.category(), NamedTextColor.WHITE));
        if (recipe.blueprint()) {
            facts.add(fact("Tanulás", "tervrajzból", NamedTextColor.AQUA));
        }
        if (recipe.faction() != null) {
            facts.add(fact("Frakció", recipe.faction().getFullName(), NamedTextColor.WHITE));
        }
        if (recipe.job() != null && !recipe.job().isBlank()) {
            facts.add(fact("Kaszt", humanize(recipe.job()), NamedTextColor.WHITE));
        }

        final List<Component> usage = profile == Profile.KEY
                ? List.of(TooltipPresentation.line(
                        "A hozzá tartozó kapu vagy zár aktiválására szolgál.",
                        NamedTextColor.YELLOW))
                : List.of();
        return compose(profile, recipe.kind() == null ? "" : humanize(recipe.kind()),
                facts, usage, legacyLines(recipe.lore()));
    }

    public static List<Component> currency(final CurrencyType currency) {
        Objects.requireNonNull(currency, "currency");
        final List<Component> facts = List.of(
                fact("Kibocsátó", currency.toFactionType().getFullName(), NamedTextColor.WHITE),
                fact("Forma", "fizikai fizetőeszköz", NamedTextColor.GOLD));
        final List<Component> usage = List.of(
                TooltipPresentation.line("Játékosok között fizikailag átadható.", NamedTextColor.GRAY),
                TooltipPresentation.line("Bankban befizethető a számládra.", NamedTextColor.GRAY));
        final List<Component> story = List.of(
                TooltipPresentation.line("Az IceSMP gazdaságának kézben tartható valutája.",
                        NamedTextColor.DARK_GRAY));
        return compose(Profile.CURRENCY, currency.getDisplayName(), facts, usage, story);
    }

    public static List<Component> moneyPouch() {
        final List<Component> facts = List.of(
                fact("Tartalom", "felbontásig rejtett", NamedTextColor.YELLOW),
                fact("Típus", "talált pénz", NamedTextColor.GOLD));
        final List<Component> usage = List.of(
                TooltipPresentation.line("Jobb katt  •  bontsd ki az erszényt.", NamedTextColor.GOLD),
                TooltipPresentation.line("Fizikai vereteket ad az inventorydba.", NamedTextColor.GRAY));
        final List<Component> story = List.of(
                TooltipPresentation.line("Valami csörög odabent...", NamedTextColor.GRAY),
                TooltipPresentation.line("„Valaki elvesztette. Most a tiéd.”", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true));
        return compose(Profile.CURRENCY, "ERSZÉNY", facts, usage, story);
    }

    public static List<Component> relic(final RelicDefinition definition, final UUID owner) {
        Objects.requireNonNull(definition, "definition");
        final List<Component> facts = new ArrayList<>();
        facts.add(fact("Típus", "világ-egyedi relikvia", NamedTextColor.LIGHT_PURPLE));
        if (owner != null) {
            facts.add(fact("Tulajdon", "tulajdonoshoz kötött", NamedTextColor.WHITE));
        }
        final List<Component> story = new ArrayList<>();
        if (definition.description() != null && !definition.description().isBlank()) {
            story.add(TooltipPresentation.line(definition.description(), NamedTextColor.GRAY));
        }
        story.addAll(legacyLines(definition.lore()));
        return compose(Profile.RELIC, definition.displayName(), facts, List.of(), story);
    }

    public static List<Component> developerArtifact(final DevArtifactDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        final DevArtifactPresentation presentation = definition.presentationSource().current();
        final DevArtifactPolicy policy = definition.policySource().current();
        final List<Component> facts = new ArrayList<>();
        facts.add(fact("Hozzáférés", "kijelölt fejlesztő", NamedTextColor.LIGHT_PURPLE));
        facts.add(fact("Tulajdon", "örökös, tulajdonoshoz kötött", NamedTextColor.WHITE));
        if (policy.autoRestore()) {
            facts.add(fact("Védelem", "automatikus helyreállítás", NamedTextColor.GREEN));
        }
        if (policy.mainHandOnly()) {
            facts.add(fact("Aktiválás", "főkéz", NamedTextColor.YELLOW));
        }

        final List<Component> usage = switch (definition.id()) {
            case "csodalatos_bingulus" -> List.of(
                    TooltipPresentation.line(
                            "Aktív online birtoklás közben időszakos jutalmakat sorsol.",
                            NamedTextColor.LIGHT_PURPLE));
            case "dev_world_weaver" -> List.of(
                    TooltipPresentation.line(
                            "WorldWeaver-sessionök és runtime providerek fejlesztői vezérlésére.",
                            NamedTextColor.LIGHT_PURPLE));
            default -> List.of();
        };
        return compose(Profile.DEVELOPER_ARTIFACT, "DEV ARTIFACT", facts, usage,
                legacyLines(presentation.lore()));
    }

    public static List<Component> crateKey(final String crateName, final List<Component> rewardOdds) {
        final List<Component> facts = List.of(
                fact("Láda", crateName == null || crateName.isBlank() ? "ismeretlen" : crateName,
                        NamedTextColor.YELLOW));
        final List<Component> usage = List.of(
                TooltipPresentation.line("Jobb katt a hozzá tartozó ládán  •  kinyitás",
                        NamedTextColor.YELLOW));
        return compose(Profile.KEY, "LÁDAKULCS", facts, usage,
                rewardOdds == null ? List.of() : rewardOdds);
    }

    public static List<Component> legacyLines(final List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) return List.of();
        final List<Component> result = new ArrayList<>();
        for (final String raw : rawLines) {
            if (raw == null || raw.isBlank()) {
                result.add(Component.empty());
            } else {
                result.add(LEGACY.deserialize(raw)
                        .colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        return List.copyOf(result);
    }

    public static String humanize(final String raw) {
        if (raw == null || raw.isBlank()) return "";
        final String cleaned = raw.trim().replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private static void addSection(final List<TooltipEngine.Section> sections,
                                   final TooltipEngine.SectionId id, final int order,
                                   final Component heading, final List<Component> content) {
        if (content == null || content.isEmpty()) return;
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(heading);
        for (final Component line : content) {
            if (line != null && !line.equals(Component.empty())) {
                lines.add(line);
            } else if (line != null) {
                lines.add(line);
            }
        }
        sections.add(TooltipEngine.generated(id, order, lines));
    }
}
