# EnthusiaEvents Final Test Checklist

Use this for the final player test before moving from the test server to the live server.

## Latest Gameplay And Setup Fixes

- [ ] `/ee setup <EVENT> <mapId>` opens that map's setup palette; no extra tool argument is needed.
- [ ] `/ee setup save` validates, saves, and closes the active setup palette. `/ee setup <EVENT> <mapId> save` works when that same map is open.
- [ ] BedWars reconfiguration: `/ee eventtp BEDWARS <mapId>`, `/ee map clear BEDWARS <mapId> beds`, then `/ee setup BEDWARS <mapId>` lets staff save actual beds again.
- [ ] Horse Race reconfiguration: `/ee eventtp HORSE_RACE <mapId>`, `/ee map clear HORSE_RACE <mapId> checkpoints`, then `/ee setup HORSE_RACE <mapId>` lets staff rebuild checkpoints.
- [ ] Capture the Flag reconfiguration: `/ee eventtp CAPTURE_THE_FLAG <mapId>`, `/ee map clear CAPTURE_THE_FLAG <mapId> spawns`, then `/ee setup CAPTURE_THE_FLAG <mapId>` lets staff reset team spawns.
- [ ] Fight 1v1 reconfiguration: `/ee eventtp FIGHT_1V1 <mapId>`, `/ee map clear FIGHT_1V1 <mapId> all confirm`, then `/ee setup FIGHT_1V1 <mapId>` clears that map's saved setup without deleting its world.
- [ ] CTF, Fight 1v1/2v2/FFA, BedWars, and SkyWars allow players to drop items; other active event modes block manual item drops.
- [ ] BedWars death transfers resources directly to the killer and queues overflow for later inventory delivery instead of dropping transfer loot on the ground.
- [ ] Lethal event damage never opens the vanilla death screen or increments Minecraft death statistics during a normal event path.
- [ ] SkyWars simulated deaths drop the player's inventory and armor normally, then immediately move the player to event spectator state.
- [ ] Elytra Race lethal damage immediately returns the player to their last checkpoint without a death screen.
- [ ] Fight, Spleef, Splegg, and Hot Potato lethal damage immediately eliminates the player without a vanilla death screen.
- [ ] Natural mobs do not spawn in configured event-map worlds, the event hub, or the trophy room; BedWars shops, BedWars utility mobs, Boat Race boats, and Horse Race horses still spawn.
- [ ] Crafting is blocked for event players in every event except SkyWars.
- [ ] Manual item dropping is allowed only in CTF, Fight variants, BedWars, and SkyWars; other active event modes block it.
- [ ] Player deaths keep and clear event items in every event except SkyWars; SkyWars deaths drop inventory normally.
- [ ] No event kit, armor, or manually dropped item can be duplicated or carried into a later event.
- [ ] BedWars setup rejects marker blocks and saves an actual bed's color and facing for each team.
- [ ] Legacy BedWars bed markers are cleared on load and setup validation requires every configured team spawn to have a real saved bed.
- [ ] BedWars rebuilds both halves of every configured bed before teleporting players into the match.
- [ ] Enemy BedWars beds break without dropping bed items; own beds and unconfigured beds cannot be broken.
- [ ] Elytra Race allows solid contact at checkpoint blocks and the current safe respawn/start, but touching or standing on other terrain returns the player to the latest checkpoint.
- [ ] Capture Players prisoners cannot walk out of their jail and do not remain invulnerable while jailed.
- [ ] Fight 1v1 contestants remain held for prestart without a flying kick, then lose temporary flight permission at Go.
- [ ] Fight kits and armor cannot be dropped to reproduce the reported armor duplicate.
- [ ] Sumo 1v1 and 2v2 brackets continue correctly if a current contestant, teammate, or queued player disconnects.
- [ ] Boat Race boats are pushed back toward the surface only when fully submerged and no longer occasionally continue sinking.
- [ ] `/ee map clear <EVENT> <mapId> beds` clears only saved beds.
- [ ] `/ee map clear <EVENT> <mapId> spawns` and `finishes` clear only their requested setup entries.
- [ ] `/ee map clear <EVENT> <mapId> all` warns without changing data until the final `confirm` argument is supplied.
- [ ] Full map clear keeps the map ID and world assignment so setup can be redone in place.

