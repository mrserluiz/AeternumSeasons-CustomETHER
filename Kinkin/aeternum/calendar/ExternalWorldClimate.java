package Kinkin.aeternum.calendar;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.plugin.Plugin;

/** Generic optional consumer; contains no Terra2 dependency or world-writing API. */
public final class ExternalWorldClimate {
    private record Provider(Plugin owner, Function<World, Map<String, String>> read) {}
    private record Cached(Map<String, String> data, WorldClimateProfile profile) {}
    private volatile Provider provider;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();

    public synchronized void register(Plugin owner, Function<World, Map<String, String>> source) {
        Objects.requireNonNull(owner); Objects.requireNonNull(source);
        if(provider != null && provider.owner() != owner)
            throw new IllegalStateException("An external climate provider is already registered");
        provider = new Provider(owner, source); cache.clear(); warned.clear();
    }
    public synchronized void unregister(Plugin owner) {
        if(provider != null && provider.owner() == owner) clear();
    }
    public synchronized void clear() { provider = null; cache.clear(); warned.clear(); }
    public WorldClimateProfile profile(World world) {
        Provider current = provider;
        if(world == null || current == null || !current.owner().isEnabled()) return null;
        try {
            Map<String, String> data = current.read().apply(world);
            if(data == null || data.isEmpty()) { cache.remove(world.getUID()); return null; }
            Cached old = cache.get(world.getUID());
            if(old != null && old.data().equals(data)) return old.profile();
            var result = new WorldClimateProfile(data.get("season"), data.get("reference-biome"));
            if(Registry.BIOME.get(NamespacedKey.fromString(result.climateBiome())) == null)
                throw new IllegalArgumentException("Unknown vanilla climate reference");
            if(cache.size() >= 512) cache.clear();
            cache.put(world.getUID(), new Cached(Map.copyOf(data), result));
            return result;
        } catch(RuntimeException invalid) {
            if(warned.size() < 128 && warned.add(world.getUID())) current.owner().getLogger().warning(
                "External climate refused for " + world.getName() + ": " + invalid.getMessage());
            cache.remove(world.getUID());
            return null;
        }
    }
}
