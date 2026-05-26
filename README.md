# NovaBlock

A reimagined OneBlock plugin for Paper 1.21.4+ with 12 themed phases, boss fights,
loot rooms, a skill tree with gameplay-affecting perks, server-wide events, and
Bedrock/mobile-friendly UIs throughout.

## Features

- **12 themed phases** — Plains → Underground → Frozen Tundra → Burning Desert →
  Sunken Reef → Nether Gates → Ancient Mines → Lush Garden → Stronghold Halls →
  End Voyage → Celestial Vault → Void Beyond
- **3 bosses** with distinct mechanics (Magma Tyrant, Frostborn Sentinel, Void Herald)
- **3 loot-room types** — Parkour, Arena (wave defense), Crystal Cache (amethyst-break race)
- **Prophecy system** — see and lock the next 10 blocks for bonus rewards
- **4 skill trees with 16 gameplay perks** — Mining, Combat, Magic, Luck;
  perks affect drops, damage, XP, prophecy slots, loot rates, and coin gains
- **Daily quests**, **coin economy**, **prestige**, **leaderboard**
- **Server-wide events** (Diamond Hour, Double Coins, Blood Moon, Lush Bloom, Rift Storm)
- **Co-op islands** with shared storage and per-island flags (PVP, fly, mob
  spawning, fire spread, keep-inventory, always-day, and more)
- **Paxel** — soulbound multi-tool that tiers up with your phase
- **PlaceholderAPI** support (optional)
- **Bedrock/Geyser** compatible — every menu is chest-based, no chat prompts

## Integrations

NovaBlock relies on a couple of external plugins so it can focus on what's unique:

- **xEconomy** (required) — coins, shop (`/shop`), bank (`/bank`), sell (`/sell`).
  NovaBlock awards coins via xEconomy; the in-menu Shop/Bank/Sell buttons just
  open xEconomy's UIs.
- **xPets** (optional) — companions, summoning, leveling. NovaBlock's `/ob pets`
  and the in-menu Pets button just open xPets's `/pets` UI when installed.
- **PlaceholderAPI** (optional) — exposes the `%novablock_*%` placeholders below.
- **Floodgate / Geyser** (optional) — used for Bedrock-friendly UI tweaks.

## Building

```
mvn package
```

Output: `target/NovaBlock-<version>.jar`.

## Installing

1. Drop the jar in your Paper `plugins/` folder (alongside xEconomy)
2. Restart the server (the plugin creates the `novablock_world` on first enable)
3. Players run `/ob create` to start

## Commands

Player command (`/ob`, aliases `oneblock`, `nb`, `novablock`):

- `/ob` — main menu
- `/ob create` / `/ob home` / `/ob fix` — island lifecycle
- `/ob menu` / `/ob prophecy` / `/ob skills` / `/ob quests` / `/ob leaderboard`
- `/ob phase` — show current phase + progress
- `/ob prestige` — open prestige menu (requires Phase 12 completion)
- `/ob flags` / `/ob storage` — island settings + shared 54-slot vault
- `/ob invite <player>` + `/ob accept` / `/ob leave` — co-op
- `/ob pets` — bridge to xPets (if installed)
- `/ob toggle` — hide/show hotbar menu item
- `/ob help` — open the in-game guide

Admin command (`/obadmin`, `novablock.admin`):

- `reload | setphase <player> <idx> | spawnboss <id> | givecoins <player> <amt>`
- `event <name|stop> [minutes] | wipe <player> | fix <player|all>`
- `givepaxel <player> | flags <player> | storage <player>`
- `menu <add|remove|rename|list>` — manage custom main-menu buttons
- `freshstart <player> confirm` — wipe NovaBlock + xEconomy player data

Other commands:

- `/sb` (`/scoreboard`, `/sidebar`) — toggle sidebar
- `/novahelp` (`/obhelp`, `/guide`) — open the help GUI

## Placeholders (with PlaceholderAPI installed)

```
%novablock_phase%            %novablock_skill_mining_level%
%novablock_phase_name%       %novablock_skill_combat_level%
%novablock_phase_progress%   %novablock_skill_magic_level%
%novablock_phase_required%   %novablock_skill_luck_level%
%novablock_blocks%           %novablock_quest_name%
%novablock_coins%            %novablock_quest_progress%
%novablock_event_name%       %novablock_prestige_level%
%novablock_event_seconds_left%
```

## Tunable data files

All of these are loaded from `plugins/NovaBlock/` on first run and re-read by
`/obadmin reload`:

- `config.yml` — top-level numbers (cooldowns, anti-AFK, prestige, slot size)
- `bosses.yml` — boss HP, damage, defeat-cooldown
- `skills.yml` — XP curve constants, perk tuning
- `lootrooms.yml` — loot-room timing and rewards
- `messages.yml` — user-facing strings

## Status

Stable enough to run a small public server on. Boss/loot-room cadence is tunable
and skill perks now actually affect gameplay; expect to keep tuning numbers after
your first 30 minutes of real play.