## Latest Fixes To Test

- [ ] Elytra Race restores each player's original max health after finish, leave, relog restore, `/ee restore`, `/ee emergencyrestore`, and `/ee forcestop`.
- [ ] A player with non-default max health before Elytra Race gets that exact max health back, not hardcoded 20.
- [ ] Quit during an event, wait/restart, rejoin, and confirm the pending snapshot is restored before the player is considered clean.
- [ ] A player with a pending unrestored snapshot cannot `/event join` another event.
- [ ] `/ee retryrestores` only reports completion after the player inventory/location/state actually restore.
- [ ] `/ee restore <player>` reports failure if teleport/apply fails and leaves the snapshot pending.
- [ ] `/ee stuckcheck <player>` shows event tags, metadata, participant/spectator status, unrestored snapshot status, max health, gamemode, glowing, vehicle/passenger state, and potion effects.
- [ ] `/ee emergencyrestore <player>` clears stuck event state and restores the snapshot when possible.
- [ ] If emergency snapshot teleport fails, the player is moved to a safe fallback and the original snapshot remains pending.
- [ ] `/ee reload` is blocked while any event session is active.
- [ ] Plugin disable/restart during an event does not mark snapshots restored unless the player inventory/location/state actually restored.
- [ ] Event force stop, not-enough-players cancel, normal finish, leave, relog restore, and failed start all clear runtime state without leaving event items, vehicles, glowing, potions, XP countdown, or spectator mode behind.
- [ ] Joining an event heals the player in the waiting hub, but leaving/restoring returns them to the health they had before joining.
- [ ] Players are fully healed before the active phase starts.
- [ ] Trophy room entry clears event items for every event and shows normal 10-heart health before final restore.
- [ ] `/ee eventtp <EVENT> [mapId]` teleports staff to a configured event map without starting the event.
- [ ] `/ee autostart status` shows automatic event starts are disabled by default.
- [ ] `/ee autostart on` enables automatic hourly event votes.
- [ ] `/ee autostart off` disables automatic hourly event votes again.
- [ ] With autostart off, wait across an hourly vote time and confirm no automatic vote starts.
- [ ] `/ee disable <EVENT>` prevents that event from appearing in `/event start`, random votes, automatic votes, and `/ee forcestart`.
- [ ] `/ee enable <EVENT>` restores that event to starts/votes.
- [ ] `/ee disabled` lists disabled events clearly.
- [ ] `/ee enabled` lists enabled events clearly.
- [ ] `/ee status` shows active event, autostart state, and disabled events.
- [ ] `/ee private <EVENT>` starts a private waiting event without global chat announcements.
- [ ] `/ee private <EVENT>` works even if that event is disabled.
- [ ] `/ee invite <player>` invites a player to the active private event.
- [ ] Invited players can join the private event with `/event join`.
- [ ] A non-invited player cannot join or spectate a private event.
- [ ] Private event does not countdown or start until `/ee advance` or `/ee forcestart`.
- [ ] Private event winner/no-winner messages are not announced globally or privately.
- [ ] Private event other messages are only visible to invited/event players.
- [ ] Private event start sound does not play globally.
- [ ] `Fight 2v2` runs as a 2v2 bracket: only two teams fight at a time and waiting teams are spectators.
- [ ] `Sumo 2v2` runs as a 2v2 bracket: only two teams fight at a time and waiting teams are spectators.
- [ ] 2v2 friendly fire is blocked.
- [ ] 2v2 winners advance as a team after both opponents are eliminated.
- [ ] OITC scoreboard shows top players and your kills.
- [ ] Quake lasts about 4 minutes.
- [ ] OITC lasts about 5 minutes.
- [ ] Winner/no-winner message appears when players enter the trophy room, not after trophy cleanup.

## Core Regression Test

