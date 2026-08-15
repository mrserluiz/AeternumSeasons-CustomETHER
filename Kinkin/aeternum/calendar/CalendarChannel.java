package Kinkin.aeternum.calendar;

import java.util.Locale;

public enum CalendarChannel {
   OVERWORLD,
   NETHER;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static CalendarChannel fromString(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ex) {
            return null;
         }
      } else {
         return null;
      }
   }
}
