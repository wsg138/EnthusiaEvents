# Testing Checklist

## Capture Players

- [ ] Configure per-team `capture-zone`, `jail-zone`, and `free-zone` areas in setup and confirm save validation blocks missing team areas.
- [ ] Confirm players spawn with the Capture Players kit: team-colored leather helmet, diamond chestplate, iron leggings, diamond boots, netherite sword, axe, shield, golden apples, and steak.
- [ ] Confirm a lethal hit captures the victim onto the killer's head instead of jailing them immediately.
- [ ] Confirm a carrier can stack multiple captured enemies by getting multiple lethal kills.
- [ ] Confirm killing a carrier frees captured enemies back to their spawn.
- [ ] Confirm killing a rescuer sends the rescued teammate back to the original jail.
- [ ] Confirm walking a captured enemy into your team's `capture-zone` sends that enemy into your team's `jail-zone`.
- [ ] Confirm jailed players cannot leave jail and are teleported back inside if they try.
- [ ] Confirm an empty-headed teammate can stand in the enemy `free-zone` for 5 seconds and pick up exactly one jailed teammate.
- [ ] Confirm a rescuer carrying a teammate can return them to their own `capture-zone` and respawn that teammate.
- [ ] Confirm round wins increment when a team jails all opponents.
- [ ] Confirm the event resets players correctly between rounds.
- [ ] Confirm the event ends when one team reaches 3 round wins.
- [ ] Confirm the scoreboard shows round number, red/blue round wins, and jailed counts correctly.
- [ ] Confirm timer expiry awards the match to the team with more round wins, or no winner on a tie.

## Combat

- [ ] Confirm CTF flags can be captured, dropped, returned, and scored correctly.
- [ ] Confirm Fight and Sumo brackets run two players at a time.
- [ ] Confirm Fight kit voting items appear for 1v1, 2v2, and FFA.

## BedWars

- [ ] Confirm the Solo-style item shop layout, page switching, prices, and purchase sounds.
- [ ] Confirm permanent armor and team upgrades persist after respawning.
- [ ] Confirm Sharpness, Protection, Haste, Forge, Heal Pool, and traps work.
- [ ] Confirm killers receive the victim's iron, gold, diamonds, and emeralds.
- [ ] Confirm dropped items and container contents are cleared when the event resets.

## Races

- [ ] Confirm Boat Race boats do not collide.
- [ ] Confirm Elytra players cannot finish without completing every checkpoint.
- [ ] Confirm Elytra checkpoint setup still works end to end on the live map.

## Red Light Green Light

- [ ] Confirm setup can save both the light display and a separate finish-line area.
- [ ] Confirm moving on red eliminates the player.
- [ ] Confirm crossing the finish line on green records the player as finished.
- [ ] Confirm first place remains the winner while other players can continue finishing.
- [ ] Confirm the event ends when everyone finishes or when the timer expires.

## Plugins

- [ ] Confirm Newbie protection does not block event PvP.
- [ ] Confirm NotBounties does not award event bounties.
