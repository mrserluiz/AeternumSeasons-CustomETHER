package Kinkin.aeternum.calendar;

import java.util.Locale;

public enum CalendarMode {
   SEASONS,
   MONTHS;

   public static CalendarMode fromString(String raw, CalendarMode def) {
      if (raw != null && !raw.isBlank()) {
         try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ex) {
            return def;
         }
      } else {
         return def;
      }
   }
}
