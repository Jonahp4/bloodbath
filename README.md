# Bloodbath

Twelve blood-soaked ability weapons with real 3D models, the Blood Knight's armour set, the
Blood Knight himself as an animated boss fight, and **the Bloodlands**: a red dimension behind a
portal you build yourself, with blood lakes, rare Blood Drops and the **Blood Anvil** that bleeds
your weapons into stronger ones. For **Paper 1.21.11**. Comes with commands, an armory menu, kill ranks, a config for every number, Blood Core crafting recipes, and
a resource pack that the plugin hosts for you.

**Downloads**

| File | What it is |
|---|---|
| [`dist/paper/Bloodbath-1.6.0.jar`](dist/paper/Bloodbath-1.6.0.jar) | The plugin. This is the one you want. |
| [`dist/Bloodbath-ResourcePack-1.6.0.zip`](dist/Bloodbath-ResourcePack-1.6.0.zip) | The resource pack, if you'd rather host it yourself (the plugin already contains it). |
| [`dist/fabric/unchartedsmp-bloodbath-0.3.1.jar`](dist/fabric/unchartedsmp-bloodbath-0.3.1.jar) | The old Fabric mod (10 weapons, no commands). Kept for reference. |

## Install

1. Paper **1.21.11**, Java 21. Paper forks such as Purpur work too. Folia isn't supported.
   (1.4.0 and older ran on 1.21.4 and newer; from 1.5.0 the plugin is built for 1.21.11 only,
   because the Bloodlands biome uses 1.21.11's data format.)
2. Drop `Bloodbath-1.6.0.jar` into `plugins/` and restart.
3. **Restart once more** to open the Bloodlands: on the first start the plugin installs the
   Bloodlands biome as a datapack in your main world, and the server only reads datapacks at
   startup. `/bb bloodlands` tells you where it's at.
4. That's it for the 3D models: players get a download prompt when they join. The pack comes from
   the plugin's public copy on GitHub (checked to be identical to the one inside the jar), so
   there's no port to open. No other plugins are needed. See [Resource pack](#resource-pack).
5. In game: `/bb armory` to browse everything, or `/bb give <you> all`.

Players need a 1.21.11 client to see the 3D models. Everyone else, and anyone who declines the
pack, sees named netherite swords and a bow. Every weapon still works for them.

## The weapons

| Weapon (`id`) | Right-click | Also |
|---|---|---|
| **Bloodrift Blade** (`riftblade`) | Tear a bleeding rift up to 14 blocks ahead that drags enemies in. Use again within 6s to step through: the rift snaps shut on everything around you (4 damage, Slowness II 1.5s). 14s. | 9 damage sword, speed 1.6 |
| **Bloodhook** (`bloodhook`) | Chain the enemy you're looking at: it bites (2 damage), holds them (Slowness 1s) and reels you in. Hooking the same target again within 12s extends the range from 12 up to 28 blocks. A miss only costs half the 7s cooldown. | Can't hook through walls |
| **Clotblade** (`nullblade`) | Throw down a clot field (4 blocks, 8s) that suppresses everyone's abilities inside. | Hits clot a player's blood: no abilities for 2.5s, and a player can only be clotted by hits once every 8s. 7 damage sword. Its own field never stops it. |
| **Blood Meteor Gauntlet** (`meteor_gauntlet`) | Or punch a block: a blood meteor lands there 1.6s later, 8 damage and a big launch in 4.5 blocks, and leaves a crater that bleeds whoever stands in it for 3s. 15s. | Never hits its wielder |
| **Crimson Gravestone** (`gravestone`) | A blood pool drags everything within 6 blocks in for 2s, then erupts under them: 6 damage, a bleeding wound, and a throw straight up so they land next to you. 18s. | |
| **Bleeding Chronos** (`chronos`) | Leave a blood-mark. Use again within 8s to snap back to it, facing and all, winning back 30% of the health you lost since (up to 3 hearts). The blood you leave behind bursts on whoever was chasing you (3 damage, a slow). | Cooldown starts on recall; a mark that dries up unused costs 6s |
| **Crimson Thunder Pike** (`thunder_pike`) | Hurl charged blood up to 20 blocks, call crimson lightning there (7 damage, stuns for 1s) and ride it. 13s. | The lightning is cosmetic: no fires, no charged creepers |
| **Blood Mirrorfang** (`mirrorfang`) | A blood mirror of you hunts the nearest enemy within 10 blocks for 6s, gliding after it and slashing every 0.7s (3 damage). | You can outrun it. Dupe-proof, see below |
| **Hemorrhage Scythe** (`void_scythe`) | **Harvest** (5s): a blood arc that cuts everything in front of you and makes it bleed. | Every hit adds a bleed stack that hurts every 1.5s (ignores armour); the 5th hemorrhages for 9 damage and 6 to everything around. 12 damage, attack speed 1.0. |
| **Sanguine Paradox Bow** (`paradox_bow`) | A real bow. | **Paradox Echo:** a fully drawn shot fires twice. A blood rift opens where you shot from; 1.5s later a phantom arrow tears out of it and homes into whatever your arrow hit, wherever it ran (6 damage, pierces on the way). What it marked glows until then. If the arrow hit nothing living, the echo retraces its real flight path instead. |
| **Vampire Fang** (`vampire_fang`) | **Blood Dash** (9s): lunge along your aim, cutting (5 damage) and drinking from everything you pass through. | Every hit heals you for 25% of the damage it actually dealt. Fast 6 damage dagger. |
| **Blood Grimoire** (`blood_grimoire`) | **Transfusion** (16s): a blood tether drains 5 health from a creature over 1.5s into you, slowing it. Armour doesn't stop it. | Sneak + right-click a player to give them 8 health (and Regeneration) for 4 of yours. Works with PvP off. |

**In PvP**, 35% of every ability hit on a player goes straight through armour (as magic damage;
Protection still reduces it). Without that, abilities did almost nothing against full netherite.
Tune it with `gameplay.ability-armor-pierce`, and scale every ability hit on players with
`gameplay.pvp-ability-damage`.

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
blocks), at full detail within `full-detail-distance` (24) and thinner further out: shapes (rings,
beams, crescents, domes) send every 2nd point to players up to twice that and every 4th beyond.
Effects nobody can see aren't sent at all, and nothing is ever drawn right in front of your own
camera. `effects.particle-multiplier` scales everything.

