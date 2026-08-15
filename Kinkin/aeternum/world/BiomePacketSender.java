package Kinkin.aeternum.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class BiomePacketSender {
   private final JavaPlugin plugin;
   private final Map<Class<?>, Method> worldHandleMethods = new HashMap<>();
   private final Map<Class<?>, Method> levelChunkMethods = new HashMap<>();
   private final Map<Class<?>, Method> playerHandleMethods = new HashMap<>();
   private final Map<Class<?>, Field> connectionFields = new HashMap<>();
   private final Map<Class<?>, Method> sendMethods = new HashMap<>();
   private boolean resolved;
   private boolean supported;
   private boolean warned;
   private Class<?> levelChunkClass;
   private Class<?> packetClass;
   private Method packetFactory;

   BiomePacketSender(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   boolean isSupported() {
      this.resolvePacketTypes();
      return this.supported;
   }

   boolean send(World world, List<Chunk> chunks) {
      if (chunks.isEmpty()) {
         return true;
      }

      if (!this.isSupported()) {
         return false;
      }

      try {
         Object level = this.worldHandle(world).invoke(world);
         Method getChunk = this.levelChunkMethod(level.getClass());
         List<Object> handles = new ArrayList<>(chunks.size());

         for (Chunk chunk : chunks) {
            handles.add(getChunk.invoke(level, chunk.getX(), chunk.getZ()));
         }

         int view = Bukkit.getViewDistance() + 1;
         Map<Player, List<Object>> chunksByViewer = new LinkedHashMap<>();

         for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            int playerChunkX = location.getBlockX() >> 4;
            int playerChunkZ = location.getBlockZ() >> 4;
            List<Object> visible = null;

            for (int i = 0; i < chunks.size(); i++) {
               Chunk chunk = chunks.get(i);
               if (Math.abs(playerChunkX - chunk.getX()) <= view && Math.abs(playerChunkZ - chunk.getZ()) <= view) {
                  if (visible == null) {
                     visible = new ArrayList<>();
                  }

                  visible.add(handles.get(i));
               }
            }

            if (visible != null && !visible.isEmpty()) {
               chunksByViewer.put(player, visible);
            }
         }

         for (Entry<Player, List<Object>> entry : chunksByViewer.entrySet()) {
            Object packet = this.packetFactory.invoke(null, entry.getValue());
            this.sendPacket(entry.getKey(), packet);
         }

         return true;
      } catch (Throwable error) {
         this.disable(error);
         return false;
      }
   }

   private synchronized void resolvePacketTypes() {
      if (!this.resolved) {
         this.resolved = true;

         try {
            this.levelChunkClass = Class.forName("net.minecraft.world.level.chunk.LevelChunk");
            this.packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket");

            for (Method method : this.packetClass.getDeclaredMethods()) {
               if (Modifier.isStatic(method.getModifiers())
                  && method.getParameterCount() == 1
                  && List.class.isAssignableFrom(method.getParameterTypes()[0])
                  && this.packetClass.isAssignableFrom(method.getReturnType())) {
                  method.trySetAccessible();
                  this.packetFactory = method;
                  break;
               }
            }

            if (this.packetFactory == null) {
               throw new NoSuchMethodException("ClientboundChunksBiomesPacket.forChunks(List)");
            }

            this.supported = true;
         } catch (Throwable error) {
            this.disable(error);
         }
      }
   }

   private Method worldHandle(World world) throws NoSuchMethodException {
      Method cached = this.worldHandleMethods.get(world.getClass());
      if (cached != null) {
         return cached;
      }

      Method method = world.getClass().getMethod("getHandle");
      method.trySetAccessible();
      this.worldHandleMethods.put(world.getClass(), method);
      return method;
   }

   private Method levelChunkMethod(Class<?> levelClass) throws NoSuchMethodException {
      Method cached = this.levelChunkMethods.get(levelClass);
      if (cached != null) {
         return cached;
      }

      for (Method method : levelClass.getMethods()) {
         Class<?>[] parameters = method.getParameterTypes();
         if (parameters.length == 2
            && parameters[0] == int.class
            && parameters[1] == int.class
            && this.levelChunkClass.isAssignableFrom(method.getReturnType())) {
            method.trySetAccessible();
            this.levelChunkMethods.put(levelClass, method);
            return method;
         }
      }

      throw new NoSuchMethodException(levelClass.getName() + ".getChunk(int,int)");
   }

   private void sendPacket(Player player, Object packet) throws ReflectiveOperationException {
      Method getHandle = this.playerHandleMethods.get(player.getClass());
      if (getHandle == null) {
         getHandle = player.getClass().getMethod("getHandle");
         getHandle.trySetAccessible();
         this.playerHandleMethods.put(player.getClass(), getHandle);
      }

      Object handle = getHandle.invoke(player);
      Field connectionField = this.connectionFields.get(handle.getClass());
      if (connectionField == null) {
         connectionField = this.findConnectionField(handle.getClass());
         this.connectionFields.put(handle.getClass(), connectionField);
      }

      Object connection = connectionField.get(handle);
      Method send = this.sendMethods.get(connection.getClass());
      if (send == null) {
         send = this.findSendMethod(connection.getClass());
         this.sendMethods.put(connection.getClass(), send);
      }

      send.invoke(connection, packet);
   }

   private Field findConnectionField(Class<?> owner) throws NoSuchFieldException {
      for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
         for (Field field : current.getDeclaredFields()) {
            if (field.getType().getName().endsWith("ServerGamePacketListenerImpl")) {
               field.trySetAccessible();
               return field;
            }
         }
      }

      throw new NoSuchFieldException(owner.getName() + ".connection");
   }

   private Method findSendMethod(Class<?> connectionClass) throws NoSuchMethodException {
      Method fallback = null;

      for (Method method : connectionClass.getMethods()) {
         if (method.getParameterCount() == 1 && method.getReturnType() == void.class && method.getParameterTypes()[0].isAssignableFrom(this.packetClass)) {
            if (method.getName().equals("send")) {
               method.trySetAccessible();
               return method;
            }

            fallback = method;
         }
      }

      if (fallback != null) {
         fallback.trySetAccessible();
         return fallback;
      } else {
         throw new NoSuchMethodException(connectionClass.getName() + ".send(Packet)");
      }
   }

   private synchronized void disable(Throwable error) {
      this.supported = false;
      this.resolved = true;
      if (!this.warned) {
         this.warned = true;
         this.plugin
            .getLogger()
            .warning(
               "[BiomeSpoof] No se pudo usar el paquete ligero de biomas; se usará refreshChunk limitado. Causa: "
                  + error.getClass().getSimpleName()
                  + ": "
                  + error.getMessage()
            );
      }
   }
}
