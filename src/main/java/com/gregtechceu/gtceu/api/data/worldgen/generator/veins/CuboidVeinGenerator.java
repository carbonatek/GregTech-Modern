package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.minecraft.core.SectionPos.*;

@Accessors(fluent = true, chain = true)
@NoArgsConstructor
public class CuboidVeinGenerator extends VeinGenerator {

    public static final MapCodec<CuboidVeinGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ClassicVeinGenerator.Layer.CODEC.fieldOf("top").forGetter(val -> val.top),
            ClassicVeinGenerator.Layer.CODEC.fieldOf("middle").forGetter(val -> val.middle),
            ClassicVeinGenerator.Layer.CODEC.fieldOf("bottom").forGetter(val -> val.bottom),
            ClassicVeinGenerator.Layer.CODEC.fieldOf("spread").forGetter(val -> val.spread),
            Codec.INT.fieldOf("min_y").forGetter(val -> val.minY),
            Codec.INT.fieldOf("max_y").forGetter(val -> val.maxY)).apply(instance, CuboidVeinGenerator::new));

    private ClassicVeinGenerator.Layer top;
    private ClassicVeinGenerator.Layer middle;
    private ClassicVeinGenerator.Layer bottom;
    private ClassicVeinGenerator.Layer spread;

    @Setter
    private int minY, maxY;

    private int spreadDivisor;
    private int totalLayers;
    private int bottomLayer;
    private int middleLayer1;
    private int middleLayer2;
    private int topLayer;

    public CuboidVeinGenerator(ClassicVeinGenerator.Layer top, ClassicVeinGenerator.Layer middle,
                               ClassicVeinGenerator.Layer bottom, ClassicVeinGenerator.Layer spread,
                               int minY, int maxY) {
        this.top = top;
        this.middle = middle;
        this.bottom = bottom;
        this.spread = spread;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        List<VeinEntry> entries = new ArrayList<>(top.size() + middle.size() + bottom.size() + spread.size());
        VeinGenerator.mapTarget(top.target, top.layers).forEach(entries::add);
        VeinGenerator.mapTarget(middle.target, middle.layers).forEach(entries::add);
        VeinGenerator.mapTarget(bottom.target, bottom.layers).forEach(entries::add);
        VeinGenerator.mapTarget(spread.target, 1).forEach(entries::add);
        return entries;
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
                                                   BlockPos origin) {
        Long2ObjectMap<OreBlockPlacer> generatedSections = new Long2ObjectOpenHashMap<>();

        int size = entry.clusterSize().sample(random) / 2;

        int westBound = origin.getX() - random.nextInt(size);
        int eastBound = origin.getX() + SectionPos.SECTION_SIZE + random.nextInt(size);
        int northBound = origin.getZ() - random.nextInt(size);
        int southBound = origin.getZ() + SectionPos.SECTION_SIZE + random.nextInt(size);

        int startY = minY + random.nextInt(maxY - minY - 5) - 1;

        int sectionY0 = blockToSectionCoord(startY), sectionY1 = blockToSectionCoord(startY + totalLayers);
        int sectionX0 = blockToSectionCoord(westBound), sectionX1 = blockToSectionCoord(eastBound);
        int sectionZ0 = blockToSectionCoord(northBound), sectionZ1 = blockToSectionCoord(southBound);

        for (int sectionY = sectionY0; sectionY <= sectionY1; sectionY++) {
            for (int sectionX = sectionX0; sectionX < sectionX1; sectionX++) {
                for (int sectionZ = sectionZ0; sectionZ < sectionZ1; sectionZ++) {
                    final long randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order

                    long packedSectionPos = SectionPos.asLong(sectionX, sectionY, sectionZ);

                    final int westBoundInSection = sectionRelative(westBound - sectionToBlockCoord(sectionX));
                    final int eastBoundInSection = sectionRelative(eastBound - sectionToBlockCoord(sectionX));
                    final int northBoundInSection = sectionRelative(northBound - sectionToBlockCoord(sectionZ));
                    final int southBoundInSection = sectionRelative(southBound - sectionToBlockCoord(sectionZ));

                    int _layerOffset = totalLayers - (startY - sectionToBlockCoord(sectionY));
                    int _totalLayers = totalLayers;
                    if (_layerOffset < 0) {
                        _totalLayers = -_layerOffset;
                        _layerOffset = 0;
                    }
                    final int totalLayers = _totalLayers;
                    final int layerOffset = _layerOffset;
                    generatedSections.put(packedSectionPos, (access, section) -> {
                        SectionPos sectionPos = SectionPos.of(packedSectionPos);
                        placeSection(level, access, section, sectionPos, origin, randomSeed, entry,
                                westBoundInSection, eastBoundInSection, northBoundInSection, southBoundInSection,
                                startY, layerOffset, totalLayers);
                    });
                }
            }
        }

        return generatedSections;
    }

    private void placeSection(WorldGenLevel level, BulkSectionAccess access, LevelChunkSection section,
                              SectionPos sectionPos, BlockPos origin, long randomSeed, GTOreDefinition entry,
                              int westBoundInSection, int eastBoundInSection,
                              int northBoundInSection, int southBoundInSection,
                              int startY, int layerOffset, int totalLayers) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (; layerOffset <= totalLayers; layerOffset++) {
            int sY = startY + layerOffset;
            if (level.isOutsideBuildHeight(sY)) continue;

            for (int sX = westBoundInSection; sX < eastBoundInSection; sX++) {
                for (int sZ = northBoundInSection; sZ < southBoundInSection; sZ++) {
                    // determine density based on distance from the origin chunk
                    // this makes the vein more concentrated towards the center
                    int xLength = (sX + sectionPos.minBlockX()) - origin.getX();
                    int zLength = (sZ + sectionPos.minBlockZ()) - origin.getZ();
                    double volume = Math.sqrt(2 + (xLength * xLength) + (zLength * zLength));

                    int localDensity = (int) Math.max(1, 8 * entry.density() / volume);
                    int weightX = Math.max(1,
                            Math.max(Mth.abs(westBoundInSection - sX), Mth.abs(eastBoundInSection - sX)) /
                                    localDensity);
                    int weightZ = Math.max(1,
                            Math.max(Mth.abs(southBoundInSection - sZ), Mth.abs(northBoundInSection - sZ)) /
                                    localDensity);

                    pos.set(sX + sectionPos.minBlockX(), sY + sectionPos.minBlockY(), sZ + sectionPos.minBlockZ());
                    if (layerOffset <= bottomLayer) {
                        // layers 0, 1, and 2 are bottom and spread
                        if (!placeBottom(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                            placeSpread(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ);
                        }
                    } else if (layerOffset <= middleLayer1) {
                        // layer 3 is bottom, middle, and spread
                        if (!placeMiddle(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                            if (!placeBottom(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                                placeSpread(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ);
                            }
                        }
                    } else if (layerOffset <= middleLayer2) {
                        // layer 4 is middle, and spread
                        if (!placeMiddle(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                            placeSpread(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ);
                        }
                    } else if (layerOffset <= topLayer) {
                        // layers 5 and 6 is top, middle, and spread
                        if (!placeMiddle(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                            if (!placeTop(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                                placeSpread(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ);
                            }
                        }
                    } else {
                        // layers 7 and 8 is top and spread
                        if (!placeTop(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ)) {
                            placeSpread(access, section, entry, pos, random, weightX, weightZ, sX, sY, sZ);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if an ore should be placed
     *
     * @param random  the random to use
     * @param weightX the x weight
     * @param weightZ the z weight
     * @return whether the ore should be placed
     */
    protected static boolean shouldPlaceOre(@NotNull RandomSource random, int weightX, int weightZ) {
        return random.nextInt(weightX) == 0 || random.nextInt(weightZ) == 0;
    }

    /**
     * Place the top ore
     *
     * @return whether the ore was placed
     */
    private boolean placeTop(BulkSectionAccess access, LevelChunkSection section, GTOreDefinition entry,
                             BlockPos pos, RandomSource random, int weightX, int weightZ, int sX, int sY, int sZ) {
        if (shouldPlaceOre(random, weightX, weightZ)) {
            placeOre(access, section, pos, random, this.top.target, entry, sX, sY, sZ);
            return true;
        }
        return false;
    }

    /**
     * Place the middle ore
     *
     * @return whether the ore was placed
     */
    private boolean placeMiddle(BulkSectionAccess access, LevelChunkSection section, GTOreDefinition entry,
                                BlockPos pos, RandomSource random, int weightX, int weightZ, int sX, int sY, int sZ) {
        if (random.nextInt(2) == 0 && shouldPlaceOre(random, weightX, weightZ)) {
            placeOre(access, section, pos, random, this.middle.target, entry, sX, sY, sZ);
            return true;
        }
        return false;
    }

    /**
     * Place the bottom ore
     *
     * @return whether the ore was placed
     */
    private boolean placeBottom(BulkSectionAccess access, LevelChunkSection section, GTOreDefinition entry,
                                BlockPos pos, RandomSource random, int weightX, int weightZ, int sX, int sY, int sZ) {
        if (shouldPlaceOre(random, weightX, weightZ)) {
            placeOre(access, section, pos, random, this.bottom.target, entry, sX, sY, sZ);
            return true;
        }
        return false;
    }

    /**
     * Place the spread ore
     *
     * @return whether the ore was placed
     */
    private boolean placeSpread(BulkSectionAccess access, LevelChunkSection section, GTOreDefinition entry,
                                BlockPos pos, RandomSource random, int weightX, int weightZ, int sX, int sY, int sZ) {
        if (random.nextFloat() <= entry.density() * spreadDivisor && shouldPlaceOre(random, weightX, weightZ)) {
            placeOre(access, section, pos, random, this.spread.target, entry, sX, sY, sZ);
            return true;
        }
        return false;
    }

    public void placeOre(BulkSectionAccess access, LevelChunkSection section, BlockPos pos, RandomSource random,
                         Either<List<OreConfiguration.TargetBlockState>, Material> ore, GTOreDefinition entry,
                         int sX, int sY, int sZ) {
        BlockState existing = section.getBlockState(sX, sY, sZ);

        ore.ifLeft(blockStates -> {
            for (OreConfiguration.TargetBlockState targetState : blockStates) {
                if (!OreVeinUtil.canPlaceOre(existing, access::getBlockState, random, entry, targetState, pos))
                    continue;
                if (targetState.state.isAir()) continue;

                section.setBlockState(sX, sY, sZ, targetState.state, false);
                break;
            }
        }).ifRight(material -> {
            if (!OreVeinUtil.canPlaceOre(existing, access::getBlockState, random, entry, pos)) return;

            BlockState currentState = access.getBlockState(pos);
            var prefix = ChemicalHelper.getOrePrefix(currentState);
            if (prefix.isEmpty()) return;

            Block toPlace = ChemicalHelper.getBlock(prefix.get(), material);
            if (toPlace == null || toPlace.defaultBlockState().isAir())
                return;

            section.setBlockState(sX, sY, sZ, toPlace.defaultBlockState(), false);
        });
    }

    @Override
    public VeinGenerator build() {
        top.layers = top.layers == -1 ? 2 : top.layers;
        middle.layers = middle.layers == -1 ? 3 : middle.layers;
        bottom.layers = bottom.layers == -1 ? 2 : bottom.layers;

        // Ensure "middle" is not more than the total top and bottom layers
        Preconditions.checkArgument(top.layers + bottom.layers >= middle.layers,
                "Error: cannot have more \"middle\" layers than top and bottom layers combined!");

        totalLayers = top.layers + middle.layers + bottom.layers;
        bottomLayer = (int) (totalLayers / 7.0f * 2.0f);
        middleLayer1 = (int) (totalLayers / 7.0f * 3.0f);
        middleLayer2 = (int) (totalLayers / 7.0f * 4.0f);
        topLayer = (int) (totalLayers / 7.0f * 6.0f);
        spreadDivisor = (top.layers + middle.layers - 1) / 2;
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new CuboidVeinGenerator(top.copy(), middle.copy(), bottom.copy(), spread.copy(), minY, maxY);
    }

    @Override
    public VeinGeneratorType<?> type() {
        return GTVeinGenerators.CUBOID.value();
    }

    public CuboidVeinGenerator top(Consumer<ClassicVeinGenerator.Layer.Builder> builder) {
        ClassicVeinGenerator.Layer.Builder layerBuilder = new ClassicVeinGenerator.Layer.Builder(
                AlwaysTrueTest.INSTANCE);
        builder.accept(layerBuilder);
        top = layerBuilder.build();
        return this;
    }

    public CuboidVeinGenerator middle(Consumer<ClassicVeinGenerator.Layer.Builder> builder) {
        ClassicVeinGenerator.Layer.Builder layerBuilder = new ClassicVeinGenerator.Layer.Builder(
                AlwaysTrueTest.INSTANCE);
        builder.accept(layerBuilder);
        middle = layerBuilder.build();
        return this;
    }

    public CuboidVeinGenerator bottom(Consumer<ClassicVeinGenerator.Layer.Builder> builder) {
        ClassicVeinGenerator.Layer.Builder layerBuilder = new ClassicVeinGenerator.Layer.Builder(
                AlwaysTrueTest.INSTANCE);
        builder.accept(layerBuilder);
        bottom = layerBuilder.build();
        return this;
    }

    public CuboidVeinGenerator spread(Consumer<ClassicVeinGenerator.Layer.Builder> builder) {
        ClassicVeinGenerator.Layer.Builder layerBuilder = new ClassicVeinGenerator.Layer.Builder(
                AlwaysTrueTest.INSTANCE);
        builder.accept(layerBuilder);
        spread = layerBuilder.build();
        return this;
    }
}
