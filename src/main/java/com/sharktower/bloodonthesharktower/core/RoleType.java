package com.sharktower.bloodonthesharktower.core;

import java.util.Locale;

/**
 * Port of the original BOTB team/type model.
 *
 * Translation lookup is intentionally deferred until the client/UI port. The
 * identifiers, alignment defaults and colours match the original 1.3.0 class.
 */
public enum RoleType {
    TOWNSFOLK(true,  -16733441),
    OUTSIDER(true,   -16720470),
    MINION(false,       -43691),
    DEMON(false,      -5636096),
    TRAVELER(true,    -6737204),
    FABLED(true,      -2838729),
    LORIC(true,      -13726889),
    NONE(true,              -1);

    private final boolean defaultGood;
    private final int color;
    private final String translationKey;

    RoleType(boolean defaultGood, int color) {
        this.defaultGood = defaultGood;
        this.color = color;
        this.translationKey = "roletype.blood_on_the_sharktower."
                + name().toLowerCase(Locale.ROOT);
    }

    public boolean isDefaultGood() {
        return defaultGood;
    }

    public int getColor() {
        return color;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return RoleText.get(translationKey, name());
    }

    public String getShortName() {
        return RoleText.get(translationKey + ".short", getDisplayName());
    }

    public String getPluralName() {
        return RoleText.get(translationKey + ".plural", getDisplayName());
    }
}
