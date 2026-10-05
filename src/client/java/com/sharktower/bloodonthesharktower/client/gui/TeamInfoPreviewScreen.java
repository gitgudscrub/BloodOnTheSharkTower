package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Read-only paginated review; Share sends the server's opaque preview token. */
public final class TeamInfoPreviewScreen extends Screen {
    private static String pendingToken;
    private static String pendingText;
    private static int deferredOpenTicks = -1;
    private static boolean registered;

    private final String token;
    private final String text;
    private List<FormattedCharSequence> lines = List.of();
    private int page;

    public TeamInfoPreviewScreen(String token, String text) {
        super(Component.literal("Share Team Info"));
        this.token = token;
        this.text = text;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (deferredOpenTicks < 0) return;
            if (deferredOpenTicks-- > 0) return;

            String token = pendingToken;
            String text = pendingText;
            pendingToken = null;
            pendingText = null;
            deferredOpenTicks = -1;
            if (token == null || text == null) return;

            client.gui.setScreen(new TeamInfoPreviewScreen(token, text));
        });
    }

    /**
     * Network callbacks only queue the preview. Opening on END_CLIENT_TICK avoids
     * Minecraft 26.3's current input/screen lifecycle immediately undoing it.
     */
    public static void queue(String token, String text) {
        pendingToken = token;
        pendingText = text;
        deferredOpenTicks = 1;
    }

    public static void clearPending() {
        pendingToken = null;
        pendingText = null;
        deferredOpenTicks = -1;
    }

    private int pageSize() {
        return Math.max(1, (this.height - 86) / 12);
    }

    protected void init() {
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (String line : text.split("\\n", -1)) {
            if (line.isEmpty()) wrapped.add(FormattedCharSequence.EMPTY);
            else wrapped.addAll(this.font.split(Component.literal(line), Math.max(80, this.width - 32)));
        }
        lines = List.copyOf(wrapped);
        page = Math.min(page, Math.max(0, (lines.size() - 1) / pageSize()));

        this.addRenderableWidget(Button.builder(Component.literal("Previous"), b ->
                page = Math.max(0, page - 1))
                .bounds(12, this.height - 58, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Next"), b ->
                page = Math.min((lines.size() - 1) / pageSize(), page + 1))
                .bounds(this.width - 92, this.height - 58, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Share Privately"), b -> {
            ClientStorytellerActions.send("team_info_share", token);
            onClose();
        }).bounds(this.width / 2 - 110, this.height - 30, 130, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 + 30, this.height - 30, 80, 20).build());
    }

    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        String title = "Share Team Info — ST preview";
        g.text(this.font, title, (this.width - this.font.width(title)) / 2, 12, UiDrawing.GOLD, true);
        int start = page * pageSize();
        for (int i = start; i < Math.min(lines.size(), start + pageSize()); i++) {
            g.text(this.font, lines.get(i), 16, 32 + (i - start) * 12, UiDrawing.TEXT, false);
        }
        String count = "Page " + (page + 1) + " / " + Math.max(1, (lines.size() + pageSize() - 1) / pageSize());
        g.text(this.font, count, (this.width - this.font.width(count)) / 2, this.height - 52, UiDrawing.MUTED, false);
    }
}
