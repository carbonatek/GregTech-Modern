package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.common.data.worldgen.GTVeinGenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@NoArgsConstructor
public class NoopVeinGenerator extends VeinGenerator {

    public static final NoopVeinGenerator INSTANCE = new NoopVeinGenerator();
    public static final MapCodec<NoopVeinGenerator> CODEC = MapCodec.unit(() -> INSTANCE);

    @Override
    public List<VeinEntry> getAllEntries() {
        return Collections.emptyList();
    }

    @Override
    public Long2ObjectMap<OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
                                                   BlockPos origin) {
        return Long2ObjectMaps.emptyMap();
    }

    @Override
    public VeinGenerator build() {
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return INSTANCE;
    }

    @Override
    public VeinGeneratorType<?> type() {
        return GTVeinGenerators.NO_OP.value();
    }
}
