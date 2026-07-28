package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.selection.CuboidSelectionService;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, strictly validated snapshot of the native AFK-zone configuration. */
public final class AfkZoneCatalog {

    public static final long MAX_INTERVAL_SECONDS = 86_400L;
    public static final int MAX_ROLL_COUNT = 16;
    public static final double MAX_WEIGHT = 1_000_000.0D;
    public static final long MAX_CONFIGURED_ZONE_VOLUME = 100_000_000L;
    public static final long MAX_CONFIGURED_CURRENCY_REWARD = 1_000_000L;
    public static final int MAX_CONFIGURED_ITEM_AMOUNT = 64;
    private static final Set<String> COMMAND_PLACEHOLDERS = Set.of("player", "uuid", "zone");

    public enum RewardType {
        CURRENCY,
        ITEM,
        COMMAND
    }

    public record Reward(RewardType type, double weight, CurrencyType currency, long currencyAmount,
                         Material material, int itemAmount, String command, String description) {
        public Reward {
            if (type == null || !Double.isFinite(weight) || weight <= 0.0D || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException("Hibás AFK reward type/weight.");
            }
        }
    }

    public record Zone(String id, String displayName, boolean enabled,
                       CuboidSelectionService.Cuboid cuboid, String permission,
                       long intervalMillis, int rollCount, List<Reward> rewards, double totalWeight,
                       String enterMessage, String leaveMessage, String title, String subtitle,
                       String actionbar, String bossbarText, BossBar.Color bossbarColor,
                       BossBar.Overlay bossbarOverlay) {
        public Zone {
            rewards = List.copyOf(rewards);
        }

        public boolean contains(final org.bukkit.Location location) {
            return enabled && cuboid.contains(location);
        }
    }

    public record Snapshot(Map<String, Zone> zones, Map<String, List<String>> errors) {
        public Snapshot {
            zones = Collections.unmodifiableMap(new LinkedHashMap<>(zones));
            final Map<String, List<String>> copied = new LinkedHashMap<>();
            errors.forEach((key, value) -> copied.put(key, List.copyOf(value)));
            errors = Collections.unmodifiableMap(copied);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }

    private AfkZoneCatalog() {
    }

