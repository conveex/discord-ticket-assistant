package org.cnvx.discordtickets.browser;

import java.util.Objects;

public record BrowserStatusEvent(
        BrowserConnectionState state,
        String detail
) {

    public BrowserStatusEvent {
        Objects.requireNonNull(
                state,
                "El estado del navegador es obligatorio."
        );

        detail = detail == null
                ? ""
                : detail.trim();
    }
}