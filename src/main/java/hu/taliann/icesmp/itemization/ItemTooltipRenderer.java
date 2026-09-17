package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.ux.TooltipEngine;
import hu.taliann.icesmp.ux.TooltipPresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single presentation authority for canonical item tooltips.
 *
 * <p>This deliberately only changes the client-facing lore. Item identity, rolls and the
 * attribute projection remain owned by {@link ItemIdentityService}; keeping this split means a
 * tooltip refresh can never change gameplay state.</p>
 */
public final class ItemTooltipRenderer {

    private ItemTooltipRenderer() {
    }

    public static Component itemName(final String displayName, final ItemRarity rarity) {
        return line(displayName, color(rarity))
                .decoration(TextDecoration.BOLD, true);
    }

    public static List<Component> render(final ItemTemplate template, final ItemInstance instance,
                                         final ItemTemplateRegistry templates) {
        final String stageId = instance.ascension().stageId();
        final List<TooltipEngine.Section> sections = new ArrayList<>();

        addSection(sections, TooltipEngine.SectionId.HEADER, 0, false, List.of(
                line(template.rarity().displayName().toUpperCase(Locale.ROOT), color(template.rarity()))
                        .decoration(TextDecoration.BOLD, true)
                        .append(line("  •  ", NamedTextColor.DARK_GRAY))
                        .append(line("Tárgyszint " + instance.itemLevel(), NamedTextColor.GRAY))));

        String typeLine = family(template.family()) + "  •  " + slot(template.slot());
        if (template.isArmorFamilyEquipment()) {
            typeLine += "  •  " + template.armorFamily().displayName();
        }
        addSection(sections, TooltipEngine.SectionId.TYPE, 10, false, List.of(
                TooltipPresentation.withIcon(TooltipPresentation.Glyph.TYPE,
                        line(typeLine, NamedTextColor.GRAY))));

        final Map<String, Double> fixedStats = template.fixedStatsAt(stageId);
        final boolean hasStats = template.baseDamage() > 0.0D || template.baseArmor() > 0.0D
                || !fixedStats.isEmpty() || !instance.rolls().isEmpty();
        if (hasStats) {
            final List<Component> stats = new ArrayList<>();
            stats.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.STATS, "Harcértékek", NamedTextColor.GOLD));
            if (template.baseDamage() > 0.0D) {
                stats.add(statLine("attack_damage", template.baseDamage(), null));
            }
            if (template.baseArmor() > 0.0D) {
                stats.add(statLine("armor", template.baseArmor(), null));
            }
            fixedStats.forEach((id, value) -> stats.add(statLine(id, value, null)));
            instance.rolls().forEach((id, roll) ->
                    stats.add(statLine(id, roll.value(), roll.quality())));
            addSection(sections, TooltipEngine.SectionId.PRIMARY_STATS, 20, true, stats);
        }

        final List<Component> requirements = new ArrayList<>();
        if (template.levelRequirementAt(stageId) > 0) {
            requirements.add(requirementLine("Harci szint",
                    Integer.toString(template.levelRequirementAt(stageId))));
        }
        addRestriction(requirements, "Kaszt", template.classRestrictions());
        addRestriction(requirements, "Specializáció", template.specializationRestrictions());
        addRestriction(requirements, "Szakma", template.professionRestrictions());
        if (!requirements.isEmpty()) {
            requirements.add(0, TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.REQUIREMENTS,
                    "Követelmények", NamedTextColor.YELLOW));
            addSection(sections, TooltipEngine.SectionId.REQUIREMENTS, 30, true, requirements);
        }

        if (!template.signatureEffectId().isBlank()) {
            final SignatureEffectRegistry.Definition effect =
                    SignatureEffectRegistry.require(template.signatureEffectId());
            final List<Component> effects = new ArrayList<>();
            effects.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.EFFECT, "Egyedi hatás", NamedTextColor.GOLD));
            effects.add(line(effect.displayName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true));
            effects.add(line(effect.tooltip(), NamedTextColor.YELLOW));
            final int signatureTier = template.signatureTierAt(stageId);
            if (signatureTier > 1) {
                effects.add(line("Fokozat  •  " + signatureTier, NamedTextColor.GOLD));
            }
            addSection(sections, TooltipEngine.SectionId.EFFECTS, 40, true, effects);
        }

        final int socketCapacity = template.runeSocketCountAt(stageId);
        if (socketCapacity > 0) {
            final List<Component> sockets = new ArrayList<>();
            sockets.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.SOCKETS,
                    "Rúnák  " + instance.runes().size() + "/" + socketCapacity,
                    NamedTextColor.AQUA));
            for (int socket = 0; socket < socketCapacity; socket++) {
                final boolean filled = socket < instance.runes().size();
                final String rune = filled ? displayRune(instance.runes().get(socket)) : "Üres foglalat";
                sockets.add(line(filled ? "◆  " + rune : "◇  " + rune,
                        filled ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            }
            addSection(sections, TooltipEngine.SectionId.SOCKETS, 50, true, sockets);
        }

        final List<Component> equipment = new ArrayList<>();
        if (!template.setId().isBlank()) {
            final ItemSetDefinition set = templates.requireSet(template.setId());
            equipment.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.EQUIPMENT,
                    "Szett  •  " + set.displayName(), NamedTextColor.DARK_GREEN));
            set.tierStats().forEach((pieces, stats) -> stats.forEach((id, value) ->
                    equipment.add(line("  " + pieces + " db  ", NamedTextColor.DARK_GRAY)
                            .append(statLine(id, value, null)))));
        }
        if (!template.ascensionPath().isEmpty()) {
            if (!equipment.isEmpty()) equipment.add(Component.empty());
            equipment.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.ASCENSION,
                    "Felemelkedés", NamedTextColor.LIGHT_PURPLE));
            equipment.add(line(readableId(instance.ascension().stageId()),
                    NamedTextColor.LIGHT_PURPLE));
        }
        addSection(sections, TooltipEngine.SectionId.EQUIPMENT, 60, true, equipment);

        final List<String> authoredLore = template.loreAt(stageId);
        if (!authoredLore.isEmpty()) {
            final List<Component> story = new ArrayList<>();
            story.add(TooltipPresentation.sectionHeading(
                    TooltipPresentation.Glyph.STORY, "Történet", NamedTextColor.DARK_PURPLE));
            authoredLore.forEach(text -> story.add(line(text, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true)));
            addSection(sections, TooltipEngine.SectionId.STORY, 80, true, story);
        }

        final List<Component> provenance = new ArrayList<>();
        provenance.add(TooltipPresentation.sectionHeading(
                TooltipPresentation.Glyph.ORIGIN, "Eredet", NamedTextColor.DARK_GRAY));
        provenance.add(line("Forrás  •  " + instance.origin().sourceId(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        if (!instance.origin().creationLocation().isBlank()) {
            provenance.add(line("Hely  •  " + instance.origin().creationLocation(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        if (instance.origin().crafterId() != null) {
            final String crafter = instance.origin().crafterNameSnapshot().isBlank()
                    ? instance.origin().crafterId().toString() : instance.origin().crafterNameSnapshot();
            provenance.add(line("Készítette  •  " + crafter
                    + (instance.origin().masterwork() ? "  •  Mestermű" : ""), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        addSection(sections, TooltipEngine.SectionId.PROVENANCE, 90, true, provenance);

        return TooltipEngine.render(sections);
    }

    private static void addSection(final List<TooltipEngine.Section> sections,
                                   final TooltipEngine.SectionId id, final int order,
                                   final boolean separated, final List<Component> lines) {
        if (lines == null || lines.isEmpty()) return;
        final List<Component> rendered = new ArrayList<>(lines.size() + (separated ? 1 : 0));
        if (separated) rendered.add(Component.empty());
        rendered.addAll(lines);
        sections.add(TooltipEngine.Section.of(id, order, rendered));
    }

    private static void addRestriction(final List<Component> lore, final String label,
                                       final java.util.Set<String> values) {
        if (values == null || values.isEmpty()) return;
        lore.add(requirementLine(label, values.stream().map(ItemTooltipRenderer::readableId)
                .sorted().reduce((left, right) -> left + ", " + right).orElse("")));
    }

    private static Component requirementLine(final String label, final String value) {
        return line("◇  ", NamedTextColor.DARK_GRAY)
                .append(line(label + "  ", NamedTextColor.GRAY))
                .append(line(value, NamedTextColor.YELLOW));
    }

    private static Component statLine(final String statId, final double value, final Double quality) {
        final String formatted = Math.abs(value - Math.rint(value)) < 0.000_001D
                ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
        final String amount = (value >= 0.0D ? "+" : "") + formatted;
        final ItemStatCatalog.Definition definition = ItemStatCatalog.require(statId);
        Component result = line(statIcon(definition.id()) + "  ", statAccent(definition.id()))
                .append(line(definition.displayName(), NamedTextColor.GRAY))
                .append(line("  " + amount,
                        value >= 0.0D ? NamedTextColor.WHITE : NamedTextColor.RED));
        if (quality != null) {
            result = result.append(line("  •  " + Math.round(quality * 100.0D) + "%",
                    NamedTextColor.DARK_GRAY));
        }
        return result;
    }

    private static Component line(final String text, final NamedTextColor color) {
        return TooltipPresentation.line(text, color);
    }

    private static String statIcon(final String id) {
        return switch (id) {
            case "attack_damage" -> "⚔";
            case "attack_speed" -> "↯";
            case "ability_power" -> "✦";
            case "max_health" -> "♥";
            case "armor", "armor_toughness" -> "◆";
            case "knockback_resistance" -> "◈";
            case "movement_speed" -> "»";
            default -> "•";
        };
    }

    private static NamedTextColor statAccent(final String id) {
        return switch (id) {
            case "attack_damage" -> NamedTextColor.RED;
            case "attack_speed" -> NamedTextColor.GOLD;
            case "ability_power" -> NamedTextColor.LIGHT_PURPLE;
            case "max_health" -> NamedTextColor.RED;
            case "armor", "armor_toughness" -> NamedTextColor.AQUA;
            case "knockback_resistance" -> NamedTextColor.BLUE;
            case "movement_speed" -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    private static String family(final ItemTemplate.Family family) {
        return switch (family) {
            case WEAPON -> "Fegyver";
            case ARMOR -> "Páncél";
            case TOOL -> "Eszköz";
            case ACCESSORY -> "Kiegészítő";
            case CONSUMABLE -> "Fogyasztható";
            case MATERIAL -> "Alapanyag";
            case OTHER -> "Tárgy";
        };
    }

    private static String slot(final ItemTemplate.Slot slot) {
        return switch (slot) {
            case HEAD -> "Fej";
            case CHEST -> "Mellkas";
            case LEGS -> "Láb";
            case FEET -> "Lábfej";
            case MAIN_HAND -> "Főkéz";
            case OFF_HAND -> "Offhand";
            case TWO_HAND -> "Kétkezes";
            case ACCESSORY -> "Kiegészítő";
            case BODY -> "Test";
            case NONE -> "Általános";
        };
    }

    private static String readableId(final String raw) {
        final String value = raw == null ? "" : raw.substring(raw.lastIndexOf(':') + 1)
                .replace('_', ' ').replace('-', ' ');
        if (value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static NamedTextColor color(final ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.BLUE;
            case EPIC -> NamedTextColor.DARK_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
            case MYTHIC -> NamedTextColor.RED;
        };
    }

    private static String displayRune(final String rune) {
        final String normalized = ItemStatCatalog.normalizeId(rune);
        final String withoutPrefix = normalized.startsWith("runa_")
                ? normalized.substring("runa_".length()) : normalized;
        return readableId(withoutPrefix);
    }
}
