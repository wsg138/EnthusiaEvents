# Testing Checklist

## Core

- [ ] Confirm last-player and last-finisher event endings wait about 3 seconds before ending.

## Races

- [ ] Confirm Boat Race boats do not collide.
- [ ] Confirm normally placed gold blocks form one irregular Elytra checkpoint area until `Next / New Checkpoint` is used.
- [ ] Confirm Elytra checkpoint marker blocks restore to their previous blocks after setup is saved or cancelled.
- [ ] Confirm Elytra checkpoints register once and respawn correctly.

## Combat

- [ ] Confirm Quake shots cannot pass through walls.
- [ ] Confirm Quake death sound and respawn countdown work.
- [ ] Confirm CTF deaths restore the kit in the same slots.
- [ ] Confirm CTF flags can be captured, dropped, and returned.
- [ ] Confirm KBFFA pearls can be thrown during the event.
- [ ] Confirm Fight and Sumo brackets run two players at a time.
- [ ] Confirm Fight kit voting and previews work from the join/countdown hub hotbar.

## BedWars

- [ ] Confirm BedWars shopkeepers open their GUI and keep the configured facing.
- [ ] Confirm players are eliminated after their bed is broken.
- [ ] Confirm BedWars death and elimination messages are correct.
- [ ] Confirm BedWars TNT only breaks player-placed blocks.
- [ ] Confirm the BedWars scoreboard layout looks correct in game.
- [ ] Confirm dropped BedWars items are cleared when the event resets.
- [ ] Confirm killers receive the victim's iron, gold, diamonds, and emeralds.

## Plugins

- [ ] Confirm CombatLogX does not tag event players.
- [ ] Confirm Newbie protection does not block event PvP.
- [ ] Confirm NotBounties does not award event bounties.
- [ ] Confirm LCE combat works in configured event worlds, including BedWars.

## Server

- [ ] Import and load the `DuelArena` world on the server.
- [ ] Import and load the `redlightgreenlight` world on the server.
