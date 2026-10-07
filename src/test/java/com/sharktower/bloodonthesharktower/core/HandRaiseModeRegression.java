package com.sharktower.bloodonthesharktower.core;
import com.sharktower.bloodonthesharktower.daytime.*;
import java.util.*;
public final class HandRaiseModeRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        for (int[] phase:List.of(new int[]{0,0},new int[]{1,0},new int[]{2,1})) {
            check(HandRaiseMode.determine(phase[0],phase[1],false,null,false,null,false,false)==HandRaiseMode.OFF,"Setup/Night have no speaking HUD");
            check(HandRaiseMode.determine(phase[0],phase[1],true,a,true,b,true,false)==HandRaiseMode.OFF,"Stale election state cannot show hands in Setup/Night");
        }
        check(HandRaiseMode.determine(1,1,false,null,false,null,false,false)==HandRaiseMode.OFF,"Day before nominations hides speaking queue");
        check(HandRaiseMode.determine(1,1,true,null,false,null,false,false)==HandRaiseMode.SPEAKING,"Open nominations without a nominee permits speaking queue");
        check(HandRaiseMode.determine(1,1,true,a,false,null,false,false)==HandRaiseMode.VOTING,"Nomination discussion uses voting hands before the clock starts");
        check(HandRaiseMode.determine(1,1,true,null,true,null,false,false)==HandRaiseMode.VOTING,"Vote in progress blocks speaking queue");
        check(HandRaiseMode.determine(1,1,false,a,true,null,false,false)==HandRaiseMode.OFF,"Closed nominations hide stale voting state");
        check(HandRaiseMode.determine(1,1,false,null,false,a,false,false)==HandRaiseMode.VOTING,"Existing daytime exile call retains voting controls");
        check(HandRaiseMode.determine(1,1,true,null,false,null,true,false)==HandRaiseMode.VOTING,"Exile support uses voting hands");
        check(HandRaiseMode.determine(1,1,true,null,false,null,false,true)==HandRaiseMode.OFF,"End-game presentation hides hands");
        DaytimeState.openNominations(Set.of(a,b),Set.of(),List.of(),List.of());
        AttentionHands.set(a,true);AttentionHands.set(b,true);
        DaytimeState.setMarkedForExecution(a, 3);
        check(HandRaiseMode.determine(1,1,DaytimeState.areNominationsOpen(),DaytimeState.getCurrentNominee(),false,null,false,false)==HandRaiseMode.SPEAKING,"Player marked for execution still permits speaking between nominations");
        DaytimeState.setCurrentNominee(b);DaytimeState.clearRaisedHands();
        check(AttentionHands.positions().get(a)==1 && AttentionHands.positions().get(b)==2,"Voting hand changes preserve independent speaking order");
        DaytimeState.closeNominations();check(AttentionHands.positions().isEmpty(),"Closing nominations clears speaking queue");
        AttentionHands.set(a,true);DaytimeState.resetDaily();check(AttentionHands.positions().isEmpty(),"Dusk/new day clears speaking queue");
        AttentionHands.set(a,true);DaytimeState.openNominations(Set.of(a,b),Set.of(),List.of(),List.of());
        check(AttentionHands.positions().isEmpty(),"New nomination window begins with an empty queue");
        DaytimeState.hardReset(Set.of(),Set.of());
        System.out.println("PASS: "+checks+" nomination-only hand mode and speaking queue lifecycle checks");
    }
}
