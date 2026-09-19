package com.sharktower.bloodonthesharktower.daytime;

/** Logical portion of BOTB's election configuration; world-indicator block names are retained. */
public final class ElectionConfig {
    private final ElectionType type;
    private final boolean skipUsedGhostVotes;
    private final boolean applyBansheeMultiplier;
    private final boolean applyOrganGrinderMode;
    private final boolean trackLegionVotes;
    private final boolean triggerRoleAbilities;
    private final boolean useAliveForThreshold;
    private final boolean consumeGhostVotes;
    private final String indicatorBlockOn;
    private final String indicatorBlockOff;
    private final String indicatorBlockGhostOn;
    private final String indicatorBlockGhostOff;
    private final String indicatorBlockDouble;

    private ElectionConfig(Builder b) {
        this.type = b.type;
        this.skipUsedGhostVotes = b.skipUsedGhostVotes;
        this.applyBansheeMultiplier = b.applyBansheeMultiplier;
        this.applyOrganGrinderMode = b.applyOrganGrinderMode;
        this.trackLegionVotes = b.trackLegionVotes;
        this.triggerRoleAbilities = b.triggerRoleAbilities;
        this.useAliveForThreshold = b.useAliveForThreshold;
        this.consumeGhostVotes = b.consumeGhostVotes;
        this.indicatorBlockOn = b.indicatorBlockOn;
        this.indicatorBlockOff = b.indicatorBlockOff;
        this.indicatorBlockGhostOn = b.indicatorBlockGhostOn;
        this.indicatorBlockGhostOff = b.indicatorBlockGhostOff;
        this.indicatorBlockDouble = b.indicatorBlockDouble;
    }

    public ElectionType getType() { return type; }
    public boolean skipUsedGhostVotes() { return skipUsedGhostVotes; }
    public boolean applyBansheeMultiplier() { return applyBansheeMultiplier; }
    public boolean applyOrganGrinderMode() { return applyOrganGrinderMode; }
    public boolean trackLegionVotes() { return trackLegionVotes; }
    public boolean triggerRoleAbilities() { return triggerRoleAbilities; }
    public boolean useAliveForThreshold() { return useAliveForThreshold; }
    public boolean consumeGhostVotes() { return consumeGhostVotes; }
    public String getIndicatorBlockOn() { return indicatorBlockOn; }
    public String getIndicatorBlockOff() { return indicatorBlockOff; }
    public String getIndicatorBlockGhostOn() { return indicatorBlockGhostOn; }
    public String getIndicatorBlockGhostOff() { return indicatorBlockGhostOff; }
    public String getIndicatorBlockDouble() { return indicatorBlockDouble; }

    public static ElectionConfig forVote(boolean organGrinder) {
        return new Builder(ElectionType.VOTE)
                .skipUsedGhostVotes(true)
                .applyBansheeMultiplier(true)
                .applyOrganGrinderMode(organGrinder)
                .useAliveForThreshold(true)
                .consumeGhostVotes(true)
                .build();
    }

    public static ElectionConfig forExileSupport() {
        return new Builder(ElectionType.EXILE_SUPPORT)
                .skipUsedGhostVotes(false)
                .applyBansheeMultiplier(false)
                .useAliveForThreshold(false)
                .consumeGhostVotes(false)
                .build();
    }

    public static final class Builder {
        private final ElectionType type;
        private boolean skipUsedGhostVotes;
        private boolean applyBansheeMultiplier;
        private boolean applyOrganGrinderMode;
        private boolean trackLegionVotes;
        private boolean triggerRoleAbilities = true;
        private boolean useAliveForThreshold;
        private boolean consumeGhostVotes;
        private String indicatorBlockOn = "";
        private String indicatorBlockOff = "";
        private String indicatorBlockGhostOn = "";
        private String indicatorBlockGhostOff = "";
        private String indicatorBlockDouble = "";

        public Builder(ElectionType type) { this.type = type; }
        public Builder skipUsedGhostVotes(boolean v) { this.skipUsedGhostVotes = v; return this; }
        public Builder applyBansheeMultiplier(boolean v) { this.applyBansheeMultiplier = v; return this; }
        public Builder applyOrganGrinderMode(boolean v) { this.applyOrganGrinderMode = v; return this; }
        public Builder trackLegionVotes(boolean v) { this.trackLegionVotes = v; return this; }
        public Builder triggerRoleAbilities(boolean v) { this.triggerRoleAbilities = v; return this; }
        public Builder useAliveForThreshold(boolean v) { this.useAliveForThreshold = v; return this; }
        public Builder consumeGhostVotes(boolean v) { this.consumeGhostVotes = v; return this; }
        public Builder indicatorBlockOn(String v) { this.indicatorBlockOn = v; return this; }
        public Builder indicatorBlockOff(String v) { this.indicatorBlockOff = v; return this; }
        public Builder indicatorBlockGhostOn(String v) { this.indicatorBlockGhostOn = v; return this; }
        public Builder indicatorBlockGhostOff(String v) { this.indicatorBlockGhostOff = v; return this; }
        public Builder indicatorBlockDouble(String v) { this.indicatorBlockDouble = v; return this; }
        public ElectionConfig build() { return new ElectionConfig(this); }
    }
}
