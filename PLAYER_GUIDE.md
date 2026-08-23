# EnthusiaEvents Player Guide

This guide describes the **current implemented behavior** of EnthusiaEvents and the current Enthusia SMP deployment. It is intended to be a reliable source for future player-facing documentation and wiki generation.

The technical README and `Specifications.md` contain development and setup details. Where an old specification disagrees with current code or the live server configuration, this guide follows the current implementation and live configuration.

## Current deployment status

The current live Enthusia SMP configuration has:

- automatic hourly event scheduling: **disabled**
- chat mini-events: **disabled**
- minimum players for a physical event: **2**
- ready-player threshold: **2**
- join phase: **5 minutes**
- ready countdown: **60 seconds**
- final start countdown: **10 seconds**
- normal active-event limit: **10 minutes**
- trophy-room period: **10 seconds**
- configured winner reward: **100 economy units**
- configured player cost to start a chosen event: **150 economy units**
- configured `random-start-cost`: **750 economy units**

The code historically calls the random-start path a discounted/random start, but the current live value (750) is higher than the chosen-event value (150). Do not describe random event starts as discounted unless the deployment is changed.

Because automatic scheduling is currently disabled, do not advertise an hourly automatic schedule as a currently active SMP feature. The framework for hourly voting remains implemented and can be enabled by staff.

## Player commands

| Command | Behavior |
| --- | --- |
| `/event` | Shows the current event and phase, or reports that no event is running. |
| `/event join` | Joins the current event while joining is permitted. |
| `/event leave` | Leaves the event and restores the player's saved pre-event state. |
| `/event spectate` | Attempts to spectate the current event. |
| `/event vote` | Opens the event-vote GUI while a vote is active. |
| `/event start` | Opens the GUI of currently startable events. |
| `/event start <event>` | Attempts to start a specific event directly. |
| `/event stats` | Opens event statistics. |
| `/event next` | Shows the next scheduled hourly-vote time. `/event time` and `/event timer` are aliases. |

Normal event commands are available to players through the plugin's ordinary player permission. Starting an event is also player-accessible unless the server's permission configuration overrides it.

## Starting an event

`/event start` only lists events that:

1. are enabled in the event registry,
2. have not been disabled by staff, and
3. have at least one usable, fully configured map.

A player who is not exempt from event-start costs pays the configured Vault economy cost when the event starts. The current chosen-event cost is 150.

A start fails if another event session already exists, the selected event is disabled, no usable map exists, or the player cannot afford the cost.

The plugin contains a separate random-vote start path, but the ordinary current `/event start` GUI selects a specific event. Future documentation should only describe a random-start button if the deployed GUI actually exposes it.

## Event lifecycle

The event framework can run through these phases:

1. **Vote** - players vote between available events when a vote-driven start is used.
2. **Join** - players join and are moved to the waiting hub.
3. **Countdown / pre-start** - players are prepared and the selected map/game initializes.
4. **Active** - event-specific gameplay runs.
5. **Results / trophy room** - winners and results are shown.
6. **Restore** - saved player state is restored and event runtime state is cleaned up.

Only one physical event session runs at a time.

## Player-state protection

Before an event player is moved into event gameplay, EnthusiaEvents uses a snapshot/restore system so normal SMP state is not supposed to be consumed by the event. The system is designed to preserve and restore important state including inventory/loadout, armor/offhand, health, food, experience, game mode, potion effects, flight/movement state, and the player's original location.

The plugin also retains failed/pending restores rather than silently deleting them. Staff have dedicated inspection, retry, restore and emergency-recovery commands for players whose state could not be restored normally.

## Waiting hub and event isolation

The current deployment blocks common escape/economy commands while players are inside controlled event areas, including:

- `/accept`
- `/tpaccept`
- `/tpa`
- `/tpahere`
- `/tpask`
- `/spawn`
- `/home`
- `/warp`
- `/rtp`
- `/back`
- `/withdraw`
- `/deposit`

Normal external teleports and portals are blocked as well. The waiting hub and trophy room are locked against normal gameplay interaction.

Event teleport handling is also designed to prevent delayed-teleport exploits such as throwing an ender pearl and then leaving the event before it lands.

## Voting

When a vote phase is active, `/event vote` opens a GUI showing the candidate events, their descriptions and their current vote counts. A player can change their vote by selecting another choice.

The framework can select up to five event choices for a scheduled/random vote according to the current `events.vote-options` setting.

## Current event roster

The current live config registers the following event types. `Fight 2v2` and `Sumo 2v2` are additionally placed on the staff-disabled list, so they should not be presented as currently startable even though their implementations exist.

### Combat and elimination

