# Eternal Confluence Tweaks

Designed for the Eternal Confluence modpack, this mod provides configurable fixes for Minecraft 1.12.2 and makes mobs from selected mods respect Corail Tombstone's Ghostly Shape effect. It can also be used in any other Minecraft 1.12.2 Forge installation with the relevant target mods.

## General fixes

- **Blood Arsenal** (`bloodArsenalSlateItem`): Fixes compressed slate variants having incorrect names, variant handling, and crafting recipe behavior.
- **Roots** (`rootsAirStateMatcher`): Fixes Wildwood disappearing instead of converting correctly to Runed Wildwood.
- **Nutrition UEL + Spice of Life** (`nutritionSpiceOfLifeLastItem`): Restores Nutrition awards when food is consumed from a Spice of Life lunchbox or lunchbag, including the last item in a stack.
- **Vanilla Food Pantry + AppleCore** (`vanillaFoodPantryServings`): Prevents VFP multi-use foods from removing hunger and saturation above 20 hunger. Calculates servings using your increased maximum hunger, such as that provided by Scaling Feast in EC.
- **Vanilla Food Pantry + AppleCore + Spice of Life: Carrot Edition** (`vanillaFoodPantryFoodValues`): Prevents eating VFP food from incorrectly changing Carrot's "tasted foods" count and bonus hearts. Keeps previously eaten foods counted using their own hunger values.
- **Player saturation** (`clampNegativeSaturation`): Resets negative player saturation to zero at the end of each player tick on the server and client. Preserves positive saturation, including values above 20, and works without Scaling Feast or AppleCore.
- **Scaling Feast** (`scalingFeastSafeSaturationHud`): Prevents the saturation HUD from crashing on negative saturation by skipping the affected bar until saturation is nonnegative. Keeps the saturation display enabled and preserves normal colour rotation for values above 20. This safeguard works independently of the player saturation correction.
- **Lycanites Mobs** (`lycanitesMeleeLineOfSight`): Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles.
- **MoreChids** (`morechidsClassGeneration`): Fixes a startup crash when MoreChids generates custom Orechid classes.
- **Waystones** (`waystonesVillageWeight`, `villageWaystoneWeight`): Makes village Waystones more likely to generate by increasing their piece weight to `100` by default. The weight is configurable; Waystones' original village chance and maximum of one Waystone per village remain in effect.
- **Witchery Villages + ToroQuest** (`witcheryVillagesToroGuards`): Replaces Witchery Villages guard spawns with ToroQuest guards affiliated with the local active Province, so ToroQuest crime and reputation behavior applies. Suppresses guard spawns in Provinces without a Lord and leaves guards unchanged where no Province exists. This only has an effect when both Witchery Villages and ToroQuest are installed.
- **Witchery Villages + ToroQuest** (`witcheryVillagesToroWalls`): Extends Witchery Villages wall footprints around ToroQuest village structures, leaving space between the wall and each building. This only has an effect when both mods are installed.

## Vanilla Food Pantry compatibility

These two fixes address separate problems in Eternal Confluence. Both settings default to `true` and can be changed independently. Keep both enabled when using EC's VFP, AppleCore, Scaling Feast, and Spice of Life: Carrot Edition combination. These are requirements for the intended compatibility cases, not mandatory dependencies of ECT itself.

### Hunger restoration: `vanillaFoodPantryServings`

**For players:** Uses AppleCore to find your maximum hunger. Prevents VFP multi-use foods from removing hunger and saturation when your hunger is above 20, and calculates how much food to eat using your increased hunger limit. This covers foods eaten in portions, such as breads, sandwiches, and pies. The intended combination is VFP, AppleCore, and a mod that increases maximum hunger, such as EC's Scaling Feast.

**Technical behavior:** VFP 4.3.1 calculates a serving in `VfpPantryItem.onItemUseFinish` (`func_77654_b`) using `20 - currentHunger`. At 24 hunger, this produces a negative amount. ECT replaces that calculation with one based on `AppleCoreAPI.accessor.getMaxHunger(player)` and rejects invalid or empty servings before food effects or portion changes occur. VFP's existing portion preferences, larger servings at low health, final partial servings, food effects, and container handling remain in place. Existing `FoodTotal`, `FoodPortion`, and `FoodUsed` NBT remains compatible.

### Carrot tasted-foods progress: `vanillaFoodPantryFoodValues`

**For players:** Fixes incorrect food values reported through VFP's AppleCore integration. Prevents eating VFP food from making your Spice of Life: Carrot Edition "tasted foods" count and bonus hearts incorrectly drop or jump. Ensures previously eaten foods are counted using their own hunger values. The intended combination is VFP, AppleCore, and Spice of Life: Carrot Edition; increased maximum hunger is not needed for this fix.

**Technical behavior:** During consumption, VFP's temporary serving value can override `getFoodValues(ItemStack)` for other VFP foods queried on the same thread. Carrot recalculates the eligibility of foods in its saved history during AppleCore's `FoodEaten` event, comparing their hunger values with its `minimumFoodValue` setting. The leaked serving value can therefore change which previously eaten foods qualify, without deleting food history. ECT changes VFP's `onPortionConsumed` and `getFoodValues` methods together so the temporary values apply only to the exact stack being consumed. Other stacks report their own values. The normal AppleCore food-addition and dietary-modifier path is retained, and the serving context is restored or cleared after nested consumption or exceptions.

The hunger fix can hide the progress symptom by making servings positive, but it does not correct the leaked food values. The progress fix corrects those values but does not change the serving calculation. Neither setting requires the other to be enabled.

Verified against `vanillafoodpantry-mc1.12.2-4.3.1`, `AppleCore-mc1.12.2-3.4.0`, and `solcarrot-1.12.2-1.8.4`. The generated config lists the tested versions beside each setting. Both fixes use fingerprint checks to leave unrecognized target methods unchanged.

When upgrading from the combined `vanillaFoodPantryServings` option, its existing enabled or disabled choice is carried over to the new `vanillaFoodPantryFoodValues` option unless that option already has an explicit value. Changing either setting requires a full client/server restart.

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
