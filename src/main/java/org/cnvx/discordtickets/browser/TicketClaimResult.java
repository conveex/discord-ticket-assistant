package org.cnvx.discordtickets.browser;

import java.util.Objects;

public record TicketClaimResult(
        TicketClaimStatus status,
        String claimedBy,
        String detail
) {

    public TicketClaimResult {
        Objects.requireNonNull(
                status,
                "El estado de la reclamación es obligatorio."
        );

        claimedBy = claimedBy == null
                ? ""
                : claimedBy.trim();

        detail = detail == null
                ? ""
                : detail.trim();
    }
}
