# Bloodbath

Twelve blood-soaked ability weapons with real 3D models, the Blood Knight's armour set, and the
Blood Knight himself as an animated boss fight, for **Paper 1.21.4 and newer**. Comes with
commands, an armory menu, kill ranks, a config for every number, Blood Core crafting recipes, and
a resource pack that the plugin hosts for you.

**Downloads**

| File | What it is |
|---|---|
| [`dist/paper/Bloodbath-1.4.0.jar`](dist/paper/Bloodbath-1.4.0.jar) | The plugin. This is the one you want. |
| [`dist/Bloodbath-ResourcePack-1.4.0.zip`](dist/Bloodbath-ResourcePack-1.4.0.zip) | The resource pack, if you'd rather host it yourself (the plugin already contains it). |
| [`dist/fabric/unchartedsmp-bloodbath-0.3.1.jar`](dist/fabric/unchartedsmp-bloodbath-0.3.1.jar) | The old Fabric mod (10 weapons, no commands). Kept for reference. |

## Install

1. Paper **1.21.4 or newer** (checked against 1.21.4, 1.21.11 and 26.3), Java 21. Paper forks such
   as Purpur work too. Folia isn't supported.
2. Drop `Bloodbath-1.4.0.jar` into `plugins/` and restart.
3. That's it for the 3D models: players get a download prompt when they join. The pack comes from
   the plugin's public copy on GitHub (checked to be identical to the one inside the jar), so
   there's no port to open. No other plugins are needed. See [Resource pack](#resource-pack).
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
is painted at 4x vanilla resolution and shaped so it doesn't read as a box: an open-faced helm
(brow band, nasal guard, cheek guards), a cuirass that stops above the hips so the waist tapers
into the belt, pauldrons, mail and vambraces that leave the hands bare, and low sabatons. Curved
shading on every plate, engraved crimson filigree, and one glowing heart-gem. The worn armour and the icons come from the resource pack. **Without the
pack, the pieces look like netherite in the inventory but are invisible when worn** (vanilla has
no model for them). Keep that in mind if the pack is optional on your server, or turn the set off
with `armor.enabled: false`.

**Kill ranks.** Weapons count their kills and rank up: Blooded (5), Crimson (25), Sanguine (100),
Bloodbath (250). The count lives on the item, so it travels with the weapon when it's traded.

## The Blood Knight (boss)

The armour's owner, as a real fight, and a hard one. With the pack he's a fully animated 3D model
(the model you gave, rigged into 16 bones): he claws his way out of the ground, shifts his weight
while he waits, walks with planted, heavy strides, turns his head to follow you, and every attack
has its own wind-up, strike and follow-through, with a blood trail behind the sword.

**He can't walk through walls.** His body is a solid 2.4 blocks wide, as wide as the model, so he
walks round what's in the way. His charge stops dead against a wall (and he reels from it), his
leap is real physics, and none of his attacks go through walls except Blood Spikes and Blood Rain.

**Summoning.** Sneak and right-click **crying obsidian** holding a **Blood Core** (one is used).
Six seconds of omens (tolling bells, the ground shaking, blood welling up) and he rises. Or
`/bb boss summon` as an op. One at a time by default.

**2000 health** (+60% for each extra player in the arena when he stands), 20 armour, 12
toughness, 18 per sword hit, and every hit of his leaves 2 bleed stacks that ignore armour.

| Attack | Damage | Tell | How to live |
|---|---|---|---|
| **Cleave** | 28 | Sword drawn back across his body; a blood arc marks the ground. | Get out of the arc, or behind a wall. |
| **Slam** | 24 | Sword raised in both hands, then a shockwave along the ground. | Jump it, or put a wall between you. |
| **Blood Spikes** | 20 | He plunges the sword; rings open under players. Walls don't help. | Step off the ring. |
| **Shield Charge** | 24 | He braces behind his shield; a line is drawn to his target. | Sidestep. Bait him into a wall and he staggers. |
| **Leap** | 26 | He crouches; a circle marks where he'll land. Not under a roof. | Get out of the circle. |
| **Blood Grasp** | 10 + a hammer blow | Sword levelled at you, then a tether drags you in. | Break line of sight before the pull. |
| **Blood Rain** *(bloodied)* | 14 a drop | Sword to the sky; circles marked round every player. | Leave the circles, or get under a roof. |
| **Whirlwind** *(last stand)* | 12 a beat | Three full turns with the sword held out. | Run. |

