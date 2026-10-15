package net.minecraftforge.fml.common.gameevent;
import net.minecraft.entity.player.EntityPlayer;
public class TickEvent {
    public enum Phase { START, END }
    public static class PlayerTickEvent {
        public final Phase phase;
        public final EntityPlayer player;
        public PlayerTickEvent(Phase phase, EntityPlayer player) { this.phase = phase; this.player = player; }
    }
}
