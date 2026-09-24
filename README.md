# Uncharted SMP: Bloodbath

A blood-themed rework of the **Uncharted SMP** weapons mod for Fabric **1.21.11**: ten ability
weapons, each with a real 3D model, a shared blood palette for every particle and sound, and a
rewritten core that closes the dupe and exploit holes in the original.

**Download:** [`dist/unchartedsmp-bloodbath-0.3.0.jar`](dist/unchartedsmp-bloodbath-0.3.0.jar)

It replaces `unchartedsmp-server-only.jar`. Item ids are unchanged (`unchartedsmp:riftblade`, …),
so weapons players already own keep working and pick up the new look.

## The weapons

| Item id | New name | Ability |
|---|---|---|
| `riftblade` | **Bloodrift Blade** | Tear a bleeding rift up to 14 blocks ahead that drags enemies in. Use again within 6s to step through. |
| `bloodhook` | **Bloodhook** | Chain of blood at the player you're looking at; reels you in. Repeat hooks on the same target extend range 10 → 28. |
| `nullblade` | **Clotblade** | Hits clot a player's blood, suppressing their abilities for 4s. Right-click: 8s clot field (4-block radius). |
| `meteor_gauntlet` | **Blood Meteor Gauntlet** | Punch a block or right-click the ground (24 blocks): a blood meteor lands 2s later for 5 damage and a big launch. |
| `gravestone` | **Crimson Gravestone** | Blood pool drags everything within 6 blocks inward for 3s, then erupts. |
| `chronos` | **Bleeding Chronos** | Leave a blood-mark, use again within 8s to snap back to it. |
| `thunder_pike` | **Crimson Thunder Pike** | Bolt of charged blood up to 22 blocks; crimson lightning strikes and you ride it there. |
| `mirrorfang` | **Blood Mirrorfang** | 7s blood-mirror of you that slashes the nearest enemy every 0.5s. |
| `void_scythe` | **Hemorrhage Scythe** | Every hit adds bleed; the 5th hemorrhages for 10 damage plus 8 to everything nearby. |
| `paradox_bow` | **Sanguine Paradox Bow** | A real bow (draw animation, shoots arrows, takes bow enchantments). Fully drawn shots leave a Paradox Echo that flies back along the path 3s later for 7 damage. |

Cooldowns, damage and ranges are the same as the original.

## What's new in 0.3.0

- **New models** in the style of a hand-made Blockbench sword: chunky shapes, mottled blood-steel
  blades with bright highlights and white shine, banded gray grips, and gray-framed diamond gems
  at guards and pommels. Every weapon has its own painted texture.
- **The bow is a real bow.** It draws with a pull animation (idle + 3 pull stages, like vanilla),
  is held like a bow, fires your arrows, has durability and takes Power, Punch, Flame, Infinity,
  Unbreaking and Mending. A fully drawn shot also releases the Paradox Echo.
- **Indicators everywhere:**
  - an action-bar status line while holding a weapon: cooldown bar, open rift timer, blood-mark
    timer, bow draw meter ("ECHO PRIMED" at full draw), bleed stacks, hook range, mirror timer;
  - a bell, a blood ring and a "ready" message when any ability comes off cooldown;
  - blood dripping off the weapon in your hand (plus a per-weapon accent), glowing when ready;
  - telegraphs for every delayed hit (meteor landing rings, thunder landing ring, echo countdown,
    Chronos blood-clock, clot-field edge);
  - blood that visibly flows: into the bow as you draw, into rifts, along the hook chain, and from
    anything a Bloodbath weapon kills back into the killer;
  - clot particles over players whose abilities are suppressed, drips from bleeding targets;
  - a splash on every melee hit and a gory burst on every kill.
  Ability particles are sent as important/long-range, so they show even on "Decreased" or
  "Minimal" particle settings.
- **Tooltips:** every weapon has a red name and lore lines explaining its ability and cooldown.
- **Enchanting:** Bloodrift Blade, Clotblade and Hemorrhage Scythe accept sword enchantments
  (Sharpness, Smite, Sweeping Edge, Fire Aspect, Looting, Knockback, Unbreaking, Mending).
- **Meteor Gauntlet** can also be used with right-click on the ground you're looking at.

## What changed

### Dupes and exploits fixed

- **Mirrorfang item dupe (critical).** The mirror was an armor stand wearing *full copies* of the
  caster's armor and main-hand item (enchantments, shulker contents, everything), and anyone could
  right-click it to take them. If the chunk unloaded or the server stopped during the 7 seconds,
  the stand and its copied gear stayed in the world forever. Now the mirror wears bare display
  copies only, every interaction with it is cancelled, it's deleted on server stop, and any mirror
  that loads from disk (chunk reload, restart, crash) is deleted on sight.
