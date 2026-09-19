package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.crates.CrateFormatting;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.dev.artifact.DevArtifactDefinition;
import hu.taliann.icesmp.dev.artifact.DevArtifactPresentation;
import hu.taliann.icesmp.dev.artifact.WorldWeaverArtifactBehavior;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.managers.CrateManager;
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
        TOKEN,
        KEY,
        UPGRADE,
        UTILITY,
        CAPTURE,
        SIEGE,
        CATALYST
    }

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private SpecialItemTooltipRenderer() {
    }

    public static Profile profileOf(final ConfigurationSection material) {
        if (material == null) return Profile.PROFESSION_MATERIAL;
        final String raw = material.getString("tooltip-profile", "PROFESSION_MATERIAL");
        if (raw == null || raw.isBlank()) return Profile.PROFESSION_MATERIAL;
        try {
            return Profile.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final IllegalArgumentException ignored) {
            return Profile.PROFESSION_MATERIAL;
        }
    }

    public static String styleId(final Profile profile) {
        if (profile == null) return "icesmp:profession";
        return switch (profile) {
            case QUEST -> "icesmp:quest";
            case TOKEN -> "icesmp:token";
            case KEY -> "icesmp:key";
            case UPGRADE -> "icesmp:upgrade";
            case UTILITY -> "icesmp:utility";
            case CAPTURE -> "icesmp:capture";
            case SIEGE -> "icesmp:siege";
            case CATALYST -> "icesmp:catalyst";
            case DEVELOPER_ARTIFACT, DEBUG_PROBE -> "icesmp:developer";
            case RELIC -> "icesmp:ereklye";
            case BLUEPRINT -> "icesmp:blueprint";
            case CURRENCY -> "icesmp:currency_neutral";
            case MONEY_POUCH -> "icesmp:money_pouch";
            case PROFESSION_MATERIAL, PROFESSION_RESULT -> "icesmp:profession";
        };
    }

    public static List<Component> uniqueItem(final ConfigurationSection material) {
        return switch (profileOf(material)) {
            case QUEST -> genericUnique(material, Profile.QUEST,
                    TooltipPresentation.Glyph.QUEST, "KÜLDETÉSI TÁRGY", NamedTextColor.GOLD);
            case TOKEN -> genericUnique(material, Profile.TOKEN,
                    TooltipPresentation.Glyph.TOKEN, "HALADÁSI TÁRGY", NamedTextColor.LIGHT_PURPLE);
            case UPGRADE -> genericUnique(material, Profile.UPGRADE,
                    TooltipPresentation.Glyph.UPGRADE, "FEJLESZTÉS", NamedTextColor.AQUA);
            case UTILITY -> genericUnique(material, Profile.UTILITY,
                    TooltipPresentation.Glyph.UTILITY, "SEGÉDESZKÖZ", NamedTextColor.GRAY);
            default -> professionMaterial(material);
        };
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
        final List<Component> potion = professionPotionEffects(potionEffects);
        if (!potion.isEmpty()) {
            add(sections, TooltipEngine.SectionId.PRIMARY_STATS, 25, true, potion);
        }
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

    public static List<Component> companionItem(final String tag,
                                                    final Component description,
                                                    final Component restriction) {
        final String normalized = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        final String type = switch (normalized) {
            case "pet_armor" -> "TÁRSFELSZERELÉS";
            case "heart", "seal" -> "IDÉZŐ KELLÉK";
            default -> "TÁRSKÖTŐ ESZKÖZ";
        };
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.COMPANION,
                        badge(type, NamedTextColor.GREEN))));
        final List<Component> use = new ArrayList<>();
        use.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.EFFECT, "Rendeltetés", NamedTextColor.GREEN));
        if (description != null) {
            use.add(description.decoration(TextDecoration.ITALIC, false));
        }
        if (restriction != null) {
            use.add(restriction.decoration(TextDecoration.ITALIC, false));
        }
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, use);
        return TooltipEngine.render(sections);
    }

    public static List<Component> siegeWeapon() {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.SIEGE,
                        badge("OSTROMESZKÖZ", NamedTextColor.RED))));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Harci szerep", NamedTextColor.RED),
                labelled("Aktív", "csak ostrom alatt", NamedTextColor.YELLOW),
                labelled("Használat", "jobb katt • pusztító lövés a célpontra",
                        NamedTextColor.WHITE)));
        add(sections, TooltipEngine.SectionId.FLAVOR, 80, true, List.of(
                TooltipPresentation.line(
                                "„A Hetedik Vérháború öröksége — csak háborúban szólal meg.”",
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)));
        return TooltipEngine.render(sections);
    }

    public static List<Component> catalystHeader(final JobType jobType,
                                                  final String evolution,
                                                  final String activeSpec) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        Component type = badge("LÉLEKKAPOCS", NamedTextColor.LIGHT_PURPLE);
        if (jobType != null) {
            type = type.append(TooltipPresentation.line("  •  ", NamedTextColor.DARK_GRAY))
                    .append(jobType.getDisplayName().decoration(TextDecoration.ITALIC, false));
        }
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.CATALYST, type)));
        final List<Component> state = new ArrayList<>();
        state.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.EFFECT, "Állapot", NamedTextColor.LIGHT_PURPLE));
        state.add(labelled("Forma", evolution == null || evolution.isBlank()
                ? "kezdeti" : evolution, NamedTextColor.WHITE));
        state.add(labelled("Aktív út", activeSpec == null || activeSpec.isBlank()
                ? "még nincs specializáció" : activeSpec, NamedTextColor.GRAY));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, state);
        return TooltipEngine.render(sections);
    }

    public static List<Component> crateKey(final String crateName,
                                               final List<CrateManager.RewardOdds> odds) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.KEY,
                        badge("LÁDAKULCS", NamedTextColor.YELLOW))));
        final List<Component> use = new ArrayList<>();
        use.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.EFFECT, "Használat", NamedTextColor.YELLOW));
        if (crateName != null && !crateName.isBlank()) {
            use.add(TooltipPresentation.line("Láda  ", NamedTextColor.DARK_GRAY)
                    .append(LEGACY.deserialize(crateName)
                            .decoration(TextDecoration.ITALIC, false)));
        }
        use.add(TooltipPresentation.line(
                "Jobb katt a megfelelő ládán • kinyitás", NamedTextColor.GRAY));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, use);

        if (odds != null && !odds.isEmpty()) {
            final List<Component> rewards = new ArrayList<>();
            rewards.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.STATS, "Jutalomesélyek", NamedTextColor.YELLOW));
            final int shown = Math.min(3, odds.size());
            for (int index = 0; index < shown; index++) {
                final CrateManager.RewardOdds reward = odds.get(index);
                rewards.add(TooltipPresentation.line("◆  " + reward.description(),
                                NamedTextColor.GRAY)
                        .append(TooltipPresentation.line(
                                "  " + CrateFormatting.decimal(reward.percent()) + "%",
                                NamedTextColor.YELLOW)));
            }
            if (odds.size() > shown) {
                rewards.add(TooltipPresentation.line(
                        "…és további " + (odds.size() - shown) + " jutalom",
                        NamedTextColor.DARK_GRAY));
            }
            add(sections, TooltipEngine.SectionId.CUSTOM, 30, true, rewards);
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
                TooltipPresentation.classification(
                        "VALUTA", currencyIssuer(currency), accent)));

        final List<Component> flavor = new ArrayList<>();
        for (final String line : wrap(currencyLore(currency), 42)) {
            flavor.add(TooltipPresentation.line(line, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        add(sections, TooltipEngine.SectionId.FLAVOR, 80, true, flavor);
        return TooltipEngine.render(sections);
    }

    public static List<Component> moneyPouch() {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.classification(
                        "ERSZÉNY", "", NamedTextColor.GOLD)));
        add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, List.of(
                labelled("Tartalom", "ismeretlen", NamedTextColor.GRAY),
                labelled("Jobb katt", "kinyitás", NamedTextColor.YELLOW)));
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

    private static List<Component> genericUnique(final ConfigurationSection material,
                                                       final Profile profile,
                                                       final TooltipPresentation.Glyph glyph,
                                                       final String badge,
                                                       final NamedTextColor accent) {
        final List<TooltipEngine.Section> sections = new ArrayList<>();
        add(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(glyph, badge(badge, accent))));

        if (material != null) {
            final String description = material.getString("tooltip-description", "").trim();
            final String use = material.getString("tooltip-use", "").trim();
            final List<Component> purpose = new ArrayList<>();
            if (!description.isBlank() || !use.isBlank()) {
                purpose.add(TooltipPresentation.sectionHeading(
                        TooltipPresentation.Glyph.EFFECT, "Rendeltetés", accent));
                if (!description.isBlank()) {
                    for (final String line : wrap(description, 42)) {
                        purpose.add(TooltipPresentation.line(line, NamedTextColor.GRAY));
                    }
                }
                if (!use.isBlank()) {
                    purpose.add(labelled("Használat", use, NamedTextColor.WHITE));
                }
                add(sections, TooltipEngine.SectionId.EFFECTS, 20, true, purpose);
            }
            addAuthoredLore(sections, material.getStringList("lore"), 80);
        }
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

    private static String currencyIssuer(final CurrencyType currency) {
        return switch (currency) {
            case RED, BLUE, NEUTRAL -> currency.toFactionType().getFullName();
            case DARK -> "Thanaopolis";
        };
    }

    private static String currencyLore(final CurrencyType currency) {
        return switch (currency) {
            case RED -> "Vörösrézből vert érme; érintésre mindig enyhén meleg.";
            case BLUE -> "Tiszta jégből és ezüstből vert, hidegen csillanó veret.";
            case NEUTRAL -> "Caldestera Bankárszövetségének messze földön elfogadott pénze.";
            case DARK -> "Thanaopolis felől áramló veret, a Csontszámvevő pénze.";
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
