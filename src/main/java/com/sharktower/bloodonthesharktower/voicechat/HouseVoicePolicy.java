package com.sharktower.bloodonthesharktower.voicechat;
import java.util.*;
/** Phase-independent physical house choice and audio isolation. */
public final class HouseVoicePolicy {
    private HouseVoicePolicy() {}
    public static Integer houseFor(Integer ownSeat, boolean storyteller, Map<Integer,Double> distances, double radius) {
        if (radius < 0 || !Double.isFinite(radius)) return null;
        double limit=radius*radius;
        if (!storyteller) {
            Double distance=ownSeat==null ? null : distances.get(ownSeat);
            return distance!=null && distance>=0 && distance<=limit ? ownSeat : null;
        }
        return distances.entrySet().stream().filter(e->e.getValue()!=null && e.getValue()>=0 && e.getValue()<=limit)
                .min(Comparator.<Map.Entry<Integer,Double>>comparingDouble(Map.Entry::getValue).thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(null);
    }
    public static boolean sameRoom(Integer senderHouse,Integer receiverHouse) {
        return Objects.equals(senderHouse,receiverHouse);
    }
}
