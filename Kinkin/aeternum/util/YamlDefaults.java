package Kinkin.aeternum.util;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlDefaults {
   private YamlDefaults() {
   }

   public static void merge(JavaPlugin plugin, String fileName) {
      try {
         if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
         }

         File outFile = new File(plugin.getDataFolder(), fileName);
         File parent = outFile.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         if (!outFile.exists()) {
            plugin.saveResource(fileName, false);
            return;
         }

         YamlConfiguration current = YamlConfiguration.loadConfiguration(outFile);
         InputStream in = plugin.getResource(fileName);
         if (in == null) {
            return;
         }

         Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);

         YamlConfiguration def;
         try {
            def = YamlConfiguration.loadConfiguration(r);
         } catch (Throwable var11) {
            try {
               r.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }

            throw var11;
         }

         r.close();
         boolean var13 = false;
         if ("climate.yml".equals(fileName)
            && !current.contains("biome_spoof.client_refresh.use_biome_packets")
            && current.getInt("biome_spoof.client_refresh.interval_ticks", 3) == 3
            && current.getInt("biome_spoof.client_refresh.chunks_per_interval", 1) == 1) {
            current.set("biome_spoof.client_refresh.interval_ticks", 1);
            current.set("biome_spoof.client_refresh.chunks_per_interval", 12);
            var13 = true;
         }

         if ("climate.yml".equals(fileName)) {
            if (!current.contains("villager_type_overrides.appearance.override_chance") && current.contains("villager_type_overrides.override_chance")) {
               current.set("villager_type_overrides.appearance.override_chance", current.getDouble("villager_type_overrides.override_chance", 1.0));
               var13 = true;
            }

            if (!current.contains("villager_type_overrides.profession_rotation.show_lazy_tag") && current.contains("villager_type_overrides.show_lazy_tag")) {
               current.set("villager_type_overrides.profession_rotation.show_lazy_tag", current.getBoolean("villager_type_overrides.show_lazy_tag", true));
               var13 = true;
            }
         }

         for (String key : def.getKeys(true)) {
            if (!current.contains(key)) {
               current.set(key, def.get(key));
               var13 = true;
            }
         }

         if (var13) {
            current.save(outFile);
            plugin.getLogger().info("[AeternumSeasons] Updated defaults merged into " + fileName);
         }
      } catch (Exception ex) {
         plugin.getLogger().warning("[AeternumSeasons] Could not merge defaults for " + fileName + ": " + ex.getMessage());
      }
   }
}
