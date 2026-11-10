package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;
import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * Responsible for (pre)generating ore veins.<br>
 * This does not actually place any of the ore's blocks, and delegates to the applicable vein generator.
 */
public class OreGenerator {

    public record VeinConfiguration(GeneratedVeinMetadata data, RandomSource random) {

        public RandomSource newRandom() {
            return new XoroshiroRandomSource(random.nextLong());
        }
    }

    public List<GeneratedVeinMetadata> generateMetadata(final WorldGenLevel level, ChunkGenerator chunkGenerator,
                                                        ChunkPos chunkPos) {
        return createConfigs(level, chunkGenerator, chunkPos).stream()
                .map(OreGenerator::logVeinGeneration)
                .map(entry -> entry.data)
                .peek(data -> {
                    int gridX = Math.floorDiv(chunkPos.x, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize);
                    int gridZ = Math.floorDiv(chunkPos.z, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize);
                    ServerCache.instance.addVein(level.getLevel().dimension(), gridX, gridZ, data);
                })
                .toList();
    }

    /**
     * Generates the vein for the specified chunk metadata.<br>
     * If the chunk is not located on one of the ore vein grid's intersections, no vein will be generated.
     *
     * <p>
     * Note that depending on the configured random offset, the actual center of the generated vein may be located
     * outside the specified origin chunk.
     *
     * @return The generated vein for the specified chunk metadata.<br>
     *         {@code Optional.empty()} if no vein exists at this chunk.
     */
    public List<GeneratedVein> generateOres(WorldGenLevel level, List<GeneratedVeinMetadata> metadata,
                                            ChunkPos chunkPos) {
        return metadata.stream()
                .map(data -> {
                    RandomSource random = new XoroshiroRandomSource(level.getSeed() ^ chunkPos.toLong());
                    return new VeinConfiguration(data, random);
                })
                .flatMap(config -> generateOres(config, level, chunkPos).stream())
                .toList();
    }

    public Optional<GeneratedVein> generateOres(VeinConfiguration config, WorldGenLevel level, ChunkPos chunkPos) {
        GTOreDefinition definition = config.data.definition().value();
        Long2ObjectMap<OreBlockPlacer> oresBySection = definition.veinGenerator()
                .generate(level, config.newRandom(), definition, config.data.center());

        if (oresBySection.isEmpty()) {
            logEmptyVein(config);
            return Optional.empty();
        }

        Map<ChunkPos, OreIndicatorPlacer> generatedIndicators = new HashMap<>();
        for (IndicatorGenerator gen : definition.indicatorGenerators()) {
            Map<ChunkPos, OreIndicatorPlacer> generated = gen.generate(level, config.newRandom(), config.data);
            for (var entry : generated.entrySet()) {
                generatedIndicators.merge(entry.getKey(), entry.getValue(), (existing, added) -> {
                    return (a, l) -> {
                        existing.placeIndicators(a, l);
                        added.placeIndicators(a, l);
                    };
                });
            }
        }

        return Optional.of(new GeneratedVein(chunkPos, definition.layer(), oresBySection, generatedIndicators));
    }

    private List<VeinConfiguration> createConfigs(WorldGenLevel level, ChunkGenerator generator, ChunkPos chunkPos) {
        var random = new XoroshiroRandomSource(level.getSeed() ^ chunkPos.toLong());

        return OreVeinUtil.getVeinCenter(chunkPos, random).stream()
                .flatMap(veinCenter -> getEntries(level, veinCenter, random).map(entry -> {
                    if (entry == null) return null;

                    BlockPos origin = computeVeinOrigin(level, generator, chunkPos, random, veinCenter, entry.value())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Cannot determine y coordinate for the vein at " + veinCenter));

                    return new VeinConfiguration(new GeneratedVeinMetadata(chunkPos, origin, entry), random);
                })).toList();
    }

    private Stream<Holder<GTOreDefinition>> getEntries(WorldGenLevel level, BlockPos veinCenter,
                                                       XoroshiroRandomSource random) {
        return level.registryAccess().registryOrThrow(GTRegistries.Keys.WORLD_GEN_LAYER).stream()
                .filter(layer -> layer.isApplicableForLevel(level.getLevel().dimension()))
                .map(layer -> {
                    int quartX = QuartPos.fromBlock(veinCenter.getX());
                    int quartY = QuartPos.fromBlock(veinCenter.getY());
                    int quartZ = QuartPos.fromBlock(veinCenter.getZ());
                    return getEntry(level, level.getUncachedNoiseBiome(quartX, quartY, quartZ), random, layer);
                })
                .filter(Objects::nonNull);
    }

    private @Nullable Holder<GTOreDefinition> getEntry(WorldGenLevel level, Holder<Biome> biome, RandomSource random,
                                                       IWorldGenLayer layer) {
        var veins = WorldGeneratorUtils.getCachedBiomeVeins(level.getLevel(), biome).stream()
                .filter(vein -> vein.vein().value().layer().equals(layer))
                .toList();
        var randomVein = GTUtil.getRandomItem(random, veins);
        return randomVein == null ? null : randomVein.vein();
    }

    private static Optional<BlockPos> computeVeinOrigin(WorldGenLevel level, ChunkGenerator generator, ChunkPos pos,
                                                        RandomSource random, BlockPos veinCenter,
                                                        GTOreDefinition entry) {
        int layerSeed = level.getLevel().registryAccess().registryOrThrow(GTRegistries.Keys.WORLD_GEN_LAYER)
                .getKey(entry.layer()).hashCode();

        var layeredRandom = new XoroshiroRandomSource(random.nextLong() ^ ((long) layerSeed));

        veinCenter = OreVeinUtil.getVeinCenter(pos, layeredRandom).orElse(veinCenter);
        return entry.heightRange().getPositions(
                new PlacementContext(level, generator, Optional.empty()),
                layeredRandom, veinCenter).findFirst();
    }

    // /////////////////////////// //
    // ********* LOGGING ********* //
    // /////////////////////////// //

    private static VeinConfiguration logVeinGeneration(VeinConfiguration config) {
        if (ConfigHolder.INSTANCE.dev.debugWorldgen) {
            GTCEu.LOGGER.debug("Generating vein {} at {}",
                    config.data.definition().getKey().location(), config.data.center());
        }

        return config;
    }

    private static void logEmptyVein(VeinConfiguration config) {
        if (ConfigHolder.INSTANCE.dev.debugWorldgen) {
            GTCEu.LOGGER.debug("No blocks generated for vein {} at {}",
                    config.data.definition().getKey().location(), config.data.center());
        }
    }
}
