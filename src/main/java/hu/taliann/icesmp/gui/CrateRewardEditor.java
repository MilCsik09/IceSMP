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

/** Pure copy-on-write editor for one complete staged crate reward list. */
public final class CrateRewardEditor {

    private static final double EDITOR_DECIMAL_MINIMUM = 0.01D;

    public record Mutation(List<Map<String, Object>> rewards, String error) {
        public boolean successful() { return error == null; }
        public static Mutation fail(final String error) { return new Mutation(List.of(), error); }
    }

    private CrateRewardEditor() { }

    public static List<Map<String, Object>> rewards(final ConfigManager configManager,
                                                    final String crateId) {
        if (configManager == null || configManager.getConfiguration() == null) return List.of();
        return rewards(configManager.getConfiguration().getList(path(crateId)));
    }

    /** Converts either the live YAML list or a session-staged list to a defensive typed copy. */
    public static List<Map<String, Object>> rewards(final Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof Map<?, ?> map)) return List.of();
            final Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(copy);
        }
        return result;
    }

    public static Map<String, Object> reward(final Object raw, final int index) {
        final List<Map<String, Object>> values = rewards(raw);
        return validIndex(values, index) ? Map.copyOf(values.get(index)) : Map.of();
    }

    public static Mutation addItem(final Object raw) {
        final List<Map<String, Object>> values = mutable(raw);
        if (values.isEmpty()) return Mutation.fail("A jelenlegi rewardlista nem olvasható.");
        if (values.size() >= CrateRules.MAX_REWARDS) {
            return Mutation.fail("Egy crate legfeljebb " + CrateRules.MAX_REWARDS + " jutalmat tartalmazhat.");
        }
        final Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", "item");
        reward.put("weight", 1.0D);
        reward.put("material", "STONE");
        reward.put("amount", 1);
        reward.put("description", "&7Új tárgyjutalom");
        values.add(reward);
        return success(values);
    }

    public static Mutation delete(final Object raw, final int index) {
        final List<Map<String, Object>> values = mutable(raw);
        if (!validIndex(values, index)) return Mutation.fail("A reward már nem létezik.");
        if (values.size() <= 1) return Mutation.fail("A crate legalább egy rewardot kötelezően megtart.");
        values.remove(index);
        return success(values);
    }

    public static Mutation setNumber(final Object raw, final int index,
                                     final String field, final double value) {
        final List<Map<String, Object>> values = mutable(raw);
        if (!validIndex(values, index)) return Mutation.fail("A reward már nem létezik.");
        final Map<String, Object> reward = values.get(index);
        final String type = type(reward);
        try {
            if ("weight".equals(field)) {
                reward.put("weight", CrateRules.positiveWeight(Math.max(EDITOR_DECIMAL_MINIMUM, value)));
            } else if ("amount".equals(field)) {
                if ("command".equals(type)) return Mutation.fail("A command rewardnak nincs amount mezője.");
                if ("currency".equals(type)) {
                    reward.put("amount", CrateRules.currencyAmount(Math.max(EDITOR_DECIMAL_MINIMUM, value)));
                } else if ("recipe-item".equals(type)) {
                    reward.put("amount", CrateRules.boundedPositiveInt((int) Math.round(value), 1,
                            CrateRules.MAX_RECIPE_REWARD_AMOUNT, "amount"));
                } else {
                    reward.put("amount", CrateRules.itemAmount((int) Math.round(value), 1));
                }
            } else return Mutation.fail("Ismeretlen reward számmező: " + field);
        } catch (final IllegalArgumentException invalid) {
            return Mutation.fail(invalid.getMessage());
        }
        return success(values);
    }

    public static Mutation setText(final Object raw, final int index,
                                   final String field, final String input) {
        final List<Map<String, Object>> values = mutable(raw);
        if (!validIndex(values, index)) return Mutation.fail("A reward már nem létezik.");
        final Map<String, Object> reward = values.get(index);
        final String type = type(reward);
        final String value = input == null ? "" : input.strip();
        try {
            switch (field) {
                case "description" -> {
                    if (value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                        return Mutation.fail("A reward leírása legfeljebb 512 karakteres, egysoros szöveg lehet.");
                    }
                    reward.put("description", value);
                }
                case "material" -> {
                    if (!"item".equals(type)) return Mutation.fail("Material csak item rewardnál szerkeszthető.");
                    final Material material = Material.matchMaterial(value);
                    if (material == null || material.isAir()) return Mutation.fail("Ismeretlen vagy AIR material: " + value);
                    reward.put("material", material.name());
                }
                case "command" -> {
                    if (!"command".equals(type)) return Mutation.fail("Command csak command rewardnál szerkeszthető.");
                    reward.put("command", CrateRules.validateCommand(value));
                }
                default -> { return Mutation.fail("Ismeretlen reward szövegmező: " + field); }
            }
        } catch (final IllegalArgumentException invalid) {
            return Mutation.fail(invalid.getMessage());
        }
        return success(values);
    }

    public static Mutation cycleCurrency(final Object raw, final int index) {
        final List<Map<String, Object>> values = mutable(raw);
        if (!validIndex(values, index)) return Mutation.fail("A reward már nem létezik.");
        final Map<String, Object> reward = values.get(index);
        if (!"currency".equals(type(reward))) return Mutation.fail("Valuta csak currency rewardnál váltható.");
        final CurrencyType current = CurrencyType.fromInput(String.valueOf(reward.get("currency")));
        final CurrencyType[] currencies = CurrencyType.values();
        reward.put("currency", currencies[current == null ? 0 : (current.ordinal() + 1) % currencies.length].name());
        return success(values);
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

    public static String path(final String crateId) { return "crates." + crateId + ".rewards"; }

    private static List<Map<String, Object>> mutable(final Object raw) {
        final List<Map<String, Object>> copy = new ArrayList<>();
        for (final Map<String, Object> reward : rewards(raw)) copy.add(new LinkedHashMap<>(reward));
        return copy;
    }

    private static Mutation success(final List<Map<String, Object>> rewards) {
        return new Mutation(rewards.stream().map(reward -> Map.copyOf(new LinkedHashMap<>(reward))).toList(), null);
    }

    private static boolean validIndex(final List<?> values, final int index) {
        return index >= 0 && index < values.size();
    }
}
