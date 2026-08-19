package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpecializationProgressStore;
import hu.taliann.icesmp.prologue.PrologueContentPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Economic meaning for the existing profession specializations.
 * The policy reads the existing Profile v2 specialization authority and applies deterministic,
 * bounded effects only; no random conservation proc exists, so retry cannot manufacture value.
 */
public final class ProfessionSpecializationEconomyPolicy {

    public enum Role {
        NONE,
        PROCESSING_EFFICIENCY,
        PROCESSING_YIELD,
        EQUIPMENT_EXPERTISE,
        CONSUMABLE_EFFICIENCY,
        CONSUMABLE_YIELD,
        SERVICE_EXPERTISE,
        BLUEPRINT_EFFICIENCY
    }

    public record Effect(Role role, String specializationId,
                         double inputMultiplier, double outputMultiplier) {
        public Effect {
            role = role == null ? Role.NONE : role;
            specializationId = specializationId == null ? "" : specializationId;
            if (!Double.isFinite(inputMultiplier) || inputMultiplier <= 0.0D
                    || inputMultiplier > 1.0D) {
                throw new IllegalArgumentException("invalid specialization input multiplier");
            }
            if (!Double.isFinite(outputMultiplier) || outputMultiplier < 1.0D
                    || outputMultiplier > 1.25D) {
                throw new IllegalArgumentException("invalid specialization output multiplier");
            }
        }

        public static Effect none() {
            return new Effect(Role.NONE, "", 1.0D, 1.0D);
        }

        public long adjustInput(final long baseAmount) {
            if (baseAmount <= 0L) return baseAmount;
            return Math.max(1L, (long) Math.ceil(baseAmount * inputMultiplier));
        }

        public List<ItemStack> adjustOutputs(final ProfessionRecipeCatalog.Recipe recipe,
                                             final List<ItemStack> outputs) {
            if (outputMultiplier <= 1.0D || outputs == null || outputs.isEmpty()
                    || recipe.templateId() != null || recipe.affixTier() != null) {
                return outputs;
            }
            long total = 0L;
            for (final ItemStack output : outputs) {
                if (output == null || output.getType().isAir()) return outputs;
                total += output.getAmount();
            }
            final long bonusLong = (long) Math.floor(total * (outputMultiplier - 1.0D));
            if (bonusLong <= 0L || bonusLong > Integer.MAX_VALUE) return outputs;
            final ArrayList<ItemStack> adjusted = new ArrayList<>(outputs.size());
            for (final ItemStack output : outputs) adjusted.add(output.clone());
            final ItemStack first = adjusted.getFirst();
            final long firstAmount = (long) first.getAmount() + bonusLong;
            if (firstAmount > Integer.MAX_VALUE) return outputs;
            first.setAmount((int) firstAmount);
            return List.copyOf(adjusted);
        }
    }

    private static final PlayerProfileSpecializationProgressStore STORE =
            new PlayerProfileSpecializationProgressStore();

    private ProfessionSpecializationEconomyPolicy() { }

    public static Effect effectFor(final Player player,
                                   final ProfessionRecipeCatalog.Recipe recipe) {
        if (player == null || recipe == null) return Effect.none();
        final ConfigManager config = ConfigManager.current();
        if (config != null && !PrologueContentPolicy.specializationAvailable(config)) {
            return Effect.none();
        }
        final ProfessionSpecializationType specialization;
        try {
            specialization = STORE.professionSpecialization(player.getUniqueId()).orElse(null);
        } catch (final RuntimeException unavailableProfile) {
            return Effect.none();
        }
        if (specialization == null
                || specialization.getParentProfession() != recipe.profession()) {
            return Effect.none();
        }

        final Role role = roleOf(specialization);
        if (!applies(specialization, role, recipe)) return Effect.none();
        final double efficiency = bounded(config == null ? 0.10D : config.getDouble(
                "professions.specialization.economy-efficiency-percent", 0.10D), 0.0D, 0.25D);
        final double yield = bounded(config == null ? 0.10D : config.getDouble(
                "professions.specialization.economy-yield-percent", 0.10D), 0.0D, 0.25D);
        final boolean yieldRole = role == Role.PROCESSING_YIELD
                || role == Role.CONSUMABLE_YIELD;
        return new Effect(role, specialization.getId(),
                yieldRole ? 1.0D : 1.0D - efficiency,
                yieldRole ? 1.0D + yield : 1.0D);
    }

    public static Role roleOf(final ProfessionSpecializationType specialization) {
        if (specialization == null) return Role.NONE;
        return switch (specialization) {
            case PROSPECTOR, NATURALIST, FORESTER, TRANSMUTER -> Role.PROCESSING_EFFICIENCY;
            case EXCAVATOR, BOTANIST, CARPENTER, ANGLER -> Role.PROCESSING_YIELD;
            case WEAPONSMITH, ARMORSMITH, ARCANIST -> Role.EQUIPMENT_EXPERTISE;
            case POTION_MASTER, CHEF -> Role.CONSUMABLE_YIELD;
            case BUTCHER -> Role.CONSUMABLE_EFFICIENCY;
            case RUNEKEEPER -> Role.SERVICE_EXPERTISE;
            case TREASURE_HUNTER -> Role.BLUEPRINT_EFFICIENCY;
        };
    }

    private static boolean applies(final ProfessionSpecializationType specialization,
                                   final Role role,
                                   final ProfessionRecipeCatalog.Recipe recipe) {
        return switch (role) {
            case PROCESSING_EFFICIENCY, PROCESSING_YIELD -> isProcessing(recipe);
            case CONSUMABLE_EFFICIENCY, CONSUMABLE_YIELD -> isConsumable(recipe);
            case SERVICE_EXPERTISE -> isService(recipe);
            case BLUEPRINT_EFFICIENCY -> recipe.blueprint();
            case EQUIPMENT_EXPERTISE -> switch (specialization) {
                case ARMORSMITH -> recipe.templateId() != null && isArmorResult(recipe.result());
                case WEAPONSMITH -> recipe.templateId() != null && !isArmorResult(recipe.result());
                case ARCANIST -> recipe.templateId() != null;
                default -> false;
            };
            case NONE -> false;
        };
    }

    private static boolean isProcessing(final ProfessionRecipeCatalog.Recipe recipe) {
        final String kind = recipe.kind() == null ? "" : recipe.kind().toLowerCase(Locale.ROOT);
        return "processing".equals(kind) || "hozam".equals(kind);
    }

    private static boolean isConsumable(final ProfessionRecipeCatalog.Recipe recipe) {
        final String category = recipe.category() == null
                ? "" : recipe.category().toLowerCase(Locale.ROOT);
        return category.contains("étel") || category.contains("ital")
                || category.contains("főzet") || category.contains("consum");
    }

    private static boolean isService(final ProfessionRecipeCatalog.Recipe recipe) {
        final String kind = recipe.kind() == null ? "" : recipe.kind().toLowerCase(Locale.ROOT);
        final String category = recipe.category() == null
                ? "" : recipe.category().toLowerCase(Locale.ROOT);
        return "service".equals(kind) || category.contains("rúna")
                || category.contains("bűvöl") || category.contains("salvage");
    }

    private static boolean isArmorResult(final Material material) {
        if (material == null) return false;
        final String id = material.name();
        return id.endsWith("_HELMET") || id.endsWith("_CHESTPLATE")
                || id.endsWith("_LEGGINGS") || id.endsWith("_BOOTS");
    }

    private static double bounded(final double value, final double minimum, final double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
