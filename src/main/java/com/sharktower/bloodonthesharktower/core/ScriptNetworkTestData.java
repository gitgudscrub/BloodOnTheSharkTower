package com.sharktower.bloodonthesharktower.core;

/** Fixed diagnostic script used only to validate full Script network transport. */
public final class ScriptNetworkTestData {
    private ScriptNetworkTestData() {}

    public static final String JSON = """
            [
              {"id":"_meta","name":"Network Test","author":"Sharktower Port Test"},
              "washerwoman",
              "librarian",
              "empath",
              "drunk",
              "poisoner",
              "imp"
            ]
            """;

    public static Script create() {
        return Script.fromJson(JSON).orElseThrow(() ->
                new IllegalStateException("Built-in network test script failed to parse")
        );
    }
}
