# Eternal Confluence Tweaks

Eternal Confluence Tweaks (ECT) provides independently configurable fixes and compatibility tweaks for Minecraft 1.12.2. Designed for the Eternal Confluence modpack, it can also be used in other Forge installations with the relevant mods.

## Installation and configuration

Requires Minecraft 1.12.2 and Forge. Place the ECT JAR in the instance's `mods` folder.

Settings are in `config/eternalconfluencetweaks.cfg`. All tweaks default to `true` and can be enabled or disabled individually. Changes require a full game/server restart.

## Tweaks

### Astral Sorcery — configurable perk leveling

A tweak that lets you set how much total experience is needed for each Astral Sorcery perk level using a custom formula in the config.

Hooks `PerkLevelManager`'s level, XP requirement, progress, and cap queries. The server validates a cumulative threshold table for Astral Sorcery's configured global cap and sends both during the Forge handshake. Clients ignore their local toggle and formula for that connection and clear the table on disconnect. An enabled server rejects clients that cannot synchronize its thresholds.

Invalid active formulas stop server startup with an error instead of silently using another curve.

In formulas, `i` starts at `1`; `prev` is `0` initially and then the preceding whole-XP threshold. Results are rounded down to whole XP thresholds. The default requires 1,000 total XP for level 2, 5,000 for level 3, and 14,000 for level 4. The generated config explains supported arithmetic, functions, and validation limits.

- **Requires:** Astral Sorcery; ECT on both client and server when this tweak is enabled
- **Tested ver:** astralsorcery-1.12.2-1.10.27
- **Config:** `astralPerkLevelFormulaEnabled`
- **Formula:** `astralPerkLevelFormula` — default `prev + 1000 * i * i`

---

### Blood Arsenal — compressed slate variants

Fixes compressed slate variants having incorrect names and crafting behavior. Each compressed slate uses the correct item variant.

Changes `IBABlock.getItemBlock` to use `BlockSlate`'s existing subtype-aware item instead of constructing a generic `ItemBlock`. Other block types retain their original item-registration path.

- **Requires:** Blood Arsenal
- **Tested ver:** BloodArsenal-1.12.2-2.2.2-31
- **Config:** `bloodArsenalSlateItem`

---

### Roots — Wildwood conversion

Fixes Wildwood disappearing instead of converting to Runed Wildwood.

Adds an air-block check to `StateUtil$StateMatcher.calculateState`. If a registry lookup returns `Blocks.AIR`, Roots follows its existing fallback path instead of accepting air as the resolved block.

- **Requires:** Roots
- **Tested ver:** Roots-1.12.2-3.1.9.2
- **Config:** `rootsAirStateMatcher`

---

### Nutrition UEL + Spice of Life — last food in a container

Fixes missing Nutrition awards when the last food in a lunchbox or lunchbag stack is consumed. The final serving awards nutrition like earlier servings.

Spice of Life forwards the consumed food stack to Forge's finish-use event after consumption has reduced its count. ECT changes Nutrition's `EventEatFood.finishUsingItem` to receive a count-one snapshot when that stack has reached zero. The original stack count is restored, and Nutrition's existing food-validity and application-phase rules remain in effect.

- **Requires:** Nutrition UEL + Spice of Life
- **Tested ver:** Nutrition-UEL-4.16.0, SpiceOfLife-mc1.12-1.3.12
- **Config:** `nutritionSpiceOfLifeLastItem`

---

### Vanilla Food Pantry + AppleCore — expanded hunger

Lets VFP multi-use foods restore hunger correctly when your hunger is 20 or higher but below your increased maximum. Prevents foods such as breads, sandwiches, and pies from removing hunger and saturation instead of restoring them.

Replaces the hardcoded `20 - currentHunger` calculation in `VfpPantryItem.onItemUseFinish` with a calculation using `AppleCoreAPI.accessor.getMaxHunger(player)`. Rejects invalid or empty servings before applying food effects or updating portion data. VFP's portion preferences, larger servings at low health, final partial servings, food effects, and container handling remain in place.

- **Requires:** Vanilla Food Pantry + AppleCore
- **Tested ver:** vanillafoodpantry-mc1.12.2-4.3.1, AppleCore-mc1.12.2-3.4.0
- **Config:** `vanillaFoodPantryServings`

