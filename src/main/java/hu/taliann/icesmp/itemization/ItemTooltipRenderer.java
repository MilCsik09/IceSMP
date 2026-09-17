package hu.taliann.icesmp.itemization;

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

    private static final String DIVIDER = "──────────────";

    private ItemTooltipRenderer() {
    }

    public static Component itemName(final String displayName, final ItemRarity rarity) {
        return line(displayName, color(rarity));
    }

    public static List<Component> render(final ItemTemplate template, final ItemInstance instance,
                                         final ItemTemplateRegistry templates) {
        final String stageId = instance.ascension().stageId();
        final ArrayList<Component> lore = new ArrayList<>();

        lore.add(line(template.rarity().displayName() + "  •  Tárgyszint "
                + instance.itemLevel(), color(template.rarity())));
        lore.add(line("▸ " + family(template.family()) + "  /  " + slot(template.slot()),
                NamedTextColor.GRAY));
        if (template.isArmorFamilyEquipment()) {
            lore.add(line("▸ Páncéltípus: " + template.armorFamily().displayName(),
                    NamedTextColor.GRAY));
        }
        if (template.levelRequirementAt(stageId) > 0) {
            lore.add(line("⚑ Harci szint követelmény: " + template.levelRequirementAt(stageId),
                    NamedTextColor.YELLOW));
        }
        addRestriction(lore, "Kaszt", template.classRestrictions());
        addRestriction(lore, "Specializáció", template.specializationRestrictions());
        addRestriction(lore, "Szakma", template.professionRestrictions());

        final List<String> authoredLore = template.loreAt(stageId);
        if (!authoredLore.isEmpty()) {
            lore.add(divider());
            authoredLore.forEach(text -> lore.add(line(text, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true)));
        }

        final Map<String, Double> fixedStats = template.fixedStatsAt(stageId);
        final boolean hasStats = template.baseDamage() > 0.0D || template.baseArmor() > 0.0D
                || !fixedStats.isEmpty() || !instance.rolls().isEmpty();
        if (hasStats) {
            lore.add(divider());
            lore.add(line("✦ Tulajdonságok", NamedTextColor.AQUA));
            if (template.baseDamage() > 0.0D) {
                lore.add(statLine("attack_damage", template.baseDamage(), null));
            }
            if (template.baseArmor() > 0.0D) {
                lore.add(statLine("armor", template.baseArmor(), null));
            }
            fixedStats.forEach((id, value) -> lore.add(statLine(id, value, null)));
            instance.rolls().forEach((id, roll) -> lore.add(statLine(id, roll.value(), roll.quality())));
        }

        if (!template.signatureEffectId().isBlank()) {
            final SignatureEffectRegistry.Definition effect =
                    SignatureEffectRegistry.require(template.signatureEffectId());
            lore.add(divider());
            lore.add(line("✦ " + effect.displayName(), NamedTextColor.GOLD));
            lore.add(line(effect.tooltip(), NamedTextColor.YELLOW));
            final int signatureTier = template.signatureTierAt(stageId);
            if (signatureTier > 1) {
                lore.add(line("Fokozat: " + signatureTier, NamedTextColor.GOLD));
            }
        }

        if (template.runeSocketCountAt(stageId) > 0) {
            final int capacity = template.runeSocketCountAt(stageId);
            lore.add(divider());
            lore.add(line("◆ Rúnahely: " + instance.runes().size() + "/" + capacity,
                    NamedTextColor.AQUA));
            for (int socket = 0; socket < capacity; socket++) {
                final boolean filled = socket < instance.runes().size();
                final String rune = filled ? displayRune(instance.runes().get(socket)) : "üres";
                lore.add(line((filled ? "  ◆ " : "  ◇ ") + (socket + 1) + ". foglalat: " + rune,
                        filled ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            }
        }

        if (!template.setId().isBlank()) {
            final ItemSetDefinition set = templates.requireSet(template.setId());
            lore.add(divider());
            lore.add(line("✧ Szett: " + set.displayName(), NamedTextColor.DARK_GREEN));
            set.tierStats().forEach((pieces, stats) -> stats.forEach((id, value) ->
                    lore.add(line("  " + pieces + " db: ", NamedTextColor.DARK_GRAY)
                            .append(statLine(id, value, null)))));
        }

        if (!template.ascensionPath().isEmpty()) {
            lore.add(divider());
            lore.add(line("⬆ Felemelkedés: " + instance.ascension().stageId(),
                    NamedTextColor.LIGHT_PURPLE));
        }

        lore.add(divider());
        lore.add(line("Forrás: " + instance.origin().sourceId(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        if (!instance.origin().creationLocation().isBlank()) {
            lore.add(line("Készült: " + instance.origin().creationLocation(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        if (instance.origin().crafterId() != null) {
            final String crafter = instance.origin().crafterNameSnapshot().isBlank()
                    ? instance.origin().crafterId().toString() : instance.origin().crafterNameSnapshot();
            lore.add(line("Készítette: " + crafter
                    + (instance.origin().masterwork() ? "  •  Mestermű" : ""), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        return List.copyOf(lore);
    }

    private static void addRestriction(final List<Component> lore, final String label,
                                       final java.util.Set<String> values) {
        if (values == null || values.isEmpty()) return;
        lore.add(line("⚑ " + label + ": " + values.stream().map(ItemTooltipRenderer::readableId)
                .sorted().reduce((left, right) -> left + ", " + right).orElse(""), NamedTextColor.YELLOW));
    }

    private static Component statLine(final String statId, final double value, final Double quality) {
        final String formatted = Math.abs(value - Math.rint(value)) < 0.000_001D
                ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
        final String amount = (value >= 0.0D ? "+" : "") + formatted;
        final ItemStatCatalog.Definition definition = ItemStatCatalog.require(statId);
        final Component result = line(statIcon(definition.id()) + " " + amount + " "
                + definition.displayName(), value >= 0.0D ? NamedTextColor.GRAY : NamedTextColor.RED);
        if (quality == null) return result;
        return result.append(line("  (" + Math.round(quality * 100.0D) + "%)", NamedTextColor.DARK_GRAY));
    }

    private static Component divider() {
        return line(DIVIDER, NamedTextColor.DARK_GRAY);
    }

    private static Component line(final String text, final NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
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
