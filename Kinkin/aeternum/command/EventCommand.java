package Kinkin.aeternum.command;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.events.SeasonalEvent;
import Kinkin.aeternum.events.SeasonalEventService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EventCommand implements CommandExecutor, TabCompleter {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonalEventService events;

   public EventCommand(AeternumSeasonsPlugin plugin, SeasonalEventService events) {
      this.plugin = plugin;
      this.events = events;
   }

   public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
      Player p = sender instanceof Player ? (Player)sender : null;
      if (!sender.hasPermission("aeternum.events")) {
         if (p != null) {
            sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.no_permission"));
         } else {
            sender.sendMessage("[Aeternum] No permission.");
         }

         return true;
      } else {
         if (args.length == 0) {
            this.sendUsage(sender, p, label);
            return true;
         }

         String sub = args[0].toLowerCase(Locale.ROOT);
         if (sub.equals("list")) {
            this.handleList(sender, p);
            return true;
         }

         if (sub.equals("info")) {
            this.handleInfo(sender, p);
            return true;
         }

         if (sub.equals("stop")) {
            this.events.forceStop();
            if (p != null) {
               sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.stopped"));
            } else {
               sender.sendMessage("[Aeternum] Active event stopped.");
            }

            return true;
         } else if (sub.equals("start")) {
            if (args.length < 2) {
               this.sendUsage(sender, p, label);
               return true;
            }

            String id = args[1].toLowerCase(Locale.ROOT);
            Integer days = null;
            if (args.length >= 3) {
               try {
                  days = Integer.parseInt(args[2]);
               } catch (NumberFormatException var10) {
               }
            }

            boolean ok = this.events.forceStart(id, days);
            if (!ok) {
               if (p != null) {
                  sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.no_such_event").replace("{id}", id));
               } else {
                  sender.sendMessage("[Aeternum] Unknown event: " + id);
               }

               return true;
            } else {
               if (p != null) {
                  sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.started").replace("{id}", id));
               } else {
                  sender.sendMessage("[Aeternum] Forced start of event: " + id);
               }

               return true;
            }
         } else {
            this.sendUsage(sender, p, label);
            return true;
         }
      }
   }

   private void sendUsage(CommandSender sender, Player p, String label) {
      if (p != null) {
         sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.usage").replace("{cmd}", "/" + label));
      } else {
         sender.sendMessage("Usage: /" + label + " <list|info|start|stop> [id] [days]");
      }
   }

   private void handleList(CommandSender sender, Player p) {
      String header = p != null ? this.plugin.lang.tr(p, "cmd.event.list.header") : "Registered events:";
      sender.sendMessage(header);

      for (String id : this.events.getRegisteredEventIds()) {
         SeasonalEvent ev = this.events.getEventById(id);
         String line;
         if (p != null) {
            line = this.plugin.lang.tr(p, "cmd.event.list.item").replace("{id}", id).replace("{name}", ev.getDisplayName());
         } else {
            line = "- " + id + " (" + ev.getDisplayName() + ")";
         }

         sender.sendMessage(line);
      }
   }

   private void handleInfo(CommandSender sender, Player p) {
      SeasonalEvent ev = this.events.getActive();
      if (ev == null) {
         if (p != null) {
            sender.sendMessage(this.plugin.lang.tr(p, "cmd.event.no_active"));
         } else {
            sender.sendMessage("[Aeternum] No active event.");
         }
      } else {
         int days = this.events.getDaysRemaining();
         if (p != null) {
            String msg = this.plugin
               .lang
               .tr(p, "cmd.event.info")
               .replace("{id}", ev.getId())
               .replace("{name}", ev.getDisplayName())
               .replace("{days}", String.valueOf(days));
            sender.sendMessage(msg);
         } else {
            sender.sendMessage("[Aeternum] Active: " + ev.getId() + " (" + ev.getDisplayName() + "), days remaining=" + days);
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
      List<String> out = new ArrayList<>();
      if (args.length == 1) {
         String p = args[0].toLowerCase(Locale.ROOT);

         for (String s : new String[]{"list", "info", "start", "stop"}) {
            if (s.startsWith(p)) {
               out.add(s);
            }
         }

         return out;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
         String p = args[1].toLowerCase(Locale.ROOT);

         for (String id : this.events.getRegisteredEventIds()) {
            if (id.startsWith(p)) {
               out.add(id);
            }
         }

         return out;
      } else {
         return out;
      }
   }
}
