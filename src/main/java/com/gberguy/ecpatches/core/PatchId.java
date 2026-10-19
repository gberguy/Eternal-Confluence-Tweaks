package com.gberguy.ecpatches.core;

public enum PatchId {
    ASTRAL_PERK_FORMULA("astralPerkLevelFormulaEnabled", "astralsorcery", "Astral Sorcery", "Uses a configurable cumulative perk XP formula in local worlds and on the server. Clients use server thresholds regardless of local settings.", "astralsorcery-1.12.2-1.10.27"),
    BLOOD_ARSENAL("bloodArsenalSlateItem", "bloodarsenal", "Blood Arsenal", "Fixes compressed slate variants having incorrect names and crafting behavior. Ensures each compressed slate uses the correct item variant.", "BloodArsenal-1.12.2-2.2.2-31"),
    ROOTS("rootsAirStateMatcher", "roots", "Roots", "Fixes Wildwood disappearing instead of converting to Runed Wildwood.", "Roots-1.12.2-3.1.9.2"),
    NUTRITION_LUNCHBOX("nutritionSpiceOfLifeLastItem", "nutrition", "Nutrition UEL + Spice of Life", "Fixes missing Nutrition awards when the last food in a lunchbox or lunchbag stack is consumed. Ensures the final serving awards nutrition like earlier servings.", "Nutrition-UEL-4.16.0, SpiceOfLife-mc1.12-1.3.12"),
    VFP_FOOD("vanillaFoodPantryServings", "vanillafoodpantry", "Vanilla Food Pantry + AppleCore", "Uses AppleCore to find your maximum hunger. Prevents VFP multi-use foods from removing hunger and saturation when your hunger is above 20, and calculates how much food to eat using your increased hunger limit.", "vanillafoodpantry-mc1.12.2-4.3.1, AppleCore-mc1.12.2-3.4.0"),
    VFP_FOOD_VALUES("vanillaFoodPantryFoodValues", "vanillafoodpantry", "Vanilla Food Pantry + AppleCore + Spice of Life: Carrot Edition", "Fixes incorrect food values reported through VFP's AppleCore integration. Prevents eating VFP food from making your Spice of Life: Carrot Edition \"tasted foods\" count and bonus hearts incorrectly drop or jump. Ensures previously eaten foods are counted using their own hunger values.", "vanillafoodpantry-mc1.12.2-4.3.1, AppleCore-mc1.12.2-3.4.0, solcarrot-1.12.2-1.8.4"),
    PLAYER_SATURATION("clampNegativeSaturation", "minecraft", "Minecraft", "Resets negative player saturation to zero on the server and client. Preserves positive saturation, including values above 20.", "Minecraft 1.12.2"),
    SCALING_FEAST_SATURATION_HUD("scalingFeastSafeSaturationHud", "scalingfeast", "Scaling Feast", "Prevents the saturation bar from crashing when player saturation is negative. Temporarily hides the affected bar until saturation is nonnegative.", "scalingfeast-1.10.4"),
    LYCANITES_MELEE("lycanitesMeleeLineOfSight", "lycanitesmobs", "Lycanites Mobs", "Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles. Requires the mob to see its target before attacking.", "lycanitesmobs-1.12.2-2.0.8.10"),
    MORECHIDS("morechidsClassGeneration", "morechids", "MoreChids", "Fixes a startup crash when MoreChids generates custom Orechid flowers.", "morechids-1.3.0"),
    WAYSTONES_VILLAGE("waystonesVillageWeight", "waystones", "Waystones", "Increases the selection weight of Waystones during village generation. Preserves Waystones' own generation chance and limit of one Waystone per village.", "Waystones_1.12.2-4.1.0"),
    LYCANITES_GHOST("lycanitesGhostlyShape", "lycanitesmobs", "Lycanites Mobs + Corail Tombstone", "Makes Lycanites mobs ignore players affected by Ghostly Shape.", "lycanitesmobs-1.12.2-2.0.8.10, tombstone-1.12.2-4.7.6"),
    WIZARDRY_GHOST("ebWizardryGhostlyShape", "ebwizardry", "Electroblob's Wizardry + Corail Tombstone", "Makes Wizardry mobs ignore players affected by Ghostly Shape.", "ElectroblobsWizardry-4.3.19, tombstone-1.12.2-4.7.6"),
    ANCIENT_GHOST("ancientSpellcraftGhostlyShape", "ancientspellcraft", "Ancient Spellcraft + Corail Tombstone", "Makes Ancient Spellcraft mobs ignore players affected by Ghostly Shape.", "AncientSpellcraft-1.12.2-1.8.3, tombstone-1.12.2-4.7.6"),
    TORO_GHOST("toroQuestGhostlyShape", "toroquest", "ToroQuest + Corail Tombstone", "Makes ToroQuest mobs ignore players affected by Ghostly Shape.", "ToroQuest-Revamped-3-25-2022-fix, tombstone-1.12.2-4.7.6"),
    WITCHERY_VILLAGES_TORO_GUARDS("witcheryVillagesToroGuards", "witcherywalls", "Witchery Villages + ToroQuest", "Replaces Witchery Villages guards inside ToroQuest Provinces with ToroQuest guards belonging to that Province. Prevents those Witchery Villages guards from spawning in Provinces without a Lord. Leaves guards outside Provinces unchanged.", "witcherywalls-1.12.2-1.2.0, ToroQuest-Revamped-3-25-2022-fix"),
    WITCHERY_VILLAGES_TORO_WALLS("witcheryVillagesToroWalls", "witcherywalls", "Witchery Villages + ToroQuest", "Expands Witchery Villages wall footprints around ToroQuest keeps, barracks, guard towers, shops, and walls. Adds an eight-block margin around these structures.", "witcherywalls-1.12.2-1.2.0, ToroQuest-Revamped-3-25-2022-fix");

    public final String key;
    public final String modId;
    public final String modName;
    public final String description;
    public final String testedVersions;

    public boolean ghostly() {
        return this == LYCANITES_GHOST || this == WIZARDRY_GHOST || this == ANCIENT_GHOST || this == TORO_GHOST;
    }

    PatchId(String key, String modId, String modName, String description, String testedVersions) {
        this.key = key;
        this.modId = modId;
        this.modName = modName;
        this.description = description;
        this.testedVersions = testedVersions;
    }
}
