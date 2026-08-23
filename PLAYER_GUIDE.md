# EnthusiaEvents Player Guide

This guide describes the current EnthusiaEvents implementation and the latest retained Enthusia server configuration. It is intended to be the canonical source for future player-facing documentation and wiki generation.

## Production status — important

The latest Enthusia **plugin-JAR manifest does not contain an EnthusiaEvents jar**. The server snapshot still contains `plugins/EnthusiaEvents/` configuration/map data, but that directory is retained data and does not by itself mean the plugin is currently loaded.

Therefore the correct current classification is **implemented / configured, but not presently installed as an active production plugin**. Do not advertise `/event` or the event roster as currently available until a newer jar manifest shows EnthusiaEvents deployed again.

The configuration values below are useful as the intended/current retained deployment setup for the next deployment, not proof of a running feature.

## Retained deployment configuration

The latest server configuration has:

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

The older design describes a random start as discounted, but the retained configuration currently makes random start more expensive than chosen start. Do not call it discounted unless the deployment/config is changed.

## Implemented player commands

When deployed, the plugin provides:

| Command | Behavior |
| --- | --- |
| `/event` | Shows the current event and phase, or reports that no event is running. |
| `/event join` | Joins the current event while joining is permitted. |
| `/event leave` | Leaves the event and restores the player's saved pre-event state. |
| `/event spectate` | Attempts to spectate the current event. |
| `/event vote` | Opens the event-vote GUI while a vote is active. |
| `/event start` | Opens the GUI of startable events. |
| `/event start <event>` | Attempts to start a specific event directly. |
| `/event stats` | Opens event statistics. |
| `/event next` | Shows the next scheduled hourly-vote time. `/event time` and `/event timer` are aliases. |

`/event start` only offers event definitions that are enabled, not staff-disabled, and have at least one usable configured map. Player-paid starts use Vault unless the player has the free-start permission.

## Event lifecycle

The framework can run through:

1. **Vote** — players vote between available event choices when a vote-driven start is used.
2. **Join** — players join and are moved to the waiting hub.
3. **Countdown / pre-start** — players are prepared and map/game state initializes.
4. **Active** — event-specific gameplay runs.
5. **Results / trophy room** — winners/results are shown.
6. **Restore** — saved player state is restored and temporary runtime state is cleaned up.

Only one physical event session can run at a time.

## Player-state protection

Before gameplay, EnthusiaEvents snapshots normal SMP state so event equipment/gameplay does not intentionally consume the player's real loadout. Restore coverage includes important state such as inventory, armor/offhand, health, food, experience, game mode, potion effects, flight/movement state, and original location.

Failed/pending restores are retained for staff recovery rather than silently discarded. Staff have inspection, retry, restore and emergency-recovery commands.

## Waiting hub and event isolation

The retained production configuration blocks common escape/economy commands in controlled event areas, including `/accept`, `/tpaccept`, `/tpa`, `/tpahere`, `/tpask`, `/spawn`, `/home`, `/warp`, `/rtp`, `/back`, `/withdraw`, and `/deposit`.

External teleports/portals are blocked according to event state. The implementation also protects against delayed-teleport exploits such as throwing an ender pearl and leaving before it lands.

## Voting

A vote GUI displays candidate events and live vote counts; selecting another candidate changes the player's vote. The retained setting is up to **5 choices** for a scheduled/random vote.

## Implemented event roster

The retained config registers these event types. `Fight 2v2` and `Sumo 2v2` are additionally on the staff-disabled list. Because the jar is not currently installed, none should be presented as presently playable solely from this config.

### Combat and elimination

| Event | Implemented objective / behavior |
| --- | --- |
| **SkyWars** | Last player standing; tiered loot/chests, kits and restorable modified terrain. |
| **BedWars** | Last team alive; team beds, generators, item/upgrade shops, upgrades/traps and respawns while the bed survives. |
| **Fight 1v1** | Direct/bracket combat; last surviving player wins. |
| **Fight 2v2** | Team fight implementation; additionally disabled in retained config. |
| **Fight FFA** | Free-for-all; last player standing. |
| **Sumo 1v1** | Knock opponent off; last player on platform wins. |
| **Sumo 2v2** | Team sumo implementation; additionally disabled in retained config. |
| **Sumo FFA** | Free-for-all sumo. |
| **Knockback FFA** | Knockback-focused elimination; registry specifically permits ender pearls. |
| **Quake** | Quake/railgun-style scoring and respawns. |
| **One in the Chamber** | Limited-shot projectile combat with scoring and respawns. |

### Team objectives

| Event | Implemented objective / behavior |
| --- | --- |
| **Capture the Flag** | Team flags, carriers, drops/returns and scoring; intended win condition is three captures. |
| **Capture Players** | Capture/carry enemy players into jail/capture areas and rescue prisoners. Retained config uses 5 captures toward a round and 3 round wins. |

### Party / survival

| Event | Implemented objective / behavior |
| --- | --- |
| **Block Party** | Reach the announced concrete color before other colors disappear; falling eliminates. |
| **Hot Potato** | Pass the potato and survive successive eliminations. |
| **Spleef** | Break the configured floor to eliminate opponents. |
| **Splegg** | Projectile-based floor destruction. |
| **Red Light Green Light** | Advance toward the finish while movement is restricted during red-light periods. |

### Racing / completion

| Event | Implemented objective / behavior |
| --- | --- |
| **Boat Race** | Boat finish-order race. |
| **Horse Race** | Horse finish-order race with recovery logic. |
| **Elytra Race** | Elytra course using required rings/checkpoint surfaces and recovery. |
| **Parkour** | Checkpoint/finish-order parkour with last-safe-position recovery. |

## BedWars depth

BedWars is a full event implementation rather than only a last-team-standing wrapper. It includes configured team beds, iron/gold/diamond/emerald resource flow, item and upgrade shops, Quick Buy state, progressive tools, permanent shears, armor tiers, Sharpness, Protection, Haste, forge upgrades, traps, Heal Pool, Feather Falling, TNT, bed bugs/silverfish, bridge eggs, bed-based respawn/final elimination, and tracked map/entity cleanup.

If/when Events returns to production, BedWars warrants its own wiki page rather than one sentence in an event list.

## Fight-event kits

The framework contains saved kit and kit-voting support for relevant fight events. Event kits include inventory, armor and offhand state and are separate from the player's normal SMP loadout, which is restored afterward.

## Spectating

Eliminated players and explicit spectators are constrained inside the event system. The implementation is designed to prevent interaction/escape and prevent spectating from becoming an unrestricted teleport mechanism.

## Statistics

`/event stats` implements general and event-specific statistics/leaderboards, including concepts such as events played, wins/losses, win ratio, streaks and per-event records.

## Rewards

The retained config specifies a winner reward of **100 economy units**. Do not infer additional rewards from old specifications unless current code/config also provides them.

## Wiki/source rules

- **Current production availability:** not installed according to the latest jar manifest.
- **Implementation reference:** this repository/code.
- **Next-deployment values:** latest retained `plugins/EnthusiaEvents/` snapshot.
- Automatic hourly scheduling and chat events remain disabled in that retained config.
- Fight 2v2 and Sumo 2v2 remain staff-disabled there.
- Map/config existence does not prove an event is usable; map validation must pass after deployment.

Once an EnthusiaEvents jar returns to the production jar manifest, re-check the config and update this status before publishing it as live.