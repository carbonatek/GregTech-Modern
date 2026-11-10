package com.gregtechceu.gtceu.api.data.worldgen.ores;

import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Responsible for actually placing a vein's blocks in a section.
 * 
 * <p>
 * This is invoked for every chunk section a vein needs to be placed in when the relevant chunk is being generated.<br>
 * The positions are <b>NOT</b> computed in advance for you, so you should compute them in advance yourself if possible.
 */
@FunctionalInterface
public interface OreBlockPlacer {

    /**
     * Place blocks at the (precomputed) location this placer is mapped to.
     */
    void placeBlocks(BulkSectionAccess access, LevelChunkSection section);
}
