package com.sharktower.bloodonthesharktower.setup;
public final class ScriptImportRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static void rejects(Runnable action,String message) { try { action.run(); } catch (IllegalArgumentException e) { checks++;return; }throw new AssertionError(message); }
    public static void main(String[] args) {
        var page=BotcScriptImport.resolve("https://botcscripts.com/script/6457");
        check(page.scriptId() && page.endpoint().getPath().equals("/api/script_ids/6457/"),"Page IDs use script-ID API, not version API");
        check(BotcScriptImport.resolve("https://www.botcscripts.com/script/6457/1.2.0/download").version().equals("1.2.0"),"Explicit page version preserved");
        check(!BotcScriptImport.resolve("https://botcscripts.com/api/scripts/11395/json/").scriptId(),"JSON links use version IDs");
        check(!BotcScriptImport.resolve("https://botcscripts.com/api/scripts/11395/").scriptId(),"Version metadata links supported");
        for (String bad:new String[]{"http://botcscripts.com/script/1","https://evil.test/script/1","https://botcscripts.com.evil.test/script/1","https://botcscripts.com@evil.test/script/1","https://user@botcscripts.com/script/1","https://botcscripts.com:8443/script/1","https://botcscripts.com/api/scripts/","https://botcscripts.com/script/1?url=https://evil.test","https://botcscripts.com/script/%2e%2e/1","file:///tmp/script.json","https://botcscripts.com/api/script_ids/1/json/"})
            rejects(()->BotcScriptImport.resolve(bad),"Reject unsupported or unsafe link: "+bad);
        check(BotcScriptImport.filename("../../A Script! 🦈").equals("a-script.json"),"Names sanitized without traversal");
        check(BotcScriptImport.filename("🦈").equals("custom-script.json"),"Empty sanitized names have a fallback");
        check(BotcScriptImport.filename("a".repeat(200)).length()==85,"Filename length bounded");
        check(BotcScriptImport.validate("[{\"id\":\"_meta\",\"name\":\"Test\"},\"chef\",\"imp\"]").allRoles().size()==2,"Official script validates");
        rejects(()->BotcScriptImport.validate("[\"chef\",\"unsupported-character\"]"),"Unknown IDs cannot silently disappear");
        rejects(()->BotcScriptImport.validate("[{\"id\":\"_meta\"}]"),"Empty scripts rejected");
        rejects(()->BotcScriptImport.validate("{\"error\":\"not a script\"}"),"Non-script JSON rejected");
        rejects(()->BotcScriptImport.validate("[\"chef\",42]"),"Malformed entries rejected");
        rejects(()->BotcScriptImport.validate(" ".repeat(BotcScriptImport.MAX_BYTES+1)),"Oversized payload rejected");
        String homebrew="[{\"id\":\"_meta\",\"name\":\"Saved\",\"bootlegger\":[\"Keep rule\"]},{\"id\":\"testhomebrew\",\"name\":\"Test Homebrew\",\"team\":\"townsfolk\",\"ability\":\"An ability\"},\"imp\"]";
        var custom=BotcScriptImport.validate(homebrew);
        String edited=com.sharktower.bloodonthesharktower.core.ScriptSelection.build(custom,new java.util.LinkedHashSet<>(java.util.List.of("testhomebrew","chef")));
        var saved=BotcScriptImport.validate(edited);
        check(saved.customRoles().size()==1,"Builder retains homebrew definition");
        check(saved.bootlegger().contains("Keep rule") && saved.name().equals("Saved"),"Builder retains metadata and rules");
        check(saved.getScriptRole("imp").isEmpty() && saved.getScriptRole("chef").isPresent(),"Builder removes and adds selected official characters");
        System.out.println("PASS: "+checks+" custom-script import validation checks");
    }
}
