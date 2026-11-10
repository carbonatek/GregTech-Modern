package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.common.data.GTOreVeins;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

import java.util.Optional;
import java.util.function.Function;

public class OreVeinUtil {

    private OreVeinUtil() {}

    public static boolean canPlaceOre(BlockState state, Function<BlockPos, BlockState> adjacentStateAccessor,
                                      RandomSource random, GTOreDefinition entry,
                                      OreConfiguration.TargetBlockState targetState, BlockPos pos) {
        if (!targetState.target.test(state, random)) return false;
        if (shouldSkipAirCheck(random, entry.discardChanceOnAirExposure())) return true;

        return !Feature.isAdjacentToAir(adjacentStateAccessor, pos);
    }

    public static boolean canPlaceOre(BlockState state, Function<BlockPos, BlockState> adjacentStateAccessor,
                                      RandomSource random, GTOreDefinition entry, BlockPos pos) {
        if (!entry.layer().getTarget().test(state, random)) return false;
        if (shouldSkipAirCheck(random, entry.discardChanceOnAirExposure())) return true;

        return !Feature.isAdjacentToAir(adjacentStateAccessor, pos);
    }

    protected static boolean shouldSkipAirCheck(RandomSource random, float chance) {
        if (chance <= 0.0f) return true;
        if (chance >= 1.0f) return false;
        return random.nextFloat() >= chance;
    }

    /**
     * Resolves a vein's center for the supplied chunk position.
     * 
     * <p>
     * Note that depending on the config value for the random vein offset, its actual
     * center may be outside the supplied chunk.
     * 
     * @return The origin of the vein to be generated.<br>
     *         {@code Optional.empty()} if no vein should exist for the specified chunk.
     */
    public static Optional<BlockPos> getVeinCenter(ChunkPos chunkPos, RandomSource random) {
        int gridSize = ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize;
        int randomOffset = ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinRandomOffset;

        if (chunkPos.x % gridSize != 0 || chunkPos.z % gridSize != 0)
            return Optional.empty();

        var chunkCenter = chunkPos.getMiddleBlockPosition(0);

        if (randomOffset == 0)
            return Optional.of(chunkCenter);

        return Optional.of(chunkCenter.offset(
                random.nextInt(-randomOffset, +randomOffset),
                0,
                random.nextInt(-randomOffset, +randomOffset)));
    }

    /**
     * @return The radius (in chunks) to search for adjacent veins.<br>
     *         Depends on the largest registered vein size, as well as the configured random vein offset.
     */
    static int getMaxVeinSearchDistance() {
        double halfVeinSize = GTOreVeins.getLargestVeinSize() / 2.0;
        int randomOffset = ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinRandomOffset;

        return (int) Math.ceil((halfVeinSize + randomOffset) / 16.0);
    }

    /**
     * @return The radius (in chunks) to search for adjacent indicators.<br>
     *         Depends on the largest registered indicator size, as well as the configured random vein offset.
     */
    static int getMaxIndicatorSearchDistance() {
        return getMaxVeinSearchDistance() + (int) Math.ceil((double) GTOreVeins.getLargestIndicatorOffset() / 16.0);
    }
}
