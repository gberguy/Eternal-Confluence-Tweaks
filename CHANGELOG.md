# Changelog

## [0.4.8] - 2026-10-19

### Added

- Added independently configurable Astral Sorcery perk leveling with `astralPerkLevelFormulaEnabled` and `astralPerkLevelFormula`. The default formula is `prev + 1000 * i * i`, with validation and clear errors for unusable curves.
- Added server-authoritative perk XP thresholds synchronized before login. Clients follow the server's curve and global cap regardless of local settings, while Astral Sorcery's Game Stages restrictions, fractional XP, research, and existing perks are preserved.
- Added a README section explaining the Astral Sorcery tweak, its implementation, config settings, and compatibility requirements.

### Changed

- Placed the Astral enable toggle directly above its formula setting, below the explanatory comments. Removed the Astral Level Nerf and restart reminders from those generated comments; existing configs refresh without losing user settings.
- Moved README dividers between complete tweak entries and simplified the Astral Sorcery description.

## [0.4.7] - 2026-10-16

### Changed
- Standardized every generated config entry with its target mods, tested versions, and a single-line description. Simplified the Ghostly Shape descriptions and clarified the Waystones weight setting.
- Existing configs refresh their generated comments on launch while preserving toggle values, village weight, custom notes, and unknown options. No manual config deletion is needed.
- Reorganized the README into a section for each tweak, with a plain-language description followed by an implementation summary, mod requirements, tested versions, and config keys.

## [0.4.6] - 2026-10-16

### Changed
- Split the VFP fixes into two independent settings: `vanillaFoodPantryServings` for hunger restoration above 20, and `vanillaFoodPantryFoodValues` for Spice of Life: Carrot Edition tasted-foods progress. Both default to enabled; existing choices from the combined option are preserved when adding the new setting.
- Updated both VFP config entries with clearer descriptions, the target mod combinations, and tested version information. Existing generated comments are replaced without changing user toggle values.
- Added plain-language and technical README explanations of both VFP fixes, their intended EC compatibility cases, and how they interact.

## [0.4.5] - 2026-10-16

### Fixed
- Fixed Vanilla Food Pantry multi-use foods subtracting hunger and saturation above 20 hunger. Servings now respect AppleCore's per-player maximum hunger, and invalid or empty servings leave the food unchanged.
- Fixed temporary VFP serving nutrition affecting unrelated food queries during consumption, which could lower Spice of Life: Carrot Edition progress and milestone health bonuses. Serving values now apply only to the consumed stack, with cleanup after nested consumption or event failures.
- Added the independently configurable `vanillaFoodPantryServings` option, enabled by default. Preserves existing food NBT, portion rules, effects, containers, and dietary modifiers on client and server.

## [0.4.4] - 2026-10-16

### Fixed
- Fixed dedicated-server disconnects and repeated login failures when correcting negative player saturation. The safeguard now accesses the shared saturation field instead of calling a client-only Minecraft method.
- Added error handling for saturation field access failures so they are logged without throwing from the player tick handler.

## [0.4.3] - 2026-10-15

### Fixed
- Added an independently configurable safeguard that resets negative player saturation to zero on the server and client, preserving positive values above 20 without requiring Scaling Feast or AppleCore.
- Prevented Scaling Feast's saturation HUD from crashing on negative saturation while keeping its saturation display enabled. The HUD protection works independently of player saturation correction.

## [0.4.2] - 2026-10-15

### Fixed
- Restored Nutrition UEL awards for food consumed from Spice of Life lunchboxes and lunchbags when the food stack is depleted.

## [0.4.1] - 2026-09-27

### Added
- Added optional Witchery Villages wall routing around ToroQuest village structures.

### Changed
- Witchery Villages guard replacement now suppresses guard spawns in ToroQuest Provinces without a Lord.

## [0.4.0] - 2026-09-27

### Added
- Added optional Witchery Villages guard replacement with ToroQuest guards affiliated with the local Province for ToroQuest crime and reputation behavior.

## [0.3.1] - 2026-09-27

### Changed
- Added explanations for the Waystones enable switch and village weight to the generated config.

## [0.3.0] - 2026-09-27

### Added
- Added an optional Waystones village-generation weight setting, defaulting to 100.

### Changed
- Renamed the project to Eternal Confluence Tweaks and updated the mod ID and generated config filename.
