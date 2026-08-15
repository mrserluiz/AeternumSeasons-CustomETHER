package Kinkin.aeternum;

import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import Kinkin.aeternum.lang.LanguageManager;
import java.util.Map;
import org.bukkit.entity.Player;

public final class SeasonGuide {
   private SeasonGuide() {
   }

   public static void sendGuide(Player p, AeternumSeasonsPlugin plugin) {
      LanguageManager lang = plugin.lang;
      SeasonService seasons = plugin.getSeasons();
      CalendarState st = seasons.getStateCopy(p.getWorld());
      Season current = st.season;
      p.sendMessage("§6§m------------§r §e" + lang.tr(p, "guide.title") + " §6§m------------");
      p.sendMessage("§f" + lang.trf(p, "guide.current", Map.of("season", lang.tr(p, "season." + current.name()))));
      p.sendMessage("");
      p.sendMessage("§d" + lang.tr(p, "guide.section.events"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.blood_moon"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.heat_wave"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.winter_freeze"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.magic_storm"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.festival"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.fishing"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.mining"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.tornado"));
      p.sendMessage("§7- " + lang.tr(p, "guide.events.restful_sleep"));
      p.sendMessage("");
      p.sendMessage("§a" + lang.tr(p, "guide.section.crops"));

      for (Season s : Season.values()) {
         String seasonName = lang.tr(p, "season." + s.name());
         String lineKey = "guide.crops." + s.name();
         p.sendMessage("§7- " + seasonName + ": §f" + lang.tr(p, lineKey));
      }

      p.sendMessage("");
      p.sendMessage("§b" + lang.tr(p, "guide.section.migration"));

      for (Season s : Season.values()) {
         String seasonName = lang.tr(p, "season." + s.name());
         String lineKey = "guide.migration." + s.name();
         p.sendMessage("§7- " + seasonName + ": §f" + lang.tr(p, lineKey));
      }

      p.sendMessage("");
      p.sendMessage("§6" + lang.tr(p, "guide.section.stables"));
      p.sendMessage("§7- " + lang.tr(p, "guide.stables.winter"));
      p.sendMessage("§7- " + lang.tr(p, "guide.stables.breeding"));
      p.sendMessage("§7- " + lang.tr(p, "guide.stables.bonus_drops"));
      p.sendMessage("");
      p.sendMessage("§9" + lang.tr(p, "guide.section.weather"));

      for (Season s : Season.values()) {
         String seasonName = lang.tr(p, "season." + s.name());
         String lineKey = "guide.weather." + s.name();
         p.sendMessage("§7- " + seasonName + ": §f" + lang.tr(p, lineKey));
      }

      p.sendMessage("");
      p.sendMessage("§6" + lang.tr(p, "guide.footer"));
   }
}
