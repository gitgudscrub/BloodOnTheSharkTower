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
        if (role.equals("alchemist") && label.equals("psychopath")) return true;
        if (role.equals("cannibal") && label.equals("poisoned")) return true;
        if (role.equals("snakecharmer") && label.equals("poisoned")) return true;
        if (role.equals("organgrinder") && label.equals("drunk")) return true;
        if (role.equals("po") && label.equals("3 attacks")) return true;
        if (role.equals("fanggu") && label.equals("once")) return true;
        return role.equals("leviathan") && label.startsWith("day ");
    }
    /**
     * Best guess at the character a reminder belongs to when it was placed
     * without one (typed by hand, or a generic marker): a script character
     * named in the text ("Is the Drunk"), then a script character whose
     * official or custom reminders include the text ("Red Herring"), then a
     * standard character for common generic markers. Empty when nothing fits.
     * Only used to choose an icon; the reminder itself is unchanged.
     */
    public static Optional<String> inferSourceId(String text, Script script) {
        String label = text == null ? "" : text.trim();
        if (label.isEmpty()) return Optional.empty();
        String flat = normalize(label);
        List<ScriptRole> roles = script == null ? List.of() : script.allRoles();
        ScriptRole named = null;
        for (ScriptRole role : roles) {
            String name = normalize(role.getDisplayName());
            if (name.length() >= 3 && flat.contains(name)
                    && (named == null || name.length() > normalize(named.getDisplayName()).length())) named = role;
        }
        if (named != null) return Optional.of(named.getId());
        for (ScriptRole role : roles) {
            List<String> labels = role instanceof ScriptRole.Custom custom
                    ? concat(custom.customRole().reminders(), custom.customRole().remindersGlobal())
                    : OFFICIAL.getOrDefault(normalize(role.getId()), List.of());
            for (String candidate : labels) {
                if (candidate.equalsIgnoreCase(label)) return Optional.of(role.getId());
            }
        }
        String fallback = switch (flat) {
            case "poisoned" -> "poisoner";
            case "drunk" -> "drunk";
            case "protected", "safe" -> "monk";
            case "demon" -> "imp";
            case "minion" -> "baron";
            case "redherring" -> "fortuneteller";
            case "mad" -> "cerenovus";
            default -> "";
        };
        if (!fallback.isEmpty()) return Optional.of(fallback);
        // Any official character named in the text, even off-script ("Washerwoman Townsfolk").
        Role namedOfficial = null;
        for (Role role : Role.values()) {
            String name = normalize(role.getDisplayName());
            if (name.length() >= 4 && flat.contains(name)
                    && (namedOfficial == null || name.length() > normalize(namedOfficial.getDisplayName()).length())) namedOfficial = role;
        }
        if (namedOfficial != null) return Optional.of(namedOfficial.getId());
        String unique = null;
        for (var entry : OFFICIAL.entrySet()) {
            for (String candidate : entry.getValue()) {
                if (!candidate.equalsIgnoreCase(label)) continue;
                if (unique != null && !unique.equals(entry.getKey())) return Optional.empty();
                unique = entry.getKey();
            }
        }
        return Optional.ofNullable(unique);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
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
