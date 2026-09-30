package io.github.mrserluiz.ethercraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DimensionService {
    private final EtherCraftPlugin plugin;
    private final String name;
    public DimensionService(EtherCraftPlugin plugin) {
        this.plugin = plugin;
        name = plugin.getConfig().getString("frost.world", "ethercraft_frost");
        if (!name.matches("ethercraft_[a-z0-9_]{1,40}") || name.equals("ethercraft_nether") || name.equals("ethercraft_the_end"))
            throw new IllegalArgumentException("Use um nome próprio como ethercraft_frost.");
    }
    public String name() { return name; }
    private Path manifest() { return Bukkit.getWorldContainer().toPath().resolve(name).resolve("ethercraft-world.yml"); }
    public boolean owns(World world) {
        if (world == null || !world.getName().equals(name)) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(manifest().toFile());
        return "EtherCraftCustom".equals(y.getString("owner")) && "frost-v1".equals(y.getString("generator"))
            && world.getUID().toString().equals(y.getString("uuid"));
    }
    /** Explicit admin command only. Existing foreign saves always rejected. */
    public World createOrLoad() throws IOException {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            if (!owns(loaded)) throw new IOException("Mundo carregado sem manifesto próprio; operação bloqueada.");
            return loaded;
        }
        Path folder = manifest().getParent();
        if (Files.isSymbolicLink(folder)) throw new IOException("Pasta de mundo simbólica bloqueada.");
        boolean existing = Files.exists(folder);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(manifest().toFile());
        if (existing && !("EtherCraftCustom".equals(y.getString("owner")) && "frost-v1".equals(y.getString("generator"))))
            throw new IOException("Pasta existente sem manifesto compatível; operação bloqueada.");
        UUID expected = null;
        if (existing) {
            try { expected = UUID.fromString(y.getString("uuid", "")); }
            catch (IllegalArgumentException error) { throw new IOException("UUID do manifesto inválido.", error); }
            // Check before WorldCreator can mutate an existing save.
            Path uid = folder.resolve("uid.dat");
            if (!Files.isRegularFile(uid)) throw new IOException("uid.dat ausente; carregamento bloqueado.");
            try (var input = new java.io.DataInputStream(Files.newInputStream(uid))) {
                UUID actual = new UUID(input.readLong(), input.readLong());
                if (!actual.equals(expected)) throw new IOException("UUID do save diverge do manifesto.");
            }
        }
        World world = new WorldCreator(name).environment(World.Environment.NORMAL)
            .seed(plugin.getConfig().getLong("frost.seed", 0)).generator(new FrostGenerator()).createWorld();
        if (world == null) throw new IOException("Paper não conseguiu criar/carregar Frost.");
        if (expected != null && !expected.equals(world.getUID())) throw new IOException("UUID carregado diverge do manifesto.");
        y.set("owner", "EtherCraftCustom"); y.set("generator", "frost-v1");
        y.set("uuid", world.getUID().toString()); y.set("seed", world.getSeed()); y.set("schema", 1);
        y.save(manifest().toFile());
        return world;
    }
}
