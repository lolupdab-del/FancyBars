# Fancy Bars

Skyblocker's **Fancy Status Bars** for Hypixel SkyBlock, pulled out into a standalone Fabric mod.

Health, Intelligence (mana + overflow), Defense, Vitality, Speed, Air and Experience/Rift-time bars that replace the vanilla
hearts/hunger/armor/XP HUD while you're on SkyBlock. Same drag-and-drop editor, colours, icons and text options as in Skyblocker,
plus the optional vanilla-style mana bar.

**Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API ≥ 0.157.0+26.2 · Java 25**

## Usage

| Command | What it does |
|---|---|
| `/fancybars` | Open the Fancy Bars menu: turn each bar and its icon on/off, resize bars, recolour bars, overflow mana, absorption and text (also via Mod Menu) |
| `/fancybars edit` | Open the drag-and-drop layout editor directly (also the **Edit GUI** button in the menu) |
| `/fancybars config` | Open the extra settings (also **More Settings...** in the menu) |

Config files live in `.minecraft/config/fancybars/` (`config.json` and `status_bars.json`).
Your Skyblocker layout can be reused: copy `config/skyblocker/status_bars.json` to `config/fancybars/status_bars.json`, as the format is identical.

If you also run Skyblocker, turn off its *Fancy Bars* option so the two don't draw on top of each other.

## Building

You need JDK 25.

```
./gradlew build
```

The mod jar ends up in `build/libs/fancybars-1.1.0+26.2.jar`. Put it in your `mods` folder next to Fabric API.

No JDK? Push this folder to a GitHub repo: the included workflow builds it, and the jar appears under **Actions → latest run → Artifacts**.

## Differences from Skyblocker

* SkyBlock / Rift detection uses the Hypixel Mod API through the bundled [HM API](https://github.com/AzureAaron/hm-api) library, the same way Skyblocker does.
* Settings are a small vanilla-style screen instead of YACL, so there are no extra dependencies.
* The speed bar's cap ignores your pet (Skyblocker raises it for Black Cat and lowers it for Snail using its pet cache, which isn't ported). Racing Helmet, Young Dragon armour and the Cactus Knife are still handled.
* The legacy (pre-`status_bars.json`) Skyblocker bar positions aren't migrated.

## License

The code is from [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) by the Skyblocker team and is distributed under the
**GNU LGPL v3.0 or later**, the same as the original (see `LICENSE`). HM API is Apache-2.0.
