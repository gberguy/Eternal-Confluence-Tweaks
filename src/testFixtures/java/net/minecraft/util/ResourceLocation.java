package net.minecraft.util;
public class ResourceLocation {
    private final String value;
    public ResourceLocation(String value) { this.value = value; }
    public ResourceLocation(String namespace, String path) { this(namespace + ":" + path); }
    public String getNamespace() { return value.split(":")[0]; }
    public String getPath() { return value.substring(value.indexOf(':') + 1); }
}
