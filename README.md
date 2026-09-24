# Bloodbath

Twelve blood-soaked ability weapons with real 3D models, the Blood Knight's armour set, and the
Blood Knight himself as an animated boss fight, for **Paper 1.21.4 and newer**. Comes with
commands, an armory menu, kill ranks, a config for every number, Blood Core crafting recipes, and
a resource pack that the plugin hosts for you.

**Downloads**

| File | What it is |
|---|---|
| [`dist/paper/Bloodbath-1.3.0.jar`](dist/paper/Bloodbath-1.3.0.jar) | The plugin. This is the one you want. |
| [`dist/Bloodbath-ResourcePack-1.3.0.zip`](dist/Bloodbath-ResourcePack-1.3.0.zip) | The resource pack, if you'd rather host it yourself (the plugin already contains it). |
| [`dist/fabric/unchartedsmp-bloodbath-0.3.1.jar`](dist/fabric/unchartedsmp-bloodbath-0.3.1.jar) | The old Fabric mod (10 weapons, no commands). Kept for reference. |

## Install

1. Paper **1.21.4 or newer** (checked against 1.21.4, 1.21.11 and 26.3), Java 21. Paper forks such
   as Purpur work too. Folia isn't supported.
2. Drop `Bloodbath-1.3.0.jar` into `plugins/` and restart.
3. Let players download the 3D models: open **TCP port 8163** on your firewall/router. The plugin
   serves the resource pack from there and players get a download prompt when they join. Can't
   open a port? See [Resource pack](#resource-pack).
4. In game: `/bb armory` to browse everything, or `/bb give <you> all`.

Players need a 1.21.4+ client to see the 3D models. Everyone else, and anyone who declines the
pack, sees named netherite swords and a bow. Every weapon still works for them.

## The weapons

| Weapon (`id`) | Right-click | Also |
|---|---|---|
| **Bloodrift Blade** (`riftblade`) | Tear a bleeding rift up to 14 blocks ahead that drags enemies in. Use again within 6s to step through. | 10 damage sword |
| **Bloodhook** (`bloodhook`) | Chain whatever you're looking at and reel yourself to it. Hooking the same target again within 12s extends the range from 10 up to 28 blocks. | Can't hook through walls |
| **Clotblade** (`nullblade`) | Throw down a clot field (4 blocks, 8s) that suppresses everyone's abilities inside. | Hits clot a player's blood: no abilities for 4s. Its own field never stops it. |
| **Blood Meteor Gauntlet** (`meteor_gauntlet`) | Or punch a block: a blood meteor lands there 2s later, 5 damage and a big launch in 4.5 blocks. | Never hits its wielder |
| **Crimson Gravestone** (`gravestone`) | A blood pool drags everything within 6 blocks in for 3s, then erupts and hurls it away. | |
| **Bleeding Chronos** (`chronos`) | Leave a blood-mark. Use again within 8s to snap back to it, facing and all. | Cooldown starts on recall |
| **Crimson Thunder Pike** (`thunder_pike`) | Hurl charged blood up to 22 blocks, call crimson lightning there (5 damage) and ride it. | The lightning is cosmetic: no fires, no charged creepers |
| **Blood Mirrorfang** (`mirrorfang`) | A blood mirror of you fights for 7s, slashing the nearest enemy every half second. | Dupe-proof, see below |
| **Hemorrhage Scythe** (`void_scythe`) | **Harvest:** a blood arc that cuts everything in front of you and makes it bleed. | Every hit adds a bleed stack that hurts every 1.5s (ignores armour); the 5th hemorrhages for 10 damage and 8 to everything around. 12 damage, attack speed 1.0. |
| **Sanguine Paradox Bow** (`paradox_bow`) | A real bow. | **Paradox Echo:** a fully drawn shot fires twice. A blood rift opens where you shot from; 1.5s later a phantom arrow tears out of it and homes into whatever your arrow hit, wherever it ran (7 damage, pierces on the way). If the arrow hit nothing living, the echo retraces its real flight path instead. |
| **Vampire Fang** *(new)* (`vampire_fang`) | **Blood Dash:** lunge along your aim, cutting and drinking from everything you pass through. | Every hit heals you for 25% of the damage. Fast 6 damage dagger. |
| **Blood Grimoire** *(new)* (`blood_grimoire`) | **Transfusion:** a blood tether drains 6 health from a creature over 1.5s into you. | Sneak + right-click a player to give them 8 health (and Regeneration) for 4 of yours. Works with PvP off. |

Every number above (cooldowns, damage, ranges, durations) is in `config.yml`. Right-clicking a
door, chest or button with a weapon uses the block as usual; sneak to use the ability instead.
Weapons work from the off hand too.

While you hold a weapon, the action bar shows its cooldown and state (rift timer, bleed stacks,
bow draw, echo lock-on, hook range, mirror timer...). A bell and a blood ring tell you when an
ability is ready. Ability hits tick softly when they land and a skull flashes under your crosshair
on a kill. Every delayed hit is telegraphed on the ground, and kills burst and bleed back into the
killer.

**The held aura.** The weapon in your hand drips blood and throws embers. You see your own aura
too: it's drawn low, under your hand, so it never sits in front of your camera. Everyone else sees
the full version around the weapon.

**Particles are cheap.** Each effect goes only to players within `effects.view-distance` (64
blocks), at full detail within `full-detail-distance` (24) and thinner further out. Effects nobody
can see aren't sent at all.

## Blood Knight armour

The boss's armour, worn by players: **Blood Knight Helm, Cuirass, Greaves and Sabatons**
(`/bb give <you> armor`). Every piece beats its netherite counterpart:

| | Armor | Toughness | Knockback resistance | Extra |
|---|---|---|---|---|
| Netherite (helm / chest / legs / boots) | 3 / 8 / 6 / 3 | 3 | 0.1 | |
| **Blood Knight** | **4 / 9 / 7 / 4** | **3.5** | **0.15** | **+1 heart each**, fireproof |

| Set bonus | |
|---|---|
| **2 pieces, Bloodlust** | Every kill heals you 1.5 hearts. |
| **3 pieces, Barbed Blood** | Whatever hits you in melee takes 1 heart back. |
| **4 pieces, Blood Knight** | While the whole set is on: **Strength, Speed and Fire Resistance**. They switch on the moment the fourth piece goes on (with a flash and a sound) and come off the moment any piece comes off. Only these effects are touched: a Strength potion you drank stays yours. Editable in `armor.full-set-effects`. |
| **4 pieces, Blood Rage** | When a hit leaves you at 40% health or less: Strength II and Resistance for 8s and a blood shockwave that hurls everything within 4 blocks away. Your screen edges run red and your heart pounds until it fades. 60s cooldown, shown on the action bar. A clot (Clotblade) stops it. |

The Sabatons leave bloody footprints and a full set drips blood (you see it too). The worn armour
is painted at 4x vanilla resolution: brushed gunmetal plates with bevels and rivets, a grilled
faceplate, engraved crimson filigree, a gem at the heart and a chainmail band. The worn armour and the icons come from the resource pack. **Without the
pack, the pieces look like netherite in the inventory but are invisible when worn** (vanilla has
no model for them). Keep that in mind if the pack is optional on your server, or turn the set off
with `armor.enabled: false`.

**Kill ranks.** Weapons count their kills and rank up: Blooded (5), Crimson (25), Sanguine (100),
Bloodbath (250). The count lives on the item, so it travels with the weapon when it's traded.

## The Blood Knight (boss)

The armour's owner, as a real fight. With the pack he's a fully animated 3D model (the model you
gave, rigged into 16 bones): he claws his way out of the ground, idles with his sword breathing,
walks, and every attack has its own animation and tells.

**Summoning.** Sneak and right-click **crying obsidian** holding a **Blood Core** (one is used).
Six seconds of omens (tolling bells, the ground shaking, blood welling up) and he rises. Or
`/bb boss summon` as an op. One at a time by default.

| Attack | Tell | How to live |
|---|---|---|
| **Cleave** (12) | He winds his sword back; a blood arc marks the ground in front of him. | Get out of the arc. |
| **Slam** (10) | Sword raised over his head, then a shockwave rolls out along the ground. | Jump over the ring. |
| **Blood Spikes** (9) | He plunges the sword; rings open under up to 3 players. | Step off the ring before it erupts. |
| **Shield Charge** (8) | He braces behind his shield and a line is drawn to his target. | Sidestep the line. |

At half health he's **bloodied**: he roars, knocks everyone back, blinds them with Darkness, the
bar turns purple, and his attacks come 30% faster and hit 20% harder. Mist hangs over the arena
while he fights. Anyone who dies in the arena is announced as claimed by the Blood Knight. If
everyone leaves (60s) he sinks back into the ground.

When he falls: a death animation, a burst of blood, a victory sound for everyone nearby, and his
loot: **2 to 4 Blood Cores**, a 35% chance of one Blood Knight armour piece, and 500 XP.

He can't be hurt by falling, fire, lava, suffocation, drowning, cramming, freezing, wither or
poison, is pulled back if knocked into the void, never despawns and is never saved to disk: a
restart, the plugin being disabled, or his world unloading ends the fight cleanly. Without the pack, players see a giant wither skeleton in netherite.

## Blood Core

A reskinned nether star: a thorned blood crystal whose heart beats (animated, from the pack). It's
the ingredient every Bloodbath recipe needs. Get it from the boss, craft one (nether star in the
middle, ghast tears and redstone blocks around it) or `/bb give <you> core`.

## Commands

`/bloodbath`, `/bb` or `/blood`:

| Command | Who | |
|---|---|---|
| `/bb help` | everyone | |
| `/bb armory` | everyone | Browse every weapon and armour piece. Admins click to take one. |
| `/bb list` | everyone | Hover a name to see the item. |
| `/bb info <weapon\|piece>` | everyone | Accepts ids or names ("scythe", "Clotblade", "helm"). No name: the weapon in your hand. |
| `/bb hud [on\|off]` | everyone | Hide or show the action-bar line for yourself. Remembered. |
| `/bb pack` | everyone | Get the resource pack again. |
| `/bb give <player\|all> <weapon\|piece\|armor\|core [n]\|all>` | op | `/bb give <weapon>` gives it to yourself. `armor` is the whole set; `core 16` is 16 Blood Cores; `all` is every weapon plus the set. |
| `/bb reset [player\|all]` | op | Clear cooldowns and clots. |
| `/bb pack <player\|all>` | op | Send the pack to someone else. |
| `/bb status` | op | Pack server, downloads, recipes, live effects, disabled weapons. |
| `/bb boss summon\|stop\|status` | op | Summon the Blood Knight where you're looking, end every fight, or see what's running. |
| `/bb debug` | op | Toggle a chat log of every ability hit you deal: damage, health before and after, or which plugin cancelled it. |
| `/bb reload` | op | Reload `config.yml`. Weapons update as players hold them. |

Permissions: `bloodbath.use` (use abilities), `bloodbath.command`, `bloodbath.armory` and
`bloodbath.craft` are on for everyone; `bloodbath.give` and `bloodbath.admin` are op-only.

## Config

`plugins/Bloodbath/config.yml` is commented. Highlights:

- `weapons.<id>`: `enabled` plus every cooldown, damage, range and duration.
- `gameplay.disabled-worlds`: worlds where abilities don't work (weapons still hit).
- `gameplay.respect-world-pvp`: abilities don't hurt, pull or launch players where PvP is off.
- `effects.particle-multiplier`: 0.5 halves every particle, 0 turns them off.
- `effects.view-distance` / `full-detail-distance`: who gets sent particles, and how many.
- `effects.pack-visuals`: the custom particle sprites, HUD icons and armory art for players with
  the pack. `effects.rage-vignette`, `hud.hit-markers`: turn those off individually.
- `sounds.volume` scales every Bloodbath sound; `sounds.replace` swaps any of them for another
  sound (including one from your own pack), or `''` to mute it.
- `boss`: health, armour, damage of each attack, arena size, how many at once, the ritual, loot.
- `kill-tracking`: turn it off, or count only player kills.
- `armor`: the Blood Knight set on or off, and every set-bonus number.
- `recipes`: on by default. One recipe per weapon and armour piece, each needing a **Blood Core**,
  plus the recipe for the core itself (all editable; write `BLOOD_CORE` as an ingredient).
  Bloodbath items can never be used as crafting ingredients, and a core only works in Bloodbath
  recipes.
- `items.tooltip-frame`: the blood tooltip frame (see below).

## Resource pack

The pack is inside the plugin. By default (`mode: embedded`) the plugin runs a tiny web server on
port 8163 that serves only that file, and sends each player a link using the same address they
typed to join. There's nothing to configure if that port is reachable.

- **Behind BungeeCord/Velocity/TCPShield**, or players join through a different address than the
  pack should use: set `resource-pack.public-host` to your server's domain or IP.
- **Can't open a port** (most shared hosts only give you one): the plugin exports the pack to
  `plugins/Bloodbath/Bloodbath-ResourcePack.zip`. Upload it anywhere that gives a direct download
  link, set `mode: url` and put the link in `url`. Or merge it into your server's own pack.
- `required: true` kicks players who decline. Only use it once the download works for everyone.
- **Blood tooltip frame:** weapons can get a blood-red tooltip frame from the pack, but players
  without the pack would see a broken purple-and-black tooltip instead. So it's on only when the
  pack is required (`items.tooltip-frame: auto`); set it to `true` or `false` to force it.

If a download fails, the player is told, and the console says why (usually the port).

The pack overrides the vanilla netherite sword, bow and netherite armour models only for Bloodbath
items (matched by `custom_model_data`); every other item, armour trims included, is untouched.

## Dupe and exploit safety

- **Blood Mirror:** an armor stand dressed like you, which makes it the one place an item dupe
  could hide. It only wears bare display copies (no enchantments, no contents, not real weapons),
  every slot is locked, every interaction and all damage are cancelled, it drops nothing if killed,
  it's never saved to disk, it's removed when the plugin stops, and any mirror that isn't live is
  deleted on sight.
- **Armory:** every icon is a sheet of paper wearing the weapon's look, and every click in the
  menu is cancelled, so nothing real can be pulled out of it.
- **Crafting:** weapons and armour are netherite underneath, so they're blocked as crafting
  ingredients (that also stops the grid's repair recipe turning two weapons into one sword).
- **Teleports** (rift, pike, recall) never put you inside blocks, and don't carry you across
  dimensions mid-cast. Bloodhook and Transfusion need line of sight.
- **Cooldowns and clots survive relogging.**
- Ability damage goes through the normal damage pipeline as a player attack, so armor, claims and
  PvP protection plugins apply, and kills are credited to the right player and weapon.

## What's new in 1.3.0

- **The Blood Knight boss**: the model you gave, rigged and animated, with four telegraphed
  attacks, a second phase, custom sounds, mist, a yellow boss bar with custom art for pack users,
  and loot.
- **Blood Core**: an animated reskinned nether star, now part of every recipe. Recipes are on by
  default.
- **Particles are back where you can see them.** In 1.2 the held-weapon aura and the armour drips
  were shown only to other players, so on your own screen weapons looked plain. You now see a
  version drawn under your hand. New effects: blood mist, drips, embers, a crimson "blood nova"
  shockwave sprite (pack), spirals, columns, converging and rising blood.
- **Abilities no longer swallow your next swing.** Ability damage used to leave the target
  invulnerable for half a second, which ate the melee hit that should've followed, so on-hit
  passives (bleed, lifesteal, barbs) seemed not to work. Fixed. `/bb debug` shows exactly what
  every ability hit did.
- **Bleed actually bleeds**: stacks now deal damage over time.
- **Hemorrhage Scythe attack speed 1.0.**
- **Armour** beats netherite on every stat, and the full set gives Strength, Speed and Fire
  Resistance while worn (gone the moment a piece comes off). Repainted at 4x resolution, with
  icons of its own.
- **Pack art**: HUD icons for the cooldown bar, the armory menu's own background, the Blood Core,
  the blood nova sprite and the boss bar.
- **Performance**: particles go only to players in range, with distance-based detail, and are
  skipped when nobody can see them. Armour wearers are tracked by events instead of scanning every
  player every tick. Boss model updates are only sent when a part actually moved.
- **Sounds**: master volume and a replace/mute map.

## What's new in 1.2.0

- **Sanguine Paradox Bow, reworked.**
  - **It's held like a bow now.** Its 3D model was rotated 90° from the vanilla bow's pose,
    so in first person it lay across the screen. It now sits exactly where the vanilla bow
    does, idle and drawing, and in third person too.
  - **New model:** a recurve with dark blood-steel limbs, a glowing vein along the inside, bone
    spurs and a red-fletched arrow.
  - **The echo actually lands.** It used to come back 3s later along wherever you were
    looking. Now it homes into what your arrow hit, or retraces the arrow's real flight.
  - **Nothing in your face:** the blood gathering while you draw is shown to others only, and
    your arrow's trail starts a few blocks out.
- **Blood Knight armour, revamped.**
  - Normal vanilla-style inventory icons for all four pieces.
  - A new, clean worn look: bevelled plates, crimson trims, burning visor eyes, a glowing
    breastplate core, knee glows.
  - New 3-piece bonus (Barbed Blood), a red screen tint while raging, the rage on the action
    bar, and footprints.
- **Blood drips are blood red.** They used lava drips, which showed up as orange squares.
- **The status line clears** as soon as you put a weapon away (it used to linger for 3s, which
  made a plain bow look like the Paradox Bow).
- Hit markers and a kill marker (`hud.hit-markers`).
- The Bloodhook chain and the Bloodrift's launch now come from your hand instead of your eyes.

## What's new in 1.1.0

- **Blood Knight armour**, based on the boss model: four pieces with set bonuses (Bloodlust and
  Blood Rage), their own worn texture, an armory row, recipes and config.
- **No more texture flicker.** Every model had overlapping faces that z-fought (flickered)
  at some angles. The generator now finds them and pushes the smaller face out. It also pads every
  texture with a 1-pixel border so edges don't bleed at a distance.

## What's new in 1.0.0

- **Paper plugin.** Ported from the Fabric mod. No client mod and no Polymer needed.
- **Commands, armory menu, config, recipes, kill ranks.**
- **Two new weapons:** Vampire Fang and Blood Grimoire, with 3D models in the same style.
- **Self-hosted resource pack** with a fallback: without it, weapons look vanilla instead of
  broken.
- **Blood tooltip frame.**
- **Hemorrhage Scythe's right-click** used to only play an effect. It's now Harvest.
- **Abilities always land:** a melee hit a moment earlier used to eat the ability's damage
  (hurt immunity). In the Fabric version, a fully charged 5th scythe hit meant the hemorrhage did
  no damage at all to its own target.
- **Bloodhook** can hook mobs too, and its pull scales with distance so you land at the target.
- Kills from abilities and Paradox Bow arrows count for the weapon that made them.

## Building

```sh
./gradlew -p paper build   # -> paper/build/libs/Bloodbath-1.3.0.jar (+ the pack zip), runs the tests
```

The tests load the plugin into [MockBukkit](https://github.com/MockBukkit/MockBukkit) (a mock
Paper 1.21.11 server) and play it: every weapon's ability and passive, kill tracking, the armory,
the mirror's dupe guards, the armour set bonuses, the bow's echo, that nothing is drawn in a
player's own face, commands, config reloads, recipes and the pack server. The test world
checks particle data the way Paper does, and any error the plugin logs fails the test.

`paper/offline-build/build.sh` builds the same plugin without Gradle or network access (it needs
the Paper API jars in `paper/offline-build/.cache/libs`). It's what produced `dist/`; its output
is byte-for-byte reproducible.

### 3D models

The models, textures, tooltip frame and pack overrides in `resourcepack/` are all generated by
[`tools/models/generate.py`](tools/models/generate.py) (needs `numpy` and `pillow`). Edit the cube
lists there and rerun it. Each weapon is 11-37 cubes with its own painted texture; the bow has an
idle model and three pull stages. The armour's icons and worn textures
(`textures/entity/equipment`) are drawn there too. The `.json` models open in Blockbench as Java Block/Item models.

## The Fabric mod (legacy)

`dist/fabric/unchartedsmp-bloodbath-0.3.1.jar` is the previous version, a Fabric 1.21.11 mod with
the first ten weapons. It needs Fabric API, and vanilla clients need Polymer + PolyMc on the server.
It isn't developed further; its source is in `fabric/`.

- Build: `./gradlew -p fabric build`, or offline with `fabric/offline-build/build.sh`, which
  compiles against Yarn-named stubs, remaps to intermediary and verifies every Minecraft reference
  against the official 1.21.11 mappings.
- **Moving a world from Fabric to Paper:** the Fabric mod's items (`unchartedsmp:riftblade`, ...)
  don't exist on Paper, so they disappear from inventories. Hand weapons out again with `/bb give`.