## Blood Knight armour

The boss's armour, worn by players: **Blood Knight Helm, Cuirass, Greaves and Sabatons**
(`/bb give <you> armor`). Every piece beats its netherite counterpart:

| | Armor | Toughness | Knockback resistance | Extra |
|---|---|---|---|---|
| Netherite (helm / chest / legs / boots) | 3 / 8 / 6 / 3 | 3 | 0.1 | |
| **Blood Knight** | **4 / 9 / 7 / 4** | **3.5** | **0.15** | **+½ heart each**, fireproof |

| Set bonus | |
|---|---|
| **2 pieces, Bloodlust** | Every kill heals you 1.5 hearts. |
| **3 pieces, Barbed Blood** | Whatever hits you in melee takes ¾ of a heart back. |
| **4 pieces, Blood Knight** | While the whole set is on: **Speed and Fire Resistance**. They switch on the moment the fourth piece goes on (with a flash and a sound) and come off the moment any piece comes off. Only these effects are touched: a potion you drank stays yours. Editable in `armor.full-set-effects`. |
| **4 pieces, Blood Rage** | When a hit leaves you at 40% health or less: Strength II and Resistance for 8s and a blood shockwave that hurls everything within 4 blocks away. Your screen edges run red and your heart pounds until it fades. 60s cooldown, shown on the action bar. A clot (Clotblade) stops it. |

**The look** is the boss's own: black iron plate lacquered in blood red, a Blood Core set into the
breastplate, a glow in each knee. **The helm is a real 3D model** on your head: a closed great helm
with a slit of burning eyes (they glow in the dark), a fanged grin and a swept fin crest. The rest
is painted onto the body the way vanilla armour is (the game has no way to wear other 3D models).
The Sabatons leave bloody footprints and a full set drips blood (you see it too).

**Edit it in Blockbench:** `tools/blockbench/blood_knight_helm.bbmodel` is the helm (a Java
block/item model; export it over `resourcepack/assets/unchartedsmp/models/item/blood_knight_helm.json`
and its texture over `textures/item/blood_knight_helm_model.png`), and
`tools/blockbench/blood_knight_armor.bbmodel` is the player's body with both worn layers on it
(paint in 3D, then save the two textures over
`textures/entity/equipment/humanoid/blood_knight.png` and `humanoid_leggings/blood_knight.png`).
Both projects are generated from `tools/models/armor.py`, which is also where the art is drawn.

**Without the pack**, the armour is invisible when worn and the helm is a missing-texture cube.
Keep that in mind if the pack is optional on your server, set `items.custom-ids: false` (the
pieces then show as netherite in the inventory), or turn the set off with `armor.enabled: false`.

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

## The Bloodlands

The Bloodlands are a dimension of their own (`world-name: Bloodlands`) under a dusk sky that never moves.

