package com.sharktower.bloodonthesharktower.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays text out on written-book pages. A book page is 114 px wide and shows
 * 14 lines and does not scroll, so text is wrapped here (a little narrower
 * than the page, with the vanilla font's widths) and pages are broken before
 * they overflow. Words never split unless one alone is wider than a line.
 */
final class BookPages {
    static final int LINE_WIDTH = 110;
    static final int LINES_PER_PAGE = 13;

    private record Line(String text, ChatFormatting colour, boolean bold) {}

    private final List<List<Line>> pages = new ArrayList<>();
    private List<Line> page;

    /** Starts a new page (no-op on an empty one). */
    void newPage() {
        if (page != null && page.isEmpty()) return;
        page = new ArrayList<>();
        pages.add(page);
    }

    void heading(String text, ChatFormatting colour) {
        block(text, colour, true, false);
    }

    void subheading(String text, ChatFormatting colour) {
        block(text, colour, true, true);
    }

    void paragraph(String text, ChatFormatting colour) {
        block(text, colour, false, true);
    }

    void bullet(String text) {
        List<String> lines = wrap(text, false, LINE_WIDTH - width("- ", false));
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) out.add(new Line((i == 0 ? "- " : "  ") + lines.get(i), null, false));
        add(out, false);
    }

    private void block(String text, ChatFormatting colour, boolean bold, boolean gapBefore) {
        List<Line> out = new ArrayList<>();
        for (String paragraphLine : text.split("\n")) {
            for (String line : wrap(paragraphLine, bold, LINE_WIDTH)) out.add(new Line(line, colour, bold));
        }
        add(out, gapBefore);
    }

    /** Adds lines with a blank line before them (unless at the top of a page), keeping a heading with what follows. */
    private void add(List<Line> lines, boolean gapBefore) {
        if (page == null) newPage();
        if (gapBefore && !page.isEmpty()) {
            if (page.size() + 1 >= LINES_PER_PAGE) newPage();
            else page.add(new Line("", null, false));
        }
        // Don't leave a heading alone at the bottom of a page.
        if (!page.isEmpty() && page.size() + Math.min(lines.size(), 2) > LINES_PER_PAGE) newPage();
        for (Line line : lines) {
            if (page.size() >= LINES_PER_PAGE) newPage();
            page.add(line);
        }
    }

    /**
     * Simple markdown: "# " starts a new page with a title, "## " a subheading,
     * "- " a bullet, numbered lines stay as they are, blank lines split paragraphs.
     */
    void markdown(String text) {
        StringBuilder para = new StringBuilder();
        for (String raw : text.replace("\r\n", "\n").split("\n", -1)) {
            String line = raw.strip();
            boolean special = line.startsWith("#") || line.startsWith("- ") || line.matches("\\d+\\. .*");
            if ((line.isEmpty() || special) && !para.isEmpty()) {
                paragraph(para.toString(), null);
                para.setLength(0);
            }
            if (line.isEmpty()) continue;
            if (line.startsWith("## ")) subheading(line.substring(3), ChatFormatting.DARK_RED);
            else if (line.startsWith("# ")) {
                newPage();
                heading(line.substring(2), ChatFormatting.DARK_RED);
            } else if (line.startsWith("- ")) bullet(line.substring(2));
            else if (line.matches("\\d+\\. .*")) bullet(line);
            else para.append(para.isEmpty() ? "" : " ").append(line);
        }
        if (!para.isEmpty()) paragraph(para.toString(), null);
    }

    List<Component> pages() {
        List<Component> out = new ArrayList<>();
        for (List<Line> lines : pages) {
            if (lines.isEmpty()) continue;
            MutableComponent component = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (i > 0) component.append(Component.literal("\n"));
                MutableComponent text = Component.literal(line.text());
                if (line.colour() != null) text.withStyle(line.colour());
                if (line.bold()) text.withStyle(ChatFormatting.BOLD);
                component.append(text);
            }
            out.add(component);
        }
        if (out.isEmpty()) out.add(Component.literal(""));
        return out;
    }

    int pageCount() {
        return (int) pages.stream().filter(p -> !p.isEmpty()).count();
    }

    // ------------------------------------------------------------- Measuring

    static List<String> wrap(String text, boolean bold, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, bold) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) lines.add(line.toString());
            line.setLength(0);
            // A word wider than the line is split by character.
            while (width(word, bold) > maxWidth) {
                int cut = word.length() - 1;
                while (cut > 1 && width(word.substring(0, cut), bold) > maxWidth) cut--;
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** Pixel width in the vanilla font (advance includes the 1 px gap; bold adds 1). */
    static int width(String text, boolean bold) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) total += advance(text.charAt(i)) + (bold ? 1 : 0);
        return total;
    }

    static int advance(char c) {
        return switch (c) {
            case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', ' ', '(', ')', '[', ']', '{', '}', '"', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> c > 0x7f ? 7 : 6;
        };
    }
}
