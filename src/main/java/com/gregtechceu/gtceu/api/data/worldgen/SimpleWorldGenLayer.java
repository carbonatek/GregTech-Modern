package com.gregtechceu.gtceu.api.data.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import com.mojang.serialization.JsonOps;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class SimpleWorldGenLayer implements IWorldGenLayer {

    private final ResourceLocation id;
    private final IWorldGenLayer.RuleTestSupplier target;
    @Getter
    private final Set<ResourceKey<Level>> levels;

    public SimpleWorldGenLayer(ResourceLocation id, IWorldGenLayer.RuleTestSupplier target,
                               Set<ResourceKey<Level>> levels) {
        this.id = id;
        this.target = target;
        this.levels = levels;
    }

    @Override
    public @NotNull String getSerializedName() {
        return id.toString();
    }

    @Override
    public String toString() {
        return getSerializedName() + "[" +
                RuleTest.CODEC.encodeStart(JsonOps.INSTANCE, this.target.get()).result().orElse(null) + "]" +
                ",dimensions=" + this.levels.toString();
    }

    @Override
    public int hashCode() {
        return getSerializedName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IWorldGenLayer that)) return false;

        return this.getSerializedName().equals(that.getSerializedName());
    }

    public RuleTest getTarget() {
        return this.target.get();
    }

    @Override
    public boolean existsInDimension(ResourceKey<Level> level) {
        return this.levels.contains(level);
    }
}
