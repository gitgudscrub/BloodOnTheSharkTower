package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.timer.ClientTimerState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Original BOTB timer layout on the 26.2 screen API.
 *
 * The preset geometry follows BOTB (30s/1m/2m, then 3m/5m/10m). The timer
 * control packet is restored in the integration batch, so these buttons give
 * the matching /bots command until then rather than pretending to act locally.
 */
public class TimerScreen extends Screen {
    public TimerScreen() {
        super(Component.literal("Clocktower Timer"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 100;

        addPreset(cx - 115, top, "30 sec", 30);
        addPreset(cx - 35, top, "1 min", 60);
        addPreset(cx + 45, top, "2 min", 120);
        addPreset(cx - 115, top + 30, "3 min", 180);
        addPreset(cx - 35, top + 30, "5 min", 300);
        addPreset(cx + 45, top + 30, "10 min", 600);

        this.addRenderableWidget(Button.builder(
                        Component.literal(ClientTimerState.isPaused ? "Resume" : "Pause"),
                        b -> showCommand(ClientTimerState.isPaused ? "/bots timer resume" : "/bots timer pause"))
                .bounds(cx - 100, top + 125, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Stop"), b -> showCommand("/bots timer stop"))
                .bounds(cx + 5, top + 125, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(cx - 100, top + 180, 200, 20).build());
    }

    private void addPreset(int x, int y, String label, int seconds) {
        this.addRenderableWidget(Button.builder(Component.literal(label), b -> showCommand("/bots timer start " + seconds))
                .bounds(x, y, 70, 20).build());
    }

    private void showCommand(String command) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("Timer control: " + command));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = this.height / 2 - 100;

        drawCentered(graphics, "Clocktower Timer", top - 24, UiDrawing.TEXT, true);
        String status = !ClientTimerState.isActive ? "IDLE" : (ClientTimerState.isPaused ? "PAUSED" : "RUNNING");
        drawCentered(graphics, status + "   " + format(ClientTimerState.remainingSeconds), top + 72,
                ClientTimerState.isPaused ? UiDrawing.GOLD : UiDrawing.TEXT, true);

        drawCentered(graphics, "Custom time", top + 102, UiDrawing.MUTED, false);
        graphics.fill(cx - 100, top + 98, cx + 100, top + 99, 0xFF777777);
        drawCentered(graphics, "Preset buttons mirror BOTB; server controls currently use /bots timer …",
                top + 154, UiDrawing.MUTED, false);
    }

    private static String format(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%d:%02d", safe / 60, safe % 60);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
