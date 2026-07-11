# EnthusiaEvents Retest Update

Copy this message into Discord for the owner/staff retest.

---

## **Important Command Change**

All event/map commands now tab-complete event names, map IDs, saved setup IDs, reset sections, and confirmations.

**Open a setup palette:**
```
/ee setup <EVENT> <mapId>
```

**Save and exit setup:**
```
/ee setup save
```

The longer form also works while that map is open:
```
/ee setup <EVENT> <mapId> save
```

**Teleport to a map without starting its event:**
```
/ee eventtp <EVENT> <mapId>
```

**List map IDs for an event:**
```
/ee map list <EVENT>
```

---

## **Gameplay Updates To Retest**

- Natural mobs are blocked in event maps, the event hub, and trophy room.
- Crafting is blocked in every event except SkyWars.
- Players may manually drop items in **CTF**, **Fight 1v1/2v2/FFA**, **BedWars**, and **SkyWars**. Other event modes still block manual drops.
- Event deaths are simulated where possible. Normal lethal event damage should not open the vanilla death screen or add Minecraft death statistics.
- SkyWars drops inventory and armor, then moves the player to spectator state.
- BedWars kill resources transfer directly to the killer. If their inventory is full, resources are queued for delivery instead of dropping on the ground.
- Elytra Race lethal damage returns the player to their last checkpoint.
- Capture Players jail containment, Fight prestart flight handling, Sumo disconnect brackets, and Boat Race submerged-boat recovery were updated.

---

## **BedWars: Required Setup Refresh**

Old BedWars bed markers are no longer valid. Each team must have a real placed bed saved from the setup palette.

```
/ee eventtp BEDWARS <mapId>
/ee map clear BEDWARS <mapId> beds
/ee setup BEDWARS <mapId>
```

In the palette, use the **Team Tools** page. Select the team, hold **Team Bed**, and click that team's actual placed bed. The bed's material and facing are saved. Repeat for every team, then run:

```
/ee setup save
```

Retest that enemy beds break, own beds do not, beds rebuild before the match, and bed-alive respawns still work.

---

## **Horse Race: Checkpoint Refresh**

```
/ee eventtp HORSE_RACE <mapId>
/ee map clear HORSE_RACE <mapId> checkpoints
/ee setup HORSE_RACE <mapId>
```

Recreate the checkpoint areas/points, then save:

```
/ee setup save
```

Retest mounted checkpoint detection and falling recovery.

---

## **Capture The Flag: Spawn Refresh**

```
/ee eventtp CAPTURE_THE_FLAG <mapId>
/ee map clear CAPTURE_THE_FLAG <mapId> spawns
/ee setup CAPTURE_THE_FLAG <mapId>
```

Use the team selector and set each team spawn again. Do **not** clear flags unless they also need to be redone. Save with:

```
/ee setup save
```

Retest flags, carriers, captures, returns, respawns, and item dropping.

---

## **Fight 1v1: Full Setup Reset**

This clears saved setup values for only the selected Fight 1v1 map. It does **not** delete the map world.

```
/ee eventtp FIGHT_1V1 <mapId>
/ee map clear FIGHT_1V1 <mapId> all confirm
/ee setup FIGHT_1V1 <mapId>
```

Set the region, team spawns, and spectator spawn again, then save:

```
/ee setup save
```

Retest a full 1v1 match: prestart, no flying kick, combat, elimination, spectator state, next bracket match, and restore.

---

## **Map Reset Reference**

Clear one setup category without deleting the world:

```
/ee map clear <EVENT> <mapId> <section>
```

Useful sections: `beds`, `spawns`, `finishes`, `checkpoints`, `checkpoint-blocks`, `shops`, `generators`, `areas`, `chests`, `points`, `spectator`, and `region`.

Clear all saved setup for one map while keeping its map ID/world:

```
/ee map clear <EVENT> <mapId> all confirm
```

---

## **Priority Retest List**

1. BedWars real-bed setup, breaking, respawns, shops, and resource transfer.
2. Fight 1v1 full setup rebuild and a complete bracket match.
3. SkyWars simulated death with normal item drops.
4. Elytra Race checkpoint respawn and terrain restrictions.
5. CTF team spawns, flag handling, and allowed item drops.
6. Capture Players jail containment.
7. Sumo 1v1/2v2 disconnect behavior.
8. Boat Race submerged-boat recovery.
