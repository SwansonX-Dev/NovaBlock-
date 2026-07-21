## Magic skill: enchanting

Magic was fed only by loot rooms and prophecies — sparse, mostly passive sources — and was the one skill with no scaling passive. The enchanting table now trains it.

**XP** scales with the *level cost* of the enchant (`xp-per-action 8 x expLevelCost`), so a table-30 roll is worth 240 XP and a cheap level-1 roll is 8. Creative enchanting is skipped.

**New scaling passive:** 0.15%/level, capped at 15% — a chance that one rolled enchantment comes out +1 level (vanilla max respected).

**Four new perks:**

| Perk | Lv | Effect |
|---|---|---|
| Lapis Affinity | 15 | 35% chance to refund the lapis the table consumed |
| Runesmith | 25 | The +1 proc may exceed the vanilla cap by one |
| Soul Siphon | 40 | Refunds 30% of the levels the enchant cost |
| Cursebreaker | 50 | Strips Binding/Vanishing curses from the result |

Rough pace: Lv10 (Riftwalker) ~27 max enchants, Lv25 (Runesmith) ~145, Lv50 (Cursebreaker) ~551. Loot rooms and prophecies contribute as before.

**Config:** new `skills.magic` block in `skills.yml` (passive, `xp-per-action`, and `enchanting.lapis-refund-chance` / `xp-refund-fraction`). All code-defaulted — a stale live config keeps working, no edit required.

Also includes everything from 0.38.0 (Tree Feller clearing the whole connected stand), which never got its own release.

**Deploy:** drop `NovaBlock-0.39.0.jar`, restart. Not runtime-tested in-game.
