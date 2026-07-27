package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.TicketCategory;

import java.util.Objects;

public record ParsedTicketChannel(
        TicketCategory category,
        String ticketNumber,
        String matchedChannelName
) {

    public ParsedTicketChannel {
        Objects.requireNonNull(category);
        Objects.requireNonNull(ticketNumber);
        Objects.requireNonNull(matchedChannelName);
    }
}
