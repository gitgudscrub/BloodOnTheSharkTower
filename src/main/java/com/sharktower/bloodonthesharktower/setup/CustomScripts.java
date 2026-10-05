package com.sharktower.bloodonthesharktower.setup;

import com.google.gson.Gson;
import com.sharktower.bloodonthesharktower.networking.*;
import com.sharktower.bloodonthesharktower.states.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Persistent server library. All state changes and conflict decisions run on the server thread. */
public final class CustomScripts {
    private CustomScripts() {}
    private record Pending(String token, String json, String filename, String previous, long expiry) {}
    private static final Map<UUID,Pending> pending = new HashMap<>();
    private static final Set<UUID> downloading = new HashSet<>();
    private static Path folder() throws Exception {
        Path root = FabricLoader.getInstance().getConfigDir().resolve("blood_on_the_sharktower/custom-scripts").toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("Custom-scripts folder must not be a symlink.");
        return root;
    }
    private static Path file(String name) throws Exception {
        if (name == null || name.length() > 120 || !name.endsWith(".json") || name.contains("/") || name.contains("\\") || name.contains(".."))
            throw new IllegalArgumentException("Invalid script filename.");
        Path root = folder(); Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root) || Files.isSymbolicLink(path)) throw new IllegalArgumentException("Invalid script file path.");
        return path;
    }
    private static String read(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > BotcScriptImport.MAX_BYTES)
            throw new IllegalArgumentException("Script file is missing or exceeds 1 MiB.");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(BotcScriptImport.MAX_BYTES + 1);
            if (bytes.length > BotcScriptImport.MAX_BYTES) throw new IllegalArgumentException("Script exceeds 1 MiB.");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private static void send(ServerPlayer actor,String kind,String text,String token) { ServerPlayNetworking.send(actor,new CustomScriptsPayload(kind,text,token)); }
    public static SetupOperations.Result list(ServerPlayer actor) throws Exception {
        try (var files = Files.list(folder())) {
            var names = files.filter(p -> Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString()).filter(n -> n.length() <= 120).sorted(String.CASE_INSENSITIVE_ORDER).limit(1500).toList();
            send(actor,"list",new Gson().toJson(names),"");
        }
        return SetupOperations.Result.ok("Custom scripts refreshed.");
    }
    public static SetupOperations.Result load(ServerPlayer actor,String name) throws Exception {
        String json = read(file(name)); BotcScriptImport.validate(json);
        var result = SetupOperations.loadScriptJson(json);
        if (result.ok()) { ServerPlayNetworking.send(actor,new SendScriptS2CPayload(ServerState.currentScript));send(actor,"loaded","",""); }
        return result;
    }
    public static SetupOperations.Result start(MinecraftServer server,ServerPlayer actor,String link) {
        BotcScriptImport.resolve(link);
        UUID id=actor.getUUID();
        if (!downloading.add(id)) return SetupOperations.Result.fail("An import is already running.");
        pending.remove(id);
        long generation=ServerState.resetGeneration;
        Thread.ofVirtual().name("sharktower-script-import").start(() -> {
            String json=null,error=null;
            try { json=BotcScriptImport.download(link); } catch (Exception e) { error=e instanceof IllegalArgumentException ? e.getMessage() : "BotC Scripts download failed or timed out. Try again later."; }
            final String downloaded=json, failure=error;
            server.execute(() -> {
                downloading.remove(id);
                if (server.getPlayerList().getPlayer(id)!=actor || !StorytellerState.isStoryteller(id) || generation!=ServerState.resetGeneration) return;
                try {
                    if (failure!=null) throw new IllegalArgumentException(failure);
                    var script=BotcScriptImport.validate(downloaded);String name=BotcScriptImport.filename(script.name());Path path=file(name);
                    if (!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
                        try (var candidates=Files.list(folder())) {
                            for (Path candidate:candidates.filter(p -> Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                                try {
                                    var saved=com.sharktower.bloodonthesharktower.core.Script.fromJson(read(file(candidate.getFileName().toString())));
                                    if (saved.isPresent() && saved.get().name().equalsIgnoreCase(script.name())) { path=candidate;name=candidate.getFileName().toString();break; }
                                } catch (Exception ignored) { /* A malformed local file remains visible and reports its error on load. */ }
                            }
                        }
                    }
                    if (Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
                        String token=UUID.randomUUID().toString();pending.put(id,new Pending(token,downloaded,name,read(path),System.currentTimeMillis()+300000));
                        send(actor,"conflict",name,token);
                    } else { Files.writeString(path,downloaded,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);list(actor);send(actor,"status","Saved " + name,""); }
                } catch (Exception e) { String message=e.getMessage()==null ? "Could not save the script." : e.getMessage();send(actor,"status",message,"");actor.sendSystemMessage(Component.literal(message)); }
            });
        });
        return SetupOperations.Result.ok("Downloading script from BotC Scripts…");
    }
    public static SetupOperations.Result confirm(ServerPlayer actor,String argument) throws Exception {
        String[] parts=argument.split("\\|",-1);Pending item=pending.get(actor.getUUID());
        if (parts.length!=2 || item==null || !item.token().equals(parts[0]) || item.expiry()<System.currentTimeMillis()) return SetupOperations.Result.fail("Import expired. Import the script again.");
        if (parts[1].equals("cancel")) { pending.remove(actor.getUUID());return SetupOperations.Result.ok("Import cancelled."); }
        Path path=file(item.filename());
        if (parts[1].equals("replace")) {
            if (!read(path).equals(item.previous())) return SetupOperations.Result.fail("The file changed since the prompt. Import again before replacing it.");
            Path temp=Files.createTempFile(folder(),"script-",".tmp");
            try { Files.writeString(temp,item.json(),StandardCharsets.UTF_8);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temp); }
        } else if (parts[1].equals("distinct")) {
            String stem=item.filename().substring(0,item.filename().length()-5);
            for (int i=2; ;i++) {
                path=file(stem+"-"+i+".json");
                try { Files.writeString(path,item.json(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);break; }
                catch (FileAlreadyExistsException ignored) { if(i>=10000) throw new IllegalArgumentException("Too many saved versions."); }
            }
        } else return SetupOperations.Result.fail("Invalid import decision.");
        pending.remove(actor.getUUID());list(actor);send(actor,"status","Saved " + path.getFileName(),"");
        return SetupOperations.Result.ok("Script saved.");
    }
    public static void clearPending() { pending.clear(); }
}
