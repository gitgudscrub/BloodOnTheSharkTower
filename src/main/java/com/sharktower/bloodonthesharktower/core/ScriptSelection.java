package com.sharktower.bloodonthesharktower.core;
import com.google.gson.*;
import java.util.*;
/** Edit character selection without discarding imported metadata or homebrew definitions. */
public final class ScriptSelection {
    private ScriptSelection() {}
    public static String build(Script original,Set<String> selected) {
        JsonArray output=new JsonArray();Set<String> included=new HashSet<>();boolean metadata=false;
        if (original!=null) {
            for (JsonElement entry:JsonParser.parseString(original.rawJson()).getAsJsonArray()) {
                String id=entry.isJsonPrimitive()?entry.getAsString():entry.getAsJsonObject().get("id").getAsString();
                if (id.equals("_meta")) { output.add(entry);metadata=true;continue; }
                var role=original.getScriptRole(id);
                if (role.isEmpty()) continue;
                if (role.get().getTeam()==RoleType.FABLED || role.get().getTeam()==RoleType.LORIC || selected.contains(role.get().getId())) {
                    output.add(entry);included.add(role.get().getId());
                }
            }
        }
        if (!metadata) { JsonObject meta=new JsonObject();meta.addProperty("id","_meta");meta.addProperty("name","Sharktower Custom Script");output.add(meta); }
        for (String id:selected) {
            if (included.contains(id)) continue;
            Role role=Role.findById(id);
            if (role==null || role==Role.NO_ROLE) throw new IllegalArgumentException("Character is not defined in the current script: "+id);
            output.add(role.getId());
        }
        return output.toString();
    }
}
