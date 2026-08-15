package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

public final class BiomeBackupStore {
   private static final int BACKUP_MAGIC = -1364129008;
   private static final byte BACKUP_VERSION = 1;
   private static final int CONTAINER_MAGIC = -1364128752;
   private static final byte CONTAINER_VERSION = 1;
   private static final int RECORD_MAGIC = -1364082687;
   private static final int CONTAINER_HEADER_BYTES = 21;
   private static final int RECORD_HEADER_BYTES = 20;
   private static final int MAX_PAYLOAD_BYTES = 16777216;
   private static final String CONTAINER_EXTENSION = ".asb";
   private final AeternumSeasonsPlugin plugin;
   private final Path root;
   private final boolean packedStorage;
   private final boolean migrateLegacyFiles;
   private final Map<UUID, BiomeBackupStore.WorldContainer> containers = new ConcurrentHashMap<>();
   private final AtomicBoolean restoreInProgress = new AtomicBoolean(false);
   private final Object legacyOperationLock = new Object();

   public BiomeBackupStore(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.root = plugin.getDataFolder().toPath().resolve("biome_backups");
      String storage = plugin.cfg.climate.getString("biome_spoof.disk_backup.storage", "PACKED");
      this.packedStorage = !"LEGACY_FILES".equalsIgnoreCase(storage) && !"UNPACKED".equalsIgnoreCase(storage);
      this.migrateLegacyFiles = this.packedStorage && plugin.cfg.climate.getBoolean("biome_spoof.disk_backup.migrate_legacy_files", true);

      try {
         Files.createDirectories(this.root);
      } catch (IOException e) {
         plugin.getLogger().warning("[BiomeBackup] No se pudo crear la carpeta: " + e.getMessage());
      }

      if (this.migrateLegacyFiles) {
         Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getScheduler().runTaskAsynchronously(plugin, this::migrateLegacyBackups), 1L);
      }
   }

   public void saveFirstTouch(Chunk chunk, Biome[] originalGrid, int stepXZ, int stepY) {
      if (!this.restoreInProgress.get()) {
         World world = chunk.getWorld();
         UUID worldId = world.getUID();
         int cx = chunk.getX();
         int cz = chunk.getZ();
         long coordinate = coordinateKey(cx, cz);
         BiomeBackupStore.WorldContainer state = this.container(worldId);
         if (!state.known.contains(coordinate) && state.pending.add(coordinate)) {
            Biome[] copy = Arrays.copyOf(originalGrid, originalGrid.length);
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
               try {
                  if (this.packedStorage) {
                     this.savePacked(state, cx, cz, copy, stepXZ, stepY, minY, maxY);
                  } else {
                     this.saveLegacy(state, cx, cz, copy, stepXZ, stepY, minY, maxY);
                  }
               } catch (Throwable t) {
                  this.plugin.getLogger().warning("[BiomeBackup] Error guardando " + worldId + " " + cx + "," + cz + ": " + t.getMessage());
               } finally {
                  state.pending.remove(coordinate);
               }
            });
         }
      }
   }

   public void loadOriginalGridAsync(World world, int cx, int cz, int expectedStepXZ, int expectedStepY, Consumer<Biome[]> callback) {
      UUID worldId = world.getUID();
      int minY = world.getMinHeight();
      int maxY = world.getMaxHeight();
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         Biome[] result = null;

         try {
            result = this.loadOriginalGrid(worldId, cx, cz, expectedStepXZ, expectedStepY, minY, maxY);
         } catch (Throwable t) {
            this.plugin.getLogger().warning("[BiomeBackup] Could not read " + worldId + " " + cx + "," + cz + ": " + t.getMessage());
         }

         Biome[] completed = result;
         if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(completed));
         }
      });
   }

   private Biome[] loadOriginalGrid(UUID worldId, int cx, int cz, int expectedStepXZ, int expectedStepY, int expectedMinY, int expectedMaxY) throws IOException {
      byte[] payload = null;
      BiomeBackupStore.WorldContainer state = this.container(worldId);
      synchronized (state.lock) {
         this.ensureContainerLoaded(state);
         if (state.records.containsKey(coordinateKey(cx, cz))) {
            payload = this.readPackedPayload(state, cx, cz);
         }
      }

      if (payload == null) {
         synchronized (this.legacyOperationLock) {
            synchronized (state.lock) {
               this.ensureContainerLoaded(state);
               if (state.records.containsKey(coordinateKey(cx, cz))) {
                  payload = this.readPackedPayload(state, cx, cz);
               }
            }

            if (payload == null) {
               Path legacy = this.legacyChunkFile(worldId, cx, cz);
               if (Files.isRegularFile(legacy)) {
                  payload = Files.readAllBytes(legacy);
               }
            }
         }
      }

      if (payload == null) {
         return null;
      }

      BiomeBackupStore.BackupData data = this.decodeBackup(payload);
      if (data.stepXZ == expectedStepXZ && data.stepY == expectedStepY && data.minY == expectedMinY && data.maxY == expectedMaxY) {
         Biome[] grid = new Biome[data.indices.length];

         for (int i = 0; i < data.indices.length; i++) {
            int paletteIndex = data.indices[i];
            if (paletteIndex < 0 || paletteIndex >= data.palette.length) {
               throw new IOException("Invalid palette index in biome backup");
            }

            grid[i] = this.safeBiome(data.palette[paletteIndex]);
         }

         return grid;
      } else {
         this.plugin
            .getLogger()
            .warning("[BiomeBackup] Incompatible backup for " + worldId + " " + cx + "," + cz + "; ignoring it to avoid applying a bad grid.");
         return null;
      }
   }

   private void savePacked(BiomeBackupStore.WorldContainer state, int cx, int cz, Biome[] grid, int stepXZ, int stepY, int minY, int maxY) throws IOException {
      long coordinate = coordinateKey(cx, cz);
      synchronized (state.lock) {
         this.ensureContainerLoaded(state);
         if (state.records.containsKey(coordinate)) {
            state.known.add(coordinate);
         } else {
            Path legacy = this.legacyChunkFile(state.worldId, cx, cz);
            boolean cameFromLegacy = Files.isRegularFile(legacy);
            byte[] payload;
            if (cameFromLegacy) {
               payload = Files.readAllBytes(legacy);
               this.decodeBackup(payload);
            } else {
               payload = this.encodeBackup(grid, stepXZ, stepY, minY, maxY);
            }

            this.appendRecord(state, cx, cz, payload);
            if (cameFromLegacy) {
               Files.deleteIfExists(legacy);
               this.deleteDirectoryIfEmpty(legacy.getParent());
            }
         }
      }
   }

   private void saveLegacy(BiomeBackupStore.WorldContainer state, int cx, int cz, Biome[] grid, int stepXZ, int stepY, int minY, int maxY) throws IOException {
      long coordinate = coordinateKey(cx, cz);
      Path file = this.legacyChunkFile(state.worldId, cx, cz);
      synchronized (state.lock) {
         this.ensureContainerLoaded(state);
         if (!state.records.containsKey(coordinate) && !Files.isRegularFile(file)) {
            Files.createDirectories(file.getParent());
            byte[] payload = this.encodeBackup(grid, stepXZ, stepY, minY, maxY);
            Files.write(file, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            state.known.add(coordinate);
         } else {
            state.known.add(coordinate);
         }
      }
   }

   private byte[] encodeBackup(Biome[] grid, int stepXZ, int stepY, int minY, int maxY) throws IOException {
      Map<String, Integer> paletteMap = new LinkedHashMap<>();
      List<String> palette = new ArrayList<>();
      int[] indices = new int[grid.length];

      for (int i = 0; i < grid.length; i++) {
         String name = grid[i].name();
         Integer index = paletteMap.get(name);
         if (index == null) {
            index = palette.size();
            palette.add(name);
            paletteMap.put(name, index);
         }

         indices[i] = index;
      }

      boolean useByte = palette.size() <= 255;
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(256, grid.length + 128));

      try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bytes))) {
         out.writeInt(-1364129008);
         out.writeByte(1);
         out.writeByte(stepXZ);
         out.writeByte(stepY);
         out.writeInt(minY);
         out.writeInt(maxY);
         out.writeInt(palette.size());

         for (String biome : palette) {
            byte[] name = biome.getBytes(StandardCharsets.UTF_8);
            if (name.length > 65535) {
               throw new IOException("Nombre de bioma demasiado largo");
            }

            out.writeShort(name.length);
            out.write(name);
         }

         out.writeInt(indices.length);
         out.writeBoolean(useByte);
         if (useByte) {
            for (int value : indices) {
               out.writeByte(value);
            }
         } else {
            for (int value : indices) {
               out.writeShort(value);
            }
         }
      }

      return bytes.toByteArray();
   }

   private BiomeBackupStore.WorldContainer container(UUID worldId) {
      return this.containers.computeIfAbsent(worldId, id -> new BiomeBackupStore.WorldContainer(id, this.root.resolve(id + ".asb")));
   }

   private void ensureContainerLoaded(BiomeBackupStore.WorldContainer state) throws IOException {
      if (!state.loaded) {
         state.records.clear();
         state.known.clear();
         if (!Files.exists(state.path)) {
            state.loaded = true;
         } else {
            try (FileChannel channel = FileChannel.open(state.path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
               long size = channel.size();
               if (size < 21L) {
                  throw new IOException("Contenedor incompleto: " + state.path);
               }

               ByteBuffer header = ByteBuffer.allocate(21);
               if (!readFully(channel, header, 0L)) {
                  throw new EOFException("Header incompleto");
               }

               header.flip();
               int magic = header.getInt();
               byte version = header.get();
               UUID storedWorld = new UUID(header.getLong(), header.getLong());
               if (magic != -1364128752) {
                  throw new IOException("Bad container magic");
               }

               if (version != 1) {
                  throw new IOException("Bad container version " + version);
               }

               if (!state.worldId.equals(storedWorld)) {
                  throw new IOException("World UUID no coincide");
               }

               long position = 21L;

               while (position < size) {
                  long recordStart = position;
                  if (size - position < 20L) {
                     this.truncateIncompleteTail(channel, state.path, recordStart);
                     break;
                  }

                  ByteBuffer recordHeader = ByteBuffer.allocate(20);
                  if (!readFully(channel, recordHeader, position)) {
                     this.truncateIncompleteTail(channel, state.path, recordStart);
                     break;
                  }

                  recordHeader.flip();
                  int recordMagic = recordHeader.getInt();
                  int cx = recordHeader.getInt();
                  int cz = recordHeader.getInt();
                  int length = recordHeader.getInt();
                  int checksum = recordHeader.getInt();
                  if (recordMagic != -1364082687 || length <= 0 || length > 16777216) {
                     throw new IOException("Registro inválido en offset " + recordStart);
                  }

                  long payloadOffset = position + 20L;
                  long next = payloadOffset + length;
                  if (next > size) {
                     this.truncateIncompleteTail(channel, state.path, recordStart);
                     break;
                  }

                  byte[] payload = readBytes(channel, payloadOffset, length);
                  if (checksum(payload) != checksum) {
                     this.plugin.getLogger().warning("[BiomeBackup] Registro corrupto ignorado en " + state.path.getFileName() + " offset=" + recordStart);
                  } else {
                     long key = coordinateKey(cx, cz);
                     state.records.putIfAbsent(key, new BiomeBackupStore.RecordMeta(cx, cz, recordStart, payloadOffset, length, checksum));
                     state.known.add(key);
                  }

                  position = next;
               }
            }

            state.loaded = true;
         }
      }
   }

   private void appendRecord(BiomeBackupStore.WorldContainer state, int cx, int cz, byte[] payload) throws IOException {
      if (payload.length > 0 && payload.length <= 16777216) {
         this.ensureContainerLoaded(state);
         long key = coordinateKey(cx, cz);
         if (state.records.containsKey(key)) {
            state.known.add(key);
         } else {
            Files.createDirectories(this.root);
            boolean newFile = !Files.exists(state.path);

            try (FileChannel channel = FileChannel.open(state.path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
               if (newFile || channel.size() == 0L) {
                  channel.truncate(0L);
                  this.writeContainerHeader(channel, state.worldId);
               }

               long recordStart = channel.size();
               long payloadOffset = recordStart + 20L;
               int crc = checksum(payload);
               ByteBuffer header = ByteBuffer.allocate(20);
               header.putInt(-1364082687);
               header.putInt(cx);
               header.putInt(cz);
               header.putInt(payload.length);
               header.putInt(crc);
               header.flip();
               writeFully(channel, header, recordStart);
               writeFully(channel, ByteBuffer.wrap(payload), payloadOffset);
               BiomeBackupStore.RecordMeta meta = new BiomeBackupStore.RecordMeta(cx, cz, recordStart, payloadOffset, payload.length, crc);
               state.records.put(key, meta);
               state.known.add(key);
            } catch (IOException | RuntimeException failure) {
               state.loaded = false;
               state.records.clear();
               state.known.clear();
               throw failure;
            }
         }
      } else {
         throw new IOException("Payload fuera de rango: " + payload.length);
      }
   }

   private void writeContainerHeader(FileChannel channel, UUID worldId) throws IOException {
      ByteBuffer header = ByteBuffer.allocate(21);
      header.putInt(-1364128752);
      header.put((byte)1);
      header.putLong(worldId.getMostSignificantBits());
      header.putLong(worldId.getLeastSignificantBits());
      header.flip();
      writeFully(channel, header, 0L);
   }

   private byte[] readPackedPayload(BiomeBackupStore.WorldContainer state, int cx, int cz) throws IOException {
      synchronized (state.lock) {
         this.ensureContainerLoaded(state);
         BiomeBackupStore.RecordMeta meta = state.records.get(coordinateKey(cx, cz));
         if (meta == null) {
            throw new IOException("Registro ya no existe");
         }

         try (FileChannel channel = FileChannel.open(state.path, StandardOpenOption.READ)) {
            byte[] payload = readBytes(channel, meta.payloadOffset, meta.length);
            if (checksum(payload) != meta.checksum) {
               throw new IOException("Checksum inválido");
            } else {
               return payload;
            }
         }
      }
   }

   private void truncateIncompleteTail(FileChannel channel, Path path, long position) throws IOException {
      channel.truncate(position);
      this.plugin.getLogger().warning("[BiomeBackup] Se descartó una escritura incompleta al final de " + path.getFileName());
   }

   private void migrateLegacyBackups() {
      synchronized (this.legacyOperationLock) {
         if (!this.restoreInProgress.get()) {
            List<Path> legacyFiles = this.findLegacyFiles();
            if (!legacyFiles.isEmpty()) {
               int migrated = 0;
               int alreadyPacked = 0;
               int failed = 0;

               for (Path file : legacyFiles) {
                  BiomeBackupStore.LegacyRef ref = this.parseLegacyFile(file);
                  if (ref == null) {
                     failed++;
                  } else {
                     BiomeBackupStore.WorldContainer state = this.container(ref.worldId);

                     try {
                        byte[] payload = Files.readAllBytes(file);
                        this.decodeBackup(payload);
                        synchronized (state.lock) {
                           this.ensureContainerLoaded(state);
                           long key = coordinateKey(ref.cx, ref.cz);
                           if (!state.records.containsKey(key)) {
                              this.appendRecord(state, ref.cx, ref.cz, payload);
                              migrated++;
                           } else {
                              alreadyPacked++;
                           }

                           Files.deleteIfExists(file);
                           this.deleteDirectoryIfEmpty(file.getParent());
                        }
                     } catch (Throwable t) {
                        failed++;
                        this.plugin.getLogger().warning("[BiomeBackup] No se pudo migrar " + file + ": " + t.getMessage());
                     }
                  }
               }

               this.plugin
                  .getLogger()
                  .info("[BiomeBackup] Migración a contenedores terminada. migrados=" + migrated + ", duplicados=" + alreadyPacked + ", fallidos=" + failed);
            }
         }
      }
   }

   public void startRestoreAll(CommandSender sender, int budgetChunksPerTick) {
      if (!this.restoreInProgress.compareAndSet(false, true)) {
         sender.sendMessage("§e[BiomeBackup] Ya hay una restauración en curso.");
      } else {
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
               List<BiomeBackupStore.RestoreEntry> entries;
               synchronized (this.legacyOperationLock) {
                  entries = this.collectRestoreEntries();
               }

               Bukkit.getScheduler().runTask(this.plugin, () -> {
                  if (entries.isEmpty()) {
                     this.restoreInProgress.set(false);
                     sender.sendMessage("§e[BiomeBackup] No hay backups para restaurar.");
                  } else {
                     sender.sendMessage("§a[BiomeBackup] Restaurando " + entries.size() + " chunks... budget=" + Math.max(1, budgetChunksPerTick) + "/tick");
                     new BiomeBackupStore.RestoreTask(entries.iterator(), sender, Math.max(1, budgetChunksPerTick)).runTaskTimer(this.plugin, 1L, 1L);
                  }
               });
            } catch (Throwable t) {
               this.restoreInProgress.set(false);
               this.plugin.getLogger().warning("[BiomeBackup] Error preparando restore: " + t.getMessage());
               Bukkit.getScheduler().runTask(this.plugin, () -> sender.sendMessage("§c[BiomeBackup] No se pudo preparar la restauración."));
            }
         });
      }
   }

   private List<BiomeBackupStore.RestoreEntry> collectRestoreEntries() {
      LinkedHashMap<BiomeBackupStore.ChunkRef, BiomeBackupStore.RestoreEntry> unique = new LinkedHashMap<>();

      for (UUID worldId : this.findPackedWorlds()) {
         BiomeBackupStore.WorldContainer state = this.container(worldId);

         try {
            synchronized (state.lock) {
               this.ensureContainerLoaded(state);
               List<BiomeBackupStore.RecordMeta> records = new ArrayList<>(state.records.values());
               records.sort(Comparator.comparingLong(metax -> metax.recordStart));

               for (BiomeBackupStore.RecordMeta meta : records) {
                  BiomeBackupStore.ChunkRef ref = new BiomeBackupStore.ChunkRef(worldId, meta.cx, meta.cz);
                  unique.putIfAbsent(ref, BiomeBackupStore.RestoreEntry.packed(ref));
               }
            }
         } catch (Throwable t) {
            this.plugin.getLogger().warning("[BiomeBackup] No se pudo leer " + state.path + ": " + t.getMessage());
         }
      }

      for (Path file : this.findLegacyFiles()) {
         BiomeBackupStore.LegacyRef legacy = this.parseLegacyFile(file);
         if (legacy != null) {
            BiomeBackupStore.ChunkRef ref = new BiomeBackupStore.ChunkRef(legacy.worldId, legacy.cx, legacy.cz);
            BiomeBackupStore.RestoreEntry existing = unique.get(ref);
            if (existing == null) {
               unique.put(ref, BiomeBackupStore.RestoreEntry.legacy(ref, file));
            } else {
               existing.legacyAliases.add(file);
            }
         }
      }

      return new ArrayList<>(unique.values());
   }

   private void finishRestoreAsync(CommandSender sender, int restored, int failed, Map<UUID, Set<Long>> restoredPacked, List<Path> restoredLegacy) {
      Bukkit.getScheduler()
         .runTaskAsynchronously(
            this.plugin,
            () -> {
               int cleanupFailures = 0;

               for (Path file : restoredLegacy) {
                  try {
                     Files.deleteIfExists(file);
                     this.deleteDirectoryIfEmpty(file.getParent());
                  } catch (IOException e) {
                     cleanupFailures++;
                     this.plugin.getLogger().warning("[BiomeBackup] No se pudo borrar " + file + ": " + e.getMessage());
                  }
               }

               for (Entry<UUID, Set<Long>> entry : restoredPacked.entrySet()) {
                  try {
                     this.compactContainer(this.container(entry.getKey()), entry.getValue());
                  } catch (Throwable t) {
                     cleanupFailures++;
                     this.plugin.getLogger().warning("[BiomeBackup] No se pudo compactar " + entry.getKey() + ": " + t.getMessage());
                  }
               }

               int finalCleanupFailures = cleanupFailures;
               Bukkit.getScheduler()
                  .runTask(
                     this.plugin,
                     () -> {
                        this.restoreInProgress.set(false);
                        sender.sendMessage(
                           "§a[BiomeBackup] Restore terminado. OK="
                              + restored
                              + ", FAIL="
                              + failed
                              + (finalCleanupFailures == 0 ? "" : ", CLEANUP_FAIL=" + finalCleanupFailures)
                        );
                     }
                  );
            }
         );
   }

   private void compactContainer(BiomeBackupStore.WorldContainer state, Set<Long> removed) throws IOException {
      synchronized (state.lock) {
         this.ensureContainerLoaded(state);
         List<BiomeBackupStore.RecordMeta> kept = new ArrayList<>();

         for (Entry<Long, BiomeBackupStore.RecordMeta> entry : state.records.entrySet()) {
            if (!removed.contains(entry.getKey())) {
               kept.add(entry.getValue());
            }
         }

         kept.sort(Comparator.comparingLong(metax -> metax.recordStart));
         if (kept.size() != state.records.size()) {
            if (kept.isEmpty()) {
               Files.deleteIfExists(state.path);
               state.records.clear();
               state.known.removeAll(removed);
               state.loaded = true;
            } else {
               Path temp = state.path.resolveSibling(state.path.getFileName() + ".tmp");

               try (
                  FileChannel source = FileChannel.open(state.path, StandardOpenOption.READ);
                  FileChannel target = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
               ) {
                  this.writeContainerHeader(target, state.worldId);

                  for (BiomeBackupStore.RecordMeta meta : kept) {
                     byte[] payload = readBytes(source, meta.payloadOffset, meta.length);
                     this.writeRecordAtEnd(target, meta.cx, meta.cz, payload);
                  }

                  target.force(true);
               }

               try {
                  Files.move(temp, state.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
               } catch (AtomicMoveNotSupportedException ignored) {
                  Files.move(temp, state.path, StandardCopyOption.REPLACE_EXISTING);
               }

               state.loaded = false;
               this.ensureContainerLoaded(state);
            }
         }
      }
   }

   private void writeRecordAtEnd(FileChannel channel, int cx, int cz, byte[] payload) throws IOException {
      long start = channel.size();
      int crc = checksum(payload);
      ByteBuffer header = ByteBuffer.allocate(20);
      header.putInt(-1364082687).putInt(cx).putInt(cz).putInt(payload.length).putInt(crc).flip();
      writeFully(channel, header, start);
      writeFully(channel, ByteBuffer.wrap(payload), start + 20L);
   }

   private BiomeBackupStore.BackupData decodeBackup(byte[] payload) throws IOException {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(payload)))) {
         int magic = in.readInt();
         if (magic != -1364129008) {
            throw new IOException("Bad backup magic");
         }

         byte version = in.readByte();
         if (version != 1) {
            throw new IOException("Bad backup version " + version);
         }

         int stepXZ = in.readUnsignedByte();
         int stepY = in.readUnsignedByte();
         int minY = in.readInt();
         int maxY = in.readInt();
         if (stepXZ <= 0 || stepY <= 0 || maxY <= minY) {
            throw new IOException("Rangos inválidos");
         }

         int paletteSize = in.readInt();
         if (paletteSize <= 0 || paletteSize > 65535) {
            throw new IOException("Paleta inválida");
         }

         String[] palette = new String[paletteSize];

         for (int i = 0; i < paletteSize; i++) {
            int length = in.readUnsignedShort();
            byte[] name = new byte[length];
            in.readFully(name);
            palette[i] = new String(name, StandardCharsets.UTF_8);
         }

         int count = in.readInt();
         if (count < 0 || count > 1000000) {
            throw new IOException("Grid inválido: " + count);
         }

         boolean useByte = in.readBoolean();
         int[] indices = new int[count];
         if (useByte) {
            for (int i = 0; i < count; i++) {
               indices[i] = in.readUnsignedByte();
            }
         } else {
            for (int i = 0; i < count; i++) {
               indices[i] = in.readUnsignedShort();
            }
         }

         return new BiomeBackupStore.BackupData(stepXZ, stepY, minY, maxY, palette, indices);
      }
   }

   private void applyBackup(World world, int cx, int cz, BiomeBackupStore.BackupData data) {
      world.getChunkAt(cx, cz);
      int baseX = cx << 4;
      int baseZ = cz << 4;
      int currentMin = world.getMinHeight();
      int currentMax = world.getMaxHeight();
      int index = 0;
      int x = 0;

      while (x < 16) {
         for (int z = 0; z < 16; z += data.stepXZ) {
            for (int y = data.minY; y < data.maxY && index < data.indices.length; y += data.stepY) {
               int paletteIndex = data.indices[index++];
               if (paletteIndex >= 0 && paletteIndex < data.palette.length && y >= currentMin && y < currentMax) {
                  world.setBiome(baseX + x, y, baseZ + z, this.safeBiome(data.palette[paletteIndex]));
               }
            }
         }

         x += data.stepXZ;
      }

      world.refreshChunk(cx, cz);
   }

   private Biome safeBiome(String name) {
      try {
         return Biome.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
         this.plugin.getLogger().warning("[BiomeBackup] Bioma desconocido '" + name + "', usando PLAINS");
         return Biome.PLAINS;
      }
   }

   private Set<UUID> findPackedWorlds() {
      Set<UUID> worlds = new LinkedHashSet<>(this.containers.keySet());
      if (!Files.isDirectory(this.root)) {
         return worlds;
      }

      try (Stream<Path> stream = Files.list(this.root)) {
         stream.filter(x$0 -> Files.isRegularFile(x$0)).map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".asb")).forEach(name -> {
            String raw = name.substring(0, name.length() - ".asb".length());

            try {
               worlds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
               this.plugin.getLogger().warning("[BiomeBackup] Contenedor con nombre inválido: " + name);
            }
         });
      } catch (IOException e) {
         this.plugin.getLogger().warning("[BiomeBackup] Error buscando contenedores: " + e.getMessage());
      }

      return worlds;
   }

   private List<Path> findLegacyFiles() {
      List<Path> files = new ArrayList<>();
      if (!Files.isDirectory(this.root)) {
         return files;
      }

      try (Stream<Path> stream = Files.walk(this.root)) {
         stream.filter(x$0 -> Files.isRegularFile(x$0)).filter(path -> path.getFileName().toString().endsWith(".bin")).forEach(files::add);
      } catch (IOException e) {
         this.plugin.getLogger().warning("[BiomeBackup] Error leyendo backups legacy: " + e.getMessage());
      }

      return files;
   }

   private BiomeBackupStore.LegacyRef parseLegacyFile(Path file) {
      try {
         Path parent = file.getParent();
         if (parent == null) {
            return null;
         } else {
            UUID worldId = UUID.fromString(parent.getFileName().toString());
            String name = file.getFileName().toString();
            int underscore = name.indexOf(95);
            int extension = name.lastIndexOf(".bin");
            if (underscore > 0 && extension > underscore) {
               int cx = Integer.parseInt(name.substring(0, underscore));
               int cz = Integer.parseInt(name.substring(underscore + 1, extension));
               return new BiomeBackupStore.LegacyRef(worldId, cx, cz);
            } else {
               return null;
            }
         }
      } catch (IllegalArgumentException ignored) {
         this.plugin.getLogger().warning("[BiomeBackup] Nombre legacy inválido: " + file);
         return null;
      }
   }

   private Path legacyChunkFile(UUID worldId, int cx, int cz) {
      return this.root.resolve(worldId.toString()).resolve(cx + "_" + cz + ".bin");
   }

   private void deleteDirectoryIfEmpty(Path directory) {
      if (directory != null && !directory.equals(this.root)) {
         try {
            Files.delete(directory);
         } catch (DirectoryNotEmptyException var3) {
         } catch (IOException e) {
            this.plugin.getLogger().fine("[BiomeBackup] No se pudo limpiar carpeta vacía: " + e.getMessage());
         }
      }
   }

   private static long coordinateKey(int cx, int cz) {
      return (cx & 4294967295L) << 32 | cz & 4294967295L;
   }

   private static int checksum(byte[] payload) {
      CRC32 crc = new CRC32();
      crc.update(payload);
      return (int)crc.getValue();
   }

   private static byte[] readBytes(FileChannel channel, long position, int length) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(length);
      if (!readFully(channel, buffer, position)) {
         throw new EOFException("Payload incompleto");
      } else {
         return buffer.array();
      }
   }

   private static boolean readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
      long offset = position;

      while (buffer.hasRemaining()) {
         int read = channel.read(buffer, offset);
         if (read < 0) {
            return false;
         }

         if (read != 0) {
            offset += read;
         }
      }

      return true;
   }

   private static void writeFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
      long offset = position;

      while (buffer.hasRemaining()) {
         int written = channel.write(buffer, offset);
         if (written <= 0) {
            throw new EOFException("No se pudo completar la escritura");
         }

         offset += written;
      }
   }

   private record BackupData(int stepXZ, int stepY, int minY, int maxY, String[] palette, int[] indices) {
   }

   private record ChunkRef(UUID worldId, int cx, int cz) {
   }

   private record LegacyRef(UUID worldId, int cx, int cz) {
   }

   private record RecordMeta(int cx, int cz, long recordStart, long payloadOffset, int length, int checksum) {
   }

   private static final class RestoreEntry {
      final BiomeBackupStore.ChunkRef ref;
      final boolean packed;
      final Path legacyFile;
      final List<Path> legacyAliases = new ArrayList<>();

      private RestoreEntry(BiomeBackupStore.ChunkRef ref, boolean packed, Path legacyFile) {
         this.ref = ref;
         this.packed = packed;
         this.legacyFile = legacyFile;
      }

      static BiomeBackupStore.RestoreEntry packed(BiomeBackupStore.ChunkRef ref) {
         return new BiomeBackupStore.RestoreEntry(ref, true, null);
      }

      static BiomeBackupStore.RestoreEntry legacy(BiomeBackupStore.ChunkRef ref, Path file) {
         return new BiomeBackupStore.RestoreEntry(ref, false, file);
      }
   }

   private final class RestoreTask extends BukkitRunnable {
      private final Iterator<BiomeBackupStore.RestoreEntry> iterator;
      private final CommandSender sender;
      private final int budget;
      private final Map<UUID, Set<Long>> restoredPacked = new HashMap<>();
      private final List<Path> restoredLegacy = new ArrayList<>();
      private int restored;
      private int failed;

      private RestoreTask(Iterator<BiomeBackupStore.RestoreEntry> iterator, CommandSender sender, int budget) {
         this.iterator = iterator;
         this.sender = sender;
         this.budget = budget;
      }

      public void run() {
         for (int processed = 0; processed < this.budget && this.iterator.hasNext(); processed++) {
            BiomeBackupStore.RestoreEntry entry = this.iterator.next();
            if (this.restoreOne(entry)) {
               this.restored++;
               if (entry.packed) {
                  this.restoredPacked
                     .computeIfAbsent(entry.ref.worldId, ignored -> new HashSet<>())
                     .add(BiomeBackupStore.coordinateKey(entry.ref.cx, entry.ref.cz));
               } else if (entry.legacyFile != null) {
                  this.restoredLegacy.add(entry.legacyFile);
               }

               this.restoredLegacy.addAll(entry.legacyAliases);
            } else {
               this.failed++;
            }
         }

         if (!this.iterator.hasNext()) {
            this.cancel();
            this.sender.sendMessage("§e[BiomeBackup] Biomas aplicados; compactando respaldos...");
            BiomeBackupStore.this.finishRestoreAsync(this.sender, this.restored, this.failed, this.restoredPacked, this.restoredLegacy);
         }
      }

      private boolean restoreOne(BiomeBackupStore.RestoreEntry entry) {
         try {
            World world = Bukkit.getWorld(entry.ref.worldId);
            if (world == null) {
               BiomeBackupStore.this.plugin.getLogger().warning("[BiomeBackup] Mundo no cargado: " + entry.ref.worldId);
               return false;
            }

            byte[] payload;
            if (entry.packed) {
               payload = BiomeBackupStore.this.readPackedPayload(BiomeBackupStore.this.container(entry.ref.worldId), entry.ref.cx, entry.ref.cz);
            } else {
               payload = Files.readAllBytes(entry.legacyFile);
            }

            BiomeBackupStore.BackupData data = BiomeBackupStore.this.decodeBackup(payload);
            BiomeBackupStore.this.applyBackup(world, entry.ref.cx, entry.ref.cz, data);
            return true;
         } catch (Throwable t) {
            BiomeBackupStore.this.plugin
               .getLogger()
               .warning("[BiomeBackup] Restore error " + entry.ref.worldId + " " + entry.ref.cx + "," + entry.ref.cz + ": " + t.getMessage());
            return false;
         }
      }
   }

   private static final class WorldContainer {
      final UUID worldId;
      final Path path;
      final Object lock = new Object();
      final Map<Long, BiomeBackupStore.RecordMeta> records = new HashMap<>();
      final Set<Long> known = ConcurrentHashMap.newKeySet();
      final Set<Long> pending = ConcurrentHashMap.newKeySet();
      boolean loaded;

      WorldContainer(UUID worldId, Path path) {
         this.worldId = worldId;
         this.path = path;
      }
   }
}
