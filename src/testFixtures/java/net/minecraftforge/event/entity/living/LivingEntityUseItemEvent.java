package net.minecraftforge.event.entity.living;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

public class LivingEntityUseItemEvent extends LivingEvent {
    private final ItemStack item;

    public LivingEntityUseItemEvent(EntityLivingBase entity, ItemStack item) {
        super(entity);
        this.item = item;
    }

    public Entity getEntity() { return getEntityLiving(); }
    public ItemStack getItem() { return item; }

    public static class Finish extends LivingEntityUseItemEvent {
        public Finish(EntityLivingBase entity, ItemStack item) { super(entity, item); }
    }
}
