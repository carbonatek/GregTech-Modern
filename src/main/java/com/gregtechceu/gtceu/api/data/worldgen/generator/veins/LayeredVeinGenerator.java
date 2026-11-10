package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTLayerPattern;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;
import com.gregtechceu.gtceu.common.data.worldgen.GTVeinGenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.minecraft.core.SectionPos.blockToSectionCoord;
import static net.minecraft.core.SectionPos.sectionRelative;

@SuppressWarnings("UnusedReturnValue")
@NoArgsConstructor
public class LayeredVeinGenerator extends VeinGenerator {

    // spotless:off
    public static final MapCodec<LayeredVeinGenerator> CODEC = GTLayerPattern.CODEC.listOf().fieldOf("layer_patterns")
            .xmap(LayeredVeinGenerator::new, LayeredVeinGenerator::getLayerPatterns);
    // spotless:on
    private final List<NonNullSupplier<GTLayerPattern>> bakingLayerPatterns = new ArrayList<>();

    public List<GTLayerPattern> layerPatterns;

    public List<GTLayerPattern> getLayerPatterns() {
        if (layerPatterns == null || this.layerPatterns.isEmpty()) {
            layerPatterns = bakingLayerPatterns.stream().map(Supplier::get).collect(Collectors.toList());
        }
        return layerPatterns;
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        return getLayerPatterns().stream()
                .flatMap(pattern -> pattern.layers.stream())
                .flatMap(GTLayerPattern.Layer::asVeinEntries)
                .distinct()
                .toList();
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
                                                   BlockPos origin) {
        Long2ObjectMap<OreBlockPlacer> generatedSections = new Long2ObjectOpenHashMap<>();
        List<GTLayerPattern> patternPool = this.getLayerPatterns();
        if (patternPool.isEmpty()) return generatedSections;

        // minor optimization, usually only one layer pool exists
        GTLayerPattern layerPattern = patternPool.size() == 1 ?
                patternPool.get(0) : patternPool.get(random.nextInt(patternPool.size()));

        int size = entry.clusterSize().sample(random);
        int radius = Mth.ceil(size / 2f);

        int xMin = origin.getX() - radius;
        int yMin = origin.getY() - radius;
        int zMin = origin.getZ() - radius;

        int layerCoordinate = random.nextInt(4);
        int slantyCoordinate = random.nextInt(3);
        float slope = random.nextFloat() * 0.75f;
        List<GTLayerPattern.Layer> resolvedLayers = new ArrayList<>();
        FloatList layerDiameterOffsets = new FloatArrayList();
        resolveLayers(size, layerPattern, resolvedLayers, layerDiameterOffsets,
                layerCoordinate, slantyCoordinate, slope, random);

        int sectionX0 = blockToSectionCoord(xMin), sectionX1 = blockToSectionCoord(xMin + size);
        int sectionY0 = blockToSectionCoord(yMin), sectionY1 = blockToSectionCoord(yMin + size);
        int sectionZ0 = blockToSectionCoord(zMin), sectionZ1 = blockToSectionCoord(zMin + size);

        SectionPos.betweenClosedStream(sectionX0, sectionY0, sectionZ0, sectionX1, sectionY1, sectionZ1)
                .forEach(sectionPos -> {
                    final long randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order
                    generatedSections.put(sectionPos.asLong(), (access, section) -> {
                        placeSection(level, access, section, sectionPos, randomSeed, entry, size,
                                resolvedLayers, layerDiameterOffsets, layerCoordinate, slantyCoordinate, slope,
                                xMin, yMin, zMin);
                    });
                });

        return generatedSections;
    }

    private static void resolveLayers(int diameter, GTLayerPattern layerPattern,
                                      List<GTLayerPattern.Layer> resolvedLayers, FloatList layerDiameterOffsets,
                                      int layerCoordinate, int slantyCoordinate, float slope, RandomSource random) {
        int maxLayer = diameter;
        if (slantyCoordinate != layerCoordinate) {
            maxLayer += Mth.floor(diameter * slope);
        }
        while (maxLayer >= resolvedLayers.size()) {
            GTLayerPattern.Layer next = layerPattern.rollNext(
                    resolvedLayers.isEmpty() ? null : resolvedLayers.get(resolvedLayers.size() - 1),
                    random);

            float offset = random.nextFloat() * 0.5f + 0.5f;
            // insert the previous layer if this one is null (e.g. invalid)
            if (next == null) {
                if (resolvedLayers.isEmpty()) {
                    continue;
                }
                resolvedLayers.add(resolvedLayers.get(resolvedLayers.size() - 1));
                layerDiameterOffsets.add(offset);
                continue;
            }
            for (int i = 0; i < next.minSize + random.nextInt(1 + next.maxSize - next.minSize); i++) {
                resolvedLayers.add(next);
                layerDiameterOffsets.add(offset);
            }
        }
    }

