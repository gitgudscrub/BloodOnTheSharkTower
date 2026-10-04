package com.sharktower.bloodonthesharktower.core;
import java.util.*;
public final class TeamInformationRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static PendingRoleAssignment role(Role role) { return new PendingRoleAssignment(role,AlignmentOverride.DEFAULT); }
    private static void rejects(Runnable action,String message) {
        boolean rejected=false;try {action.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);
    }
    public static void main(String[] args) {
        UUID demon=UUID.randomUUID(),minion=UUID.randomUUID(),other=UUID.randomUUID(),magician=UUID.randomUUID(),marionette=UUID.randomUUID(),good=UUID.randomUUID(),last=UUID.randomUUID();
        Map<UUID,PendingRoleAssignment> roles=new HashMap<>();
        roles.put(demon,role(Role.IMP));roles.put(minion,role(Role.POISONER));roles.put(other,role(Role.BARON));
        roles.put(magician,role(Role.MAGICIAN));roles.put(marionette,role(Role.MARIONETTE));roles.put(good,role(Role.MONK));roles.put(last,role(Role.CHEF));
        // Magician is an interior seat: candidates share the same seat ordering,
        // rather than putting the false candidate last and revealing the trick.
        Map<UUID,Integer> seats=Map.of(demon,5,minion,2,other,6,magician,3,marionette,4,good,1,last,7);
        Map<UUID,Boolean> deaths=new HashMap<>();
        var m=TeamInformation.plan(roles,seats,deaths,false,true,false);
        check(m.recipients().keySet().equals(Set.of(minion,other)),"Only real non-Marionette Minions receive Minion Info");
        check(m.magician(),"Living Magician active");
        check(m.recipients().get(minion).demons().equals(List.of(magician,demon)),"Magician among Demon candidates in seat order");
        check(m.recipients().get(minion).minions().equals(List.of(other)),"Fellow Minions omit self and do not label Magician as a Minion");
        check(!m.recipients().get(minion).minions().contains(marionette),"Other Minions do not learn Marionette");
        check(m.recipients().get(minion).marionettes().isEmpty(),"Minion Info has no Marionette disclosure");
        var d=TeamInformation.plan(roles,seats,deaths,true,true,false);
        check(d.recipients().keySet().equals(Set.of(demon)),"Only actual Demon receives Demon Info");
        check(d.recipients().get(demon).demons().isEmpty(),"Demon learns no other Demons");
        check(d.recipients().get(demon).minions().equals(List.of(minion,magician,other)),"Demon sees Magician as a Minion candidate");
        check(d.recipients().get(demon).marionettes().isEmpty(),"Magician/Marionette jinx conceals Marionette");
        var disabled=TeamInformation.plan(roles,seats,deaths,true,false,false);
        check(!disabled.magician(),"ST can disable drunk/poisoned Magician misinformation");
        check(disabled.recipients().get(demon).minions().equals(List.of(minion,marionette,other)),"Normal Demon sees all Minions");
        check(disabled.recipients().get(demon).marionettes().equals(List.of(marionette)),"Normal Demon learns which Minion is Marionette");
        deaths.put(magician,true);
        check(!TeamInformation.plan(roles,seats,deaths,true,true,false).magician(),"Dead Magician no longer affects newly shared information");
        deaths.clear();roles.put(other,role(Role.VIZIER));
        check(!TeamInformation.plan(roles,seats,deaths,true,true,false).magician(),"Vizier jinx disables Magician");
        roles.put(other,role(Role.BARON));roles.put(last,role(Role.POPPY_GROWER));
        var pg=TeamInformation.plan(roles,seats,deaths,true,true,false);
        check(pg.withheld(),"Poppy Grower withholds identities");
        check(pg.recipients().get(demon).minions().isEmpty() && pg.recipients().get(demon).marionettes().isEmpty(),"No identities leak with Poppy Grower");
        check(TeamInformation.plan(roles,seats,deaths,false,true,false).recipients().get(minion).demons().isEmpty(),"Minion Demon identities withheld too");
        deaths.put(last,true);
        check(TeamInformation.plan(roles,seats,deaths,true,true,false).withheld(),"Poppy Grower death requires ST judgment before sharing");
        var override=TeamInformation.plan(roles,seats,deaths,true,true,true);
        check(!override.withheld() && override.recipients().get(demon).minions().contains(magician),"ST releases info after eligible PG death with Magician still applied");
        roles.put(last,role(Role.LUNATIC));
        check(!TeamInformation.plan(roles,seats,deaths,true,true,false).recipients().containsKey(last),"Lunatic receives no real Demon information");
        roles.put(last,role(Role.LEGION));rejects(()->TeamInformation.plan(roles,seats,deaths,true,true,true),"Legion requires manual special information");
        roles.put(last,role(Role.CHEF));roles.remove(good);
        rejects(()->TeamInformation.plan(roles,seats,deaths,false,true,false),"Small games require explicit ST override");
        check(!TeamInformation.plan(roles,seats,deaths,false,true,true).recipients().isEmpty(),"Small-game ST override available");
        roles.put(good,role(Role.THIEF));rejects(()->TeamInformation.plan(roles,seats,deaths,false,true,false),"Travellers do not count toward seven players");
        roles.put(good,role(Role.MONK));roles.put(minion,new PendingRoleAssignment(Role.POISONER,AlignmentOverride.FORCE_GOOD));
        check(TeamInformation.plan(roles,seats,deaths,false,true,false).recipients().containsKey(minion),"Info follows character type, including alignment overrides");
        var unseated=new HashMap<>(seats);unseated.remove(demon);
        rejects(()->TeamInformation.plan(roles,unseated,deaths,true,true,true),"Unseated Demon cannot receive information");
        roles.put(other,role(Role.MONK));roles.put(minion,role(Role.MONK));
        rejects(()->TeamInformation.plan(roles,seats,deaths,false,true,true),"Marionette alone is not a Minion Info recipient");
        System.out.println("PASS: "+checks+" team information privacy and special-role checks");
    }
}
