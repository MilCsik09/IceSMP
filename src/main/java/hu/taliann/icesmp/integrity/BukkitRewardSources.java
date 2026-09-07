package hu.taliann.icesmp.integrity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import java.util.*;

/** Capture native provenance on its owner; scheduler continuations carry only this immutable result. */
public final class BukkitRewardSources {
    private BukkitRewardSources() { }
    public static RewardSourceContext entity(final RewardChannel channel, final Entity source) {
        return new RewardSourceContext(channel, causal(source));
    }
    public static List<RewardSource> causal(final Entity source) {
        if (source == null || !Bukkit.isOwnedByCurrentRegion(source)) throw new IllegalStateException("Reward source owner required");
        final var location = source.getLocation(); final UUID world = Objects.requireNonNull(location.getWorld()).getUID();
        final Set<RewardSource> values = new LinkedHashSet<>(List.of(new RewardSource.Entity(source.getUniqueId()), new RewardSource.World(world),
                new RewardSource.Location(world, location.getX(), location.getY(), location.getZ())));
        final var kind = source instanceof org.bukkit.entity.Player ? GameplaySourceSubject.Kind.PLAYER
                : source instanceof org.bukkit.entity.Mob ? GameplaySourceSubject.Kind.MOB
                : source instanceof org.bukkit.entity.Projectile ? GameplaySourceSubject.Kind.PROJECTILE : GameplaySourceSubject.Kind.ENTITY;
        if (source instanceof org.bukkit.entity.Projectile projectile && projectile.getOwnerUniqueId() != null)
            values.add(new RewardSource.Entity(projectile.getOwnerUniqueId()));
        values.addAll(GameplaySourceCaptureGate.capture(new GameplaySourceSubject(source.getUniqueId(), kind)));
        return List.copyOf(values);
    }
    public static boolean allowed(final RewardChannel channel, final Entity source) {
        try { return GameplayRewardGate.evaluateSources(entity(channel, source)).allowed(); }
        catch (final RuntimeException | LinkageError unavailable) { return false; }
    }
    /** Capture a block's stable position without retaining the live block into profile IO. */
    public static List<RewardSource> block(final org.bukkit.block.Block block) {
        if (block == null || !Bukkit.isOwnedByCurrentRegion(block)) throw new IllegalStateException("Reward block owner required");
        final UUID world = block.getWorld().getUID();
        return List.of(new RewardSource.World(world), new RewardSource.Location(world, block.getX(), block.getY(), block.getZ()));
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
            if (cause != null) addCause(sources, cause);
            if (direct != null) addCause(sources, direct);
        }
        return new RewardSourceContext(channel, List.copyOf(sources));
    }
    private static void addCause(final Set<RewardSource> sources, final Entity cause) {
        sources.add(new RewardSource.Entity(cause.getUniqueId()));
        if (cause instanceof org.bukkit.entity.Player) return;
        if (!Bukkit.isOwnedByCurrentRegion(cause)) throw new IllegalStateException("Causal source owner unavailable");
        sources.addAll(causal(cause));
    }
    public static boolean deathAllowed(final RewardChannel channel, final org.bukkit.entity.LivingEntity victim) {
        try { return GameplayRewardGate.evaluateSources(death(channel, victim)).allowed(); }
        catch (final RuntimeException | LinkageError unavailable) { return false; }
    }
}
