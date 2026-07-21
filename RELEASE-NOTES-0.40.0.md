## Multiple islands, an island market, and inactive-island purging

The one-island-per-player assumption is gone. Everything below builds on that.

### Multiple islands

`islands.max-owned` (default **2**) sets how many islands a player may own. Islands they're only a member of don't count. Grant more per rank with `novablock.islands.max.<n>` — highest matching node wins.

`/ob islands` lists yours, `/ob islands <n>` switches which one your commands act on. The distinction that matters: **commands** follow your selection, **anything positional** follows your feet. Block breaks, quest payouts, questline progress, and minion caps all credit the island you're standing on, not the one you selected.

### Island market

`/ob sellisland <price>` lists your island; `/ob market` browses listings cheapest-first and buys.

Each island has a **floor price** — a minimum, not an appraisal — so a maxed island can't be handed to an alt for one coin. It sums island level, phase depth across all three dimensions, total prestige, purchased upgrade levels, and lifetime blocks broken. The island bank and the storage vault are deliberately excluded, so a seller emptying them before listing can't crash the floor. Every weight is configurable under `islands.market.valuation`.

On a sale the price moves buyer → seller and ownership transfers. Coins are withdrawn only after every check passes and are refunded if the transfer itself fails, so a failed buy can't eat the buyer's money. Listings live in `market.yml` and are re-validated on load — a listing whose island was deleted or purged is dropped rather than left live.

### Inactive-island purging

`/obadmin purge` previews islands idle for at least `islands.purge.after-days` (default 30), most-idle first; purging is always an explicit second step. An island's clock is stamped whenever any member joins or quits, so a co-op island stays active as long as anybody plays on it. Islands with an online member are never selected, and islands saved before activity tracking existed report 0 days idle — they're safe until their members log in once.

**This does not free disk.** Islands are 256-block slots inside the shared `oneblock` worlds, so a purge frees the registry entry and the owner's slot, but the abandoned build stays in the region files until cleared manually.

### New flag: Visitors Can Use Doors

Doors, trapdoors, fence gates, levers, and buttons were completely unprotected — any visitor could open any door on any island. `VISITOR_USE_DOORS` gates them, **defaulting off**, matching Visitors Can Build and Visitors Can Open Containers. Existing islands go from "anyone can use doors" to "members and trusted only" on restart; flip the flag in `/ob flags` to restore the old behavior per island.

Pressure plates and tripwires aren't covered — those fire on step, not click.

Also registers `novablock.flag.visitor_container_access`, which was never declared in `plugin.yml`. Behavior is unchanged; it's just visible to permission plugins now.

### Also includes

Everything from **0.38.0** (Tree Feller clearing the whole connected stand) and **0.39.0** (enchanting trains the Magic skill, plus four enchanting perks), neither of which got its own release.

---

**Config:** new `islands` block (`max-owned`, `purge.after-days`, `market.valuation.*`). All code-defaulted — a stale live config keeps working with no edit, and the packaged config does not overwrite yours.

**Deploy:** drop `NovaBlock-0.40.0.jar`, restart. Not runtime-tested in-game.
