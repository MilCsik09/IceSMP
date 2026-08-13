package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.managers.SpellRegistry;
import org.bukkit.entity.Player;

/** Behavior regression for duplicate-id fail-fast registration. */
public final class SpellRegistryRegressionSuite {

    private static int assertions;

    private SpellRegistryRegressionSuite() {
    }

    public static void main(final String[] args) {
        duplicateRegistrationFailsWithoutOverwrite();
        normalizedLookupDoesNotCreateAnotherIdentity();
        System.out.println("Spell registry regression suite passed. assertions=" + assertions);
    }

    private static void duplicateRegistrationFailsWithoutOverwrite() {
        final SpellRegistry registry = new SpellRegistry();
        final Spell first = new FakeSpell("audit_spell", "first");
        final Spell second = new FakeSpell("audit_spell", "second");
        registry.register(first);
        check(registry.getById("audit_spell") == first, "first registration is retained");
        try {
            registry.register(second);
            throw new AssertionError("duplicate registration must fail");
        } catch (final IllegalStateException expected) {
            check(expected.getMessage().contains("Duplicate spell id 'audit_spell'"),
                    "duplicate failure names the conflicting id");
        }
        check(registry.getById("audit_spell") == first,
                "failed duplicate registration never overwrites the original spell");
        check(registry.getAll().size() == 1, "duplicate failure leaves registry cardinality unchanged");
    }

    private static void normalizedLookupDoesNotCreateAnotherIdentity() {
        final SpellRegistry registry = new SpellRegistry();
        final Spell spell = new FakeSpell("mixed_case", "normalized");
        registry.register(spell);
        check(registry.getById(" MIXED_CASE ") == spell,
                "lookup normalization resolves the original registration");
        check(registry.getAll().size() == 1, "normalized lookup creates no duplicate entry");
    }

    private record FakeSpell(String id, String label) implements Spell {
        @Override public String getId() { return id; }
        @Override public String getName() { return label; }
        @Override public int getCooldown() { return 0; }
        @Override public SpellCostType getCostType() { return SpellCostType.HUNGER; }
        @Override public int getCostAmount() { return 0; }
        @Override public void execute(final Player player) { }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
