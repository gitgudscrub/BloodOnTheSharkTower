package com.sharktower.bloodonthesharktower.client.gui;

import com.google.gson.Gson;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.networking.CustomScriptsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class CustomScriptsScreen extends Screen {
    private static List<String> files = List.of();
    private static String status = "Refresh to list server scripts.";
    private static int deferredOpenTicks = -1;
    private static boolean refreshWhenOpened;
    private static boolean registered;
    // Full Storyteller state syncs can arrive for several ticks after the list
    // response. Keep this screen authoritative during that short window so a
    // late sync cannot dump the ST back into the world/Grimoire.
    private static int keepOpenTicks;

    private int page;
    private String link = "";
    private EditBox input;

    public CustomScriptsScreen() { super(Component.literal("Custom Scripts")); }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (deferredOpenTicks >= 0) {
                if (deferredOpenTicks-- > 0) return;

                deferredOpenTicks = -1;
                keepOpenTicks = 20;
                client.gui.setScreen(new CustomScriptsScreen());
                if (refreshWhenOpened) {
                    refreshWhenOpened = false;
                    ClientStorytellerActions.send("custom_scripts_list", "");
                }
                return;
            }

            if (keepOpenTicks <= 0) return;
            keepOpenTicks--;
            if (!(client.gui.screen() instanceof CustomScriptsScreen)
                    && !(client.gui.screen() instanceof ScriptImportConflictScreen)) {
                client.gui.setScreen(new CustomScriptsScreen());
            }
        });
    }

    /**
     * Open outside the originating button callback. Minecraft 26.3 can otherwise
     * let the old screen/input lifecycle immediately undo the replacement screen.
     */
    public static void openAndRefresh() {
        refreshWhenOpened = true;
        deferredOpenTicks = 1;
        keepOpenTicks = 0;
    }

    public static void clear() {
        files = List.of();
        status = "Refresh to list server scripts.";
        deferredOpenTicks = -1;
        refreshWhenOpened = false;
        keepOpenTicks = 0;
    }

    public static void receive(CustomScriptsPayload payload) {
        Minecraft client = Minecraft.getInstance();
        switch (payload.kind()) {
            case "list" -> {
                files = List.of(new Gson().fromJson(payload.text(), String[].class));
                status = files.isEmpty()
                        ? "No JSON files in the server custom-scripts folder."
                        : files.size() + " saved scripts";
                keepOpenTicks = Math.max(keepOpenTicks, 20);
                if (client.gui.screen() instanceof CustomScriptsScreen screen) {
                    screen.clearWidgets();
                    screen.init();
                } else if (!(client.gui.screen() instanceof ScriptImportConflictScreen)) {
                    client.gui.setScreen(new CustomScriptsScreen());
                }
            }
            case "loaded" -> {
                keepOpenTicks = 0;
                ScriptBuilderScreen.invalidateSelectionCache();
                client.gui.setScreen(new ScriptBuilderScreen());
            }
            case "conflict" -> {
                keepOpenTicks = 0;
                client.gui.setScreen(new ScriptImportConflictScreen(payload.token(), payload.text()));
            }
            case "status" -> status = payload.text();
        }
    }

    protected void init() {
        int w = Math.min(420, this.width - 24), left = (this.width - w) / 2;
        input = new EditBox(this.font, left, 38, w, 20, Component.literal("BotC Scripts link"));
        input.setMaxLength(512);
        input.setHint(Component.literal("Paste https://botcscripts.com/script/…"));
        input.setValue(link);
        input.setResponder(v -> link = v);
        this.addRenderableWidget(input);

        this.addRenderableWidget(Button.builder(Component.literal("Import from BotC Scripts"), b -> {
            status = "Downloading…";
            keepOpenTicks = 20;
            ClientStorytellerActions.send("custom_script_import", link);
        }).bounds(left, 62, w - 90, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            keepOpenTicks = 20;
            ClientStorytellerActions.send("custom_scripts_list", "");
        }).bounds(left + w - 84, 62, 84, 20).build());

        int rows = Math.max(1, (this.height - 102 - 58) / 24);
        int pages = Math.max(1, (files.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = page * rows; i < Math.min(files.size(), (page + 1) * rows); i++) {
            String file = files.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(file), b -> {
                keepOpenTicks = 20;
                ClientStorytellerActions.send("custom_script_load", file);
            }).bounds(left, 102 + (i - page * rows) * 24, w, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            clearWidgets();
            init();
        }).bounds(left, this.height - 52, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            clearWidgets();
            init();
        }).bounds(left + 46, this.height - 52, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back to Script Builder"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        keepOpenTicks = 0;
        deferredOpenTicks = -1;
        refreshWhenOpened = false;
        this.minecraft.gui.setScreen(new ScriptBuilderScreen());
    }

    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(this.font, "Custom Scripts", (this.width - this.font.width("Custom Scripts")) / 2, 14, UiDrawing.GOLD, true);
        g.text(this.font, this.font.plainSubstrByWidth(status, this.width - 24), 12, 88, UiDrawing.MUTED, false);
    }
}