**Three phases.**
- **Bloodied (60%).** He roars (and can't be hurt while he does), knocks everyone back and blinds
  them with Darkness. From then on he hits 20% harder, attacks 30% sooner, sometimes chains a
  Cleave straight into another attack, and adds Blood Rain.
- **Last stand (25%).** Down on one knee, then up with a roar that raises **Blood Thralls** (3, +1
  per extra player). While any stand he takes **half damage**, so kill them first. He hits 35%
  harder, attacks twice as often, adds Whirlwind, and heals from 30% of the damage he deals.
- **Berserk.** Still fighting after 8 minutes, he hits 50% harder and attacks faster still.

**No cheese.** No single hit takes more than 40 off him. Arrows and tridents do half damage. Up a
pillar or in a hole he can't path to, he drags you out with Blood Grasp or hits you with spikes and
rain. Every player he kills heals him 8%, and he drinks from them. Leave him alone for 15 seconds
and he regenerates 1% a second. Every number is in `boss:` in config.yml.

Mist hangs over the arena while he fights. Anyone who dies in the arena is announced as claimed by
the Blood Knight. If everyone leaves (60s) he sinks back into the ground.

When he falls: he drops to his knees, then face down, bells ring out for everyone nearby, the
thralls crumble, and his loot drops: **3 to 6 Blood Cores**, a 60% chance of one Blood Knight
armour piece, and 1500 XP.

He can't be hurt by falling, fire, lava, suffocation, drowning, cramming, freezing, cactus,
wither or poison, is pulled back if knocked into the void, never despawns and is never saved to
disk: a restart, the plugin being disabled, or his world unloading ends the fight cleanly. Without
the pack, players see a giant wither skeleton in netherite that moves and fights the same way.

## Blood Core

A reskinned nether star: a glossy blood orb ringed with thorns whose heart beats (animated, from
the pack). It's the ingredient every Bloodbath recipe needs. Get it from the boss, craft one
(nether star in the middle, ghast tears and redstone blocks around it) or `/bb give <you> core`.

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
| `/bb visuals [on\|off\|auto]` | everyone | The custom particles, boss model and HUD icons for you. `on` if you installed the pack yourself and the download doesn't reach you; `auto` follows your pack. Remembered. |
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

The pack is inside the plugin, and players are sent it when they join. By default
(`mode: auto`) they download it from its public copy on GitHub. The plugin downloads that copy
at startup and only uses it if it's byte-for-byte identical to the pack inside the jar, so
players can never get a different or outdated one. When there's no matching copy, the plugin
serves the pack itself from a tiny web server on port 8163, which needs that port open to the
internet. If a player's download fails from one, they're sent the other straight away.

- **`mode: embedded`**: the plugin's own server first, the GitHub copy only if a player can't
  reach it. Behind BungeeCord/Velocity/TCPShield, or if players join through a different address
  than the pack should use, set `resource-pack.public-host`.
- **`mode: url`**: a pack you host yourself, e.g. if you merged Bloodbath into your server's own
  pack. The plugin exports its pack to `plugins/Bloodbath/Bloodbath-ResourcePack.zip`; upload it
  anywhere that gives a direct download link and put the link in `url`.
- **`required: true`** kicks players who decline. Only use it once the download works for
  everyone.
- **Blood tooltip frame:** weapons and armour get a blood-red tooltip frame from the pack whenever
  the plugin sends the pack (`items.tooltip-frame: auto`). Players who decline the pack see a
  purple-and-black tooltip there instead; set it to `false` if that matters on your server.

**The custom visuals** (blood particles, the animated boss, HUD icons, the armory art) only go to
players whose game reported loading the pack. Everyone else gets vanilla-safe versions: dust
particles, and the boss as a giant wither skeleton in netherite. Nobody ever sees a missing
texture.

### Pack not showing up?

1. **Check `/bb status`** (op). It shows where the pack comes from, whether the GitHub copy
   checked out, how many online players get the custom visuals, and whether you do.
2. **"The Bloodbath resource pack couldn't be downloaded"** means your game couldn't reach the pack.
   On 1.3.2 that should only happen if GitHub is blocked where you are *and* the server's port 8163
   isn't open. Open the port (on a hosting panel: add a port and set `resource-pack.port` to it), or
   host the exported zip yourself (`mode: url`).
3. **Installed the pack by hand?** The server can't see that. Run `/bb visuals on` (or click
   *[I have it installed]* in the failure message) to get the custom visuals anyway. To give them
   to everyone because the pack is forced some other way, set `effects.pack-visuals: always`.
4. **Updated the plugin with `/reload` or a plugin manager?** Players online get the new pack
   re-sent automatically. A full restart is still the cleanest.

If a download fails, the player is told, and the console says why (usually the port).

