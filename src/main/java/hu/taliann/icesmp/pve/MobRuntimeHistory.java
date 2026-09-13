package hu.taliann.icesmp.pve;

import java.util.*;

/** Owner-only combat history. Registry reloads may introduce new IDs; active history is never evicted. */
public final class MobRuntimeHistory {
    public static final int LIMIT = 512;
    public static final int INSPECTION_LIMIT = 24;
    private final Map<String, Long> ready = new HashMap<>();
    private final Set<String> consumed = new HashSet<>();
    private long observedTick;

    public record View(Map<String, Long> cooldowns, int cooldownCount, int consumedThresholdCount) {
        public View {
            cooldowns = Collections.unmodifiableMap(new TreeMap<>(cooldowns));
            if (cooldowns.size() > INSPECTION_LIMIT || cooldownCount < cooldowns.size() || cooldownCount > LIMIT
                    || consumedThresholdCount < 0 || consumedThresholdCount > LIMIT) throw new IllegalArgumentException("Runtime history bounds");
            cooldowns.forEach((id, deadline) -> {
                if (!MobAbilityDefinition.id(id, "ability").equals(id) || deadline == null || deadline < 1)
                    throw new IllegalArgumentException("Runtime history entry");
            });
        }
        public int omittedCooldowns() { return cooldownCount - cooldowns.size(); }
    }
    public long readyAt(String ability) { return ready.getOrDefault(MobAbilityDefinition.id(ability, "ability"), 0L); }
    public void advance(long tick) {
        if (tick < observedTick) throw new IllegalArgumentException("Runtime tick moved backwards");
        observedTick = tick;
        ready.entrySet().removeIf(entry -> entry.getValue() <= tick);
    }
    public boolean begin(String ability, long tick, long deadline) {
        ability = MobAbilityDefinition.id(ability, "ability");
        if (deadline <= tick) throw new IllegalArgumentException("Cooldown must end after admission");
        advance(tick);
        if (readyAt(ability) > tick || ready.size() >= LIMIT) return false;
        ready.put(ability, deadline);
        return true;
    }
    public boolean consumed(String ability) { return consumed.contains(MobAbilityDefinition.id(ability, "ability")); }
    public boolean consume(String ability) {
        ability = MobAbilityDefinition.id(ability, "ability");
        return consumed.size() < LIMIT && consumed.add(ability);
    }
    public View view() {
        final Map<String, Long> visible = new TreeMap<>();
        ready.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(INSPECTION_LIMIT)
                .forEach(entry -> visible.put(entry.getKey(), entry.getValue()));
        return new View(visible, ready.size(), consumed.size());
    }
}
