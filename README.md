# Eternal Confluence Tweaks

Designed for the Eternal Confluence modpack, this mod provides configurable fixes for Minecraft 1.12.2 and makes mobs from selected mods respect Corail Tombstone's Ghostly Shape effect. It can also be used in any other Minecraft 1.12.2 Forge installation with the relevant target mods.

## General fixes

- **Blood Arsenal** (`bloodArsenalSlateItem`): Fixes compressed slate variants having incorrect names, variant handling, and crafting recipe behavior.
- **Roots** (`rootsAirStateMatcher`): Fixes Wildwood disappearing instead of converting correctly to Runed Wildwood.
- **Nutrition UEL + Spice of Life** (`nutritionSpiceOfLifeLastItem`): Restores Nutrition awards when food is consumed from a Spice of Life lunchbox or lunchbag, including the last item in a stack.
- **Player saturation** (`clampNegativeSaturation`): Resets negative player saturation to zero at the end of each player tick on the server and client. Preserves positive saturation, including values above 20, and works without Scaling Feast or AppleCore.
- **Scaling Feast** (`scalingFeastSafeSaturationHud`): Prevents the saturation HUD from crashing on negative saturation by skipping the affected bar until saturation is nonnegative. Keeps the saturation display enabled and preserves normal colour rotation for values above 20. This safeguard works independently of the player saturation correction.
- **Lycanites Mobs** (`lycanitesMeleeLineOfSight`): Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles.
- **MoreChids** (`morechidsClassGeneration`): Fixes a startup crash when MoreChids generates custom Orechid classes.
- **Waystones** (`waystonesVillageWeight`, `villageWaystoneWeight`): Makes village Waystones more likely to generate by increasing their piece weight to `100` by default. The weight is configurable; Waystones' original village chance and maximum of one Waystone per village remain in effect.
- **Witchery Villages + ToroQuest** (`witcheryVillagesToroGuards`): Replaces Witchery Villages guard spawns with ToroQuest guards affiliated with the local active Province, so ToroQuest crime and reputation behavior applies. Suppresses guard spawns in Provinces without a Lord and leaves guards unchanged where no Province exists. This only has an effect when both Witchery Villages and ToroQuest are installed.
- **Witchery Villages + ToroQuest** (`witcheryVillagesToroWalls`): Extends Witchery Villages wall footprints around ToroQuest village structures, leaving space between the wall and each building. This only has an effect when both mods are installed.

## Corail Tombstone: Ghostly Shape compatibility

Makes mobs from enabled mods ignore players affected by Ghostly Shape, including players they already targeted. Each integration has its own setting:

- **Lycanites Mobs** (`lycanitesGhostlyShape`)
- **Electroblob's Wizardry** (`ebWizardryGhostlyShape`)
- **Ancient Spellcraft** (`ancientSpellcraftGhostlyShape`)
- **ToroQuest** (`toroQuestGhostlyShape`)

Corail Tombstone and the mod being patched must be installed for the corresponding compatibility fix to have an effect. If the Ghostly Shape potion is unavailable, the guarded checks do nothing.

## Configuration

On first launch, the mod creates `config/eternalconfluencetweaks.cfg`. Each fix can be set to `true` or `false` independently. `villageWaystoneWeight` can be set from `1` to `1000000`. Changes take effect after restarting Minecraft.

## Requirements

- Minecraft 1.12.2
- Forge for Minecraft 1.12.2

The target mods are optional; the mod does not require every listed mod to be installed.

## Installation

Place the JAR in the instance's `mods` folder.

## License

MIT; see [LICENSE](LICENSE).

## AI use disclosure

AI tools assisted with analyzing target-mod behavior and writing portions of this mod's source code and documentation. The project owner directed the work and tested the release in-game.
