package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * One place to choose the game's script: the three base scripts, the
 * server's saved scripts, the Script Builder, or a pasted botcscripts.com
 * link or script JSON (saved to the server's library and loaded at once).
 */
public final class LoadScriptScreen extends Screen {
    /** A Minecraft string packet holds 32767 characters; leave room for the action name. */
    private static final int MAX_PASTE = 32000;

    private static String pasted = "";
    private String error = "";

    public LoadScriptScreen() {
        super(Component.literal("Choose a Script"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        boolean row = this.width >= 3 * 150 + 40;
        int bw = row ? 150 : 220;
        int y = 52;
        String[][] base = {{"Trouble Brewing", "tb"}, {"Bad Moon Rising", "bmr"}, {"Sects & Violets", "snv"}};
        ChatFormatting[] colours = {ChatFormatting.DARK_RED, ChatFormatting.GOLD, ChatFormatting.DARK_PURPLE};
        for (int i = 0; i < 3; i++) {
            String id = base[i][1];
            int x = row ? cx - (3 * bw + 12) / 2 + i * (bw + 6) : cx - bw / 2;
            int by = row ? y : y + i * 24;
            this.addRenderableWidget(SharktowerButton.builder(Component.literal(base[i][0]).withStyle(colours[i]),
                    b -> BaseThreeScreen.loadBase(id)).bounds(x, by, bw, 20).build());
        }
        y += row ? 28 : 76;
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Saved Scripts"),
                        b -> CustomScriptsScreen.openAndRefresh())
                .bounds(cx - 153, y, 150, 20)
                .tooltip(Tooltip.create(Component.literal("Scripts saved on this server, including ones you pasted or imported.")))
                .build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Build Your Own"),
                        b -> this.minecraft.gui.setScreen(new ScriptBuilderScreen()))
                .bounds(cx + 3, y, 150, 20)
                .tooltip(Tooltip.create(Component.literal("Pick characters one by one from every official character.")))
                .build());

        y += 40;
        int boxW = Math.min(360, this.width - 24);
        EditBox box = new EditBox(this.font, cx - boxW / 2, y, boxW, 20, Component.literal("Script link or JSON"));
        box.setMaxLength(1_000_000);
        box.setHint(Component.literal("Paste a botcscripts.com link or script JSON (Ctrl+V)").withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(pasted);
        box.setResponder(value -> {
            pasted = value;
            error = "";
        });
        this.addRenderableWidget(box);
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("Load Pasted Script", ChatFormatting.GOLD),
                b -> loadPasted()).bounds(cx - 75, y + 24, 150, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back to Grimoire"),
                        b -> this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(cx - 90, this.height - 28, 180, 20).build());
    }

    private void loadPasted() {
        String text = pasted.trim();
        if (text.isEmpty()) {
            error = "Paste a link or the script's JSON first.";
        } else if (text.startsWith("http")) {
            ClientStorytellerActions.send("custom_script_import", text);
            pasted = "";
            error = "Downloading… the Grimoire opens when the script is loaded.";
        } else if (!text.startsWith("[") && !text.startsWith("{")) {
            error = "That is not a link or script JSON. Script JSON starts with [ or {.";
        } else if (text.length() > MAX_PASTE) {
            error = "Too long to paste. Put the file in the server's custom-scripts folder instead.";
        } else {
            ClientStorytellerActions.send("custom_script_paste", text);
            pasted = "";
            error = "Loading…";
        }
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        centred(graphics, "Choose a Script", 16, UiDrawing.GOLD, UiTheme.dark());
        String current = ClientState.currentScript == null ? "No script loaded yet" : "Current: " + ClientState.displayScriptName();
        centred(graphics, current, 30, UiDrawing.MUTED, false);
        boolean row = this.width >= 3 * 150 + 40;
        int y = 52 + (row ? 28 : 76) + 40;
        centred(graphics, "Or paste one:", y - 12, UiDrawing.TEXT, false);
        if (!error.isEmpty()) centred(graphics, error, y + 50, UiDrawing.MUTED, false);
    }

    private void centred(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        String fitted = this.font.plainSubstrByWidth(text, this.width - 16);
        graphics.text(this.font, fitted, (this.width - this.font.width(fitted)) / 2, y, colour, shadow);
    }
}