The pack overrides the vanilla netherite sword, bow and netherite armour models only for Bloodbath
items (matched by `custom_model_data`); every other item, armour trims included, is untouched.

A pack can't add new particles, only redraw vanilla ones, so the blood sprites take over particles
that are rare in normal play: the warden's sonic boom (blood nova), sculk charge (blood splat),
sculk charge pop (blood spark), sculk soul (crimson wisp), the shrieker's ring (blood ring), the
creaking heart's trail (glowing blood streams), and pointed dripstone's lava drops (drops of blood
that fall at full weight and splash with a soft plip where they land). With the pack, sculk
spreading in the deep dark, creaking trails, dripstone lava drips and lava drip splashes look bloody
too, and dripstone lava drips plip instead of sizzling. The yellow boss bar is redrawn for the Blood
Knight.

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

## What's new in 1.4.0

- **A much harder Blood Knight.** 2000 health scaled to the number of players, heavier hits that
  make you bleed, three phases (bloodied at 60%, a last stand with Blood Thralls at 25%, berserk
  after 8 minutes) and four new attacks: **Leap**, **Blood Grasp**, **Blood Rain** and
  **Whirlwind**. Hiding, pillaring and arrow spam no longer work: see *No cheese* above.
- **No more walking through walls.** His body is now as wide as his model and solid: he walks
  round walls, his charge stops against them, and his attacks need a clear line to you.
- **Better animations.** Every move was reworked with real timing (slow wind-ups, snapping
  strikes, follow-throughs that overshoot and settle), a blood trail on the sword, a head that
  tracks you, weight shifts while he waits, heavy footsteps, and new clips for every new attack,
  the last stand, the taunt and the death.
- Players without the pack see the wither-skeleton stand-in move and fight in step with him, and
  their hits on it count.

## What's new in 1.3.3

- **Dripping that looks like dripping.** Drops used to be crying obsidian's tears, which the game
  lets drift down like snow (and players without the pack got slow falling dust). Now they're
  drops that fall at full weight, splash where they land and make a soft plip. A held weapon drips
  drop... drop... instead of streaming; a full armour set about once a second.
- **New and redrawn particles**: a glowing blood trail sprite for every stream of blood (kills,
  tethers, rifts, the grimoire), crimson wisps rising off a ready weapon and out of the dead, a
  beaded blood ring, splashes that run and dry. Kills rain blood around the body.
- Crying obsidian is purple again.

## What's new in 1.3.2

- **The pack reaches players without opening a port.** 1.3.1 relied on the plugin's own web
  server, so on hosts where port 8163 isn't reachable every download failed, and with it every
  custom visual. The pack now comes from its public copy on GitHub, which the plugin checks at
  startup is identical to its own, with the built-in server as the fallback. A failed download is
  retried from the other source at once. Admins are told once if the built-in server isn't
  reachable. Old configs on the untouched default are moved to the new mode automatically.
- **`/bb visuals on`** for players who installed the pack by hand, plus *[Retry]* and *[I have it
  installed]* buttons when a download fails.
- **The Blood Core wears its new art**: a glossy blood orb ringed with thorns, 64×64, its
  heartbeat blending smoothly between frames.
- **No more giant blood squares in your face.** No particle is ever sent to a player when it would
  spawn within a block of their own camera: your rift flow leaving your hand, or the burst when you
  step through. Everyone else still sees them. Your own weapon aura now drips low by your side.

## What's new in 1.3.1

- **The custom visuals actually show up.** They only went to players the plugin had seen load its
  pack, and it forgot them on a plugin reload (hot-swapped jar, `/reload`, PlugManX) and never saw
  packs that arrived another way. So players got the vanilla fallbacks: the boss as a wither
  skeleton in netherite, plain dust, no HUD icons. The plugin now remembers who has the pack across
  reloads, re-sends it when the plugin was updated in place, picks up a pack merged into the
  server's own, and has `effects.pack-visuals: always` for everything else.
- **Real blood particles.** Custom sprites for pack users on every blood effect: drops that burst
  into splashes and dry, glowing sparks, crimson wisps, falling drops that splash on the ground,
  and a rising blood ring on Blood Rage, set completion and the boss's roar. Everyone else still
  gets the dust versions.
- **Tooltip frame on by default** whenever the plugin sends the pack.
- **Armour reshaped**: open-faced helm, tapered waist, bare hands, low sabatons, curved shading,
  far fewer stripes and gems. Much less of a box.

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
./gradlew -p paper build   # -> paper/build/libs/Bloodbath-1.4.0.jar (+ the pack zip), runs the tests
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
