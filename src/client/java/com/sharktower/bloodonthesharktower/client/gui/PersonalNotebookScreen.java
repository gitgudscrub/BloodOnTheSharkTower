package com.sharktower.bloodonthesharktower.client.gui;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.networking.NotebookPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class PersonalNotebookScreen extends Screen {
    private final Screen parent;
    private MultiLineEditBox editor;
    private long generation;
    private String sent;
    private int ticks;
    public PersonalNotebookScreen(Screen parent) { super(Component.literal("My Notebook")); this.parent=parent; generation=ClientState.notebookGeneration; sent=ClientState.notebookText; }
    protected void init() {
        String text=editor==null ? sent : editor.getValue();
        int w=Math.min(420,this.width-24);
        editor=MultiLineEditBox.builder().setX((this.width-w)/2).setY(34)
            .setPlaceholder(Component.literal("Your private game notes…")).build(this.font,w,Math.max(30,this.height-76),Component.literal("Private notes"));
        editor.setCharacterLimit(NotebookPayload.LIMIT); editor.setValue(text);
        this.addRenderableWidget(editor); this.setInitialFocus(editor);
        this.addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(this.width/2-45,this.height-30,90,20).build());
    }
    public void tick() {
        super.tick();
        if (generation!=ClientState.notebookGeneration) { generation=ClientState.notebookGeneration; sent=ClientState.notebookText; editor.setValue(sent); }
        if (++ticks%20==0) save();
    }
    private void save() {
        if (editor==null || generation!=ClientState.notebookGeneration) return;
        String text=editor.getValue(); if (text.equals(sent)) return;
        sent=text; ClientState.notebookText=text; ClientPlayNetworking.send(new NotebookPayload(generation,text));
    }
    public void removed() { save(); super.removed(); }
    public void onClose() { save(); this.minecraft.gui.setScreen(parent); }
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);
        String title="My Notebook — only you can read this";
        g.text(this.font,title,(this.width-this.font.width(title))/2,15,UiDrawing.GOLD,true);
    }
}