- [ ] `/event join`, `/event leave`, `/event vote`, `/event start`, `/event stats`, `/event next` still work.
- [ ] `/ee forcestart <EVENT>` still works for enabled events.
- [ ] `/ee forcestop` restores players and clears event items.
- [ ] `/ee retryrestores` works for players with pending snapshots.
- [ ] Players keep their pre-event inventory, armor, health, food, location, game mode, and scoreboard after event end/leave/relog restore.
- [ ] Players cannot keep event-only items after trophy room or restore.
- [ ] Players cannot drop kit/vote/event items in hubs or active event modes where drops are blocked.
- [ ] Players cannot place/break blocks in the hub, trophy room, or protected event maps unless they are allowed/admin.
- [ ] Spectators cannot teleport to players with the spectator teleport menu.
- [ ] Blocked commands still include `/withdraw` and `/deposit` during events.
- [ ] CombatX/newbie protection and NotBounties do not affect event combat/rewards.

## Event Smoke Tests

- [ ] Capture the Flag: flags render, particles follow, slowness applies, captures require own flag home, and final capture waits before ending.
- [ ] Capture Players: capture, jail, free-zone rescue, head riding, no crouch dismount, and 3-round win all work.
- [ ] BedWars: shops spawn, team kills are blocked, hunger is blocked, item durability does not decrease, beds control respawns/final deaths.
- [ ] SkyWars: chests are locked during prestart, ender pearls work, last-player end is quick, glow starts after 5 minutes.
- [ ] Boat Race: players spawn in boats, boats have no collisions, finish line only finishes at the actual finish/checkpoint route.
- [ ] Horse Race: players spawn on saddled horses, checkpoints count while mounted, falling below recovery height returns them.
- [ ] Elytra Race: checkpoint item works, death returns to last checkpoint, health is restored after event, first finisher does not instantly end the race.
- [ ] Block Party: floor is ready before teleport, movement works during prestart, eliminations only happen below the floor, floor resets after event.
- [ ] Quake: shoot/boost work, no fall damage, player respawns after death, no hoe dropping, no event items after trophy.
- [ ] OITC: bow/arrow/axe work, axe respects cooldown, death is handled by event respawn, no fall damage or hunger.
- [ ] Splegg: shovel launches snowballs, 2-tick shot limit works, players cannot hit each other, broken blocks reset after event.
- [ ] Red Light Green Light: yellow timing is playable, finish line records finishers, event respects timer.

## Live Server Migration Plan

- [ ] Back up the live server before copying anything.
- [ ] Stop the live server or at least stop event activity before copying plugin files/worlds/configs.
- [ ] Copy the new EnthusiaEvents jar to the live server plugins folder.
- [ ] Copy EnthusiaEvents config folders/files that define maps, kits, loot, scoreboards, and event settings.
- [ ] Copy exported event worlds from the test server to the live server world folder.
- [ ] Confirm the copied worlds keep the same names referenced in `maps.yml`.
- [ ] Copy hub and trophy worlds/config if they were exported separately.
- [ ] Install or configure required dependencies on live: Vault/economy, PlaceholderAPI if used, Multiverse/world loader if used, CombatX/CombatLogX, NotBounties, OldCombatMechanics.
- [ ] Configure OldCombatMechanics for BedWars worlds, for example `Events-BedWars: [ "old" ]` or numbered worlds like `Events-BedWars-1`.
- [ ] Start the live server with `schedule.enabled: false`.
- [ ] Run `/ee reload`.
- [ ] Run `/ee disabled` and confirm any risky events are disabled before release.
- [ ] Run `/ee autostart status` and confirm automatic starts are disabled.
- [ ] Run private tests on live with `/ee private <EVENT> <staff...>`.
- [ ] Test one public forced event with staff only if needed.
- [ ] When ready to release, run `/ee autostart on`.

## Release Gate

- [ ] No player is stuck in an event world, spectator mode, or event scoreboard after leaving/relogging.
- [ ] No event item can be carried back to survival.
- [ ] No known dupes from death, disconnect, trophy room, leave, or forced stop.
- [ ] No disabled event can be started by vote, GUI, command, queue, or automatic scheduler.
- [ ] Private events do not leak global messages.
- [ ] Auto-start remains off until the exact release moment.
