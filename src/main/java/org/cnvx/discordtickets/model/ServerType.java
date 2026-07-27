package org.cnvx.discordtickets.model;

public enum ServerType {

    SKYBLOCK_MANIACS("Skyblock Maniacs"),
    KUUDRA_GANG("Kuudra Gang");

    private final String displayName;

    ServerType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
