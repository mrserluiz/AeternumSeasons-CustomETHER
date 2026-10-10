# Optional external climate API

Terra2 supplies climate metadata from `plugins/Terra2/terra2-settings.yml`. Aeternum does not configure Terra2, read its files, depend on its classes, or publish climate back to it.

The separate **Terra2AeternumBridge** addon reads Terra2's registered `org.terra2.api.climate.WorldClimateService` API 1 and supplies metadata to this generic consumer API:

```java
SeasonService.registerWorldClimateProvider(Plugin owner, Function<World, Map<String, String>> source);
SeasonService.unregisterWorldClimateProvider(Plugin owner);
```

Required fields: `season` (SPRING, SUMMER, AUTUMN, WINTER) and `reference-biome` (registered vanilla ID). Empty metadata means normal Aeternum behavior. Only one provider owns the override. Provider state is never saved to Aeternum configuration and is released when its owner stops.

## Installation

- Terra2 **7.0.24-BETA** provides the independent, read-only API.
- AeternumSeasons **4.5.2-CLIMATE-API-BETA** provides the generic consumer. Earlier versions without this API cannot receive metadata; the addon stays inactive.
- Install **Terra2AeternumBridge-1.0.0-BETA.jar** only to connect the plugins. Each plugin works independently without it. The addon has optional dependencies only.

Terra2 configuration:

```yaml
climate:
  enabled: true
generation:
  enabled: true
  protected-worlds: [world, world_nether, world_the_end]
worlds:
  aeternum_frost:
    pack: HYDRAXIA
    climate:
      season: WINTER
      reference-biome: minecraft:snowy_plains
```

Replace the world and pack names. Metadata is supplied only for explicitly configured, authorized worlds using the Terra2 generator. Protected worlds are refused. This does not create worlds or replace a generator. Use `/terra2 reload` to refresh metadata.

No Aeternum climate profile is needed. Its new default `world_climate.profiles` is empty. If 4.5.1 created a local profile, remove that specific profile or set `enabled: false` so removing the bridge restores normal behavior. Other local profiles remain supported; external metadata takes precedence while available.

## Preservation and limits

The consumer uses the supplied season and reference for temperature and snow classification. It blocks biome spoofing, FrostBiomeFixer replacement and backup restoration in a profiled world. Actual custom biome IDs remain intact. It does not force a storm or alter client precipitation rules. Crop/fauna biome mappings are not converted.

Backup palettes retain complete registry IDs; unresolved IDs reject the whole restore and preserve files, without a PLAINS fallback. Missing-ID diagnostics are bounded and deduplicated.

## Validation

Aeternum CI compiles the complete plugin and tests independent startup and save/restart on Paper 26.2. Terra2 CI also runs both plugins and the addon with the HYDRAXIA fixture, reload, protected-world checks, provider removal, and restart. Check workflow results before deploying a beta.