The land:
- rolling red plains break into hills, valleys and small stepped cliffs of tuff and andesite;
- now and then a spire of blackstone stands on its own;
- the grass, ferns and roots come in several shades of red;
- among them grow poppies, roses, wither roses, crimson fungus, dead bushes and dead or red-leaved dark oaks, and now and then you'll find a set of bleached ribs.

**Blood lakes** sit in real basins, never as a raised puddle and never spilling down a hill:
- mud and clay shores, irregular outlines;
- sizes run from pools a few blocks wide up to rare lakes over a hundred blocks across.

All water there is blood:
- standing in it makes you **bleed** and takes a little health every second (both configurable);
- it drips and bubbles, and you hear it when you wade in;
- step out and the bleeding it gave you stops.

Monsters spawn as usual; it's always dusk. About one in twelve is **Bloodbound**: red armour, more health, and a good chance of a Blood Drop. Ruined shrines hide a chest with a Blood Drop or two.

The sky, the fog and the red grass come from a biome, `bloodbath:bloodlands`. The plugin installs it as a datapack in the main world (`datapacks/bloodbath_bloodlands`), which takes one restart. Set `bloodlands.biome-datapack: false` to skip it: the world then opens at once, but with vanilla green grass and a blue sky.

### Portals

Craft **Bloodstone Frames** (8 at a time from polished blackstone bricks, crying obsidian and a Blood Core). Stand them in an upright ring like a nether portal:
- any size from **2×3 up to 20×20** inside (both limits configurable);
- corners are optional;
- light it with flint and steel or a fire charge.

If the frame isn't right, the plugin tells you why and where: the block that breaks the ring, a portal that's too small or too big, or an inside that isn't a rectangle.

Once lit, the inside fills with moving blood that glows and hums.
- **Going in:** stand in it for a second. You arrive at the Bloodlands spawn, a few blocks apart from anyone who came with you.
- **Coming back:** a return portal waits there. It takes you back to the portal you came through, or to your bed or the world spawn if that portal is gone.

A lit frame can't be broken by:
- mining, even in creative;
- explosions, pistons or fire;
- endermen, withers, water or lava;
- trees growing into it;
- a bucket or block placed in the portal.

To take a frame down:
- **Unlit:** sneak and mine a frame block to take it back. Turn this off with `portal.reclaim-inactive-frames`.
- **Lit:** only an admin can remove it, with `/bb portal remove` or by sneaking and mining it in creative.

The plugin recognises frames from its own records, not by name or block type, so ordinary reinforced deepslate is never mistaken for one.

## Blood Drops and the Blood Anvil

**Blood Drops** are rare on purpose. They come from:
- 2% of monsters killed in the Bloodlands, and 35% of Bloodbound;
- shrine chests;
- the Blood Knight, 1 to 3 per kill.

Every chance is in the config. Picking one up gives you a heartbeat and a count of how many you hold. Drops never despawn, and they can't be used in vanilla crafting.

The **Blood Anvil** is a black iron anvil with glowing cracks and a groove of blood. Craft it from two netherite ingots, a Blood Core, an anvil and three polished blackstone. Right-click it to open its menu:
- a weapon slot, a result slot and a Blood Drop slot;
- the **BLEED WEAPON** button;
- a meter of drops held against drops needed;
- five pips for the weapon's level.

The button shows what the anvil can do right now:
- empty;
- can't be bled;
- *NEED MORE BLOOD*;
- *READY*, pulsing;
- *BLEEDING...* while it works;
- *MAXIMUM LEVEL*.

The result shows exactly what the next level adds before you pay for it.

Bleeding a weapon spends the Blood Drops and raises its **Blood Level**. There are 5 levels by default, costing 1, 2, 3, 5 and 8 drops.

The weapon keeps its kills, enchantments, name, model and ability. Its lore gains a *Blood Infusion* section.

Each level adds:
- melee damage (arrow damage for the Paradox Bow);
- a share of the weapon's ability damage;
- a chance that a hit makes the target bleed.

The level is stored on the item itself and survives reloads and restarts. If you change the numbers in the config, levelled weapons follow them. Only Bloodbath weapons can be bled.

The menu is dupe-proof. Everything you see in it is a picture. Your real items are held by the anvil and saved to disk the moment they go in. You get them back when you:
- close the menu;
- log out;
- die;
- or when the server stops, or crashes (they come back on your next join).

Shift-clicks, number keys, double-clicks, dragging and spam-clicking the button are all handled. If your inventory is full, the overflow drops at your feet, owned by you.

