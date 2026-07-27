package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscordDomInspector {

    private static final String CHANNEL_ITEM_SELECTOR =
            "[data-list-item-id^='channels___']";

    private static final String HAS_MENTION_SCRIPT = """
            el => {
                let current = el;

                for (
                    let depth = 0;
                    current && depth < 5;
                    depth++, current = current.parentElement
                ) {
                    const numericBadge = current.querySelector(
                        '[class*="numberBadge"]'
                    );

                    if (
                        numericBadge
                        && /^\\d+$/.test(
                            (numericBadge.textContent || '').trim()
                        )
                    ) {
                        return true;
                    }

                    const accessibleTexts =
                        current.querySelectorAll('span');

                    for (const span of accessibleTexts) {
                        const text = span.textContent || '';

                        if (/\\d+\\s+menci[oó]n/i.test(text)) {
                            return true;
                        }
                    }
                }

                return false;
            }
            """;

    public List<ChannelObservation> findMentionedTicketChannels(
            Page page,
            ServerType server
    ) {
        Locator items = page.locator(CHANNEL_ITEM_SELECTOR);

        Map<String, ChannelObservation> observations =
                new LinkedHashMap<>();

        int count = items.count();

        for (int index = 0; index < count; index++) {
            Locator item = items.nth(index);

            try {
                String visibleText = item.innerText();

                var parsedOptional =
                        TicketChannelParser.parse(
                                server,
                                visibleText
                        );

                if (parsedOptional.isEmpty()) {
                    continue;
                }

                if (!hasMention(item)) {
                    continue;
                }

                String href = findHref(item);

                if (href == null || href.isBlank()) {
                    continue;
                }

                String absoluteUrl = resolveUrl(
                        page.url(),
                        href
                );

                String channelId =
                        extractChannelId(absoluteUrl);

                if (channelId == null) {
                    continue;
                }

                ParsedTicketChannel parsed =
                        parsedOptional.get();

                observations.put(
                        channelId,
                        new ChannelObservation(
                                server,
                                channelId,
                                parsed.matchedChannelName(),
                                parsed.ticketNumber(),
                                parsed.category(),
                                absoluteUrl
                        )
                );
            } catch (PlaywrightException exception) {
                /*
                 * Discord puede volver a dibujar la barra lateral
                 * mientras la estamos leyendo. Ese elemento se
                 * ignora y aparecerá en la siguiente inspección.
                 */
            }
        }

        return new ArrayList<>(observations.values());
    }

    private boolean hasMention(Locator item) {
        Object result = item.evaluate(HAS_MENTION_SCRIPT);
        return Boolean.TRUE.equals(result);
    }

    private String findHref(Locator item) {
        String ownHref = item.getAttribute("href");

        if (ownHref != null && !ownHref.isBlank()) {
            return ownHref;
        }

        Locator link = item.locator(
                "a[href*='/channels/']"
        ).first();

        if (link.count() == 0) {
            return null;
        }

        return link.getAttribute("href");
    }

    private String resolveUrl(
            String currentPageUrl,
            String href
    ) {
        URI hrefUri = URI.create(href);

        if (hrefUri.isAbsolute()) {
            return hrefUri.toString();
        }

        return URI.create(currentPageUrl)
                .resolve(hrefUri)
                .toString();
    }

    private String extractChannelId(String url) {
        String path = URI.create(url).getPath();

        if (path == null || path.isBlank()) {
            return null;
        }

        String[] segments = path.split("/");

        for (int index = segments.length - 1; index >= 0; index--) {
            if (!segments[index].isBlank()) {
                return segments[index];
            }
        }

        return null;
    }
}