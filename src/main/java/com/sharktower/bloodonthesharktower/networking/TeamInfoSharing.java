package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.core.*;
import com.sharktower.bloodonthesharktower.setup.LunaticBluffs;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Private, ST-controlled delivery with an exact preview and stale-plan rejection. */
public final class TeamInfoSharing {
    private TeamInfoSharing() {}
    private record State(long generation, Map<UUID, PendingRoleAssignment> roles, Map<UUID,Integer> seats,
                         Map<UUID,Boolean> deaths, List<ScriptRole> bluffs, int night, int day) {}
    private record Preview(String token, State state, Map<UUID,String> messages, long expires) {}
    private static final Map<UUID,Preview> previews = new HashMap<>();
    public static void clear() { previews.clear(); }
    private static State state() {
        return new State(ServerState.resetGeneration, Map.copyOf(ServerState.PLAYER_ROLES),
                Map.copyOf(ServerState.PLAYER_SEAT_NUMBERS), Map.copyOf(ServerState.PLAYER_DEATH_STATUS),
                List.copyOf(StorytellerState.DEMON_BLUFFS), ServerState.currentNight, ServerState.currentDay);
    }
    public static SetupOperations.Result preview(MinecraftServer server, ServerPlayer actor, String argument) {
        if (!StorytellerState.isStoryteller(actor.getUUID())) return SetupOperations.Result.fail("Storyteller only.");
        previews.remove(actor.getUUID());
        String[] options = argument.split("\\|", -1);
        if (options.length != 4 || !(options[0].equals("demon") || options[0].equals("minion"))
                || !(options[1].equals("true") || options[1].equals("false"))
                || !(options[2].equals("true") || options[2].equals("false"))
                || !(options[3].equals("true") || options[3].equals("false")))
            return SetupOperations.Result.fail("Invalid team information options.");
        boolean demon = options[0].equals("demon");
        if (LunaticBluffs.lunaticOnScript()) {
            return SetupOperations.Result.fail(
                    "Lunatic is on the current script. Minion and Demon Info are manual so the Storyteller can control the real and false starting worlds.");
        }
        State state = state();
        var plan = TeamInformation.plan(state.roles(), state.seats(), state.deaths(), demon,
                Boolean.parseBoolean(options[1]), Boolean.parseBoolean(options[2]), Boolean.parseBoolean(options[3]));
        Map<UUID,String> messages = new LinkedHashMap<>();
        String header = demon ? "Demon Info" : "Minion Info";
        StringBuilder text = new StringBuilder("Review the exact private messages below. These use committed roles.\n");
        text.append("Magician misinformation: ").append(plan.magician() ? "active" : "off").append(".\n");
        if (plan.withheld()) text.append("Poppy Grower: identities withheld. Demon still receives bluffs.\n");
        if (Boolean.parseBoolean(options[2])) text.append("Poppy Grower: ST released identities.\n");
        if (Boolean.parseBoolean(options[3])) text.append("Small Game: ST Override.\n");
        text.append("For drunk/poisoned characters or other special rulings, review your choices or use a manual visit.\n\n");
        for (var entry : plan.recipients().entrySet()) {
            String message = "[" + header + "]\n";
            var info = entry.getValue();
            if (info.withheld()) message += "Team identities are withheld.\n";
            else {
                if (!demon) message += "Demon(s): " + names(server, info.demons(), state.seats()) + "\n";
                message += (demon ? "Minion(s): " : "Other Minion(s): ") + names(server, info.minions(), state.seats()) + "\n";
                if (!info.marionettes().isEmpty()) message += "Marionette: " + names(server, info.marionettes(), state.seats()) + "\n";
            }
            if (demon) message += "Bluffs: " + (state.bluffs().isEmpty() ? "Not selected" :
                    String.join(", ", state.bluffs().stream().map(ScriptRole::getDisplayName).toList())) + "\n";
            messages.put(entry.getKey(), message);
            text.append("To ").append(name(server, entry.getKey(),state.seats())).append(":\n").append(message).append("\n");
        }
        if (text.length() > 32767) return SetupOperations.Result.fail("Preview is too large. Use a manual visit.");
        String token = UUID.randomUUID().toString();
        previews.put(actor.getUUID(),new Preview(token,state,Map.copyOf(messages),System.currentTimeMillis()+120000));
        ServerPlayNetworking.send(actor,new TeamInfoPreviewPayload(token,text.toString()));
        return SetupOperations.Result.ok("Review the preview, then Share to deliver it privately.");
    }
    public static SetupOperations.Result share(MinecraftServer server, ServerPlayer actor, String token) {
        if (!StorytellerState.isStoryteller(actor.getUUID())) return SetupOperations.Result.fail("Storyteller only.");
        Preview preview = previews.remove(actor.getUUID());
        if (preview == null || !preview.token().equals(token) || preview.expires() < System.currentTimeMillis()
                || !preview.state().equals(state()))
            return SetupOperations.Result.fail("This preview has expired or the game changed. Preview the information again.");
        // Check every recipient first so a disconnected player cannot cause a partial send.
        for (UUID id : preview.messages().keySet()) {
            if (server.getPlayerList().getPlayer(id) == null)
                return SetupOperations.Result.fail("A recipient is offline. No information was sent; preview again when they reconnect.");
        }
        preview.messages().forEach((id,message) -> server.getPlayerList().getPlayer(id).sendSystemMessage(Component.literal(message)));
        return SetupOperations.Result.ok("Shared private team information with " + preview.messages().size() + " player(s).");
    }

    /**
     * Manual bluff-only delivery for the real Demon world. This deliberately
     * checks the actual committed role, so a Lunatic who merely believes they
     * are a Demon can never receive the real bluff set through this path.
     */
    public static SetupOperations.Result sendDemonBluffs(MinecraftServer server, ServerPlayer actor) {
        if (!StorytellerState.isStoryteller(actor.getUUID())) return SetupOperations.Result.fail("Storyteller only.");
        if (StorytellerState.DEMON_BLUFFS.size() != 3) {
            return SetupOperations.Result.fail("Choose 3 Demon bluffs before sending them.");
        }

        List<ServerPlayer> recipients = new ArrayList<>();
        for (var entry : ServerState.PLAYER_ROLES.entrySet()) {
            PendingRoleAssignment assignment = entry.getValue();
            if (assignment == null || assignment.getRoleType() != RoleType.DEMON) continue;
            if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(entry.getKey())) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return SetupOperations.Result.fail("The Demon is offline. No bluff information was sent.");
            }
            recipients.add(player);
        }
        if (recipients.isEmpty()) return SetupOperations.Result.fail("There is no committed Demon to receive bluffs.");

        String names = String.join(", ", StorytellerState.DEMON_BLUFFS.stream()
                .map(ScriptRole::getDisplayName)
                .toList());
        Component message = Component.literal("[Demon Info]\nBluffs: " + names);
        recipients.forEach(player -> player.sendSystemMessage(message));
        return SetupOperations.Result.ok("Sent the 3 Demon bluffs privately to "
                + recipients.size() + " Demon player(s).");
    }

    private static String names(MinecraftServer server,List<UUID> ids,Map<UUID,Integer> seats) {
        return ids.isEmpty() ? "None" : String.join(", ",ids.stream().map(id -> name(server,id,seats)).toList());
    }
    private static String name(MinecraftServer server,UUID id,Map<UUID,Integer> seats) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return (player == null ? "Offline player" : player.getName().getString()) + " (seat " + seats.getOrDefault(id,0) + ")";
    }
}
