package com.sharktower.bloodonthesharktower.voicechat;
import com.sharktower.bloodonthesharktower.states.ServerState;
import java.util.*;
/** Exercises actual route/HUD/privacy state without bootstrapping a client. */
public final class HouseVoiceRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> owner,String name) throws Exception {
        var f=owner.getDeclaredField(name);f.setAccessible(true);return (T)f.get(null);
    }
    public static void main(String[] args) throws Exception {
        var near=Map.of(1,4.0,2,1.0);
        check(HouseVoicePolicy.houseFor(1,false,near,8)==1,"Player enters own room even when another centre is closer");
        check(HouseVoicePolicy.houseFor(1,false,Map.of(1,65.0,2,1.0),8)==null,"Leaving own house returns player outside");
        check(HouseVoicePolicy.houseFor(1,false,Map.of(1,64.0),8)==1,"House boundary is included");
        check(HouseVoicePolicy.houseFor(1,false,Map.of(1,64.1),8)==null,"Crossing boundary exits room");
        check(HouseVoicePolicy.houseFor(null,false,near,8)==null,"Unseated spectator joins no house");
        check(HouseVoicePolicy.houseFor(3,false,near,8)==null,"Unconfigured own home cannot borrow another house");
        check(HouseVoicePolicy.houseFor(null,true,near,8)==2,"ST joins nearest visited house");
        check(HouseVoicePolicy.houseFor(1,true,near,8)==2,"Seated ST can visit other houses");
        check(HouseVoicePolicy.houseFor(null,true,Map.of(1,1.0,2,1.0),8)==1,"Overlapping equidistant homes resolve consistently");
        check(HouseVoicePolicy.houseFor(null,true,Map.of(1,65.0),8)==null,"ST leaving all houses returns outside");
        check(HouseVoicePolicy.houseFor(null,true,Map.of(),8)==null,"Missing homes leave ST outside");
        check(HouseVoicePolicy.houseFor(1,false,Map.of(1,Double.NaN),8)==null,"Invalid distance does not enter house");
        check(HouseVoicePolicy.houseFor(1,false,near,-1)==null,"Invalid radius enters no house");
        check(HouseVoicePolicy.sameRoom(null,null),"Outside participants may hear each other through normal routing");
        check(!HouseVoicePolicy.sameRoom(1,null) && !HouseVoicePolicy.sameRoom(null,1),"House walls isolate audio in both directions");
        check(!HouseVoicePolicy.sameRoom(1,2),"Different houses never share audio");
        check(HouseVoicePolicy.sameRoom(1,1),"ST and owner in same house can speak");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),st=UUID.randomUUID(),out=UUID.randomUUID(),out2=UUID.randomUUID();
        NightChatManager.onVoiceServerStopped();
        Map<UUID,Integer> houses=field(NightChatManager.class,"HOUSE_ROOMS");
        houses.put(a,1);houses.put(b,2);houses.put(st,1);
        for (int[] phase:List.of(new int[]{0,0},new int[]{1,0},new int[]{1,1},new int[]{2,1})) {
            ServerState.currentNight=phase[0];ServerState.currentDay=phase[1];
            NightChatManager.syncToGamePhase();
            check(NightChatManager.routeCode(a).equals("HOUSE:1"),"House HUD persists in every phase");
            check(NightChatManager.sameNightRoom(a,st),"ST hears visited house in every phase");
            check(!NightChatManager.sameNightRoom(a,b),"Different houses isolated in every phase");
            check(!NightChatManager.sameNightRoom(a,out) && !NightChatManager.sameNightRoom(out,a),"Outside audio cannot cross house walls in any phase");
            check(NightChatManager.sameNightRoom(out,out2),"Outside speech stays on normal route in every phase");
            check(NightChatManager.routeCode(out).equals("PROXIMITY"),"No shared Night route is emitted");
        }
        ServerState.currentNight=ServerState.currentDay=2;
        NightChatManager.stopForDawn();
        check(houses.size()==3 && NightChatManager.isHouseRouted(a),"Dawn preserves occupied house rooms");
        check(NightChatManager.shouldCancelSharedNightAudio(a,b),"Compatibility packet filter still isolates houses during Day");
        NightChatManager.onVoicePlayerConnected(a);
        check(houses.get(a)==1,"Voice reconnect with unavailable API retains private house intent");
        Set<UUID> day=field(DayChatZoneManager.class,"DAY_ROUTED");
        Map<UUID,String> zones=field(DayChatZoneManager.class,"PLAYER_ZONE");
        day.add(a);zones.put(a,"garden");DayChatZoneManager.yieldToHouse(a);
        check(!day.contains(a) && !zones.containsKey(a),"House ownership removes old daytime zone membership");
        check(NightChatManager.routeCode(a).equals("HOUSE:1"),"House route takes precedence over day route");
        houses.remove(a);day.add(a);
        check(NightChatManager.routeCode(a).equals("DAY_SHARED"),"Leaving house restores existing shared daytime HUD route");
        day.clear();
        check(NightChatManager.routeCode(a).equals("PROXIMITY"),"Leaving house at Night uses proximity");
        check(NightChatManager.sameNightRoom(a,out),"Exit immediately restores outside-room audio compatibility");
        NightChatManager.onMinecraftPlayerDisconnected(b);
        check(!houses.containsKey(b),"Minecraft disconnect clears house membership");
        // Exercise real SVC assignment/reconnect/reset code with a small API
        // double; no Minecraft client or audio hardware is required.
        var voice=new FakeVoice(List.of(a,b,st));
        VoicechatIntegrationState.onServerStarted(voice.api);
        ServerState.PLAYER_SEAT_NUMBERS.put(a,1);ServerState.PLAYER_SEAT_NUMBERS.put(b,2);
        houses.put(a,1);houses.put(b,2);houses.put(st,1);
        var assign=NightChatManager.class.getDeclaredMethod("assignCurrentPrivateRoute",de.maxhenkel.voicechat.api.VoicechatServerApi.class,UUID.class);
        assign.setAccessible(true);
        for(UUID id:List.of(a,b,st)) assign.invoke(null,voice.api,id);
        check(voice.assigned.get(a).getId().equals(voice.assigned.get(st).getId()),"ST actually joins owner's SVC house group");
        check(!voice.assigned.get(a).getId().equals(voice.assigned.get(b).getId()),"Different houses receive different SVC groups");
        NightChatManager.start();
        check(voice.groups.values().stream().noneMatch(g->g.getName().equals("BOTS Night Chat")),"Legacy start never constructs a shared Night group");
        var houseGroup=voice.assigned.get(a);
        NightChatManager.stopForDawn();
        check(voice.assigned.get(a)==houseGroup && houses.get(a)==1,"Dawn does not detach a house SVC connection");
        NightChatManager.onVoicePlayerConnected(a);
        check(!voice.assigned.get(a).getId().equals(houseGroup.getId()) && voice.assigned.get(st)==houseGroup,"Reconnect isolates player until their location is checked");
        houses.put(a,2);assign.invoke(null,voice.api,a);
        check(voice.assigned.get(a).getId().equals(voice.assigned.get(b).getId()),"Reconciliation uses current house rather than stale reconnect room");
        NightChatManager.resetAll();
        check(voice.assigned.values().stream().allMatch(Objects::isNull),"Reset releases actual SVC house connections");
        VoicechatIntegrationState.onServerStopped();ServerState.PLAYER_SEAT_NUMBERS.clear();
        check(houses.isEmpty() && !NightChatManager.isHouseRouted(st),"Full reset clears house rooms in daytime too");
        check(NightChatManager.routeCode(st).equals("PROXIMITY"),"Reset clears old house HUD route");
        ServerState.currentNight=ServerState.currentDay=0;
        System.out.println("PASS: "+checks+" physical house routing and phase/privacy checks");
    }
    private static final class FakeVoice {
        final Map<UUID,de.maxhenkel.voicechat.api.Group> groups=new HashMap<>(),assigned=new HashMap<>();
        final Map<UUID,de.maxhenkel.voicechat.api.VoicechatConnection> connections=new HashMap<>();
        final de.maxhenkel.voicechat.api.VoicechatServerApi api;
        FakeVoice(List<UUID> ids) {
            for(UUID id:ids) connections.put(id,(de.maxhenkel.voicechat.api.VoicechatConnection)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),new Class<?>[]{de.maxhenkel.voicechat.api.VoicechatConnection.class},(proxy,method,args)->{
                        return switch(method.getName()) {
                            case "isConnected", "isInstalled" -> true;
                            case "getGroup" -> assigned.get(id);
                            case "setGroup" -> {assigned.put(id,(de.maxhenkel.voicechat.api.Group)args[0]);yield null;}
                            default -> null;
                        };
                    }));
            api=(de.maxhenkel.voicechat.api.VoicechatServerApi)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{de.maxhenkel.voicechat.api.VoicechatServerApi.class},(proxy,method,args)->{
                        return switch(method.getName()) {
                            case "getConnectionOf" -> connections.get((UUID)args[0]);
                            case "getGroups" -> new ArrayList<>(groups.values());
                            case "removeGroup" -> {groups.remove((UUID)args[0]);yield method.getReturnType()==boolean.class ? true : null;}
                            case "groupBuilder" -> builder(method.getReturnType());
                            default -> null;
                        };
                    });
        }
        private Object builder(Class<?> type) {
            var values=new HashMap<String,Object>();
            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
                if (method.getName().equals("build")) {
                    var group=(de.maxhenkel.voicechat.api.Group)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{de.maxhenkel.voicechat.api.Group.class},(g,m,a)->switch(m.getName()) {
                                case "getId" -> values.get("setId");
                                case "getName" -> values.get("setName");
                                default -> null;
                            });
                    groups.put(group.getId(),group);return group;
                }
                if(args!=null && args.length==1)values.put(method.getName(),args[0]);
                return method.getReturnType()==void.class ? null : proxy;
            });
        }
    }

}
