package net.minecraft.entity.player;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.FoodStats;
public class EntityPlayer extends EntityLivingBase {
    public final PlayerCapabilities capabilities = new PlayerCapabilities();
    private final FoodStats foodStats = new FoodStats();
    public FoodStats getFoodStats() { return foodStats; }
}