    /** Invalid zones/rewards are isolated and reported; valid sibling zones remain usable. */
    public static Snapshot load(final FileConfiguration config) {
        if (config == null) {
            return Snapshot.empty();
        }
        final Map<String, Zone> zones = new LinkedHashMap<>();
        final Map<String, List<String>> errors = new LinkedHashMap<>();
        final List<String> globalProblems = new ArrayList<>();
        final long maxVolume = boundedPositiveLong(config.get("afk.max-zone-volume"), 1_000_000L,
                1L, MAX_CONFIGURED_ZONE_VOLUME, "afk.max-zone-volume", globalProblems);
        final long maxCurrency = boundedPositiveLong(config.get("afk.max-currency-reward"), 1_000L,
                1L, MAX_CONFIGURED_CURRENCY_REWARD, "afk.max-currency-reward", globalProblems);
        final int maxItemAmount = boundedPositiveInt(config.get("afk.max-item-amount"), 64,
                1, MAX_CONFIGURED_ITEM_AMOUNT, "afk.max-item-amount", globalProblems);
        if (!globalProblems.isEmpty()) {
            errors.put("_global", globalProblems);
        }
        final ConfigurationSection root = config.getConfigurationSection("afk.zones");
        if (root == null) {
            return new Snapshot(zones, errors);
        }
        for (final String rawId : root.getKeys(false)) {
            final String id = normalizeId(rawId);
            final List<String> problems = new ArrayList<>();
            if (!id.equals(rawId)) {
                problems.add("Az azonosító csak kisbetűt, számot, kötőjelet és aláhúzást tartalmazhat: " + rawId);
            }
            final ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                problems.add("A zóna nem YAML-szekció.");
                errors.put(rawId, problems);
                continue;
            }
            // A tombstone felülírja a csomagolt alapdefiníciót is; nincs legacy törlési fájl.
            if (section.getBoolean("deleted", false)) {
                continue;
            }

            final UUID worldId = parseUuid(section.getString("world-uuid"), problems);
            final String worldName = trim(section.getString("world"));
            final World world = worldId == null ? Bukkit.getWorld(worldName) : Bukkit.getWorld(worldId);
            if (world == null) {
                problems.add("A világ nem található: uuid=" + section.getString("world-uuid") + ", név=" + worldName);
            } else if (!worldName.isBlank() && !world.getName().equals(worldName)) {
                problems.add("A world UUID és név eltér: " + world.getName() + " != " + worldName);
            }

            final ConfigurationSection min = section.getConfigurationSection("min");
            final ConfigurationSection max = section.getConfigurationSection("max");
            if (min == null || max == null) {
                problems.add("Hiányzik a min/max cuboid.");
            }
            final CuboidSelectionService.Cuboid cuboid;
            if (world == null || min == null || max == null) {
                cuboid = null;
            } else {
                final int firstX = coordinate(min.get("x"), "min.x", problems);
                final int firstY = coordinate(min.get("y"), "min.y", problems);
                final int firstZ = coordinate(min.get("z"), "min.z", problems);
                final int secondX = coordinate(max.get("x"), "max.x", problems);
                final int secondY = coordinate(max.get("y"), "max.y", problems);
                final int secondZ = coordinate(max.get("z"), "max.z", problems);
                final int minX = Math.min(firstX, secondX);
                final int minY = Math.min(firstY, secondY);
                final int minZ = Math.min(firstZ, secondZ);
                final int maxX = Math.max(firstX, secondX);
                final int maxY = Math.max(firstY, secondY);
                final int maxZ = Math.max(firstZ, secondZ);
                cuboid = new CuboidSelectionService.Cuboid(world.getUID(), world.getName(),
                        minX, minY, minZ, maxX, maxY, maxZ);
                if (cuboid.volume() > maxVolume) {
                    problems.add("A cuboid térfogata " + cuboid.volume() + ", maximum " + maxVolume + ".");
                }
                if (cuboid.minY() < world.getMinHeight() || cuboid.maxY() >= world.getMaxHeight()) {
                    problems.add("A cuboid Y-koordinátája a világ határán kívül esik.");
                }
            }

            final long intervalSeconds = boundedPositiveLong(section.get("reward-interval-seconds"), 600L,
                    1L, MAX_INTERVAL_SECONDS, "reward-interval-seconds", problems);
            final int rollCount = boundedPositiveInt(section.get("roll-count"), 1,
                    1, MAX_ROLL_COUNT, "roll-count", problems);

            final List<Reward> rewards = parseRewards(section.getMapList("rewards"), maxCurrency,
                    maxItemAmount, problems);
            double totalWeight = 0.0D;
            for (final Reward reward : rewards) {
                totalWeight += reward.weight();
            }
            if (!Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
                problems.add("Nincs érvényes, pozitív súlyú jutalom.");
            }

            final BossBar.Color color = parseEnum(BossBar.Color.class,
                    section.getString("bossbar.color", "BLUE"), "bossbar.color", problems);
            final BossBar.Overlay overlay = parseEnum(BossBar.Overlay.class,
                    section.getString("bossbar.overlay", "PROGRESS"), "bossbar.overlay", problems);

            final String permission = trim(section.getString("permission"));
            if (!permission.isBlank() && (!permission.startsWith("icesmp.")
                    || !permission.matches("[a-z0-9._-]+"))) {
                problems.add("A permission az icesmp.* névtérben, kisbetűsen adható meg: " + permission);
            }
            if (!problems.isEmpty()) {
                errors.put(rawId, problems);
                continue;
            }
            zones.put(id, new Zone(id,
                    blankTo(section.getString("display-name"), id),
                    section.getBoolean("enabled", true), cuboid,
                    permission, intervalSeconds * 1000L,
                    rollCount, rewards, totalWeight,
                    section.getString("messages.enter", "&b⌚ Beléptél: &f{zone}&b."),
                    section.getString("messages.leave", "&7Elhagytad: &f{zone}&7."),
                    trim(section.getString("title")), trim(section.getString("subtitle")),
                    trim(section.getString("actionbar")),
                    section.getString("bossbar.text", "⌚ {zone} — {minutes}p {seconds}mp"),
                    color, overlay));
        }
        return new Snapshot(zones, errors);
    }