---

### Vanilla Food Pantry + AppleCore + Carrot Edition — tasted-foods progress

Prevents eating VFP food from making your Spice of Life: Carrot Edition “tasted foods” count and bonus hearts incorrectly drop or jump. Previously eaten foods continue to count using their own hunger values.

VFP exposes temporary serving values while food is consumed. Those values can leak into queries for other VFP stacks when Carrot Edition recalculates food eligibility during AppleCore's `FoodEaten` event. ECT changes `onPortionConsumed` and `getFoodValues` together so temporary nutrition applies only to the exact stack being consumed. Other stacks report their own values. The serving context is restored or cleared after nested consumption or exceptions, while AppleCore's normal food-addition and dietary-modifier path is retained.

- **Requires:** Vanilla Food Pantry + AppleCore + Spice of Life: Carrot Edition
- **Tested ver:** vanillafoodpantry-mc1.12.2-4.3.1, AppleCore-mc1.12.2-3.4.0, solcarrot-1.12.2-1.8.4
- **Config:** `vanillaFoodPantryFoodValues`

This fix works independently of the expanded-hunger fix and does not require increased maximum hunger.

---

### Minecraft — negative player saturation

Resets negative player saturation to zero on the server and client. Preserves positive saturation, including values above 20.

Checks player saturation at the end of each player tick. When the value is negative, ECT writes zero to Minecraft's saturation field using reflection. This avoids relying on a saturation setter that is unavailable in the dedicated-server API.

- **Requires:** Minecraft + Forge
- **Tested ver:** Minecraft 1.12.2
- **Config:** `clampNegativeSaturation`

---

### Scaling Feast — saturation HUD safeguard

Prevents the saturation bar from crashing when player saturation is negative. Temporarily hides the affected bar until saturation is nonnegative, then allows it to display normally.

Changes `ScalingFeastUpperSaturationBarDrawable.shouldDraw` to check the player's saturation before drawing. Negative values skip the bar's rendering path, preventing invalid colour indexing. Nonnegative values retain Scaling Feast's normal display behavior.

- **Requires:** Scaling Feast
- **Tested ver:** scalingfeast-1.10.4
- **Config:** `scalingFeastSafeSaturationHud`

---

### Lycanites Mobs — melee line of sight

Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles. The mob must be able to see its target before attacking.

Adds an `EntitySenses.canSee` check to the in-range attack path in `AttackMeleeGoal`. When visibility fails, execution follows the existing branch that skips the attack.

- **Requires:** Lycanites Mobs
- **Tested ver:** lycanitesmobs-1.12.2-2.0.8.10
- **Config:** `lycanitesMeleeLineOfSight`

---

### MoreChids — custom Orechid generation

Fixes a startup crash when MoreChids generates custom Orechid flowers.

Replaces two calls to ASM's `Type.getType(String)` with `Type.getObjectType(String)` in `MorechidClassBuilder.generateMorechid`. The supplied class names are JVM internal names, so they must be interpreted as object types rather than type descriptors.

- **Requires:** MoreChids
- **Tested ver:** morechids-1.3.0
- **Config:** `morechidsClassGeneration`

---

### Waystones — village generation weight

Makes Waystones more likely to be selected during village generation. Preserves Waystones' own generation chance and limit of one Waystone per village.

Changes the piece weight returned by `VillageWaystoneCreationHandler.getVillagePieceWeight` from the original `3` to ECT's configured value, which defaults to `100`. The existing generation-chance check and piece limit remain unchanged.

- **Requires:** Waystones
- **Tested ver:** Waystones_1.12.2-4.1.0
- **Config:** `waystonesVillageWeight`
- **Weight:** `villageWaystoneWeight` — whole numbers from `1` to `1000000`; default `100`

---

### Witchery Villages + ToroQuest — village guards

Replaces Witchery Villages guards inside ToroQuest Provinces with ToroQuest guards belonging to that Province, allowing ToroQuest's guard behavior and crime system to apply. Prevents those Witchery Villages guards from spawning in Provinces without a Lord. Guards outside Provinces remain unchanged.

Handles server-side `EntityJoinWorldEvent` events for `EntityVillageGuard`. ECT looks up the Province at the guard's position and initializes a replacement `EntityGuard` with that Province's affiliation. The original join event is cancelled only after the replacement successfully spawns, or immediately when the Province has no Lord. Invalid Province data or a failed replacement leaves the original guard in place.

