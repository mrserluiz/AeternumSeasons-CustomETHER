package Kinkin.aeternum.farming;

import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class CropGrowthService {
   private final SeasonalCropConfig config;
   private final GreenhouseService greenhouse;
   private final SeasonService seasons;

   public CropGrowthService(SeasonalCropConfig config, GreenhouseService greenhouse, SeasonService seasons) {
      this.config = config;
      this.greenhouse = greenhouse;
      this.seasons = seasons;
   }

   public CropGrowthService.GrowthDecision evaluate(Block b) {
      Material m = b.getType();
      if (!this.config.isManagedCrop(m)) {
         return new CropGrowthService.GrowthDecision(false, 0);
      }

      EnumSet<Season> allowed = this.config.getAllowedSeasons(m);
      if (allowed != null && !allowed.isEmpty()) {
         CalendarState st = this.seasons.getStateCopy(b.getWorld());
         Season season = st.season;
         boolean inGreenhouse = this.greenhouse.isInGreenhouse(b);
         boolean seasonOk = allowed.contains(season);
         boolean seesSky = this.greenhouse.canSeeSky(b);
         if (!seasonOk && !inGreenhouse && seesSky) {
            return new CropGrowthService.GrowthDecision(true, 0);
         }

         double speed = 1.0;
         int light = b.getLightLevel();
         if (light < this.config.getRequiredLight()) {
            speed *= this.config.getLowLightMultiplier();
         }

         if (!seesSky && !inGreenhouse) {
            speed *= this.config.getUndergroundMultiplier();
         }

         if (this.greenhouse.isDirectlyUnderRain(b) && !inGreenhouse) {
            speed += this.config.getRainBonus();
         }

         if (inGreenhouse && season == Season.WINTER) {
            speed += this.config.getWinterGreenhouseBonus();
         }

         if (speed <= 0.0) {
            return new CropGrowthService.GrowthDecision(true, 0);
         }

         double r = ThreadLocalRandom.current().nextDouble();
         if (!(speed < 1.0)) {
            double over = speed - 1.0;
            int extra = (int)Math.floor(over);
            double frac = over - extra;
            if (r < frac) {
               extra++;
            }

            return new CropGrowthService.GrowthDecision(false, extra);
         } else {
            return r > speed ? new CropGrowthService.GrowthDecision(true, 0) : new CropGrowthService.GrowthDecision(false, 0);
         }
      } else {
         return new CropGrowthService.GrowthDecision(false, 0);
      }
   }

   public static final class GrowthDecision {
      private final boolean cancel;
      private final int extraAges;

      public GrowthDecision(boolean cancel, int extraAges) {
         this.cancel = cancel;
         this.extraAges = extraAges;
      }

      public boolean cancel() {
         return this.cancel;
      }

      public int extraAges() {
         return this.extraAges;
      }
   }
}
