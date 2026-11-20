package com.sharktower.bloodonthesharktower.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays text out on written-book pages. A book page is 114 px wide, shows 14
 * lines and does not scroll, so text is wrapped here (a little narrower than
 * the page, with the vanilla font's widths) and pages are broken before they
 * overflow. Headings stay with the text under them, short paragraphs are not
 * split, and a page break never leaves a single line on either side.
 */
final class BookPages {
    static final int LINE_WIDTH = 110;
    static final int LINES_PER_PAGE = 14;

    private record Line(String text, ChatFormatting colour, boolean bold) {}

    private final List<List<Line>> pages = new ArrayList<>();
    private List<Line> page;
    /** A heading waits here until the text under it is added, so the two stay on one page. */
    private List<Line> pendingHeading;
    private boolean pendingGap;
    private boolean pendingGapAfter;
    /** Whether the heading just placed wants a blank line under it. */
    private boolean headingGapAfter;
    /** Paragraphs up to this many lines are never split across pages. */
    static final int KEEP_TOGETHER = 4;
    /** List items are never split across pages. */
    private boolean keepWhole;

    /** Starts a new page (no-op on an empty one). */
    void newPage() {
        flushHeading();
        if (page != null && page.isEmpty()) return;
        page = new ArrayList<>();
        pages.add(page);
    }

    /** A page title with a blank line under it. */
    void title(String text, ChatFormatting colour) {
        holdHeading(text, colour, false, true);
    }

    /** A heading with its text straight under it. */
    void heading(String text, ChatFormatting colour) {
        holdHeading(text, colour, false, false);
    }

    void subheading(String text, ChatFormatting colour) {
        holdHeading(text, colour, true, false);
    }

    /** A section heading inside a chapter, with a blank line under it like a title. */
    void section(String text, ChatFormatting colour) {
        holdHeading(text, colour, true, true);
    }

    /** How many lines a paragraph of plain text takes. */
    static int lines(String text) {
        return wrap(text, false, LINE_WIDTH).size();
    }

    void paragraph(String text, ChatFormatting colour) {
        List<Line> out = new ArrayList<>();
        for (String paragraphLine : text.split("\n")) {
            for (String line : wrap(paragraphLine, false, LINE_WIDTH)) out.add(new Line(line, colour, false));
        }
        add(out, true);
    }

    /** A paragraph with no blank line before it. */
    void text(String text, ChatFormatting colour) {
        List<Line> out = new ArrayList<>();
        for (String line : wrap(text, false, LINE_WIDTH)) out.add(new Line(line, colour, false));
        add(out, false);
    }

    void bullet(String text) {
        prefixed("- ", text);
    }

    /** "1. Text": following lines are indented under the text. */
    void numbered(String text) {
        int space = text.indexOf(' ');
        prefixed(text.substring(0, space + 1), text.substring(space + 1));
    }

    private void prefixed(String prefix, String text) {
        List<String> lines = wrap(text, false, LINE_WIDTH - width(prefix, false));
        String indent = " ".repeat(Math.max(1, Math.round(width(prefix, false) / 4f)));
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) out.add(new Line((i == 0 ? prefix : indent) + lines.get(i), null, false));
        keepWhole = true;
        add(out, false);
        keepWhole = false;
    }

    private void holdHeading(String text, ChatFormatting colour, boolean gapBefore, boolean gapAfter) {
        flushHeading();
        pendingGapAfter = gapAfter;
        List<Line> out = new ArrayList<>();
        for (String line : wrap(text, true, LINE_WIDTH)) out.add(new Line(line, colour, true));
        pendingHeading = out;
        pendingGap = gapBefore;
    }

    private void flushHeading() {
        if (pendingHeading == null) return;
        List<Line> heading = pendingHeading;
        pendingHeading = null;
        place(heading, pendingGap, 0);
        headingGapAfter = pendingGapAfter;
    }

    private void add(List<Line> lines, boolean gapBefore) {
        if (pendingHeading != null) {
            List<Line> heading = pendingHeading;
            pendingHeading = null;
            // The heading needs room for itself, a gap and two lines (or all) of its text.
            int with = (gapBefore && pendingGapAfter ? 1 : 0) + (whole(lines) ? lines.size() : 2);
            place(heading, pendingGap, with);
            headingGapAfter = pendingGapAfter;
            place(lines, gapBefore, 0);
        } else {
            place(lines, gapBefore, 0);
        }
    }

    /**
     * Adds lines with a blank line before them (unless at the top of a page or
     * right under a heading). {@code reserve} more lines must fit after these.
     */
    private void place(List<Line> lines, boolean gapBefore, int reserve) {
        if (lines.isEmpty()) return;
        if (page == null) {
            page = new ArrayList<>();
            pages.add(page);
        }
        boolean underHeading = !page.isEmpty() && page.get(page.size() - 1).bold() && !headingGapAfter;
        int gap = gapBefore && !page.isEmpty() && !underHeading ? 1 : 0;
        int need = reserve > 0 ? lines.size() + reserve : (whole(lines) ? lines.size() : 2);
        if (!page.isEmpty() && page.size() + gap + need > LINES_PER_PAGE) {
            page = new ArrayList<>();
            pages.add(page);
            gap = 0;
        }
        if (gap == 1) page.add(new Line("", null, false));
        for (int i = 0; i < lines.size(); i++) {
            int left = lines.size() - i;
            boolean full = page.size() >= LINES_PER_PAGE;
            // Never leave a single line at the top of the next page.
            boolean orphan = left == 2 && i > 0 && page.size() == LINES_PER_PAGE - 1;
            if (full || orphan) {
                page = new ArrayList<>();
                pages.add(page);
            }
            page.add(lines.get(i));
        }
    }

    private boolean whole(List<Line> lines) {
        return lines.size() <= KEEP_TOGETHER || (keepWhole && lines.size() < LINES_PER_PAGE);
    }

    /**
     * Simple markdown: "# " starts a new page with a title, "## " a subheading,
     * "- " a bullet, "1. " a numbered item, blank lines split paragraphs.
     */
    void markdown(String text) {
        StringBuilder para = new StringBuilder();
        for (String raw : text.replace("\r\n", "\n").split("\n", -1)) {
            String line = raw.strip();
            boolean numbered = isNumbered(line);
            boolean special = line.startsWith("#") || line.startsWith("- ") || numbered;
            if ((line.isEmpty() || special) && !para.isEmpty()) {
                paragraph(para.toString(), null);
                para.setLength(0);
            }
            if (line.isEmpty()) continue;
            if (line.startsWith("## ")) section(line.substring(3), ChatFormatting.DARK_RED);
            else if (line.startsWith("# ")) {
                newPage();
                title(line.substring(2), ChatFormatting.DARK_RED);
            } else if (line.startsWith("- ")) bullet(line.substring(2));
            else if (numbered) numbered(line);
            else para.append(para.isEmpty() ? "" : " ").append(line);
        }
        if (!para.isEmpty()) paragraph(para.toString(), null);
    }

    private static boolean isNumbered(String line) {
        int dot = line.indexOf(". ");
        if (dot <= 0 || dot > 2) return false;
        for (int i = 0; i < dot; i++) if (!Character.isDigit(line.charAt(i))) return false;
        return true;
    }

    List<Component> pages() {
        flushHeading();
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