    /** Deterministic roll input (0 <= unit < 1) keeps the weighted algorithm directly testable. */
    public static Reward pick(final Zone zone, final double unit) {
        if (zone == null || zone.rewards().isEmpty() || !Double.isFinite(unit) || unit < 0.0D || unit >= 1.0D) {
            throw new IllegalArgumentException("Érvénytelen AFK reward roll.");
        }
        final double target = unit * zone.totalWeight();
        double cursor = 0.0D;
        for (final Reward reward : zone.rewards()) {
            cursor += reward.weight();
            if (target < cursor) {
                return reward;
            }
        }
        return zone.rewards().getLast();
    }

    public static String validateCommandTemplate(final String command) {
        final String raw = trim(command);
        if (raw.isBlank()) {
            return "üres command";
        }
        if (raw.length() > 256) {
            return "túl hosszú";
        }
        for (int index = 0; index < raw.length(); index++) {
            if (Character.isISOControl(raw.charAt(index))) {
                return "vezérlőkaraktert tartalmaz";
            }
        }
        int cursor = 0;
        while (cursor < raw.length()) {
            final char current = raw.charAt(cursor);
            if (current == '}') {
                return "árva záró kapcsos zárójel";
            }
            if (current != '{') {
                cursor++;
                continue;
            }
            final int close = raw.indexOf('}', cursor + 1);
            if (close < 0) {
                return "lezáratlan placeholder";
            }
            if (raw.indexOf('{', cursor + 1) >= 0 && raw.indexOf('{', cursor + 1) < close) {
                return "egymásba ágyazott placeholder";
            }
            final String placeholder = raw.substring(cursor + 1, close).toLowerCase(Locale.ROOT);
            if (!COMMAND_PLACEHOLDERS.contains(placeholder)) {
                return "nem engedélyezett placeholder: {" + placeholder + "}";
            }
            cursor = close + 1;
        }
        return null;
    }

