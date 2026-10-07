package com.sharktower.bloodonthesharktower.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Script-aware reminder labels; source characters and affected players are distinct. */
public final class ReminderCatalog {
    public record Option(String text, String sourceId, String sourceName) {
        public String label() { return sourceId.isBlank() ? text : sourceName + ": " + text; }
    }
    private static final Map<String, List<String>> OFFICIAL = load();
    private ReminderCatalog() {}
    public static List<Option> generic() {
        return List.of("Poisoned", "Drunk", "Protected", "Dead", "Executed", "Survived",
                "Ability Used", "Good", "Evil", "Demon", "Minion").stream()
                .map(text -> new Option(text, "", "")).toList();
    }
    public static List<Option> forScript(Script script, PendingRoleAssignment target) {
        if (script == null) return List.of();
        List<Option> options = new ArrayList<>();
        for (ScriptRole source : script.allRoles()) {
            List<String> labels = source instanceof ScriptRole.Custom custom
                    ? new ArrayList<>(custom.customRole().reminders())
                    : new ArrayList<>(OFFICIAL.getOrDefault(normalize(source.getId()), List.of()));
            if (source instanceof ScriptRole.Custom custom) labels.addAll(custom.customRole().remindersGlobal());
            for (String label : new LinkedHashSet<>(labels)) {
                String text = normalize(source.getId()).equals("tinker") && label.equals("Dead") ? "Tinker Death" : label;
                if (text.isBlank()) continue;
                if (selfOnly(source.getId(), text) && (target == null
                        || !normalize(target.getRoleId()).equals(normalize(source.getId())))) continue;
                options.add(new Option(text, source.getId(), source.getDisplayName()));
            }
        }
        options.sort(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(options);
    }
    public static boolean selfOnly(String roleId, String text) {
        String role = normalize(roleId), label = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (role.equals("tinker") && Set.of("dead", "tinker death").contains(label)) return true;
        if (label.equals("no ability")) return !role.equals("preacher");
        if (label.equals("guess used")) return true;
        if (label.equals("dead") && Set.of("grandmother", "gambler", "acrobat").contains(role)) return true;
        if (role.equals("villageidiot") && label.equals("drunk")) return true;
        if (Set.of("flowergirl", "towncrier", "hermit", "golem", "poppygrower", "plaguedoctor", "barber", "hatter", "xaan", "summoner", "riot").contains(role)) return true;
        if (role.equals("banshee") && label.equals("has ability")) return true;
        if (role.equals("pixie") && Set.of("has ability", "mad").contains(label)) return true;
        if (role.equals("cannibal") && label.equals("poisoned")) return true;
        if (role.equals("snakecharmer") && label.equals("poisoned")) return true;
        if (role.equals("organgrinder") && label.equals("drunk")) return true;
        if (role.equals("po") && label.equals("3 attacks")) return true;
        if (role.equals("fanggu") && label.equals("once")) return true;
        return role.equals("leviathan") && label.startsWith("day ");
    }
    public static String normalize(String id) { return id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static Map<String, List<String>> load() {
        try (var input = ReminderCatalog.class.getResourceAsStream("/data/blood_on_the_sharktower/reminders/official.json")) {
            if (input == null) throw new IllegalStateException("Missing official reminder catalog");
            Map<String, List<String>> result = new HashMap<>();
            for (var entry : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                List<String> labels = new ArrayList<>();
                for (var label : entry.getValue().getAsJsonArray()) labels.add(label.getAsString());
                result.put(normalize(entry.getKey()), List.copyOf(labels));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot load reminder catalog", ex); }
    }
}
