package com.nova.novablock.island;

/**
 * The three OneBlock progression tracks. Each has its own world, its own phase
 * list ({@link com.nova.novablock.phase.PhaseManager}), and its own set of
 * per-island counters on {@link IslandData}. Most dimension-aware code branches
 * through the convenience helpers that take a {@code Dimension} (e.g.
 * {@link IslandData#getPhaseIndex(Dimension)}) rather than repeating a
 * three-way {@code if} at every call site.
 *
 * <p>Unlock order: OVERWORLD is always available, NETHER unlocks at Overworld
 * Phase 7, END unlocks on the island's first prestige.
 */
public enum Dimension {
    OVERWORLD,
    NETHER,
    END;

    public boolean isOverworld() { return this == OVERWORLD; }
    public boolean isNether() { return this == NETHER; }
    public boolean isEnd() { return this == END; }
}