    static List<Reward> parseRewards(final List<Map<?, ?>> maps, final long maxCurrency,
                                              final int maxItemAmount, final List<String> problems) {
        final List<Reward> rewards = new ArrayList<>();
        for (int index = 0; index < maps.size(); index++) {
            final Map<?, ?> map = maps.get(index);
            final String prefix = "rewards[" + index + "]: ";
            final RewardType type;
            try {
                type = RewardType.valueOf(String.valueOf(value(map, "type", "")).toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                problems.add(prefix + "ismeretlen rewardtípus: " + map.get("type"));
                continue;
            }
            final double weight = number(map.get("weight"), Double.NaN);
            if (!Double.isFinite(weight) || weight <= 0.0D || weight > MAX_WEIGHT) {
                problems.add(prefix + "a weight véges, pozitív és legfeljebb " + MAX_WEIGHT + " lehet.");
                continue;
            }
            switch (type) {
                case CURRENCY -> {
                    final CurrencyType currency = CurrencyType.fromInput(String.valueOf(value(map, "currency", "")));
                    final long amount = wholePositive(map.get("amount"));
                    if (currency == null || amount < 1L || amount > maxCurrency) {
                        problems.add(prefix + "hibás currency/amount (maximum " + maxCurrency + ").");
                        continue;
                    }
                    rewards.add(new Reward(type, weight, currency, amount, null, 0, null,
                            "+" + amount + " " + currency.getDisplayName()));
                }
                case ITEM -> {
                    final Material material = Material.matchMaterial(String.valueOf(value(map, "material", "")));
                    final long amountLong = wholePositive(map.get("amount"));
                    if (amountLong < 1L || amountLong > Math.min(64, maxItemAmount)
                            || material == null || material.isAir()
                            || amountLong > material.getMaxStackSize()) {
                        problems.add(prefix + "hibás material/amount (stack- és configlimit: " + maxItemAmount + ").");
                        continue;
                    }
                    final int amount = (int) amountLong;
                    rewards.add(new Reward(type, weight, null, 0L, material, amount, null,
                            amount + "× " + material.translationKey()));
                }
                case COMMAND -> {
                    final String command = trim(String.valueOf(value(map, "command", "")));
                    final String commandProblem = validateCommandTemplate(command);
                    if (commandProblem != null) {
                        problems.add(prefix + commandProblem + ".");
                        continue;
                    }
                    rewards.add(new Reward(type, weight, null, 0L, null, 0, command,
                            blankTo(String.valueOf(value(map, "description", "")), "szerverjutalom")));
                }
            }
        }
        return rewards;
    }

    private static UUID parseUuid(final String raw, final List<String> problems) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (final IllegalArgumentException exception) {
            problems.add("Hibás world-uuid: " + raw);
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(final Class<E> type, final String raw,
                                                    final String key, final List<String> problems) {
        try {
            return Enum.valueOf(type, String.valueOf(raw).toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            problems.add("Ismeretlen " + key + ": " + raw);
            return type.getEnumConstants()[0];
        }
    }

    private static long wholePositive(final Object value) {
        if (!(value instanceof Number number)) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (final NumberFormatException exception) {
                return -1L;
            }
        }
        final double decimal = number.doubleValue();
        if (!Double.isFinite(decimal) || decimal != Math.rint(decimal) || decimal > Long.MAX_VALUE) {
            return -1L;
        }
        return (long) decimal;
    }

    private static Object value(final Map<?, ?> map, final String key, final Object fallback) {
        return map.containsKey(key) ? map.get(key) : fallback;
    }

    private static double number(final Object value, final double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private static long boundedPositiveLong(final Object value, final long fallback,
                                            final long minimum, final long maximum,
                                            final String key, final List<String> problems) {
        if (value == null) {
            return fallback;
        }
        final long parsed = wholePositive(value);
        if (parsed < minimum || parsed > maximum) {
            problems.add(key + " csak " + minimum + " és " + maximum + " közötti egész lehet.");
            return fallback;
        }
        return parsed;
    }

    private static int boundedPositiveInt(final Object value, final int fallback,
                                          final int minimum, final int maximum,
                                          final String key, final List<String> problems) {
        final long parsed = value == null ? fallback : wholePositive(value);
        if (parsed < minimum || parsed > maximum) {
            problems.add(key + " csak " + minimum + " és " + maximum + " közötti egész lehet.");
            return fallback;
        }
        return (int) parsed;
    }

    private static int coordinate(final Object value, final String key, final List<String> problems) {
        final long parsed = wholePositiveOrNegative(value);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            problems.add(key + " csak 32 bites egész koordináta lehet.");
            return 0;
        }
        return (int) parsed;
    }

    private static long wholePositiveOrNegative(final Object value) {
        if (!(value instanceof Number number)) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (final NumberFormatException exception) {
                return Long.MIN_VALUE;
            }
        }
        final double decimal = number.doubleValue();
        if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                || decimal > Long.MAX_VALUE || decimal < Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return (long) decimal;
    }

    private static String normalizeId(final String raw) {
        return trim(raw).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static String trim(final String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String blankTo(final String raw, final String fallback) {
        final String trimmed = trim(raw);
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
