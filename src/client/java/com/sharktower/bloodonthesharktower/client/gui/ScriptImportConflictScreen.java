package com.sharktower.bloodonthesharktower.client.gui;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ScriptImportConflictScreen extends Screen {
    private final String token,filename;
    public ScriptImportConflictScreen(String token,String filename) { super(Component.literal("Script Already Exists"));this.token=token;this.filename=filename; }
    protected void init() {
        int y=Math.max(70,this.height/2-24);
        add("Replace", "replace",y);add("Save Distinct Version","distinct",y+24);add("Cancel","cancel",y+48);
    }
    private void add(String label,String decision,int y) {
        this.addRenderableWidget(Button.builder(Component.literal(label),b->{ClientStorytellerActions.send("custom_script_confirm",token+"|"+decision);this.minecraft.gui.setScreen(new CustomScriptsScreen());}).bounds(this.width/2-100,y,200,20).build());
    }
    public void onClose() { ClientStorytellerActions.send("custom_script_confirm",token+"|cancel");this.minecraft.gui.setScreen(new CustomScriptsScreen()); }
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        super.extractRenderState(g,x,y,delta);String title="Script Already Exists";
        g.text(this.font,title,(this.width-this.font.width(title))/2,18,UiDrawing.GOLD,true);
        String name=this.font.plainSubstrByWidth(filename,this.width-24);g.text(this.font,name,(this.width-this.font.width(name))/2,38,UiDrawing.TEXT,false);
    }
}
