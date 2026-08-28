package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.UndeadUtil;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.LivingEntity;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Resolves bounded automatic context on the location/entity owning region thread. */
public final class TrashContextResolver {

    private final TerritoryManager territoryManager;

    public TrashContextResolver(final TerritoryManager territoryManager) {
        this.territoryManager = Objects.requireNonNull(territoryManager, "territoryManager");
    }

    public Set<TrashContext> resolve(final TrashLootSource source, final Location location,
                                     final LivingEntity sourceEntity) {
        if (location == null || location.getWorld() == null) return Set.of();
        final EnumSet<TrashContext> contexts = EnumSet.noneOf(TrashContext.class);
        final World world = location.getWorld();
        final String biome = location.getBlock().getBiome().getKey().getKey().toUpperCase(Locale.ROOT);

        if (source == TrashLootSource.FISHING || wet(location, biome)) contexts.add(TrashContext.WET);
        if (cold(biome)) contexts.add(TrashContext.COLD);
        if (world.getEnvironment() == World.Environment.NETHER || hot(biome)) contexts.add(TrashContext.HOT);
        if (world.getEnvironment() == World.Environment.NETHER) contexts.add(TrashContext.NETHER);

        final int surface = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(),
                HeightMap.WORLD_SURFACE);
        if (location.getY() >= surface) {
            contexts.add(TrashContext.OPEN_SKY);
        } else if (surface - location.getY() >= 4.0D) {
            contexts.add(TrashContext.UNDERGROUND);
        }
        if (location.getY() <= Math.min(32, world.getSeaLevel() - 32)) {
            contexts.add(TrashContext.DEEP);
        }
        if (sourceEntity != null) {
            if (UndeadUtil.isUndead(sourceEntity)) contexts.add(TrashContext.UNDEAD);
            if (humanoid(sourceEntity.getType().name())) contexts.add(TrashContext.HUMANOID);
        }
        final Territory territory = territoryManager.getTerritoryAt(location);
        if (territory != null && territory.faction() == FactionType.DARK) {
            contexts.add(TrashContext.DARK);
        }
        return Set.copyOf(contexts);
    }

    private static boolean wet(final Location location, final String biome) {
        final Block at = location.getBlock();
        final Block below = at.getRelative(0, -1, 0);
        return water(at) || water(below)
                || containsAny(biome, "OCEAN", "RIVER", "SWAMP", "BEACH");
    }

    private static boolean water(final Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static boolean cold(final String biome) {
        return containsAny(biome, "SNOW", "FROZEN", "ICE", "GROVE", "COLD", "JAGGED_PEAKS");
    }

    private static boolean hot(final String biome) {
        return containsAny(biome, "DESERT", "BADLANDS", "SAVANNA", "BASALT", "NETHER", "CRIMSON");
    }

    private static boolean humanoid(final String entityType) {
        return containsAny(entityType, "VILLAGER", "PILLAGER", "VINDICATOR", "EVOKER", "ILLUSIONER",
                "WITCH", "PIGLIN", "WANDERING_TRADER");
    }

    private static boolean containsAny(final String value, final String... needles) {
        for (final String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