    private void placeSection(WorldGenLevel level, BulkSectionAccess access, LevelChunkSection section,
                              SectionPos sectionPos, long randomSeed, GTOreDefinition entry, int size,
                              List<GTLayerPattern.Layer> resolvedLayers, FloatList layerDiameterOffsets,
                              int layerCoordinate, int slantyCoordinate, float slope,
                              int xMin, int yMin, int zMin) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < size; dx++) {
            if (blockToSectionCoord(xMin + dx) != sectionPos.x()) continue;

            float sizeFractionX = dx * 2f / size - 1;
            float xSizeSqr = sizeFractionX * sizeFractionX;
            if (xSizeSqr > 1) continue;

            for (int dy = 0; dy < size; dy++) {
                if (blockToSectionCoord(yMin + dy) != sectionPos.y()) continue;

                float sizeFractionY = dy * 2f / size - 1;
                float ySizeSqr = sizeFractionY * sizeFractionY;
                if (xSizeSqr + ySizeSqr > 1) continue;
                if (level.isOutsideBuildHeight(yMin + dy)) continue;

                for (int dz = 0; dz < size; dz++) {
                    if (blockToSectionCoord(zMin + dz) != sectionPos.z()) continue;

                    float sizeFractionZ = dz * 2f / size - 1;
                    float zSizeSqr = sizeFractionZ * sizeFractionZ;
                    // OPTIMIZATION: all values in layerDiameterOffsets are in the [0,1] range, so
                    // check if the size is >1 before doing any of that math
                    if (xSizeSqr + ySizeSqr + zSizeSqr > 1) continue;

                    int layerIndex = size;
                    if (slantyCoordinate != layerCoordinate) {
                        layerIndex += Mth.floor(size * slope);
                    }

                    if (xSizeSqr + ySizeSqr + zSizeSqr > layerDiameterOffsets.getFloat(layerIndex)) continue;

                    GTLayerPattern.Layer layer = resolvedLayers.get(layerIndex);
                    Either<List<OreConfiguration.TargetBlockState>, Material> state = layer.rollBlock(random);

                    pos.set(xMin + dx, yMin + dy, zMin + dz);
                    placeBlock(access, section, random, entry, state, pos);
                }
            }
        }
    }

    private static void placeBlock(BulkSectionAccess access, LevelChunkSection section, RandomSource random,
                                   GTOreDefinition entry,
                                   Either<List<OreConfiguration.TargetBlockState>, Material> state, BlockPos pos) {
        int x = sectionRelative(pos.getX());
        int y = sectionRelative(pos.getY());
        int z = sectionRelative(pos.getZ());

        BlockState current = section.getBlockState(x, y, z);

        if (random.nextFloat() > entry.density()) return;

        state.ifLeft(targetStates -> {
            for (OreConfiguration.TargetBlockState targetState : targetStates) {
                if (!OreVeinUtil.canPlaceOre(current, access::getBlockState, random, entry, targetState, pos))
                    continue;
                if (targetState.state.isAir()) continue;

                section.setBlockState(x, y, z, targetState.state, false);
                break;
            }
        }).ifRight(material -> {
            if (!OreVeinUtil.canPlaceOre(current, access::getBlockState, random, entry, pos)) return;

            var prefix = ChemicalHelper.getOrePrefix(current);
            if (prefix.isEmpty()) return;

            Block toPlace = ChemicalHelper.getBlock(prefix.get(), material);
            if (toPlace == null || toPlace.defaultBlockState().isAir()) return;

            section.setBlockState(x, y, z, toPlace.defaultBlockState(), false);
        });
    }

    public LayeredVeinGenerator(List<GTLayerPattern> layerPatterns) {
        super();
        this.layerPatterns = layerPatterns;
    }

    public LayeredVeinGenerator withLayerPattern(NonNullSupplier<GTLayerPattern> pattern) {
        this.bakingLayerPatterns.add(pattern);
        return this;
    }

    public VeinGenerator build() {
        if (this.layerPatterns != null && !this.layerPatterns.isEmpty()) return this;
        this.layerPatterns = this.bakingLayerPatterns.stream()
                .map(NonNullSupplier::get)
                .toList();
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new LayeredVeinGenerator(new ArrayList<>(this.layerPatterns));
    }

    @Override
    public VeinGeneratorType<?> type() {
        return GTVeinGenerators.LAYER.value();
    }
}
