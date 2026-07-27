package org.cnvx.discordtickets.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import org.cnvx.discordtickets.model.ServerType;

import java.util.Optional;

public final class DiscordTicketClaimer {

    private static final String CHAT_SELECTOR =
            "ol[data-list-id='chat-messages']";

    private static final long CHAT_TIMEOUT_MILLIS = 10_000;
    private static final long CONFIRMATION_TIMEOUT_MILLIS = 8_000;
    private static final long POLLING_INTERVAL_MILLIS = 200;

    public TicketClaimResult claim(
            Page page,
            ChannelObservation observation,
            String configuredUsername
    ) {
        boolean clickCompleted = false;

        try {
            page.navigate(
                    observation.url(),
                    new Page.NavigateOptions()
                            .setWaitUntil(
                                    WaitUntilState.DOMCONTENTLOADED
                            )
                            .setTimeout(20_000)
            );

            if (!waitForChat(page)) {
                return new TicketClaimResult(
                        TicketClaimStatus.TECHNICAL_FAILURE,
                        "",
                        "El canal abrió, pero no apareció el chat."
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

            try {
                claimButton.get().click(
                        new Locator.ClickOptions()
                                .setTimeout(5_000)
                );

                clickCompleted = true;
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

            return waitForConfirmation(
                    page,
                    configuredUsername
            );

        } catch (PlaywrightException exception) {
            /*
             * Si el clic ya terminó, no sabemos con certeza si
             * Discord procesó la interacción. Por seguridad no
             * liberaremos posteriormente ese espacio.
             */
            if (clickCompleted) {
                return new TicketClaimResult(
                        TicketClaimStatus.CONFIRMATION_TIMEOUT,
                        "",
                        "El clic terminó, pero hubo un error "
                                + "al leer la confirmación: "
                                + exception.getMessage()
                );
            }

            return new TicketClaimResult(
                    TicketClaimStatus.TECHNICAL_FAILURE,
                    "",
                    exception.getMessage()
            );
        }
    }

    private boolean waitForChat(Page page) {
        long deadline =
                System.currentTimeMillis()
                        + CHAT_TIMEOUT_MILLIS;

        Locator chat = page.locator(CHAT_SELECTOR);

        while (System.currentTimeMillis() < deadline) {
            if (chat.count() > 0 && chat.first().isVisible()) {
                return true;
            }

            page.waitForTimeout(POLLING_INTERVAL_MILLIS);
        }

        return false;
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
                        + " button:has(img[data-name='📌'])"
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

    private TicketClaimResult waitForConfirmation(
            Page page,
            String configuredUsername
    ) {
        long deadline =
                System.currentTimeMillis()
                        + CONFIRMATION_TIMEOUT_MILLIS;

        while (System.currentTimeMillis() < deadline) {
            Optional<String> confirmation =
                    findLatestConfirmationText(page);

            if (confirmation.isPresent()) {
                return resultFromConfirmation(
                        confirmation.get(),
                        configuredUsername
                );
            }

            page.waitForTimeout(POLLING_INTERVAL_MILLIS);
        }

        return new TicketClaimResult(
                TicketClaimStatus.CONFIRMATION_TIMEOUT,
                "",
                "El clic se realizó, pero Discord no mostró "
                        + "confirmación durante "
                        + CONFIRMATION_TIMEOUT_MILLIS
                        + " ms."
        );
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
}