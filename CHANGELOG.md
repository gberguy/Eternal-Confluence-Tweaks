# Changelog

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
