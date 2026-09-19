package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.ux.TooltipEngine;
import hu.taliann.icesmp.ux.TooltipPresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        final NamedTextColor accent = color(template.rarity());
        final List<TooltipEngine.Section> sections = new ArrayList<>();

        String typeLine = family(template.family()) + "  •  " + slot(template.slot());
        if (template.isArmorFamilyEquipment()) {
            typeLine += "  •  " + template.armorFamily().displayName();
        }
        typeLine += "  •  Tárgyszint " + instance.itemLevel();
        addSection(sections, TooltipEngine.SectionId.HEADER, 0, false, List.of(
                TooltipPresentation.classification(
                        template.rarity().displayName(), typeLine, accent)));

        final LinkedHashMap<String, Double> totalStats = new LinkedHashMap<>();
        if (template.baseDamage() > 0.0D) {
            totalStats.merge("attack_damage", template.baseDamage(), Double::sum);
        }
        if (template.baseArmor() > 0.0D) {
            totalStats.merge("armor", template.baseArmor(), Double::sum);
        }
        template.fixedStatsAt(stageId).forEach(
                (id, value) -> totalStats.merge(id, value, Double::sum));
        instance.rolls().forEach(
                (id, roll) -> totalStats.merge(id, roll.value(), Double::sum));
        if (!totalStats.isEmpty()) {
            final List<Component> stats = new ArrayList<>();
            totalStats.forEach((id, value) -> stats.add(statLine(id, value)));
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
        addSection(sections, TooltipEngine.SectionId.REQUIREMENTS, 30, true, requirements);

        if (!template.signatureEffectId().isBlank()) {
            final SignatureEffectRegistry.Definition effect =
                    SignatureEffectRegistry.require(template.signatureEffectId());
            final List<Component> effects = new ArrayList<>();
            effects.add(TooltipPresentation.withIcon(
                    TooltipPresentation.Glyph.EFFECT,
                    line(effect.displayName(), accent).decoration(TextDecoration.BOLD, true)));
            for (final String wrapped : wrap(effect.tooltip(), 46)) {
                effects.add(line(wrapped, NamedTextColor.YELLOW));
            }
            final int signatureTier = template.signatureTierAt(stageId);
            if (signatureTier > 1) {
                effects.add(line("Fokozat  " + signatureTier, NamedTextColor.GOLD));
            }
            addSection(sections, TooltipEngine.SectionId.EFFECTS, 40, true, effects);
        }

        final int socketCapacity = template.runeSocketCountAt(stageId);
        if (socketCapacity > 0) {
            final List<Component> sockets = new ArrayList<>();
            sockets.add(TooltipPresentation.classification(
                    "Rúnák " + instance.runes().size() + "/" + socketCapacity, "", accent));
            for (int socket = 0; socket < socketCapacity; socket++) {
                final boolean filled = socket < instance.runes().size();
                final String rune = filled ? displayRune(instance.runes().get(socket)) : "Üres foglalat";
                sockets.add(line(filled ? "◆  " + rune : "◇  " + rune,
                        filled ? accent : NamedTextColor.DARK_GRAY));
            }
            addSection(sections, TooltipEngine.SectionId.SOCKETS, 50, true, sockets);
        }

        final List<Component> equipment = new ArrayList<>();
        if (!template.setId().isBlank()) {
            final ItemSetDefinition set = templates.requireSet(template.setId());
            equipment.add(TooltipPresentation.classification(
                    "Szett", set.displayName(), accent));
            set.tierStats().forEach((pieces, stats) -> stats.forEach((id, value) ->
                    equipment.add(line(pieces + " db  ", NamedTextColor.DARK_GRAY)
                            .append(statLine(id, value)))));
        }
        if (!template.ascensionPath().isEmpty()) {
            equipment.add(TooltipPresentation.classification(
                    "Felemelkedés", readableId(instance.ascension().stageId()),
                    NamedTextColor.LIGHT_PURPLE));
        }
        addSection(sections, TooltipEngine.SectionId.EQUIPMENT, 60, true, equipment);

        final List<String> authoredLore = template.loreAt(stageId);
        if (!authoredLore.isEmpty()) {
            final List<Component> story = new ArrayList<>();
            authoredLore.forEach(text -> wrap(text, 46).forEach(wrapped ->
                    story.add(line(wrapped, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, true))));
            addSection(sections, TooltipEngine.SectionId.STORY, 80, true, story);
        }

        final List<Component> provenance = new ArrayList<>();
        if (instance.origin().crafterId() != null) {
            final String crafter = instance.origin().crafterNameSnapshot().isBlank()
                    ? instance.origin().crafterId().toString() : instance.origin().crafterNameSnapshot();
            provenance.add(line("Készítette  •  " + crafter
                    + (instance.origin().masterwork() ? "  •  Mestermű" : ""),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        } else if (!instance.origin().creationLocation().isBlank()) {
            provenance.add(line("Eredet  •  " + instance.origin().creationLocation(),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        } else if (!instance.origin().sourceId().isBlank()) {
            provenance.add(line("Eredet  •  " + readableId(instance.origin().sourceId()),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
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

    private static Component statLine(final String statId, final double value) {
        final ItemStatCatalog.Definition definition = ItemStatCatalog.require(statId);
        return line(statIcon(definition.id()) + "  ", statAccent(definition.id()))
                .append(line(definition.displayName(), NamedTextColor.GRAY))
                .append(line("  " + statAmount(definition.id(), value),
                        value >= 0.0D ? NamedTextColor.WHITE : NamedTextColor.RED));
    }

    private static String statAmount(final String statId, final double value) {
        final double shown = "movement_speed".equals(statId) ? value * 1000.0D : value;
        String formatted = String.format(Locale.ROOT, "%.2f", Math.abs(shown));
        if (formatted.endsWith(".00")) formatted = formatted.substring(0, formatted.length() - 3);
        else if (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        return (value >= 0.0D ? "+" : "-") + formatted
                + ("movement_speed".equals(statId) ? "%" : "");
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
            case LEGS -> "Nadrág";
            case FEET -> "Lábbeli";
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

    private static String displayRune(final String rune) {
        final String normalized = ItemStatCatalog.normalizeId(rune);
        final String withoutPrefix = normalized.startsWith("runa_")
                ? normalized.substring("runa_".length()) : normalized;
        return readableId(withoutPrefix);
    }
}
