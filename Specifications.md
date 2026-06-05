EnthusiaEvents — Plugin Development Specification

Overview



We are building a large-scale event system plugin for a Minecraft server running leaf (Paper fork) 1.21.11 (Java 21) with Geyser + Floodgate support for Bedrock players.



This plugin will manage scheduled server events and chat events, teleport players into event arenas, run the event logic, track statistics, and return players safely to their original state afterward.



The design must prioritize:



Performance

Reliability

Clean player restoration

Compatibility with other plugins

Geyser/Floodgate support



This plugin must never cause lag spikes even during event transitions.



Core Concepts

One Active Event Rule



Only one physical event can run at a time.



If another event is scheduled while one is running:



the new event is queued

it starts 30 seconds after the current event ends

Scheduling

Physical Events



Every hour on the hour:



Example:



4:00

5:00

6:00



Process:



Event vote starts

Players vote between 5 randomly selected events

Winning event chosen

Random map for that event selected

Join phase begins

Event starts

Chat Events



Every 15 minutes



Example times:



:15

:30

:45



These are simple chat challenges such as:



trivia

math questions

word scramble



These reward players but do not teleport them.



Event Lifecycle

Vote Phase

Join Phase (players teleport to waiting hub)

Countdown

Event Active

Event Ends

Players moved to trophy room

Stats + announcements

10 second display period

Player restoration + payouts

Cooldown

Player Entry and Exit Rules



Players may only enter events through:



/event join



Or admin teleport.



Players may leave through:



/event leave



Teleporting into event worlds by other means must be blocked.



Player Snapshot System



Before joining an event, the plugin must save:



location

world

inventory

armor

offhand

health

hunger

XP

gamemode

potion effects

flight state

movement speed



After event end or leave, all values must be restored.



This restore pipeline must be centralized and reliable.



Event Waiting Hub



During join phase players are teleported to a waiting hub/box.



In this hub:



no attacking

no block placing

no block breaking

no teleport commands

no setting spawn

no portals

no external interaction

Trophy Room



When an event ends:



All active participants and spectators are teleported to a trophy room.



Inside the trophy room:



podium displays skins of top 3 players

coordinates for podium positions must be configurable

stats update happens here

announcement happens here



After 10 seconds:



players are restored to their original location

payouts occur



Players can leave early with /event leave.



Players who left before event end do not enter the trophy room.



Spectators are restored to survival mode here.



Event Voting



Voting is done through GUI.



Each vote presents 5 random event choices.



Voting properties:



voting is anonymous

vote count displayed using stack size of item

stack size starts at 1 because stacks cannot show 0



Example display:



no votes = stack 1

1 vote = stack 2

2 votes = stack 3



Vote updates must be live for all players.



Event Start GUI



Command:



/event start



Opens GUI.



Top section:



list of events

unique icon

short description



Bottom corner:



Start Random Event button



Rules for starting events:



Must:



have permission

have required Vault money

no event currently running

not within 5 minutes of next hourly event

cooldown passed



If event fails to start due to insufficient players:



refund money

Random Start Discount



Players may start a random event vote at a discount.



Flow:



player selects "Start Random"

vote begins

players vote between 5 random events

cheaper cost than starting specific event

Blocked Commands



Configurable list.



Examples to block while in event worlds:



/spawn

/home

/warp

/tpa

/tpahere

/rtp

/back



Custom teleport plugin will also enforce restrictions.



Teleport and Ender Pearl Rules



Inside events:



teleporting may be allowed depending on event rules

ender pearls allowed if event allows



But must prevent exploit:



Player throws pearl → /event leave → pearl lands outside event.



Solution:



cancel pending pearl teleport on leave

clear velocity

ensure no delayed teleport executes.

Spectator System



Eliminated players become spectators.



Spectator restrictions:



cannot teleport to non-event players

cannot leave event world

cannot interact

cannot spectate outside players



Spectator movement must be confined within event region bounds.



Map System



Each event has its own world.



If event has multiple maps:



