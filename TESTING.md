# Testing Checklist

* \[✓] Start and stop an event.
* \[✓] Join, leave, spectate, and restore inventory/location.
* \[✓] Confirm the five-second countdown and scoreboard work.
* \[✓] Confirm events end immediately when one player remains.
* make it not end instantly, wait 3 seconds, and then end. previously it was waiting like 10 seconds

## Races

* \[✓] Horse Race checkpoints and fall recovery work.
* \[✓] Horse Race respawns the horse above the checkpoint.
* \[ ] Boat Race boats do not collide.
* \[ ] Elytra checkpoints register once and respawn correctly.
* still cant work the setup wizard. tell me what does what. my goal was to have an item to fill in the inside of a ring, therefore the area that you have to fly through, so it would place blocks inside and then when finished they would be removed and replaced with the previous block, which would be air. then i would have another item that i would place in the actual blocks of the ring, so that the player would respawn inside the ring.

## Combat

* \[ ] Quake shots cannot pass through walls.
* \[ ] Quake death sound and respawn countdown work.
* \[ ] CTF deaths restore the kit in the same slots.
* \[ ] CTF flags can be captured, dropped, and returned.
* \[✓] KBFFA PvP, starting pearl, and kill rewards work.
* it gives you an enderpearl, but you cant throw it.
* \[ ] Fight and Sumo brackets run two players at a time.
* \[X] Fight kit voting and previews work.
* the voting should happen in the hub by having each kit represented by an item and being in each players hotbar, then they should be able to click on an item to vote for that kit. there is no voting system so far that i have seen.

## BedWars

* \[✓] Item and upgrade shopkeepers spawn.
* the shop keepers spawn, but you cant open their GUI at all. we also need a way to set the direction that they face.
* \[✓] Players respawn while their bed is alive.
* dont say the "your bed saved you" mesasage. just say you died, and that should be global for all players in the event, so they should all be able to see the message saying that the player died. if their bed was gone when they died, then it would say they got eliminated.
* \[ ] Players are eliminated after their bed is broken.
* make TNT unable to break the bed, or any blocks aside from player placed blocks
* \[✓] The scoreboard shows each team's bed and player status.
* improve this. use a series of Color dot/emoji, then a check/X if they have a bed, and then a number of player, if it is 0 put an X. dont use all text cause it looks bad. make it more like a table, where it has labes at the top to show team color, Bed status, and players. then below in each row you display that.
* \[✓] Players receive no starter kit.

## Plugins

* \[ ] CombatLogX does not tag event players.
* \[ ] Newbie protection does not block event PvP.
* \[ ] NotBounties does not award event bounties.
* \[X] LCE combat works in configured event worlds.
* LCE is still not being applied to bedwars, maybe it will once i export the maps to their own separate worlds. speaking of, these two maps need added to the server
* "C:\\Users\\racec\\OneDrive\\Desktop\\DuelArena.zip"
* "C:\\Users\\racec\\OneDrive\\Desktop\\redlightgreenlight.zip"



Other issues:

items from bedwars, and possibly other events, stay and do not despawn or get cleared. we need to make sure to remove items before/after an event so that you cant save items between events or something.

this has not been tested, but make sure that in bedwars when you kill a player it gives you the ores that they had. so any iron, gold, emeralds they had on them would ge given to you and a message would be sent to them to let them know what all they received.


Code changes since last test:

* Event endings for last-player/last-finisher cases should now wait about 3 seconds instead of ending almost instantly.
* KBFFA pearls should now be throwable.
* Fight kit voting should now appear in the join/countdown hub hotbar, and vote items now use kit representative items instead of plain chests.
* BedWars shopkeepers should now keep their setup facing and open a GUI when right-clicked.
* BedWars respawn deaths should now broadcast a simple death message instead of saying "your bed saved you".
* BedWars final deaths should now broadcast an elimination message.
* BedWars TNT should now only break player-placed blocks, not beds or map blocks.
* BedWars dropped items in the event map should now be cleared when the session resets.
* BedWars killers should now receive the victim's iron, gold, diamonds, and emeralds.
* Elytra setup labels were renamed:
* Ring Area Pos 1 / Ring Area Pos 2 = select the fly-through volume.
* Save Ring Area = save that checkpoint volume.
* Ring Center = the checkpoint location itself.
* Checkpoint Respawn Point = where the player respawns for that checkpoint.
* Uploaded worlds to the server root:
* /DuelArena
* /redlightgreenlight
* These still need to be imported/loaded on the server side.
