package net.minecraft.entity;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
public class Entity {
    public World world = new World();
    public ResourceLocation registryKey;
    public World getEntityWorld() { return world; }
    public boolean isOnSameTeam(Entity other) { return false; }
}