- **Thunder Pike lightning.** Real lightning struck the wielder right after the teleport, set
  fires, and could be farmed to turn villagers into witches, pigs into piglins and creepers into
  charged creepers. The bolt is now cosmetic and the pike deals its own 5 damage (caster excluded).
- **Teleports into walls.** Thunder Pike, Bloodrift and Chronos put the player's hitbox straight
  onto a wall face (half inside it) and could be used to clip into bases. They now back off along
  the path to the nearest spot the player actually fits, and Chronos refuses to recall into a mark
  that has been built over.
- **Portal-hop mid-cast.** Delayed effects (Thunder Pike, Bloodhook) now check you're still in the
  same dimension before moving you.
- **Bloodhook through walls.** Targets now need line of sight.
- **Meteor Gauntlet self-hit.** The impact damaged and launched its own wielder. Caster is excluded.
- **Paradox Bow multi-hit.** The echo could hit the same entity several times; now once per echo.
- **Hemorrhage Scythe shared stacks.** Bleed was tracked per target only, so two players could
  stack each other's bleed. Now per attacker + target.
- **No relog resets.** Cooldowns and Clotblade debuffs survive disconnects (dropping them would let
  players reset cooldowns by relogging); short-lived personal state (rifts, marks, hook streaks) is
  cleared on disconnect.

### Performance

- The tick scheduler scanned one flat list on **every world tick** (3+ times per server tick) and
  abilities queued one lambda per tick of effect (Mirrorfang alone queued 140 per cast, Gravestone
  60). It's now a priority queue drained once per server tick, and multi-tick effects are a single
  self-rescheduling task. A failing task is logged instead of breaking the tick.
- Every per-player map (cooldowns, debuffs, rifts, marks, streaks, bleed stacks) is swept of
  expired entries once a minute. The old maps grew forever, including one entry per mob ever hit
  with the scythe.
- Area queries use a true sphere and skip spectators and armor stands.

### Code

- One `AbilityWeapon` base class handles the server-side / clot-field / cooldown boilerplate that
  each item used to copy-paste.
- Abilities are an enum (`Ability`) with their cooldowns in one place; cooldowns are a flat
  `long[]` per player.
- All timing uses a monotonic server tick counter (`ServerClock`), not per-world time.
- Blood particles and sounds live in one palette (`BloodFx`), resolved by registry id at startup.

## 3D models

Every weapon has a cuboid 3D model (11-24 cubes) with its own painted texture atlas: mottled
blood steel, black steel, banded grip metal, framed gems, glowing blood (emissive), bone, gold,
grave stone, blood-glass and bowstring. The bow has four models (idle and three pull stages)
switched by the client the same way as the vanilla bow.

- Models: `src/main/resources/assets/unchartedsmp/models/item/*.json` (open in Blockbench as a
  Java Block/Item model to tweak).
- Textures: `src/main/resources/assets/unchartedsmp/textures/item/<weapon>.png`.
- All of it is generated by [`tools/models/generate.py`](tools/models/generate.py): edit the cube
  lists there and rerun `python3 tools/models/generate.py` (needs `numpy` and `pillow`).

Models are authored upright and the -45° tilt vanilla expects for held items is folded into the
display transforms, so they sit in the hand like vanilla swords, show as a 3/4 view in the
inventory and lie flat on the ground.

## Installing

Server: Fabric Loader 0.18+, Fabric API, Minecraft 1.21.11, Java 21. Drop the jar into `mods/`.

Vanilla clients can't see custom items on their own. As with the original, run it alongside a
server-side translation layer such as **Polymer + PolyMc** for 1.21.11, which sends vanilla
clients the items along with an auto-generated resource pack built from this jar's assets (models,
textures, names). Players who install the mod client-side get the models directly.

## Building

Normal build (needs internet access to Fabric's maven and Mojang):

```sh
./gradlew build   # -> build/libs/unchartedsmp-bloodbath-0.3.0.jar
```

Offline build (what produced `dist/`; no Loom, no Mojang downloads):

```sh
tools/offline-build/build.sh   # -> dist/unchartedsmp-bloodbath-0.3.0.jar
```

It compiles against small Yarn-named API stubs, remaps the bytecode to Fabric intermediary names,
and checks every Minecraft method and field it uses against the official 1.21.11 intermediary
mappings. The build fails if any reference can't be verified.
