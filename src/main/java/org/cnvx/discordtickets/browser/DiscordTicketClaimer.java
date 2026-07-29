package org.cnvx.discordtickets.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import org.cnvx.discordtickets.model.ServerType;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DiscordTicketClaimer {

    private static final String CHAT_SELECTOR =
            "ol[data-list-id='chat-messages']";

    private static final long TARGET_CLAIM_UI_TIMEOUT_MILLIS = 2_500;

    private static final double TARGET_CLAIM_UI_POLL_MILLIS = 25;

    private static final String TARGET_CLAIM_UI_SCRIPT = """
        args => {
            const expectedSuffix =
                "/" + args.channelId;

            /*
             * La URL debe corresponder al canal objetivo.
             */
            if (!window.location.pathname.endsWith(
                    expectedSuffix
            )) {
                return false;
            }

            const chat = document.querySelector(
                "ol[data-list-id='chat-messages']"
            );

            if (!chat) {
                return false;
            }

            /*
             * Evita aceptar el chat que pertenecía al canal
             * anterior mientras Discord actualiza su interfaz.
             */
            const messages = Array.from(
                chat.querySelectorAll(
                    "li[id^='chat-messages-']"
                )
            );

            const fingerprint =
                messages.length === 0
                    ? "empty"
                    : messages.length
                        + "|"
                        + messages[0].id
                        + "|"
                        + messages[messages.length - 1].id;

            if (
                args.requireDifferentChat
                && fingerprint === args.previousFingerprint
            ) {
                return false;
            }

            const chatText = (
                chat.innerText
                || chat.textContent
                || ""
            );

            /*
             * El ticket pudo haber sido reclamado mientras
             * cambiábamos de canal.
             */
            const hasConfirmation =
                /your\\s+ticket\\s+(?:was|has\\s+been)\\s+claimed\\s+by/i
                    .test(chatText)
                ||
                /ticket\\s+has\\s+been\\s+claimed\\s+by/i
                    .test(chatText);

            if (hasConfirmation) {
                return true;
            }

            if (args.server === "SKYBLOCK_MANIACS") {
                return Boolean(
                    chat.querySelector(
                        "button img[data-name='📌'], "
                            + "button img[alt='📌']"
                    )
                );
            }

            return Array.from(
                chat.querySelectorAll("button")
            ).some(button => {
                const label = (
                    button.innerText
                    || button.textContent
                    || ""
                )
                    .replace(/\\s+/g, " ")
                    .trim()
                    .toLowerCase();

                return label === "claim"
                    || label === "✅ claim";
            });
        }
        """;

    private boolean openChannelFast(
            Page page,
            ChannelObservation observation
    ) {
        String channelSuffix =
                "/" + observation.channelId();

        if (page.url().endsWith(channelSuffix)) {
            return true;
        }

        String channelPath = URI.create(
                observation.url()
        ).getPath();

        Object clicked = page.evaluate(
                """
                path => {
                    const links = document.querySelectorAll(
                        "a[href*='/channels/']"
                    );
    
                    for (const link of links) {
                        if (link.getAttribute('href') === path) {
                            link.click();
                            return true;
                        }
                    }
    
                    return false;
                }
                """,
                channelPath
        );

        if (Boolean.TRUE.equals(clicked)) {
            try {
                page.waitForURL(
                        "**/" + observation.channelId(),
                        new Page.WaitForURLOptions()
                                .setTimeout(2_000)
                );

                return true;

            } catch (PlaywrightException ignored) {
                // Se utiliza el fallback inferior.
            }
        }

        page.navigate(
                observation.url(),
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.COMMIT
                        )
                        .setTimeout(4_000)
        );

        return page.url().endsWith(channelSuffix);
    }

    public TicketClaimResult claim(
            Page page,
            ChannelObservation observation,
            String configuredUsername
    ) {

        try {
            boolean channelWasAlreadyOpen =
                    isCurrentChannel(
                            page,
                            observation
                    );

            String previousChatFingerprint =
                    channelWasAlreadyOpen
                            ? ""
                            : readChatFingerprint(page);

            if (!openChannelFast(page, observation)) {
                return new TicketClaimResult(
                        TicketClaimStatus.TECHNICAL_FAILURE,
                        "",
                        "No fue posible abrir rápidamente el canal."
                );
            }

            boolean targetInterfaceReady =
                    waitForTargetClaimUi(
                            page,
                            observation,
                            previousChatFingerprint,
                            !channelWasAlreadyOpen
                    );

            if (!targetInterfaceReady) {
                return new TicketClaimResult(
                        TicketClaimStatus.BUTTON_NOT_FOUND,
                        "",
                        "El canal objetivo abrió, pero no apareció "
                                + "el botón ni una confirmación durante "
                                + TARGET_CLAIM_UI_TIMEOUT_MILLIS
                                + " ms."
                );
            }

            /*
             * Puede ocurrir que otra persona lo reclame entre
             * la detección y nuestra llegada al canal.
             */
            Optional<String> existingConfirmation =
                    findLatestConfirmationText(page);

            if (existingConfirmation.isPresent()) {
                return resultFromConfirmation(
                        existingConfirmation.get(),
                        configuredUsername
                );
            }

            Optional<Locator> claimButton =
                    findClaimButton(
                            page,
                            observation.server()
                    );

            if (claimButton.isEmpty()) {
                /*
                 * Volvemos a buscar confirmación porque el botón
                 * pudo desaparecer justo durante la inspección.
                 */
                existingConfirmation =
                        findLatestConfirmationText(page);

                if (existingConfirmation.isPresent()) {
                    return resultFromConfirmation(
                            existingConfirmation.get(),
                            configuredUsername
                    );
                }

                return new TicketClaimResult(
                        TicketClaimStatus.BUTTON_NOT_FOUND,
                        "",
                        "No se encontró el botón de reclamación."
                );
            }

            long clickStartedAt = System.nanoTime();

            try {
                claimButton.get().click(
                        new Locator.ClickOptions()
                                .setTimeout(3_000)
                );

            } catch (PlaywrightException exception) {
                Optional<String> confirmationAfterFailure =
                        findLatestConfirmationText(page);

                if (confirmationAfterFailure.isPresent()) {
                    return resultFromConfirmation(
                            confirmationAfterFailure.get(),
                            configuredUsername
                    );
                }

                return new TicketClaimResult(
                        TicketClaimStatus.CLICK_FAILED,
                        "",
                        exception.getMessage()
                );
            }

            long clickElapsedMillis =
                    (System.nanoTime() - clickStartedAt)
                            / 1_000_000;

            return new TicketClaimResult(
                    TicketClaimStatus.CLICK_SENT,
                    "",
                    "El clic fue enviado en "
                            + clickElapsedMillis
                            + " ms."
            );

        } catch (PlaywrightException exception) {
            return new TicketClaimResult(
                    TicketClaimStatus.TECHNICAL_FAILURE,
                    "",
                    exception.getMessage()
            );
        }
    }

    private Optional<Locator> findClaimButton(
            Page page,
            ServerType server
    ) {
        return switch (server) {
            case SKYBLOCK_MANIACS ->
                    findSkyblockClaimButton(page);

            case KUUDRA_GANG ->
                    findKuudraClaimButton(page);
        };
    }

    private Optional<Locator> findSkyblockClaimButton(
            Page page
    ) {
        Locator buttons = page.locator(
                CHAT_SELECTOR
                        + " button:has(img[data-name='📌']), "
                        + CHAT_SELECTOR
                        + " button:has(img[alt='📌'])"
        );

        return firstVisibleEnabled(buttons);
    }

    private Optional<Locator> findKuudraClaimButton(
            Page page
    ) {
        Locator buttons = page.locator(
                CHAT_SELECTOR + " button"
        );

        int count = buttons.count();

        for (int index = 0; index < count; index++) {
            Locator button = buttons.nth(index);

            if (!button.isVisible() || !button.isEnabled()) {
                continue;
            }

            String text = button.innerText()
                    .replaceAll("\\s+", " ")
                    .trim();

            /*
             * No usamos contains("Claim"), porque también
             * coincidiría con "Unclaim".
             */
            if ("Claim".equalsIgnoreCase(text)
                    || "✅ Claim".equalsIgnoreCase(text)) {
                return Optional.of(button);
            }
        }

        return Optional.empty();
    }

    private Optional<Locator> firstVisibleEnabled(
            Locator locators
    ) {
        int count = locators.count();

        for (int index = 0; index < count; index++) {
            Locator locator = locators.nth(index);

            if (locator.isVisible() && locator.isEnabled()) {
                return Optional.of(locator);
            }
        }

        return Optional.empty();
    }

    private Optional<String> findLatestConfirmationText(
            Page page
    ) {
        Locator messages = page.locator(
                CHAT_SELECTOR
                        + " li[id^='chat-messages-']"
        );

        for (int index = messages.count() - 1;
             index >= 0;
             index--) {

            Locator message = messages.nth(index);

            if (!message.isVisible()) {
                continue;
            }

            String text = message.innerText();

            if (ClaimConfirmationMatcher
                    .extractClaimedBy(text)
                    .isPresent()) {

                return Optional.of(text);
            }
        }

        return Optional.empty();
    }

    private TicketClaimResult resultFromConfirmation(
            String confirmationText,
            String configuredUsername
    ) {
        String claimedBy =
                ClaimConfirmationMatcher
                        .extractClaimedBy(confirmationText)
                        .orElse("");

        boolean ours =
                ClaimConfirmationMatcher
                        .matchesConfiguredUser(
                                claimedBy,
                                configuredUsername
                        );

        if (ours) {
            return new TicketClaimResult(
                    TicketClaimStatus.CLAIMED_BY_US,
                    claimedBy,
                    "Discord confirmó la reclamación."
            );
        }

        return new TicketClaimResult(
                TicketClaimStatus.CLAIMED_BY_OTHER,
                claimedBy,
                "Discord confirmó que otra persona "
                        + "reclamó el ticket."
        );
    }

    public Optional<TicketClaimResult>
    inspectCurrentChannelConfirmation(
            Page page,
            String configuredMinecraftUsername
    ) {
        Objects.requireNonNull(
                page,
                "La página es obligatoria."
        );

        Objects.requireNonNull(
                configuredMinecraftUsername,
                "El nombre de Minecraft es obligatorio."
        );

        return findLatestConfirmationText(page)
                .map(confirmationText ->
                        resultFromConfirmation(
                                confirmationText,
                                configuredMinecraftUsername
                        )
                );
    }

    private String readChatFingerprint(Page page) {
        Object rawFingerprint = page.evaluate(
                """
                () => {
                    const chat = document.querySelector(
                        "ol[data-list-id='chat-messages']"
                    );
    
                    if (!chat) {
                        return "";
                    }
    
                    const messages = Array.from(
                        chat.querySelectorAll(
                            "li[id^='chat-messages-']"
                        )
                    );
    
                    if (messages.length === 0) {
                        return "empty";
                    }
    
                    return messages.length
                        + "|"
                        + messages[0].id
                        + "|"
                        + messages[messages.length - 1].id;
                }
                """
        );

        return Objects.toString(
                rawFingerprint,
                ""
        );
    }

    private boolean isCurrentChannel(
            Page page,
            ChannelObservation observation
    ) {
        String path = URI.create(
                page.url()
        ).getPath();

        return path != null
                && path.endsWith(
                "/" + observation.channelId()
        );
    }

    private boolean waitForTargetClaimUi(
            Page page,
            ChannelObservation observation,
            String previousChatFingerprint,
            boolean requireDifferentChat
    ) {
        try {
            page.waitForFunction(
                    TARGET_CLAIM_UI_SCRIPT,
                    Map.of(
                            "channelId",
                            observation.channelId(),

                            "server",
                            observation.server().name(),

                            "previousFingerprint",
                            previousChatFingerprint,

                            "requireDifferentChat",
                            requireDifferentChat
                    ),
                    new Page.WaitForFunctionOptions()
                            .setTimeout(
                                    TARGET_CLAIM_UI_TIMEOUT_MILLIS
                            )
                            .setPollingInterval(
                                    TARGET_CLAIM_UI_POLL_MILLIS
                            )
            );

            return true;

        } catch (PlaywrightException exception) {
            return false;
        }
    }
}