maps exist in the same world

spaced apart



Each map has defined region used for:



player boundaries

spectator boundaries

entity cleanup

block restoration



At event end within region:



Remove:



dropped items

projectiles

mobs

temporary entities



Mob spawning disabled in event worlds.



Block Handling



Skywars:



map blocks breakable



Most other events:



players can place blocks

players can only break player placed blocks



Events requiring block reset:



Spleef

Spleeg

Bedwars



Reset system must support:



explosions

water

natural block decay

physics updates



Implementation choice (tracked changes vs snapshot) left to developer.



Loot Chest System



3 chest tiers:



Tier 1

Tier 2

Tier 3



Admins assign chest tier during setup mode.



Loot tables must be configurable.



Developer should generate balanced default loot.



Setup Tools



Admin setup tool modes:



spawn

spectator spawn

chest tier1

chest tier2

chest tier3

generator iron

generator gold

generator diamond

checkpoint



Spawn behavior:



clicking block sets spawn

player spawns on top of that block



Generators spawn ores on block above center.



Race Systems



Checkpoints only used for:



parkour



Boat / horse / elytra races do not require checkpoints.



Elytra race may use rings players must fly through.



If ring missed:



player warned.

Kit System



Admin creates kits by saving their inventory.



Kits must include:



inventory

armor slots

offhand



Command example (implementation flexible):



/ee kit save <name>

Kit Voting (Fight Events)



Players vote on kit while in hub.



Voting system:



hotbar items represent kits

clicking item casts vote

clicking another overrides vote



Kit preview:



shift + click



Opens GUI preview showing:



inventory layout

armor

offhand



Unused GUI slots filled with filler panes.



Stats System



Accessible via:



/event stats



Or clickable item given during events.



Stats GUI sections:



1 General Stats

total events played

wins

losses

win ratio

win streak

best streak

2 Leaderboards



Top 9 players.



Filters include:



most wins

most played

most losses



Player heads used.



Developer will request player head loading code from server owner when needed.



Clicking head opens that player's stats.



3 Event Stats



Shows stats per event:



times played

wins

losses

win ratio

PlaceholderAPI



Must provide placeholders for:



Player stats:



%ee\_events\_played%

%ee\_events\_wins%

%ee\_events\_losses%



Event stats:



%ee\_skywars\_wins%

%ee\_skywars\_played%



Top players (top 10):



%ee\_top\_wins\_1%

%ee\_top\_wins\_2%



Needed for future leaderboards.



Events List



Events to implement:



Combat



Skywars

Bedwars

Fight 1v1

Fight 2v2

Fight FFA

Sumo 1v1

Sumo 2v2

Sumo FFA

One in the Chamber

Capture the Flag

Capture Players



Party



Block Party

Hot Potato

Spleef

Spleeg

Red Light Green Light



Racing



Boat Race

Horse Race

Elytra Race

Parkour

Team Colors



2v2 events should visually distinguish teams.



Use colored glowing effect.



Example:



blue team

green team



Server owner will provide code snippet if needed.



Commands



Player:



/event join

/event leave

/event start

/event stats

/event next

/event time

/event timer



Admin:



/ee forcestart

/ee stop

/ee restore <player>

/ee remove <player>



Setup commands must include event type parameter when defining spawns.



Performance Requirements



This plugin must be extremely performance friendly.



Key rules:



avoid heavy synchronous loops

spread large tasks across ticks

avoid scanning huge regions unnecessarily

limit operations strictly to event bounds

avoid loading many chunks at once

clean entity removal efficiently



Transitions between events must not cause lag spikes.



Bedrock Compatibility



All mechanics must work with Geyser + Floodgate players.



Specifically ensure:



GUI interactions work

spectator handling works

inventory systems behave correctly

hotbar voting works

Final Note



This plugin will manage 20+ event types, large statistics tracking, and frequent player transitions.



The architecture must be:



modular

safe

easy to expand later



Focus on building a stable core framework first, then implementing events on top of it.

