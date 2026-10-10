package probe;
import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.Season;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;
public final class ClimateProbe extends JavaPlugin {
 public void onEnable() {
  Bukkit.getScheduler().runTaskLater(this, () -> {
   try {
    var plugin=(AeternumSeasonsPlugin)Bukkit.getPluginManager().getPlugin("AeternumSeasons");
    require(plugin!=null && plugin.isEnabled(),"Aeternum enabled");
    World world=Bukkit.getWorld("world");
    var seasons=plugin.getSeasons();
    require(seasons.preservesWorldBiomes(world),"profile protects real biomes");
    require(seasons.getStateCopy(world).season==Season.WINTER,"fixed winter");
    Biome actual=world.getBiome(0,64,0);
    require(seasons.climateBiome(world,actual)==Biome.SNOWY_PLAINS,"virtual snowy reference");
    String key=actual.getKey().toString();
    var file=new java.io.File(getDataFolder(),"biome-key.txt");
    getDataFolder().mkdirs();
    if(file.exists()) require(java.nio.file.Files.readString(file.toPath()).equals(key),"biome survives restart");
    else java.nio.file.Files.writeString(file.toPath(),key);
    seasons.persistNow();
    getLogger().info("AETERNUM_CLIMATE_RUNTIME_OK biome="+key);
   } catch(Throwable error) {
    error.printStackTrace();
    getLogger().severe("AETERNUM_CLIMATE_RUNTIME_FAILED");
   } finally { Bukkit.shutdown(); }
  },100L);
 }
 private void require(boolean result,String description) { if(!result)throw new IllegalStateException(description); }
}
