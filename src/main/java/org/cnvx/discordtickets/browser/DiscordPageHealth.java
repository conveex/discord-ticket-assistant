package org.cnvx.discordtickets.browser;

public record DiscordPageHealth(
        String currentUrl,
        String currentGuildId,
        String readyState,
        String visibilityState,
        boolean wasDiscarded,
        int renderedChannelItems,
        int guildMentionCount
) {

    public DiscordPageHealth {
        currentUrl = currentUrl == null ? "" : currentUrl;
        currentGuildId =
                currentGuildId == null ? "" : currentGuildId;
        readyState = readyState == null ? "" : readyState;
        visibilityState =
                visibilityState == null ? "" : visibilityState;

        if (renderedChannelItems < 0) {
            throw new IllegalArgumentException(
                    "La cantidad de canales no puede ser negativa."
            );
        }

        if (guildMentionCount < 0) {
            throw new IllegalArgumentException(
                    "La cantidad de menciones no puede ser negativa."
            );
        }
    }

    public boolean structurallyHealthyFor(
            String expectedGuildId
    ) {
        return !wasDiscarded
                && expectedGuildId != null
                && !expectedGuildId.isBlank()
                && expectedGuildId.equals(currentGuildId)
                && renderedChannelItems > 0;
    }
}