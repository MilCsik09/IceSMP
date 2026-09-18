package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.dev.artifact.DevArtifactDefinition;
import hu.taliann.icesmp.dev.artifact.DevArtifactPresentation;
import hu.taliann.icesmp.dev.artifact.WorldWeaverArtifactBehavior;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.relics.RelicDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared profile renderer for special-purpose IceSMP items.
 *
 * <p>Only presentation lives here. Identity, PDC, recipe/relic/artifact authority and all gameplay
 * state remain owned by their existing factories and managers.</p>
 */
public final class SpecialItemTooltipRenderer {

    public enum Profile {
        BLUEPRINT,
        PROFESSION_MATERIAL,
        PROFESSION_RESULT,
        CURRENCY,
        MONEY_POUCH,
        RELIC,
        DEVELOPER_ARTIFACT,
        DEBUG_PROBE,
        QUEST,
        TOKEN
    }

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private SpecialItemTooltipRenderer() {
    }

    public static List<Component> blueprint(final ProfessionRecipeCatalog.Recipe recipe) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.BLUEPRINT,
                        badge("TERVRAJZ", NamedTextColor.AQUA)
                                .append(TooltipPresentation.line("  •  ", NamedTextColor.DARK_GRAY))
                                .append(recipe.profession().getDisplayName()
                                        .decoration(TextDecoration.ITALIC, false)))));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Feloldás", NamedTextColor.AQUA),
                labelled("Recept", recipe.displayName(), NamedTextColor.WHITE),
                labelled("Kategória", recipe.category(), NamedTextColor.GRAY)));
        final List<Component> potion = professionPotionEffects(potionEffects);
        if (!potion.isEmpty()) {
            add(sections, TooltipEngine.SectionId.PRIMARY_STATS, 25, true, potion);
        }
        add(sections, TooltipEngine.SectionId.REQUIREMENTS, 30, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.REQUIREMENTS, "Követelmény",
                        NamedTextColor.AQUA),
                labelled("Szakmaszint", Integer.toString(recipe.level()),
                        NamedTextColor.YELLOW)));
        add(sections, TooltipEngine.SectionId.CUSTOM, 40, true, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.STORY,
                        TooltipPresentation.line("Jobb katt • megtanulod a receptet.",
                                NamedTextColor.GRAY))));
        addAuthoredLore(sections, recipe.lore(), 80);
        return TooltipEngine.render(sections);
    }

    public static List<Component> professionResult(final ProfessionRecipeCatalog.Recipe recipe,
                                                   final List<String> potionEffects) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.PROFESSION,
                        badge("SZAKMAI TÁRGY", NamedTextColor.GOLD)
                                .append(TooltipPresentation.line("  •  ", NamedTextColor.DARK_GRAY))
                                .append(recipe.profession().getDisplayName()
                                        .decoration(TextDecoration.ITALIC, false)))));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Szerep", NamedTextColor.GOLD),
                labelled("Kategória", recipe.category(), NamedTextColor.WHITE),
                labelled("Típus", humanize(recipe.kind()), NamedTextColor.GRAY)));
        add(sections, TooltipEngine.SectionId.REQUIREMENTS, 30, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.REQUIREMENTS, "Követelmény",
                        NamedTextColor.GOLD),
                labelled("Szakmaszint", Integer.toString(recipe.level()),
                        NamedTextColor.YELLOW)));
        addAuthoredLore(sections, recipe.lore(), 80);
        return TooltipEngine.render(sections);
    }

    public static List<Component> professionMaterial(final ConfigurationSection material) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        final String profession = material == null
                ? "" : material.getString("primary-profession", "").trim();
        Component type = badge("SZAKMAI ALAPANYAG", NamedTextColor.GOLD);
        if (!profession.isBlank()) {
            type = type.append(TooltipPresentation.line(
                    "  •  " + professionName(profession), NamedTextColor.GRAY));
        }
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.PROFESSION, type)));

        if (material != null) {
            final List<Component> usage = new ArrayList<>();
            final List<String> sources = material.getStringList("source-types");
            if (!sources.isEmpty()) {
                usage.add(labelled("Forrás",
                        sources.stream().limit(2)
                                .map(SpecialItemTooltipRenderer::humanizeTag)
                                .reduce((left, right) -> left + " / " + right)
                                .orElse("Ismeretlen"),
                        NamedTextColor.GRAY));
            }
            if (!profession.isBlank()) {
                usage.add(labelled("Feldolgozza", professionName(profession),
                        NamedTextColor.YELLOW));
            }
            final List<String> sinks = material.getStringList("sink-types");
            if (!sinks.isEmpty()) {
                usage.add(labelled("Felhasználás",
                        sinks.stream().limit(3)
                                .map(SpecialItemTooltipRenderer::humanizeTag)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse("Craft"),
                        NamedTextColor.WHITE));
            }
            if (!usage.isEmpty()) {
                usage.add(0, TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Szakmai szerep",
                        NamedTextColor.GOLD));
                add(sections, TooltipEngine.SectionId.EFFECTS, 30, true, usage);
            }
            addAuthoredLore(sections, material.getStringList("lore"), 80);
        }
        return TooltipEngine.render(sections);
    }

    public static List<Component> currency(final CurrencyType currency) {
        final NamedTextColor accent = switch (currency) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.AQUA;
            case NEUTRAL -> NamedTextColor.LIGHT_PURPLE;
            case DARK -> NamedTextColor.GRAY;
        };
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.CURRENCY,
                        badge("FIZIKAI VALUTA", accent)
                                .append(TooltipPresentation.line(
                                        "  •  " + currency.toFactionType().getDisplayName(),
                                        NamedTextColor.GRAY)))));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Használat", accent),
                labelled("Bank", "a nálad lévő veretek befizethetők",
                        NamedTextColor.GRAY),
                labelled("Forgalom", "kézben hordozható fizikai pénz",
                        NamedTextColor.GRAY)));
        add(sections, TooltipEngine.SectionId.FLAVOR, 80, true, List.of(
                TooltipPresentation.line(
                                "„Értéke csak addig biztos, amíg elfogadják.”",
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        return TooltipEngine.render(sections);
    }

    public static List<Component> moneyPouch() {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.POUCH,
                        badge("TALÁLT ERSZÉNY", NamedTextColor.GOLD))));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Tartalom",
                        NamedTextColor.GOLD),
                labelled("Valuta", "ismeretlen, amíg ki nem bontod",
                        NamedTextColor.GRAY),
                TooltipPresentation.line("Jobb katt • bontsd ki az erszényt.",
                        NamedTextColor.YELLOW)));
        add(sections, TooltipEngine.SectionId.FLAVOR, 80, true, List.of(
                TooltipPresentation.line("„Valaki elvesztette. Most a tiéd.”",
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        return TooltipEngine.render(sections);
    }

    public static List<Component> relic(final RelicDefinition definition) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.RELIC,
                        badge("RELIKVIA", NamedTextColor.AQUA))));
        if (definition.description() != null && !definition.description().isBlank()) {
            final List<Component> purpose = new ArrayList<>();
            purpose.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.EFFECT, "Rendeltetés",
                    NamedTextColor.AQUA));
            for (final String line : wrap(definition.description(), 42)) {
                purpose.add(TooltipPresentation.line(line, NamedTextColor.GRAY));
            }
            add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, purpose);
        }
        addAuthoredLore(sections, definition.lore(), 80);
        return TooltipEngine.render(sections);
    }

    public static List<Component> developerArtifact(final DevArtifactDefinition definition,
                                                    final DevArtifactPresentation presentation,
                                                    final DevArtifactPresentation.ModelState state) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.DEVELOPER,
                        badge("FEJLESZTŐI EREKLYE", NamedTextColor.LIGHT_PURPLE))));

        final List<Component> role = new ArrayList<>();
        role.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.EFFECT, "Rendeltetés",
                NamedTextColor.LIGHT_PURPLE));
        if (DevItemFactory.BINGULUS_ID.equals(definition.id())) {
            role.add(TooltipPresentation.line("Jutalomgenerátor",
                            NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true));
            role.add(TooltipPresentation.line(
                    "Aktív állapotban időszakosan jutalmat", NamedTextColor.GRAY));
            role.add(TooltipPresentation.line(
                    "készít elő a tulajdonosnak.", NamedTextColor.GRAY));
            role.add(labelled("Működés", "passzív", NamedTextColor.GRAY));
            if (definition.policySource().current().autoRestore()) {
                role.add(labelled("Helyreállítás", "automatikus", NamedTextColor.GREEN));
            }
        } else if (WorldWeaverArtifactBehavior.ID.equals(definition.id())) {
            role.add(TooltipPresentation.line("Világformáló eszköz",
                            NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true));
            role.add(TooltipPresentation.line(
                    "Világállapotok és runtime műveletek", NamedTextColor.GRAY));
            role.add(TooltipPresentation.line(
                    "kontrollált kezelésére.", NamedTextColor.GRAY));
            role.add(labelled("Integritás", "SANDBOX / LIVE_GM", NamedTextColor.LIGHT_PURPLE));
            role.add(labelled("Állapot", artifactState(state), NamedTextColor.GRAY));
        } else {
            role.add(TooltipPresentation.line(
                    "Belső fejlesztői rendszerhez tartozó, tulajdonoshoz kötött artifact.",
                    NamedTextColor.GRAY));
        }
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, role);

        add(sections, TooltipEngine.SectionId.REQUIREMENTS, 30, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.REQUIREMENTS, "Hozzáférés",
                        NamedTextColor.LIGHT_PURPLE),
                labelled("Authority", "csak a kijelölt tulajdonos használhatja",
                        NamedTextColor.GRAY)));

        addAuthoredLore(sections, presentation.lore(), 80);
        return TooltipEngine.render(sections);
    }

    private static List<Component> professionPotionEffects(final List<String> specs) {
        if (specs == null || specs.isEmpty()) return List.of();
        final List<Component> lines = new ArrayList<>();
        lines.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.EFFECT, "Hatás", NamedTextColor.AQUA));
        for (final String raw : specs) {
            if (raw == null || raw.isBlank()) continue;
            final String[] parts = raw.trim().split(":");
            final String effect = potionEffectName(parts[0]);
            int seconds = 30;
            int amplifier = 0;
            try {
                if (parts.length > 1) seconds = Math.max(1, Integer.parseInt(parts[1].trim()));
                if (parts.length > 2) amplifier = Math.max(0, Integer.parseInt(parts[2].trim()));
            } catch (final NumberFormatException ignored) {
                // Presentation follows the same safe defaults as ItemDataFactory.
            }
            Component line = TooltipPresentation.line("✦  " + effect, NamedTextColor.AQUA);
            if (amplifier > 0) {
                line = line.append(TooltipPresentation.line(
                        " " + roman(amplifier + 1), NamedTextColor.WHITE));
            }
            if (!parts[0].equalsIgnoreCase("INSTANT_HEALTH")
                    && !parts[0].equalsIgnoreCase("INSTANT_DAMAGE")) {
                line = line.append(TooltipPresentation.line(
                        "  •  " + formatDuration(seconds), NamedTextColor.GRAY));
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static String potionEffectName(final String raw) {
        if (raw == null) return "Hatás";
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "REGENERATION" -> "Regeneráció";
            case "INSTANT_HEALTH" -> "Azonnali gyógyítás";
            case "INSTANT_DAMAGE" -> "Azonnali sebzés";
            case "STRENGTH" -> "Erő";
            case "SPEED" -> "Sebesség";
            case "FIRE_RESISTANCE" -> "Tűzállóság";
            case "NIGHT_VISION" -> "Éjjellátás";
            case "INVISIBILITY" -> "Láthatatlanság";
            case "RESISTANCE" -> "Ellenállás";
            case "HASTE" -> "Sietség";
            case "JUMP_BOOST" -> "Ugrás";
            case "WATER_BREATHING" -> "Víz alatti légzés";
            default -> humanize(raw);
        };
    }

    private static String formatDuration(final int seconds) {
        final int minutes = seconds / 60;
        final int remainder = seconds % 60;
        return minutes > 0 ? String.format(Locale.ROOT, "%d:%02d", minutes, remainder)
                : seconds + " mp";
    }

    private static String roman(final int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private static Component badge(final String label, final TextColor color) {
        return TooltipPresentation.line(label.toUpperCase(Locale.ROOT), color)
                .decoration(TextDecoration.BOLD, true);
    }

    private static Component labelled(final String label, final String value,
                                      final TextColor valueColor) {
        return TooltipPresentation.line(label + "  ", NamedTextColor.DARK_GRAY)
                .append(TooltipPresentation.line(value, valueColor));
    }

    private static void addAuthoredLore(final List<TooltipEngine.Section> sections,
                                        final List<String> lore, final int order) {
        if (lore == null || lore.isEmpty()) return;
        final List<Component> story = new ArrayList<>();
        story.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.STORY, "Leírás", NamedTextColor.GRAY));
        for (final String raw : lore) {
            if (raw == null || raw.isBlank()) continue;
            story.add(LEGACY.deserialize(raw)
                    .decoration(TextDecoration.ITALIC, false));
        }
        add(sections, TooltipEngine.SectionId.STORY, order, true, story);
    }

    private static void add(final List<TooltipEngine.Section> sections,
                            final TooltipEngine.SectionId id, final int order,
                            final boolean separated, final List<Component> lines) {
        if (lines == null || lines.isEmpty()) return;
        final List<Component> rendered =
                new ArrayList<>(lines.size() + (separated ? 1 : 0));
        if (separated) rendered.add(Component.empty());
        rendered.addAll(lines);
        sections.add(TooltipEngine.Section.of(id, order, rendered));
    }

    private static String humanizeTag(final String raw) {
        if (raw == null || raw.isBlank()) return "Ismeretlen";
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        final int separator = normalized.indexOf(':');
        if (separator < 0) return humanize(normalized);
        final String scope = normalized.substring(0, separator);
        final String detail = humanize(normalized.substring(separator + 1));
        return switch (scope) {
            case "gathering" -> "Gyűjtögetés • " + detail;
            case "profession-processing" -> "Feldolgozás • " + detail;
            case "combat" -> "PvE • " + detail;
            case "fishing" -> "Halászat • " + detail;
            case "mining" -> "Bányászat • " + detail;
            case "hunting" -> "Vadászat • " + detail;
            case "herbalist" -> "Gyógynövény • " + detail;
            case "profession" -> "Szakma • " + professionName(normalized.substring(separator + 1));
            case "catalog" -> "Katalógus • " + detail;
            default -> humanize(scope) + " • " + detail;
        };
    }

    private static String artifactState(final DevArtifactPresentation.ModelState state) {
        if (state == null) return "nyugalmi";
        return switch (state) {
            case IDLE -> "nyugalmi";
            case SUBJECT_LOCKED -> "célpont rögzítve";
            case THREAD_HELD -> "szál megtartva";
            case CANON_ARMED -> "kanonikus művelet élesítve";
        };
    }

    private static List<String> wrap(final String raw, final int width) {
        if (raw == null || raw.isBlank()) return List.of();
        final ArrayList<String> lines = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String word : raw.trim().split("\\s+")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return List.copyOf(lines);
    }

    private static String professionName(final String raw) {
        final ProfessionType profession = ProfessionType.fromId(raw);
        return profession == null ? humanize(raw) : PLAIN.serialize(profession.getDisplayName());
    }

    private static String humanize(final String raw) {
        if (raw == null || raw.isBlank()) return "Ismeretlen";
        final String cleaned = raw.trim().replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
