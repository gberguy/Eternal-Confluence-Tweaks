package net.minecraft.util;
public class FoodStats {
    private float saturation = 5.0F;
    public int saturationWrites;
    public float getSaturationLevel() { return saturation; }
    public void setFoodSaturationLevel(float value) { saturation = value; saturationWrites++; }
}
