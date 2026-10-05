package com.sharktower.bloodonthesharktower.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.networking.CustomScriptsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CustomScriptsScreen extends Screen {
    private record SavedScript(String filename, String name) {}

    private static List<SavedScript> files = List.of();
    private static String status = "Refresh to list server scripts.";
    private static String searchQuery = "";
    private static boolean focusSearchAfterRefresh;
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

    private CustomScriptsScreen(int page, String link) {
        this();
        this.page = Math.max(0, page);
        this.link = link == null ? "" : link;
    }

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
        searchQuery = "";
        focusSearchAfterRefresh = false;
        deferredOpenTicks = -1;
        refreshWhenOpened = false;
        keepOpenTicks = 0;
    }

    public static void receive(CustomScriptsPayload payload) {
        Minecraft client = Minecraft.getInstance();
        switch (payload.kind()) {
            case "list" -> {
                files = decodeList(payload.text());
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

    private static List<SavedScript> decodeList(String json) {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<SavedScript> scripts = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                // Accept the old filename-only payload too, which makes an update
                // tolerant of a list response already in flight during reconnect.
                if (element.isJsonPrimitive()) {
                    String filename = element.getAsString();
                    scripts.add(new SavedScript(filename, filenameStem(filename)));
                    continue;
                }
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("filename") || object.get("filename").isJsonNull()) continue;
                String filename = object.get("filename").getAsString();
                String name = object.has("name") && !object.get("name").isJsonNull()
                        ? object.get("name").getAsString()
                        : "";
                if (name.isBlank()) name = filenameStem(filename);
                scripts.add(new SavedScript(filename, name));
            }
            return List.copyOf(scripts);
        } catch (RuntimeException ex) {
            status = "Could not read the custom script list.";
            return List.of();
        }
    }

    private static String filenameStem(String filename) {
        if (filename == null) return "";
        return filename.toLowerCase(Locale.ROOT).endsWith(".json") && filename.length() > 5
                ? filename.substring(0, filename.length() - 5)
                : filename;
    }

    @Override
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

        EditBox search = new EditBox(this.font, left, 88, w, 20, Component.literal("Search custom scripts"));
        search.setMaxLength(120);
        search.setHint(Component.literal("Search by script name or file name..."));
        search.setValue(searchQuery);
        search.setResponder(value -> {
            if (value.equals(searchQuery)) return;
            searchQuery = value;
            focusSearchAfterRefresh = true;
            this.minecraft.gui.setScreen(new CustomScriptsScreen(0, link));
        });
        this.addRenderableWidget(search);
        if (focusSearchAfterRefresh) {
            search.setFocused(true);
            focusSearchAfterRefresh = false;
        }

        List<SavedScript> visible = filteredFiles();
        int listTop = 126;
        int rows = Math.max(1, (this.height - listTop - 58) / 24);
        int pages = Math.max(1, (visible.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int start = page * rows;
        int end = Math.min(visible.size(), start + rows);
        for (int i = start; i < end; i++) {
            SavedScript script = visible.get(i);
            String label = displayLabel(script, w - 12);
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                keepOpenTicks = 20;
                ClientStorytellerActions.send("custom_script_load", script.filename());
            }).bounds(left, listTop + (i - start) * 24, w, 20).build());
        }

        Button previous = Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            clearWidgets();
            init();
        }).bounds(left, this.height - 52, 40, 20).build();
        previous.active = page > 0;
        this.addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            clearWidgets();
            init();
        }).bounds(left + 46, this.height - 52, 40, 20).build();
        next.active = page < pages - 1;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.literal("Back to Script Builder"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private List<SavedScript> filteredFiles() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return files;
        return files.stream()
                .filter(script -> script.name().toLowerCase(Locale.ROOT).contains(query)
                        || script.filename().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private String displayLabel(SavedScript script, int maxWidth) {
        String stem = filenameStem(script.filename());
        String text = script.name().equalsIgnoreCase(stem)
                ? script.filename()
                : script.name() + " — " + script.filename();
        if (this.font.width(text) <= maxWidth) return text;
        String suffix = "...";
        return this.font.plainSubstrByWidth(text, Math.max(1, maxWidth - this.font.width(suffix))) + suffix;
    }

    private String visibleStatus() {
        if (!searchQuery.isBlank()) {
            int matches = filteredFiles().size();
            if (matches == 0) return "No matching custom scripts.";
            return matches + " of " + files.size() + " saved scripts match.";
        }
        return status;
    }

    @Override
    public void onClose() {
        keepOpenTicks = 0;
        deferredOpenTicks = -1;
        refreshWhenOpened = false;
        this.minecraft.gui.setScreen(new ScriptBuilderScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(this.font, "Custom Scripts", (this.width - this.font.width("Custom Scripts")) / 2, 14, UiDrawing.GOLD, true);
        g.text(this.font, this.font.plainSubstrByWidth(visibleStatus(), this.width - 24), 12, 112, UiDrawing.MUTED, false);
    }
}
