package com.sharktower.bloodonthesharktower.voicechat;
import java.util.*;
/** Seat adjacency wraps around gaps; dead players still occupy their seat. */
public final class VoicePolicy {
    private VoicePolicy() {}
    public static boolean neighbours(UUID a, UUID b, Map<UUID,Integer> seats) {
        if (a==null || b==null || a.equals(b)) return false;
        var order=seats.entrySet().stream().filter(e->e.getValue()!=null).sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
        int i=order.indexOf(a), j=order.indexOf(b), n=order.size();
        return n>1 && i>=0 && j>=0 && (Math.floorMod(i-j,n)==1 || Math.floorMod(j-i,n)==1);
    }
}
