package Kinkin.aeternum.calendar;

public enum Season {
   SPRING,
   SUMMER,
   AUTUMN,
   WINTER;

   public String display() {
      return switch (this) {
         case SPRING -> "Primavera";
         case SUMMER -> "Verano";
         case AUTUMN -> "Otoño";
         case WINTER -> "Invierno";
      };
   }
}
