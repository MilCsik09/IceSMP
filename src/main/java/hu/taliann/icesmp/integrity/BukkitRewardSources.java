package hu.taliann.icesmp.integrity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import java.util.*;

/** Capture native provenance on its owner; scheduler continuations carry only this immutable result. */
public final class BukkitRewardSources {
    private BukkitRewardSources() { }
    public static RewardSourceContext entity(final RewardChannel channel, final Entity source) {
        if (source == null || !Bukkit.isOwnedByCurrentRegion(source)) throw new IllegalStateException("Reward source owner required");
        final var location = source.getLocation(); final UUID world = Objects.requireNonNull(location.getWorld()).getUID();
        return new RewardSourceContext(channel, List.of(new RewardSource.Entity(source.getUniqueId()), new RewardSource.World(world),
                new RewardSource.Location(world, location.getX(), location.getY(), location.getZ())));
    }
    public static boolean allowed(final RewardChannel channel, final Entity source) {
        try { return GameplayRewardGate.evaluateSources(entity(channel, source)).allowed(); }
        catch (final RuntimeException | LinkageError unavailable) { return false; }
    }
    public static RewardSourceContext death(final RewardChannel channel, final org.bukkit.entity.LivingEntity victim) {
        final var context = entity(channel, victim); final var killer = victim.getKiller();
        final Set<RewardSource> sources = new LinkedHashSet<>(context.sources());
        if (killer != null) sources.add(new RewardSource.Player(killer.getUniqueId()));
        final var lastDamage = victim.getLastDamageCause();
        if (lastDamage != null) {
            // UUID is immutable identity; never inspect a causal entity's mutable state from this owner.
            final var cause = lastDamage.getDamageSource().getCausingEntity();
            final var direct = lastDamage.getDamageSource().getDirectEntity();
            if (cause != null) sources.add(new RewardSource.Entity(cause.getUniqueId()));
            if (direct != null) sources.add(new RewardSource.Entity(direct.getUniqueId()));
        }
        return new RewardSourceContext(channel, List.copyOf(sources));
    }
    public static boolean deathAllowed(final RewardChannel channel, final org.bukkit.entity.LivingEntity victim) {
        try { return GameplayRewardGate.evaluateSources(death(channel, victim)).allowed(); }
        catch (final RuntimeException | LinkageError unavailable) { return false; }
    }
}
