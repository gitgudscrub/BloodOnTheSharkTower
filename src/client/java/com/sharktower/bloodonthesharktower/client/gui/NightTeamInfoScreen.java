package com.sharktower.bloodonthesharktower.client.gui;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class NightTeamInfoScreen extends Screen {
    private final boolean demon;
    public NightTeamInfoScreen(boolean demon) { super(Component.literal(demon?"Demon Info":"Minion Info")); this.demon=demon; }
    protected void init() {
        if (this.minecraft.player == null || !ClientState.storytellerPlayers.contains(this.minecraft.player.getUUID())) return;
        int y=48;
        for (var entry:ClientState.grimoireRoles.entrySet().stream().sorted(java.util.Comparator.comparingInt(e->ClientState.grimoireSeatNumbers.getOrDefault(e.getKey(),0))).toList()) {
            if (entry.getValue()==null || entry.getValue().getRoleType()!=(demon?RoleType.DEMON:RoleType.MINION)) continue;
            var id=entry.getKey(); int seat=ClientState.grimoireSeatNumbers.getOrDefault(id,0);
            this.addRenderableWidget(Button.builder(Component.literal("Visit " + ClientState.playerName(id,seat)), b->{ ClientStorytellerActions.send("night_visit",id.toString()); this.onClose(); })
                    .bounds(this.width/2-110,y,220,20).build()); y+=24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(this.width/2-45,this.height-28,90,20).build());
    }
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        super.extractRenderState(g,x,y,delta);
        String title=demon?"Demon Info: show Minions and three bluffs":"Minion Info: show Demon and fellow Minions";
        g.text(this.font,title,(this.width-this.font.width(title))/2,15,UiDrawing.GOLD,true);
        if (ClientState.activePlayerCount < 7) g.text(this.font,"No starting evil-team information below 7 players.",12,30,UiDrawing.MUTED,false);
    }
}
