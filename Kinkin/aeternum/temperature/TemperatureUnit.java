package Kinkin.aeternum.temperature;

public enum TemperatureUnit {
   CELSIUS,
   FAHRENHEIT;

   public static TemperatureUnit fromString(String raw) {
      if (raw == null) {
         return CELSIUS;
      }

      String v = raw.trim().toUpperCase();
      return !v.equals("F") && !v.equals("FAHRENHEIT") && !v.equals("FAHRENHEITS") ? CELSIUS : FAHRENHEIT;
   }

   public int fromCelsiusRounded(int celsius) {
      return this == FAHRENHEIT ? (int)Math.round(celsius * 9.0 / 5.0 + 32.0) : celsius;
   }
}
