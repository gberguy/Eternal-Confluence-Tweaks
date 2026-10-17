package com.gberguy.ecpatches.core;

public enum PatchId {
    BLOOD_ARSENAL("bloodArsenalSlateItem", "bloodarsenal", "Blood Arsenal", "Fixes compressed slate variants having incorrect names, variant handling, and crafting recipe behavior."),
    ROOTS("rootsAirStateMatcher", "roots", "Roots", "Fixes Wildwood disappearing instead of converting correctly to Runed Wildwood."),
    NUTRITION_LUNCHBOX("nutritionSpiceOfLifeLastItem", "nutrition", "Nutrition UEL / Spice of Life", "Fixes missing nutrition when the last food in a Spice of Life lunchbox or lunchbag stack is consumed."),
    VFP_FOOD("vanillaFoodPantryServings", "vanillafoodpantry", "Vanilla Food Pantry + AppleCore", "Uses AppleCore to find your maximum hunger. Prevents VFP multi-use foods from removing hunger and saturation when your hunger is above 20, and calculates how much food to eat using your increased hunger limit."),
    VFP_FOOD_VALUES("vanillaFoodPantryFoodValues", "vanillafoodpantry", "Vanilla Food Pantry + AppleCore + Spice of Life: Carrot Edition", "Fixes incorrect food values reported through VFP's AppleCore integration. Prevents eating VFP food from making your Spice of Life: Carrot Edition \"tasted foods\" count and bonus hearts incorrectly drop or jump. Ensures previously eaten foods are counted using their own hunger values."),
    PLAYER_SATURATION("clampNegativeSaturation", "minecraft", "Minecraft", "Resets negative player saturation to zero at the end of each player tick on the server and client, preserving positive values above 20."),
    SCALING_FEAST_SATURATION_HUD("scalingFeastSafeSaturationHud", "scalingfeast", "Scaling Feast", "Skips the saturation bar when player saturation is negative, preventing invalid colour indexing without disabling the saturation display."),
    LYCANITES_MELEE("lycanitesMeleeLineOfSight", "lycanitesmobs", "Lycanites Mobs", "Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles."),
    MORECHIDS("morechidsClassGeneration", "morechids", "MoreChids", "Fixes a startup crash when MoreChids generates custom Orechid classes."),
    WAYSTONES_VILLAGE("waystonesVillageWeight", "waystones", "Waystones", "Set waystonesVillageWeight to false to disable this tweak."),
    LYCANITES_GHOST("lycanitesGhostlyShape", "lycanitesmobs", "Lycanites Mobs", ""),
    WIZARDRY_GHOST("ebWizardryGhostlyShape", "ebwizardry", "Electroblob's Wizardry", ""),
    ANCIENT_GHOST("ancientSpellcraftGhostlyShape", "ancientspellcraft", "Ancient Spellcraft", ""),
    TORO_GHOST("toroQuestGhostlyShape", "toroquest", "ToroQuest", ""),
    WITCHERY_VILLAGES_TORO_GUARDS("witcheryVillagesToroGuards", "witcherywalls", "Witchery Villages + ToroQuest", "Replaces Witchery Villages guards with ToroQuest guards in active Provinces and suppresses them in Provinces without a Lord."),
    WITCHERY_VILLAGES_TORO_WALLS("witcheryVillagesToroWalls", "witcherywalls", "Witchery Villages + ToroQuest", "Expands Witchery Villages wall footprints around ToroQuest village structures when both mods are installed.");

    public final String key;
    public final String modId;
    public final String modName;
    public final String description;

    public boolean ghostly() {
        return this == LYCANITES_GHOST || this == WIZARDRY_GHOST || this == ANCIENT_GHOST || this == TORO_GHOST;
    }

    PatchId(String key, String modId, String modName, String description) {
        this.key = key;
        this.modId = modId;
        this.modName = modName;
        this.description = description;
    }
}
