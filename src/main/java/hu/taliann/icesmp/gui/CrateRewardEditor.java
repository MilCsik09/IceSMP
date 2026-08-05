package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure copy-on-write editor for one crate's reward object list. No partial map is ever published:
 * every successful operation returns a complete replacement list for one config override.
 */
public final class CrateRewardEditor {

    private static final double EDITOR_DECIMAL_MINIMUM = 0.01D;

    public record Mutation(List<Map<String, Object>> rewards, String error) {
        public boolean successful() {
            return error == null;
        }

        public static Mutation fail(final String error) {
            return new Mutation(List.of(), error);
        }
    }

    private CrateRewardEditor() {
    }

    public static List<Map<String, Object>> rewards(final ConfigManager configManager,
                                                    final String crateId) {
        if (configManager == null || crateId == null || crateId.isBlank()
                || configManager.getConfiguration() == null) {
            return List.of();
        }
        final List<?> raw = configManager.getConfiguration().getList(path(crateId));
        if (raw == null) {
            return List.of();
        }
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) {
                return List.of();
            }
            final Map<String, Object> copy = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            result.add(copy);
        }
        return result;
    }

    public static Map<String, Object> reward(final ConfigManager configManager,
                                             final String crateId, final int index) {
        final List<Map<String, Object>> rewards = rewards(configManager, crateId);
        return index >= 0 && index < rewards.size() ? Map.copyOf(rewards.get(index)) : Map.of();
    }

    public static Mutation addItem(final ConfigManager configManager, final String crateId) {
        final List<Map<String, Object>> rewards = mutable(configManager, crateId);
        if (rewards.isEmpty()) {
            return Mutation.fail("A jelenlegi rewardlista nem olvasható.");
        }
        if (rewards.size() >= CrateRules.MAX_REWARDS) {
            return Mutation.fail("Egy crate legfeljebb " + CrateRules.MAX_REWARDS + " jutalmat tartalmazhat.");
        }
        final Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", "item");
        reward.put("weight", 1.0D);
        reward.put("material", "STONE");
        reward.put("amount", 1);
        reward.put("description", "&7Új tárgyjutalom");
        rewards.add(reward);
        return new Mutation(immutable(rewards), null);
    }

    public static Mutation delete(final ConfigManager configManager, final String crateId,
                                  final int index) {
        final List<Map<String, Object>> rewards = mutable(configManager, crateId);
        if (!validIndex(rewards, index)) {
            return Mutation.fail("A reward már nem létezik vagy a lista nem olvasható.");
        }
        if (rewards.size() <= 1) {
            return Mutation.fail("A crate legalább egy rewardot kötelezően megtart.");
        }
        rewards.remove(index);
        return new Mutation(immutable(rewards), null);
    }

    public static Mutation setNumber(final ConfigManager configManager, final String crateId,
                                     final int index, final String field, final double value) {
        final List<Map<String, Object>> rewards = mutable(configManager, crateId);
        if (!validIndex(rewards, index)) {
            return Mutation.fail("A reward már nem létezik vagy a lista nem olvasható.");
        }
        final Map<String, Object> reward = rewards.get(index);
        final String type = type(reward);
        try {
            if ("weight".equals(field)) {
                reward.put("weight", CrateRules.positiveWeight(
                        Math.max(EDITOR_DECIMAL_MINIMUM, value)));
            } else if ("amount".equals(field)) {
                if ("command".equals(type)) {
                    return Mutation.fail("A command rewardnak nincs szerkeszthető amount mezője.");
                }
                if ("currency".equals(type)) {
                    reward.put("amount", CrateRules.currencyAmount(
                            Math.max(EDITOR_DECIMAL_MINIMUM, value)));
                } else if ("recipe-item".equals(type)) {
                    reward.put("amount", CrateRules.boundedPositiveInt((int) Math.round(value), 1,
                            CrateRules.MAX_RECIPE_REWARD_AMOUNT, "amount"));
                } else {
                    reward.put("amount", CrateRules.itemAmount((int) Math.round(value), 1));
                }
            } else {
                return Mutation.fail("Ismeretlen reward számmező: " + field);
            }
        } catch (final IllegalArgumentException invalid) {
            return Mutation.fail(invalid.getMessage());
        }
        return new Mutation(immutable(rewards), null);
    }

    public static Mutation setText(final ConfigManager configManager, final String crateId,
                                   final int index, final String field, final String raw) {
        final List<Map<String, Object>> rewards = mutable(configManager, crateId);
        if (!validIndex(rewards, index)) {
            return Mutation.fail("A reward már nem létezik vagy a lista nem olvasható.");
        }
        final Map<String, Object> reward = rewards.get(index);
        final String type = type(reward);
        final String value = raw == null ? "" : raw.strip();
        try {
            switch (field) {
                case "description" -> {
                    if (value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                        return Mutation.fail("A reward leírása legfeljebb 512 karakteres, egysoros szöveg lehet.");
                    }
                    reward.put("description", value);
                }
                case "material" -> {
                    if (!"item".equals(type)) {
                        return Mutation.fail("Material mező csak item rewardnál szerkeszthető.");
                    }
                    final Material material = Material.matchMaterial(value);
                    if (material == null || material.isAir()) {
                        return Mutation.fail("Ismeretlen vagy AIR reward material: " + value);
                    }
                    reward.put("material", material.name());
                }
                case "command" -> {
                    if (!"command".equals(type)) {
                        return Mutation.fail("Command mező csak command rewardnál szerkeszthető.");
                    }
                    reward.put("command", CrateRules.validateCommand(value));
                }
                default -> {
                    return Mutation.fail("Ismeretlen reward szövegmező: " + field);
                }
            }
        } catch (final IllegalArgumentException invalid) {
            return Mutation.fail(invalid.getMessage());
        }
        return new Mutation(immutable(rewards), null);
    }

    public static Mutation cycleCurrency(final ConfigManager configManager, final String crateId,
                                         final int index) {
        final List<Map<String, Object>> rewards = mutable(configManager, crateId);
        if (!validIndex(rewards, index)) {
            return Mutation.fail("A reward már nem létezik vagy a lista nem olvasható.");
        }
        final Map<String, Object> reward = rewards.get(index);
        if (!"currency".equals(type(reward))) {
            return Mutation.fail("Valuta csak currency rewardnál váltható.");
        }
        final CurrencyType current = CurrencyType.fromInput(String.valueOf(reward.get("currency")));
        final CurrencyType[] values = CurrencyType.values();
        int position = 0;
        if (current != null) {
            position = (current.ordinal() + 1) % values.length;
        }
        reward.put("currency", values[position].name());
        return new Mutation(immutable(rewards), null);
    }

    public static double numericValue(final Map<String, Object> reward, final String field,
                                      final double fallback) {
        final Object raw = reward.get(field);
        return raw instanceof Number number ? number.doubleValue() : fallback;
    }

    public static String type(final Map<String, Object> reward) {
        return String.valueOf(reward.getOrDefault("type", "item"))
                .strip().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String path(final String crateId) {
        return "crates." + crateId + ".rewards";
    }

    private static List<Map<String, Object>> mutable(final ConfigManager configManager,
                                                     final String crateId) {
        final List<Map<String, Object>> current = rewards(configManager, crateId);
        final List<Map<String, Object>> copy = new ArrayList<>(current.size());
        for (final Map<String, Object> reward : current) {
            copy.add(new LinkedHashMap<>(reward));
        }
        return copy;
    }

    private static List<Map<String, Object>> immutable(final List<Map<String, Object>> rewards) {
        return rewards.stream().map(reward -> Map.copyOf(new LinkedHashMap<>(reward))).toList();
    }

    private static boolean validIndex(final List<?> rewards, final int index) {
        return index >= 0 && index < rewards.size();
    }
}
