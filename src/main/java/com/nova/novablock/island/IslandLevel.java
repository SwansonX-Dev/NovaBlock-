package com.nova.novablock.island;

/**
 * Island level — a single headline progression number derived on demand from
 * the island's overall accomplishments. There is no separate island-XP
 * currency: the level is computed from already-persisted counters, so it is
 * always accurate and needs no migration. The {@code level} field on
 * {@link IslandData} only stores the last level we announced, so a level-up
 * message fires exactly once.
 */
public final class IslandLevel {

    private IslandLevel() {}

    /** Triangular curve: reaching level L costs {@code BASE * (L-1)*L/2} points. */
    private static final double BASE = 1000.0;

    /** Weighted progress points that feed the level curve. */
    public static long points(IslandData d) {
        return d.getBlocksBroken()
                + d.getNetherBlocksBroken()
                + (long) d.getPhaseIndex() * 3000L
                + (long) d.getNetherPhaseIndex() * 3000L
                + (long) d.getPrestigeLevel() * 30000L
                + (long) Math.max(0, d.getQuestlineStage() - 1) * 2000L;
    }

    public static int level(long points) {
        if (points <= 0) return 1;
        int level = (int) Math.floor((1.0 + Math.sqrt(1.0 + 8.0 * points / BASE)) / 2.0);
        return Math.max(1, level);
    }

    /** Total points required to reach {@code level}. */
    public static long pointsForLevel(int level) {
        if (level <= 1) return 0L;
        return (long) (BASE * (level - 1) * level / 2.0);
    }

    public static int levelOf(IslandData d) { return level(points(d)); }

    /** Points accumulated within the current level. */
    public static long progressInLevel(IslandData d) {
        long pts = points(d);
        return pts - pointsForLevel(level(pts));
    }

    /** Points needed to span the current level (current → next). */
    public static long pointsThisLevel(IslandData d) {
        int lvl = levelOf(d);
        return pointsForLevel(lvl + 1) - pointsForLevel(lvl);
    }
}
