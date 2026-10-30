# Fancy Bars

Skyblocker's **Fancy Status Bars** for Hypixel SkyBlock, pulled out into a standalone Fabric mod.

Health, Intelligence (mana + overflow), Defense, Vitality, Speed, Air and Experience/Rift-time bars that replace the vanilla
hearts/hunger/armor/XP HUD while you're on SkyBlock. Same drag-and-drop editor, colours, icons and text options as in Skyblocker,
plus the optional vanilla-style mana bar.

**Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API ≥ 0.157.0+26.2 · Java 25**

## Usage

| Command | What it does |
|---|---|
| `/fancybars` | Open the Fancy Bars menu (also via Mod Menu): Home, Bars (switch each bar and its icon on/off, recolour bars, overflow mana, absorption and text), Style (Classic / Segmented / SkyblockAddons), General and Vanilla Mana pages, with search |
| `/fancybars edit` | Open the editor (also the **Edit GUI** button): drag bars anywhere, scroll over a bar to scale it, drag its left/right edge to make it longer, right-click for more options. Bars snap to each other, the hotbar and the screen centre; hold Shift to place freely |
| `/fancybars config` | Open the menu on the General page |
| `/fancybars protect` | Protect / unprotect the held item (or press **V**, also works while hovering items in inventories) |

**Item protection** (from Skyblocker): protected items can't be dropped, sold to NPCs, salvaged, traded or auctioned. The star shown on them can be recoloured and put in any corner (menu → Items).

**Inventory GIFs** (from Inventory GIFs by lolupdab): animated GIFs from `config/invgifs` in your inventory, with their own page in the menu (previews, per-GIF switches, folder/reload buttons). Remove the separate invgifs mod if you have it installed.

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

The mod jar ends up in `build/libs/fancybars-1.6.0+26.2.jar`. Put it in your `mods` folder next to Fabric API.

No JDK? Push this folder to a GitHub repo: the included workflow builds it, and the jar appears under **Actions → latest run → Artifacts**.

## Differences from Skyblocker

* SkyBlock / Rift detection uses the Hypixel Mod API through the bundled [HM API](https://github.com/AzureAaron/hm-api) library, the same way Skyblocker does.
* Settings are a small vanilla-style screen instead of YACL, so there are no extra dependencies.
* The speed bar's cap ignores your pet (Skyblocker raises it for Black Cat and lowers it for Snail using its pet cache, which isn't ported). Racing Helmet, Young Dragon armour and the Cactus Knife are still handled.
* The legacy (pre-`status_bars.json`) Skyblocker bar positions aren't migrated.

## Credits

* Original fancy status bars: [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) (LGPL-3.0).
* "SkyblockAddons" bar style texture: [SkyblockAddons](https://github.com/Fix3dll/SkyblockAddons) by Fix3dll and contributors (LGPL-3.0).
* "Segmented" bar style textures: the **Bigmans masterpiece** resource pack.
* Inventory GIFs: ported from Inventory GIFs by lolupdab (MIT).
* UI font: [Inter](https://rsms.me/inter/) by Rasmus Andersson, SIL Open Font License 1.1 (`assets/fancybars/font/LICENSE_Inter_OFL.txt`).

## License

The code is from [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) by the Skyblocker team and is distributed under the
**GNU LGPL v3.0 or later**, the same as the original (see `LICENSE`). HM API is Apache-2.0.