| Event | Core implemented objective / behavior |
| --- | --- |
| **SkyWars** | Last player standing. Uses kits/loot infrastructure, tiered chests and a map where event terrain/placed blocks can be modified and restored. |
| **BedWars** | Last team alive. Includes team beds, resource generators, item/upgrade shops, team upgrades/traps, respawns while the bed survives, and map cleanup/reset. |
| **Fight 1v1** | Bracket/direct combat format; last surviving player wins the match. |
| **Fight 2v2** | Team fight implementation exists, but it is currently disabled in the live config. |
| **Fight FFA** | Free-for-all combat; last player standing. |
| **Sumo 1v1** | Knock the opponent off the platform; last player remaining wins. |
| **Sumo 2v2** | Team sumo implementation exists, but it is currently disabled in the live config. |
| **Sumo FFA** | Free-for-all sumo; last player on the platform wins. |
| **Knockback FFA** | Knockback-focused elimination; last player standing. Ender pearls are specifically permitted by the event registry. |
| **Quake** | Quake-style projectile/railgun combat with scoring and respawns handled by the event runtime. |
| **One in the Chamber** | Limited-shot projectile combat with scoring and respawns handled by the event runtime. |

### Team objective events

| Event | Core implemented objective / behavior |
| --- | --- |
| **Capture the Flag** | Team flags, carriers, dropped/returned flags and team scoring. The implementation's intended win condition is three captures. |
| **Capture Players** | Teams capture/carry opposing players into jail/capture areas and can rescue prisoners. Current config requires **5 captures** toward a round and **3 round wins**. |

### Party / survival events

| Event | Core implemented objective / behavior |
| --- | --- |
| **Block Party** | Players must reach the announced concrete color before other floor colors disappear; falling eliminates players. |
| **Hot Potato** | A player holds the hot potato and players pass it through gameplay until eliminations leave a winner. |
| **Spleef** | Break the configured floor/arena to eliminate opponents; last player standing. |
| **Splegg** | Projectile-based floor destruction; last player standing. |
| **Red Light Green Light** | Players advance toward the finish while movement is restricted during red-light periods. |

### Racing / completion events

| Event | Core implemented objective / behavior |
| --- | --- |
| **Boat Race** | Finish-order race using boats. |
| **Horse Race** | Finish-order horse race with recovery logic for players/horse positioning. |
| **Elytra Race** | Finish-order elytra race with required ring/checkpoint surfaces; missing/invalid course sections can trigger recovery. |
| **Parkour** | Finish-order parkour using configured checkpoints and last-safe-position recovery. |

## BedWars details

BedWars is a substantial mode inside EnthusiaEvents rather than a thin elimination wrapper. Current implementation includes:

- configured team beds that are rebuilt before matches,
- iron/gold/diamond/emerald resource handling,
- item-shop and upgrade-shop NPC/entities,
- Quick Buy state,
- progressive pickaxe and axe tiers,
- permanent shears tracking,
- armor tiers,
- team Sharpness,
- team Protection levels,
- Haste levels,
- forge upgrades,
- traps,
- Heal Pool support,
- Feather Falling levels,
- TNT handling,
- bed bugs/silverfish,
- bridge eggs,
- player respawning while the team bed remains alive,
- final elimination once the bed has been destroyed,
- tracked block and entity cleanup after the match.

Future wiki documentation can have its own BedWars page rather than treating it as only one line in the event list.

## Fight-event kit voting

The framework contains saved event kits and kit-voting support intended for fight-style events. During relevant event setup/join flow, kit choices can be represented through hotbar/GUI interactions and the selected kit is applied for event combat. Kits include inventory, armor and offhand state.

This is event loadout state, not the player's normal SMP inventory; the normal pre-event state is restored afterward.

## Spectating

Eliminated players and explicit spectators are managed inside the event system rather than being allowed to freely leave the event environment. The implementation is designed to confine spectators to the event, block normal interaction, and prevent them from using event spectating as an unrestricted teleport mechanism.

## Event statistics

`/event stats` opens the event-statistics UI. The statistics system tracks general participation/results and event-specific results, with leaderboard/profile support in the implementation.

Useful concepts for future wiki pages include:

- events played,
- wins and losses,
- win ratio,
- current/best streaks,
- per-event played/win/loss statistics,
- leaderboard views.

## Rewards

The live config currently specifies a winner reward of **100 economy units**. Reward handling uses the server economy integration and occurs through the controlled end-of-event flow.

Do not infer extra rewards from old design notes unless they are present in current config/code.

## Current deployment caveats

These distinctions are important for future wiki generation:

- **Hourly autostart exists in the plugin but is currently disabled on the SMP.**
- **Chat events exist in code but are currently disabled on the SMP.**
- **Fight 2v2 and Sumo 2v2 exist but are currently staff-disabled.**
- The old specification describes random event starts as discounted, but the current config sets chosen start to 150 and random start to 750.
- A type being implemented does not guarantee that a usable production map currently exists. `/event start` filters out events without a completed usable map.

For exact deployment/map availability, use the current server snapshot (`enthusia-server-state`) together with the plugin's map validation rather than assuming every implemented event is live.