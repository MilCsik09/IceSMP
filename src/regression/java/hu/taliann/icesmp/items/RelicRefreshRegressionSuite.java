package hu.taliann.icesmp.items;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Focused regressions for idempotent Mélytépő ItemMeta attribute refresh. */
public final class RelicRefreshRegressionSuite {

    private RelicRefreshRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        freshModifierHasExpectedContract();
        repeatedReplacementLeavesExactlyOneManagedModifier();
        duplicateLegacyEntriesAreAllSelectedForRemoval();
        foreignModifierSurvivesReplacement();
        defaultAttributeSeedingMergesByStableKey();
        factoryRemovesManagedEntriesBeforeSeedingAndAdding();
        System.out.println("Relic refresh regression suite passed.");
    }

    private static void freshModifierHasExpectedContract() throws Exception {
        final AttributeModifier modifier = RelicItemFactory.metelytepoDamageModifier(5.0D);
        check(RelicItemFactory.METELYTEPO_DAMAGE_KEY.equals(modifier.getKey()),
                "Mélytépő damage modifier key changed");
        check(Math.abs(modifier.getAmount() - 5.0D) < 1.0E-9D,
                "Mélytépő damage modifier amount is not 5.0");
        check(modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER,
                "Mélytépő damage modifier operation changed");

        final Method slotAccessor = AttributeModifier.class.getMethod("getSlotGroup");
        check(EquipmentSlotGroup.MAINHAND.equals(slotAccessor.invoke(modifier)),
                "Mélytépő damage modifier is not restricted to mainhand");
    }

    private static void repeatedReplacementLeavesExactlyOneManagedModifier() {
        List<AttributeModifier> modifiers = new ArrayList<>();
        for (int iteration = 0; iteration < 4; iteration++) {
            modifiers = replaceManaged(modifiers, RelicItemFactory.metelytepoDamageModifier(5.0D));
        }
        check(countManaged(modifiers) == 1,
                "repeated refresh compounded or duplicated the damage modifier");
    }

    private static void duplicateLegacyEntriesAreAllSelectedForRemoval() {
        final List<AttributeModifier> corrupted = List.of(
                RelicItemFactory.metelytepoDamageModifier(2.0D),
                RelicItemFactory.metelytepoDamageModifier(5.0D),
                new AttributeModifier(new NamespacedKey("other", "damage"), 3.0D,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
        check(RelicItemFactory.managedModifiers(
                corrupted, RelicItemFactory.METELYTEPO_DAMAGE_KEY).size() == 2,
                "not every same-key legacy/duplicate modifier would be removed");
    }

    private static void foreignModifierSurvivesReplacement() {
        final AttributeModifier foreign = new AttributeModifier(
                new NamespacedKey("other", "damage"), 3.0D,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        final List<AttributeModifier> refreshed = replaceManaged(
                List.of(foreign, RelicItemFactory.metelytepoDamageModifier(1.0D)),
                RelicItemFactory.metelytepoDamageModifier(5.0D));
        check(refreshed.contains(foreign),
                "refresh removed a non-IceSMP modifier");
        check(countManaged(refreshed) == 1 && refreshed.size() == 2,
                "refresh did not preserve exactly one foreign and one managed modifier");
    }

    private static void defaultAttributeSeedingMergesByStableKey() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/ItemDataFactory.java"));
        final int method = source.indexOf("public static void seedDefaultAttributeModifiers(final Material");
        final int end = source.indexOf("public static boolean applyAttributeModifiers", method);
        check(method >= 0 && end > method, "default attribute seeding method is missing");
        final String body = source.substring(method, end);
        check(body.contains("defaults.entries()")
                        && body.contains("modifier.getKey().equals(entry.getValue().getKey())")
                        && body.contains("meta.addAttributeModifier(entry.getKey(), entry.getValue())"),
                "default attributes are no longer merged idempotently by stable key");
        check(!body.contains("meta.hasAttributeModifiers()"),
                "one foreign modifier must not suppress every vanilla default attribute");
    }

    private static void factoryRemovesManagedEntriesBeforeSeedingAndAdding() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/RelicItemFactory.java"));
        final int method = source.indexOf("private void applyMetelytepoMeta");
        final int removeDamage = source.indexOf(
                "removeManagedAttributeModifiers(meta, Attribute.ATTACK_DAMAGE", method);
        final int removeSpeed = source.indexOf(
                "removeManagedAttributeModifiers(meta, Attribute.ATTACK_SPEED", method);
        final int seed = source.indexOf("seedDefaultAttributeModifiers", method);
        final int addDamage = source.indexOf("metelytepoDamageModifier(damageBonus)", method);
        check(method >= 0 && removeDamage > method && removeSpeed > method
                        && seed > removeDamage && seed > removeSpeed && addDamage > seed,
                "factory no longer removes both stable keys before seeding and re-adding");
        check(!source.substring(method, source.indexOf("private ToolRule", method))
                        .contains("try {"),
                "attribute duplication must not be hidden by try/catch");
    }

    private static List<AttributeModifier> replaceManaged(final List<AttributeModifier> current,
                                                           final AttributeModifier replacement) {
        final List<AttributeModifier> result = new ArrayList<>(current);
        result.removeAll(RelicItemFactory.managedModifiers(
                result, RelicItemFactory.METELYTEPO_DAMAGE_KEY));
        result.add(replacement);
        return result;
    }

    private static long countManaged(final List<AttributeModifier> modifiers) {
        return modifiers.stream()
                .filter(modifier -> RelicItemFactory.METELYTEPO_DAMAGE_KEY.equals(modifier.getKey()))
                .count();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
