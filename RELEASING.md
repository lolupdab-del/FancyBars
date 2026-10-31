# Releasing SBCLIENT

Every release, before bumping `mod_version` in `gradle.properties`:

1. Add a new entry at the TOP of `src/main/resources/assets/fancybars/changelog.json`
   (`version`, `date`, `added` list, `fixed` list) written for players, not developers.
2. The Home tab shows that entry as "What's new" and every older entry under "Past updates".