- **Requires:** Witchery Villages + ToroQuest
- **Tested ver:** witcherywalls-1.12.2-1.2.0, ToroQuest-Revamped-3-25-2022-fix
- **Config:** `witcheryVillagesToroGuards`

---

### Witchery Villages + ToroQuest — village walls

Expands Witchery Villages wall footprints around ToroQuest keeps, barracks, guard towers, shops, and walls. Adds an eight-block margin around those structures during wall generation.

Adds a bounds-extension call to `TileEntityVillageWallGen.setStructure`. For each recognized ToroQuest structure component, ECT expands the nearest available road bounds to include the component's bounding box plus eight blocks horizontally. The adjusted bounds are then available to Witchery Villages' wall-generation logic.

- **Requires:** Witchery Villages + ToroQuest
- **Tested ver:** witcherywalls-1.12.2-1.2.0, ToroQuest-Revamped-3-25-2022-fix
- **Config:** `witcheryVillagesToroWalls`

---

### Lycanites Mobs + Corail Tombstone — Ghostly Shape

Makes Lycanites mobs ignore players affected by Ghostly Shape.

Adds a `tombstone:ghostly_shape` potion check to Lycanites' `TargetingGoal.isEntityTargetable`. Shared vanilla targeting hooks also consult ECT's runtime guard, which identifies Lycanites mobs by their `lycanitesmobs` registry namespace and checks this integration's setting.

- **Requires:** Lycanites Mobs + Corail Tombstone
- **Tested ver:** lycanitesmobs-1.12.2-2.0.8.10, tombstone-1.12.2-4.7.6
- **Config:** `lycanitesGhostlyShape`

---

### Electroblob's Wizardry + Corail Tombstone — Ghostly Shape

Makes Wizardry mobs ignore players affected by Ghostly Shape.

Adds ECT's Ghostly Shape guard to the targeting predicates in `EntityEvilWizard` and `ISummonedCreature`, alongside the shared vanilla targeting hook. The runtime guard selects mobs in the `ebwizardry` registry namespace and checks the player's active potion and this integration's setting.

- **Requires:** Electroblob's Wizardry + Corail Tombstone
- **Tested ver:** ElectroblobsWizardry-4.3.19, tombstone-1.12.2-4.7.6
- **Config:** `ebWizardryGhostlyShape`

---

### Ancient Spellcraft + Corail Tombstone — Ghostly Shape

Makes Ancient Spellcraft mobs ignore players affected by Ghostly Shape.

Uses the shared vanilla and Wizardry targeting hooks with ECT's Ghostly Shape runtime guard. The guard identifies Ancient Spellcraft mobs by their `ancientspellcraft` registry namespace and applies this integration's setting independently of the Wizardry integration.

- **Requires:** Ancient Spellcraft + Corail Tombstone
- **Tested ver:** AncientSpellcraft-1.12.2-1.8.3, tombstone-1.12.2-4.7.6
- **Config:** `ancientSpellcraftGhostlyShape`

---

### ToroQuest + Corail Tombstone — Ghostly Shape

Makes ToroQuest mobs ignore players affected by Ghostly Shape.

Adds ECT's Ghostly Shape guard to ToroQuest's bandit, civilization-based, and Monolith Eye targeting predicates, alongside the shared vanilla targeting hook. The runtime guard selects mobs in the `toroquest` registry namespace and checks the player's active potion and this integration's setting.

- **Requires:** ToroQuest + Corail Tombstone
- **Tested ver:** ToroQuest-Revamped-3-25-2022-fix, tombstone-1.12.2-4.7.6
- **Config:** `toroQuestGhostlyShape`

## Configuration notes

Bytecode patches check target-method fingerprints and leave unrecognized code unchanged.

When upgrading from the combined VFP tweak, the existing `vanillaFoodPantryServings` choice is copied to `vanillaFoodPantryFoodValues` unless the latter already has an explicit value. After migration, the two settings can be changed independently.

## License

MIT; see [LICENSE](LICENSE).

## AI use disclosure

AI tools assisted with analyzing target-mod behavior and writing portions of this mod's source code and documentation. The project owner directed the work and tested the release in-game.
