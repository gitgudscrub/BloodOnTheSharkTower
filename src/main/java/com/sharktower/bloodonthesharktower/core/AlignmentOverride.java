package com.sharktower.bloodonthesharktower.core;

/** Port of BOTB's three-state alignment override model. */
public enum AlignmentOverride {
    DEFAULT("default"),
    FORCE_GOOD("good"),
    FORCE_BAD("bad");

    private final String translationSuffix;

    AlignmentOverride(String translationSuffix) {
        this.translationSuffix = translationSuffix;
    }

    public String getTranslationKey() {
        return "gui.blood_on_the_sharktower.alignment_override." + translationSuffix;
    }

    /** Temporary server-safe fallback until the translated UI layer is ported. */
    public String getDisplayName() {
        return switch (this) {
            case DEFAULT -> "Default";
            case FORCE_GOOD -> "Good";
            case FORCE_BAD -> "Bad";
        };
    }

    public AlignmentOverride next() {
        AlignmentOverride[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