**Bleeding** is one shared system: blood water, bled weapons and anything else that makes you bleed all stack on one timer. Damage, interval, duration, stack limit and stacking rule are all under `bleeding:`. The Blood Scythe and the Blood Knight keep their own bleeds as before.

**Worth knowing:**
- the frames are reinforced deepslate underneath, so with the pack, reinforced deepslate in
  ancient cities looks like Bloodstone too (without the portal glow);
- the anvil menu's tooltips use the game's own tooltip box with a blood frame, and the game has no
  way for a server to play a sound when you merely hover a slot, so there's none;
- without the pack everything still works: the frame is plain reinforced deepslate, the anvil is a
  normal-looking anvil, the button, meter and pips are glass, blocks and dyes.

## Commands

`/bloodbath`, `/bb` or `/blood`:

| Command | Who | |
|---|---|---|
| `/bb help` | everyone | |
| `/bb armory` | everyone | Browse every weapon and armour piece. Admins click to take one. |
| `/bb list` | everyone | Hover a name to see the item. |
| `/bb info <weapon\|piece>` | everyone | Accepts ids or names ("scythe", "Clotblade", "helm"). No name: the weapon in your hand with its Blood Level, or the Bloodlands if you hold nothing. |
| `/bb hud [on\|off]` | everyone | Hide or show the action-bar line for yourself. Remembered. |
| `/bb pack` | everyone | Get the resource pack again. |
| `/bb visuals [on\|off\|auto]` | everyone | The custom particles, boss model and HUD icons for you. `on` if you installed the pack yourself and the download doesn't reach you; `auto` follows your pack. Remembered. |
| `/bb give <player\|all> <weapon\|piece\|armor\|core [n]\|all>` | op | `/bb give <weapon>` gives it to yourself. `armor` is the whole set; `core 16` is 16 Blood Cores; `all` is every weapon plus the set. |
| `/bb give <player> <n>` | op | `n` Blood Drops (also `drop [n]`). `anvil` gives a Blood Anvil, `frame [n]` Bloodstone Frames. |
| `/bb bloodlands [tp]` | everyone / op | Whether the Bloodlands are open (and if not, why). `tp` takes an admin there. |
| `/bb portal build <w> <h>` | op | Build and light a portal of that inside size where you stand. |
| `/bb portal remove\|list\|frames [n]` | op | Remove the portal you're looking at, list every portal, or get frames. |
| `/bb setspawn` | op | Make where you stand the Bloodlands arrival point. |
| `/bb level <player> <level>` | op | Set the Blood Level of the weapon in that player's hand (0 clears it). |
| `/bb reset [player\|all]` | op | Clear cooldowns and clots. |
| `/bb pack <player\|all>` | op | Send the pack to someone else. |
| `/bb status` | op | Pack server, downloads, recipes, live effects, disabled weapons. |
| `/bb boss summon\|stop\|status` | op | Summon the Blood Knight where you're looking, end every fight, or see what's running. |
| `/bb debug` | op | Toggle a chat log of every ability hit you deal: damage, health before and after, or which plugin cancelled it. |
| `/bb reload` | op | Reload `config.yml`. Weapons update as players hold them. |

Permissions: `bloodbath.use` (use abilities), `bloodbath.command`, `bloodbath.armory`,
`bloodbath.craft` and `bloodbath.anvil` (use Blood Anvils) are on for everyone; `bloodbath.give` and `bloodbath.admin` are op-only.

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
- `items.custom-ids`: weapons and armour use their own item model ids (default) or ride netherite's.
- `gameplay.ability-armor-pierce`, `gameplay.pvp-ability-damage`: how hard abilities hit players.
- `bloodlands`: on or off, world name, seed, the datapack, frozen time, lake frequency, Bloodbound.
- `portal`: minimum and maximum size, warm-up, cooldown, frame reclaiming, arrival spread.
- `blood-water`, `bleeding`: damage, interval, duration, stacks and the stacking rule.
- `blood-drop`: every drop chance, shrine frequency, the boss's drops, pickup effects.
- `blood-anvil`, `blood-levels`: add or remove levels and set each one's cost, damage, ability
  power and bleed chance.

## Resource pack

