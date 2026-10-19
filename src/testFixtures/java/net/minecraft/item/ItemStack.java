package net.minecraft.item;

import java.util.HashMap;
import java.util.Map;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(null, 0, 0);
    private final Item item;
    private final java.util.function.Supplier<Item> delegate;
    private int count;
    private final int metadata;
    public final Map<String, String> tag = new HashMap<>();

    public ItemStack(Item item, int count, int metadata) {
        this.item = item;
        this.delegate = () -> item;
        this.count = count;
        this.metadata = metadata;
    }

    public Item getItem() { return isEmpty() ? Item.AIR : delegate.get(); }
    private Item getItemRaw() { return item; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public boolean isEmpty() { return getItemRaw() == null || getItemRaw() == Item.AIR || count <= 0; }
    public int getMetadata() { return metadata; }
    public int getItemDamage() { return metadata; }
    public String getDisplayName() { return tag.getOrDefault("displayName", ""); }

    public ItemStack copy() {
        ItemStack copy = new ItemStack(getItem(), count, metadata);
        copy.tag.putAll(tag);
        return copy;
    }
}
