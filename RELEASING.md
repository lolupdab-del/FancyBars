# Releasing SBCLIENT

Every release, before bumping `mod_version` in `gradle.properties`:

1. Add a new entry at the TOP of `src/main/resources/assets/fancybars/changelog.json`
   (`version`, `date`, `added` list, `fixed` list) written for players, not developers.
2. The Home tab shows that entry as "What's new" and every older entry under "Past updates".
3. After every release, give the owner Discord patch notes for their releases channel: short, simple, no emojis,
   one message, in this shape:

   # SBCLIENT vX.Y.Z
   **Added** — ...
   **Fixed** — ...

## Values that are set instantly but also polled

If a feature changes something right away (a click, a chat message) and also reads it from a slower source (Spotify,
the tab list, the scoreboard, a menu), pass the slow readings through `dev.fancybars.util.Held`. Otherwise the display
flicks between the new value and the stale one until the slow source catches up.
