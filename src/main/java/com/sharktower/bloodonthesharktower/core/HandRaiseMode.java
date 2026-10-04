package com.sharktower.bloodonthesharktower.core;
/** Shared client/server gate for speaking queues and election hands. */
public enum HandRaiseMode {
    OFF, SPEAKING, VOTING;
    public static HandRaiseMode determine(int night,int day,boolean nominationsOpen,
                                          Object nominee,boolean voting,Object exile,boolean exileVoting,boolean ended) {
        if (ended || day<=0 || night!=day) return OFF;
        // Traveller exile support retains its existing daytime voting controls.
        if (exile!=null || exileVoting) return VOTING;
        if (!nominationsOpen) return OFF;
        return nominee!=null || voting ? VOTING : SPEAKING;
    }
}
