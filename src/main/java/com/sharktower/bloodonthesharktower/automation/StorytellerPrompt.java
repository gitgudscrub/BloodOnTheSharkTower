package com.sharktower.bloodonthesharktower.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A decision waiting on the Storyteller: a Virgin nomination, a Witch curse,
 * a Slayer shot, an Imp star-pass. The server keeps the list; Storytellers
 * see it in the Grimoire's Resolve panel and in chat. Each action runs a
 * {@code /bots automation ...} command, so the server stays in charge of
 * what actually happens.
 *
 * @param id     one pending decision per id ("virgin", "witch", ...)
 * @param title  short heading, e.g. "Virgin nominated"
 * @param body   what happened and the recommendation
 * @param actions what the Storyteller can choose
 */
public record StorytellerPrompt(String id, String title, String body, List<Action> actions) {
    /**
     * An action command starting with this lets the Storyteller click a seat
     * in the Grimoire instead: the rest is the command the seat number is
     * appended to ("pick:/bots automation monk choose ").
     */
    public static final String PICK = "pick:";

    /** One button. {@code tone} is "danger" (kills), "ok" (nothing happens) or "neutral". */
    public record Action(String label, String command, String tone) {
        public static Action danger(String label, String command) {
            return new Action(label, command, "danger");
        }

        public static Action ok(String label, String command) {
            return new Action(label, command, "ok");
        }

        public static Action neutral(String label, String command) {
            return new Action(label, command, "neutral");
        }
    }

    public StorytellerPrompt {
        actions = List.copyOf(actions);
    }

    public static String toJson(List<StorytellerPrompt> prompts) {
        JsonArray array = new JsonArray();
        for (StorytellerPrompt prompt : prompts) {
            JsonObject object = new JsonObject();
            object.addProperty("id", prompt.id());
            object.addProperty("title", prompt.title());
            object.addProperty("body", prompt.body());
            JsonArray actions = new JsonArray();
            for (Action action : prompt.actions()) {
                JsonObject a = new JsonObject();
                a.addProperty("label", action.label());
                a.addProperty("command", action.command());
                a.addProperty("tone", action.tone());
                actions.add(a);
            }
            object.add("actions", actions);
            array.add(object);
        }
        return array.toString();
    }

    public static List<StorytellerPrompt> fromJson(String json) {
        List<StorytellerPrompt> prompts = new ArrayList<>();
        if (json == null || json.isBlank()) return prompts;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return prompts;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                List<Action> actions = new ArrayList<>();
                if (object.has("actions") && object.get("actions").isJsonArray()) {
                    for (JsonElement a : object.getAsJsonArray("actions")) {
                        if (!a.isJsonObject()) continue;
                        JsonObject action = a.getAsJsonObject();
                        actions.add(new Action(text(action, "label"), text(action, "command"), text(action, "tone")));
                    }
                }
                prompts.add(new StorytellerPrompt(text(object, "id"), text(object, "title"), text(object, "body"), actions));
            }
        } catch (RuntimeException ignored) {
            // A malformed list is treated as empty rather than breaking the Grimoire.
            prompts.clear();
        }
        return prompts;
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
