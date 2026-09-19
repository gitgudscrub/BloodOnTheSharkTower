package com.sharktower.bloodonthesharktower.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Port of BOTB's script model, JSON parser, and compressed packet codec.
 *
 * The packet transport follows the original mod: raw script JSON is GZIP
 * compressed, length-prefixed, then parsed back into a Script on receipt.
 */
public record Script(
        String name,
        String author,
        List<Role> roles,
        List<CustomRole> customRoles,
        List<ScriptRole> fabled,
        List<ScriptRole> loric,
        List<ScriptRole> travelers,
        List<String> firstNightOrder,
        List<String> otherNightOrder,
        String logo,
        String almanac,
        List<String> extraAlmanacs,
        List<String> bootlegger,
        String rawJson
) {
    private static final int MAX_COMPRESSED_BYTES = 1_048_576;
    private static final int MAX_JSON_BYTES = 16_777_216;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_EXTRA_ALMANACS = 32;

    /**
     * 26.2 port of the original Script.PACKET_CODEC.
     *
     * Keeping the raw JSON as the wire format preserves custom/homebrew role
     * definitions, metadata, night order, bootlegger rules, and future fields
     * without needing a second hand-maintained network schema.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, Script> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public Script decode(RegistryFriendlyByteBuf buffer) {
            int compressedLength = buffer.readInt();
            if (compressedLength < 0 || compressedLength > MAX_COMPRESSED_BYTES) {
                throw new IllegalArgumentException(
                        "Script compressed payload length out of range: " + compressedLength
                );
            }
            if (buffer.readableBytes() < compressedLength) {
                throw new IllegalArgumentException("Script payload truncated");
            }

            byte[] compressed = new byte[compressedLength];
            buffer.readBytes(compressed);
            String json = decompress(compressed);
            return Script.fromJson(json).orElseThrow(() ->
                    new IllegalArgumentException("Script payload is not a valid script")
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, Script script) {
            byte[] compressed = compress(script.rawJson());
            if (compressed.length > MAX_COMPRESSED_BYTES) {
                throw new IllegalArgumentException(
                        "Script compressed payload exceeds " + MAX_COMPRESSED_BYTES + " bytes"
                );
            }
            buffer.writeInt(compressed.length);
            buffer.writeBytes(compressed);
        }
    };

    public Script {
        name = name == null ? "Unknown Script" : name;
        author = author == null ? "Unknown Author" : author;
        roles = roles == null ? List.of() : List.copyOf(roles);
        customRoles = customRoles == null ? List.of() : List.copyOf(customRoles);
        fabled = fabled == null ? List.of() : List.copyOf(fabled);
        loric = loric == null ? List.of() : List.copyOf(loric);
        travelers = travelers == null ? List.of() : List.copyOf(travelers);
        firstNightOrder = firstNightOrder == null ? null : List.copyOf(firstNightOrder);
        otherNightOrder = otherNightOrder == null ? null : List.copyOf(otherNightOrder);
        extraAlmanacs = extraAlmanacs == null ? null : List.copyOf(extraAlmanacs);
        bootlegger = bootlegger == null ? null : List.copyOf(bootlegger);
        rawJson = rawJson == null ? "" : rawJson;
    }

    public List<ScriptRole> allRoles() {
        List<ScriptRole> result = new ArrayList<>();
        for (Role role : roles) result.add(new ScriptRole.Official(role));
        for (CustomRole customRole : customRoles) result.add(new ScriptRole.Custom(customRole));
        result.addAll(travelers);
        result.sort((left, right) ->
                Integer.compare(getTypeOrder(left.getTeam()), getTypeOrder(right.getTeam()))
        );
        return result;
    }

    public static int getTypeOrder(RoleType type) {
        return switch (type) {
            case TOWNSFOLK -> 0;
            case OUTSIDER -> 1;
            case MINION -> 2;
            case DEMON -> 3;
            case TRAVELER -> 4;
            default -> 5;
        };
    }

    public Optional<CustomRole> getCustomRole(String id) {
        String normalized = normalizeRoleId(id);
        for (CustomRole role : customRoles) {
            if (normalizeRoleId(role.id()).equals(normalized)) return Optional.of(role);
        }
        for (ScriptRole role : travelers) {
            if (role instanceof ScriptRole.Custom custom
                    && normalizeRoleId(custom.customRole().id()).equals(normalized)) {
                return Optional.of(custom.customRole());
            }
        }
        return Optional.empty();
    }

    public Optional<Role> getOfficialRole(String id) {
        String normalized = normalizeRoleId(id);
        return roles.stream()
                .filter(role -> normalizeRoleId(role.getId()).equals(normalized))
                .findFirst();
    }

    public boolean isCustomRole(String id) {
        return getCustomRole(id).isPresent();
    }

    public boolean hasRole(String id) {
        return getCustomRole(id).isPresent() || getOfficialRole(id).isPresent();
    }

    public Optional<ScriptRole> getScriptRole(String id) {
        String normalized = normalizeRoleId(id);

        Optional<Role> official = getOfficialRole(id);
        if (official.isPresent()) return Optional.of(new ScriptRole.Official(official.get()));

        Optional<CustomRole> custom = getCustomRole(id);
        if (custom.isPresent()) return Optional.of(new ScriptRole.Custom(custom.get()));

        for (ScriptRole role : travelers) {
            if (normalizeRoleId(role.getId()).equals(normalized)) return Optional.of(role);
        }
        for (ScriptRole role : fabled) {
            if (normalizeRoleId(role.getId()).equals(normalized)) return Optional.of(role);
        }
        for (ScriptRole role : loric) {
            if (normalizeRoleId(role.getId()).equals(normalized)) return Optional.of(role);
        }
        return Optional.empty();
    }

    private static String normalizeRoleId(String id) {
        if (id == null) return "";
        return id.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
    }

    private static Role findRoleByNormalizedId(String id) {
        return Role.findById(id);
    }

    private static void addOfficialRoleToList(
            Role role,
            List<Role> normalRoles,
            List<ScriptRole> travelers,
            List<ScriptRole> fabled,
            List<ScriptRole> loric
    ) {
        switch (role.getType()) {
            case TRAVELER -> travelers.add(new ScriptRole.Official(role));
            case FABLED -> fabled.add(new ScriptRole.Official(role));
            case LORIC -> loric.add(new ScriptRole.Official(role));
            default -> normalRoles.add(role);
        }
    }

    private static byte[] compress(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Script JSON exceeds " + MAX_JSON_BYTES + " bytes");
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(raw);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            // Mirrors the legacy codec's compatibility fallback.
            return raw;
        }
    }

    private static String decompress(byte[] bytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = gzip.read(chunk)) != -1) {
                    if (output.size() + read > MAX_JSON_BYTES) {
                        throw new IllegalArgumentException(
                                "Script JSON exceeds " + MAX_JSON_BYTES + " bytes"
                        );
                    }
                    output.write(chunk, 0, read);
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // The original mod accepted uncompressed UTF-8 as a fallback.
            if (bytes.length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Script JSON exceeds " + MAX_JSON_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static Optional<Script> fromJson(String json) {
        if (json == null) return Optional.empty();
        if (exceedsNestingDepth(json, MAX_JSON_DEPTH)) return Optional.empty();

        try {
            Gson gson = new Gson();
            java.lang.reflect.Type listType = new TypeToken<List<Object>>() {}.getType();
            List<Object> entries = gson.fromJson(json, listType);
            if (entries == null || entries.isEmpty()) return Optional.empty();

            String name = "Unknown Script";
            String author = "Unknown Author";
            List<Role> roles = new ArrayList<>();
            List<CustomRole> customRoles = new ArrayList<>();
            List<ScriptRole> fabled = new ArrayList<>();
            List<ScriptRole> loric = new ArrayList<>();
            List<ScriptRole> travelers = new ArrayList<>();
            List<String> firstNight = null;
            List<String> otherNight = null;
            String logo = null;
            String almanac = null;
            List<String> extraAlmanacs = null;
            List<String> bootlegger = null;

            for (Object entry : entries) {
                if (entry instanceof String roleId) {
                    Role role = findRoleByNormalizedId(roleId);
                    if (role != null && role != Role.NO_ROLE) {
                        addOfficialRoleToList(role, roles, travelers, fabled, loric);
                    }
                    continue;
                }

                if (!(entry instanceof Map<?, ?> rawMap)) continue;
                Map<String, Object> map = castStringObjectMap(rawMap);
                Object rawId = map.get("id");
                if (!(rawId instanceof String id)) continue;

                if ("_meta".equals(id)) {
                    name = asString(map.getOrDefault("name", name), name);
                    author = asString(map.getOrDefault("author", author), author);

                    if (map.get("firstNight") instanceof List<?> list) firstNight = stringList(list);
                    if (map.get("otherNight") instanceof List<?> list) otherNight = stringList(list);
                    if (map.get("logo") instanceof String value) logo = value;
                    if (map.get("almanac") instanceof String value) almanac = value;

                    if (map.get("extraAlmanacs") instanceof List<?> list) {
                        extraAlmanacs = stringList(list);
                        if (extraAlmanacs.size() > MAX_EXTRA_ALMANACS) {
                            extraAlmanacs = new ArrayList<>(extraAlmanacs.subList(0, MAX_EXTRA_ALMANACS));
                        }
                    }

                    Object bootleggerValue = map.get("bootlegger");
                    if (bootleggerValue instanceof List<?> list) {
                        bootlegger = new ArrayList<>();
                        for (String rule : stringList(list)) {
                            if (!rule.isBlank()) bootlegger.add(rule);
                        }
                    } else if (bootleggerValue instanceof String rule && !rule.isBlank()) {
                        bootlegger = new ArrayList<>(List.of(rule));
                    }
                    continue;
                }

                boolean hasDefinition = map.containsKey("team") || map.containsKey("ability");
                if (hasDefinition) {
                    Role official = findRoleByNormalizedId(id);
                    if (official != null && official != Role.NO_ROLE) {
                        addOfficialRoleToList(official, roles, travelers, fabled, loric);
                        continue;
                    }

                    String team = asString(map.getOrDefault("team", "townsfolk"), "townsfolk");
                    if (team.equalsIgnoreCase("fabled")) {
                        NonPlayerCharacter character = NonPlayerCharacter.fromJsonMap(
                                map, NonPlayerCharacter.FabledType.FABLED
                        );
                        fabled.add(new ScriptRole.Fabled(character));
                    } else if (team.equalsIgnoreCase("loric")) {
                        NonPlayerCharacter character = NonPlayerCharacter.fromJsonMap(
                                map, NonPlayerCharacter.FabledType.LORIC
                        );
                        loric.add(new ScriptRole.Fabled(character));
                    } else if (team.equalsIgnoreCase("traveller")
                            || team.equalsIgnoreCase("traveler")) {
                        CustomRole customRole = CustomRole.fromJsonMap(map);
                        travelers.add(new ScriptRole.Custom(customRole));
                    } else {
                        customRoles.add(CustomRole.fromJsonMap(map));
                    }
                    continue;
                }

                Role official = findRoleByNormalizedId(id);
                if (official != null && official != Role.NO_ROLE) {
                    addOfficialRoleToList(official, roles, travelers, fabled, loric);
                }
            }

            if (roles.isEmpty() && customRoles.isEmpty()) return Optional.empty();

            return Optional.of(new Script(
                    name, author, roles, customRoles, fabled, loric, travelers,
                    firstNight, otherNight, logo, almanac, extraAlmanacs,
                    bootlegger, json
            ));
        } catch (JsonSyntaxException | ClassCastException exception) {
            return Optional.empty();
        }
    }

    public boolean hasCustomRoles() {
        return !customRoles.isEmpty();
    }

    public boolean hasCustomRolesOrTravelers() {
        return !customRoles.isEmpty() || !travelers.isEmpty();
    }

    public List<CustomRole> allCustomRoles() {
        List<CustomRole> result = new ArrayList<>(customRoles);
        for (ScriptRole role : travelers) {
            if (role instanceof ScriptRole.Custom custom) result.add(custom.customRole());
        }
        return result;
    }

    public boolean hasFabledOrLoric(String id) {
        return getFabledOrLoric(id).isPresent();
    }

    public boolean hasLogo() {
        return logo != null && !logo.isEmpty();
    }

    public boolean hasBootleggerRules() {
        return bootlegger != null && !bootlegger.isEmpty();
    }

    public boolean hasAlmanac() {
        return almanac != null && !almanac.isEmpty();
    }

    public boolean hasAnyAlmanac() {
        return hasAlmanac() || (extraAlmanacs != null && !extraAlmanacs.isEmpty());
    }

    public boolean hasFabled() {
        return !fabled.isEmpty();
    }

    public boolean hasLoric() {
        return !loric.isEmpty();
    }

    public boolean hasFabledOrLoric() {
        return hasFabled() || hasLoric();
    }

    public boolean hasTravelers() {
        return !travelers.isEmpty();
    }

    public List<ScriptRole> allFabledAndLoric() {
        List<ScriptRole> result = new ArrayList<>(fabled);
        result.addAll(loric);
        return result;
    }

    public Optional<ScriptRole> getFabledOrLoric(String id) {
        String normalized = normalizeRoleId(id);
        for (ScriptRole role : fabled) {
            if (normalizeRoleId(role.getId()).equals(normalized)) return Optional.of(role);
        }
        for (ScriptRole role : loric) {
            if (normalizeRoleId(role.getId()).equals(normalized)) return Optional.of(role);
        }
        return Optional.empty();
    }

    private static boolean exceedsNestingDepth(String json, int maxDepth) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
                if (depth > maxDepth) return true;
            } else if (c == ']' || c == '}') {
                depth--;
            }
        }
        return false;
    }

    private static Map<String, Object> castStringObjectMap(Map<?, ?> raw) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static List<String> stringList(List<?> raw) {
        List<String> result = new ArrayList<>();
        for (Object value : raw) if (value instanceof String string) result.add(string);
        return result;
    }
}