The pack is inside the plugin, and players are sent it when they join. By default
(`mode: auto`) they download it from its public copies (GitHub, then jsDelivr). Each copy is a file
named after the pack's own hash (`dist/pack/<sha1>.zip`), so a copy the plugin has checked can
never change afterwards. The plugin downloads every copy at startup and only uses the ones that
are byte-for-byte the pack inside the jar, so players can never get a different or outdated one.
If a player's download fails, they're sent the next copy straight away, and then the pack from the
plugin's own tiny web server on port 8163 (which needs that port open to the internet). A copy that
fails a player is checked again, and dropped if it no longer matches.

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
2. **"Download failed; trying again from..."** is the plugin moving a player on to the next copy.
   **"The Bloodbath resource pack couldn't be downloaded"** means none worked for them: GitHub and
   jsDelivr are blocked where they are *and* the server's port 8163 isn't open. Open the port (on a
   hosting panel: add a port and set `resource-pack.port` to it), host the exported zip yourself
   (`mode: url`), or have them install `Bloodbath-ResourcePack-<version>.zip` by hand.
3. **Installed the pack by hand?** The server can't see that. Run `/bb visuals on` (or click
   *[I have it installed]* in the failure message) to get the custom visuals anyway. To give them
   to everyone because the pack is forced some other way, set `effects.pack-visuals: always`.
4. **Updated the plugin with `/reload` or a plugin manager?** Players online get the new pack
   re-sent automatically. A full restart is still the cleanest.

If a download fails, the player is told, and the console says why (usually the port).

Weapons and armour have their own item model ids (`unchartedsmp:riftblade`, ...), so another pack
that retextures netherite can't hide them. The server can't create new item *types* for a vanilla
game, so F3+H still shows the netherite base item underneath. With `items.custom-ids: false` the
pack instead switches the vanilla netherite sword, bow and armour models to ours for Bloodbath
items only (matched by `custom_model_data`); every other item, armour trims included, is untouched.

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

## What's new in 1.6.0

**Resource pack**
- **Downloads that don't break.** Each public copy of the pack is now a file named after its hash,
  so it can't change under a server that already checked it. Before, rebuilding a version could
  swap the file and make downloads fail with *Download failed; trying again from this server*.
- A player whose network blocks one copy is sent the next (jsDelivr) before the built-in server.
- A copy that fails a player is re-checked, and dropped if its file changed.

**Items and armour**
- **Own item ids.** Weapons and armour use their own item model ids instead of netherite's.
- **A new Blood Knight set**, redrawn to match the boss.
- **A real 3D helm**, with burning eyes and a fanged grin.
- **Blockbench projects** for the helm and both worn layers.

**PvP rebalance**
- **35% of ability damage to players goes through armour**, with a PvP multiplier to tune it.
- **Nerfs:**
  - the Bloodrift Blade (it out-damaged netherite by 40%);
  - the Clotblade's lockout (no more permanent clot);
  - Harvest's cooldown;
  - Mirrorfang's damage;
  - the Paradox echo;
  - the full set's permanent Strength and extra hearts.
- **Buffs and reworks:**
  - the rift snaps shut on exit;
  - the hook bites and slows;
  - the meteor's crater bleeds;
  - the Gravestone erupts for damage;
  - Chronos heals and punishes chasers;
  - the Pike stuns;
  - the mirror hunts;
  - marked prey glows;
  - a harder Blood Dash;
  - Transfusion drains through armour and slows.
- Servers that kept the old defaults get the new ones. Values an owner tuned stay theirs.

**Particles**
- **Far more particles**: crescents, helix beams, domes, vortexes, a clock face, shockwaves, stack motes, hit sprays and bleed spurts.
- **Cheaper to send:** distant players get a fraction of each shape.

## What's new in 1.5.0

- **The Bloodlands**: a new dimension of red hills, valleys, cliffs and blood lakes that make you
  bleed, with Bloodbound monsters and ruined shrines. See [The Bloodlands](#the-bloodlands).
- **Portals of any size** (2×3 to 20×20) from Bloodstone Frames that nothing can break once lit.
- **Blood Drops** and the **Blood Anvil**: bleed any Bloodbath weapon up to Blood Level V for more
  damage, stronger abilities and a chance to make targets bleed.
- One **bleeding** system with its own config.
- Paper **1.21.11** only from this version.

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
./gradlew -p paper build   # -> paper/build/libs/Bloodbath-1.6.0.jar (+ the pack zip), runs the tests
```

The tests load the plugin into [MockBukkit](https://github.com/MockBukkit/MockBukkit) (a mock
Paper 1.21.11 server) and play it: every weapon's ability and passive, kill tracking, the armory,
the mirror's dupe guards, the armour set bonuses, the bow's echo, that nothing is drawn in a
player's own face, commands, config reloads, recipes and the pack server; and the Bloodlands:
the terrain and its lakes (every lake holds its water), portals of every size lit, refused,
entered and left, frames that don't break, and every way of clicking, dragging, quitting, dying
or shutting down with a Blood Anvil open. The test world
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
