package com.sharktower.bloodonthesharktower.networking;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import java.nio.file.*;
/** No item can be dropped, traded, inspected, or targeted to retrieve somebody else's notes. */
public final class PlayerNotebooks {
    private static final Map<UUID,String> NOTES = new HashMap<>();
    private static Path path;
    private PlayerNotebooks() {}
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NOTES.clear(); path=server.getWorldPath(LevelResource.ROOT).resolve("data/sharktower-notebooks.json");
            try {
                if (Files.exists(path)) {
                    Map<String,String> read=new Gson().fromJson(Files.readString(path),new TypeToken<Map<String,String>>(){}.getType());
                    if (read!=null) read.forEach((id,text)->{ try { if (text!=null && text.length()<=NotebookPayload.LIMIT) NOTES.put(UUID.fromString(id),text); } catch (IllegalArgumentException ignored) {} });
                }
            } catch (Exception e) { BloodOnTheSharktower.LOGGER.warn("Could not load private notebooks",e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ save(); NOTES.clear(); path=null; });
        ServerPlayNetworking.registerGlobalReceiver(NotebookPayload.TYPE,(payload,context)->{
            if (payload.generation()!=ServerState.resetGeneration) { send(context.player()); return; }
            NOTES.put(context.player().getUUID(),payload.text()); save();
        });
    }
    public static void send(ServerPlayer player) { ServerPlayNetworking.send(player,new NotebookPayload(ServerState.resetGeneration,NOTES.getOrDefault(player.getUUID(),""))); }
    public static void clear() { NOTES.clear(); save(); }
    private static void save() {
        if (path==null) return;
        try {
            Files.createDirectories(path.getParent());
            Path temp=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(temp,new Gson().toJson(NOTES));
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { BloodOnTheSharktower.LOGGER.warn("Could not save private notebooks",e); }
    }
}
