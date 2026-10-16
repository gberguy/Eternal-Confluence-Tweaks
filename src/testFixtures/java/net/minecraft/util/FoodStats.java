package net.minecraft.util;
public class FoodStats {
    private int foodLevel = 20;
    private float foodSaturationLevel = 5.0F;
    private float foodExhaustionLevel;
    private int foodTimer;
    private int prevFoodLevel = 20;
    public float getSaturationLevel() { return foodSaturationLevel; }
}
