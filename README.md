# NovaBlock

A reimagined OneBlock plugin for Paper 1.21.4+ with 12 themed phases, boss fights,
loot rooms, a skill tree, pets that follow and fight alongside you, server-wide events,
and Bedrock/mobile-friendly UIs throughout.

## Features

- **12 themed phases** — Plains → Underground → Frozen Tundra → Burning Desert →
  Sunken Reef → Nether Gates → Ancient Mines → Lush Garden → Stronghold Halls →
  End Voyage → Celestial Vault → Void Beyond
- **3 bosses** with distinct mechanics (Magma Tyrant, Frostborn Sentinel, Void Herald)
- **3 loot-room types** — Parkour, Arena (wave defense), Echo Vault (Simon-says puzzle)
- **7 pets** that follow you, take task commands, level up, and fight/mine/heal/scout/carry
- **Prophecy system** — see and lock the next 10 blocks for bonus rewards
- **4 skill trees** with 16 perks at level milestones
- **Daily quests**, **coin economy**, **shop**, **leaderboard**
- **Server-wide events** (Diamond Hour, Coin Rush, Blood Moon, etc.)
- **PlaceholderAPI** support (optional)
- **Bedrock/Geyser** compatible — every menu is chest-based, no chat prompts

## Building

```
mvn package
```

Output: `target/NovaBlock-0.1.0.jar`.

## Installing

1. Drop the jar in your Paper `plugins/` folder
2. Restart the server (the plugin creates the `novablock_world` on first enable)
3. Players run `/ob create` to start

## Commands

- `/ob` — main menu
- `/ob create` / `/ob home` / `/ob menu` / `/ob prophecy` / `/ob skills` / `/ob pets`
- `/ob quest` / `/ob shop` / `/ob leaderboard` / `/ob phase`
- `/ob invite <player>` + `/ob accept` — co-op
- `/obadmin reload | setphase | spawnboss | givecoins | event | wipe`

## Placeholders (with PlaceholderAPI installed)

```
%novablock_phase%            %novablock_skill_mining_level%
%novablock_phase_name%       %novablock_skill_combat_level%
%novablock_phase_progress%   %novablock_pet_name%
%novablock_phase_required%   %novablock_pet_task%
%novablock_blocks%           %novablock_pet_level%
%novablock_coins%            %novablock_quest_name%
%novablock_event_name%       %novablock_quest_progress%
%novablock_event_seconds_left%
```

## Status

First-pass implementation. Boots and shuts down cleanly on Paper 1.21.4. Not yet
playtested with real clients — expect to tune numbers and find UX rough edges after
your first 30 minutes of real play.
