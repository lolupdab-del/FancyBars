# SBCLIENT

Skyblocker's **Fancy Status Bars** for Hypixel SkyBlock, pulled out into a standalone Fabric mod.

Health, Intelligence (mana + overflow), Defense, Vitality, Speed, Air and Experience/Rift-time bars that replace the vanilla
hearts/hunger/armor/XP HUD while you're on SkyBlock. Same drag-and-drop editor, colours, icons and text options as in Skyblocker,
plus the optional vanilla-style mana bar.

**Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API ≥ 0.157.0+26.2 · Java 25**

## Usage

| Command | What it does |
|---|---|
| `/sbclient` | Open the Fancy Bars menu (also via Mod Menu): Home, Bars (switch each bar and its icon on/off, recolour bars, overflow mana, absorption and text), Style (Classic / Segmented / SkyblockAddons), General and Vanilla Mana pages, with search |
| `/sbclient edit` | Open the editor (also the **Edit GUI** button): drag bars anywhere, scroll over a bar to scale it, drag its left/right edge to make it longer, right-click for more options. Bars snap to each other, the hotbar and the screen centre; hold Shift to place freely |
| `/sbclient config` | Open the menu on the General page |
| `/sbclient protect` | Protect / unprotect the held item (or press **V**, also works while hovering items in inventories) |

**Item protection** (from Skyblocker): protected items can't be dropped, sold to NPCs, salvaged, traded or auctioned. The star shown on them can be recoloured and put in any corner (menu → Items).

**Inventory GIFs** (from Inventory GIFs by lolupdab): animated GIFs from `config/invgifs` in your inventory, with their own page in the menu (previews, per-GIF switches, folder/reload buttons, and **Edit GIF Layout** to move/resize them in your inventory). Remove the separate invgifs mod if you have it installed.

**Dungeon secrets display** (from SkyblockAddons): chest icon + "Secrets" + found/total for the current room, colour coded. The chest icon and the word can each be turned off; move/scale it in the editor.

**Rare drop tracker** (Dungeons page): counts rare dungeon chest drops (Necron's Handle, master stars, scrolls, Dark Claymore, Giant's Sword, Recombobulators, …, plus anything Hypixel marks "RARE REWARD!"), your runs and runs since your last rare drop. Movable HUD (shown in the Dungeon Hub), a chat message on each drop, recent drop history, and each item can be untracked. Data is in `config/fancybars/rare-drops.json`.

**Backpack preview** (from Skyblocker): hover a backpack or ender chest page in the Storage menu to see its contents.

**Action bar**: its own menu page. Hide it, move/scale it in the editor, hide individual parts (health, defense, mana, mana use, skill XP gains, secrets, vitality, rift time), and with *Custom Look* draw it with a smooth font, shadow, rounded background (adjustable opacity), one custom colour and fade-out.

**Keystrokes**: three layouts (Mouse; WASD + space; Full with Tab, Caps, Shift, Q E R Z X C V F). **Inventory HUD**: your inventory as a small clean grid. Both on the HUD page, movable in the editor.

**Held item name**: the name that pops up when you switch items can be hidden, moved and scaled (menu → Items, or drag it in the editor).

While fancy bars are active the vanilla XP bar is always hidden, even if you turn the fancy XP bar off.

Config files live in `.minecraft/config/fancybars/` (`config.json` and `status_bars.json`).
Your Skyblocker layout can be reused: copy `config/skyblocker/status_bars.json` to `config/fancybars/status_bars.json`, as the format is identical.

If you also run Skyblocker, turn off its *Fancy Bars* option so the two don't draw on top of each other.

## Building

You need JDK 25.

```
./gradlew build
```

The mod jar ends up in `build/libs/fancybars-1.7.1+26.2.jar`. Put it in your `mods` folder next to Fabric API.

No JDK? Push this folder to a GitHub repo: the included workflow builds it, and the jar appears under **Actions → latest run → Artifacts**.

## Differences from Skyblocker

* SkyBlock / Rift detection uses the Hypixel Mod API through the bundled [HM API](https://github.com/AzureAaron/hm-api) library, the same way Skyblocker does.
* Settings are a small vanilla-style screen instead of YACL, so there are no extra dependencies.
* The speed bar's cap ignores your pet (Skyblocker raises it for Black Cat and lowers it for Snail using its pet cache, which isn't ported). Racing Helmet, Young Dragon armour and the Cactus Knife are still handled.
* The legacy (pre-`status_bars.json`) Skyblocker bar positions aren't migrated.

## Credits

- Cape texture list from [Cape Command](https://github.com/eclipseisoffline/capecommand) by eclipseisoffline; textures are downloaded from Mojang / the Minecraft Wiki at runtime.

* Original fancy status bars: [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) (LGPL-3.0).
* "SkyblockAddons" bar style texture: [SkyblockAddons](https://github.com/Fix3dll/SkyblockAddons) by Fix3dll and contributors (LGPL-3.0).
* "Segmented" bar style textures: the **Bigmans masterpiece** resource pack.
* Inventory GIFs: ported from Inventory GIFs by lolupdab (MIT).
* UI font: [Inter](https://rsms.me/inter/) by Rasmus Andersson, SIL Open Font License 1.1 (`assets/fancybars/font/LICENSE_Inter_OFL.txt`).

## License

The code is from [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) by the Skyblocker team and is distributed under the
**GNU LGPL v3.0 or later**, the same as the original (see `LICENSE`). HM API is Apache-2.0.
