package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.net.URI;
import java.util.*;

public final class DiscordDomInspector {

    private static final String MENTIONED_CHANNELS_SCRIPT = """
        () => {
            const result = [];

            const items = document.querySelectorAll(
                "[data-list-item-id^='channels___']"
            );

            for (const item of items) {
                let current = item;
                let hasMention = false;

                for (
                    let depth = 0;
                    current && depth < 6;
                    depth++, current = current.parentElement
                ) {
                    const badge = current.querySelector(
                        '[class*="numberBadge"]'
                    );

                    if (
                        badge
                        && /^\\d+$/.test(
                            (badge.textContent || '').trim()
                        )
                    ) {
                        hasMention = true;
                        break;
                    }

                    const spans = current.querySelectorAll('span');

                    for (const span of spans) {
                        const text = span.textContent || '';

                        if (/\\d+\\s+menci[oó]n(?:es)?/i.test(text)) {
                            hasMention = true;
                            break;
                        }
                    }

                    if (hasMention) {
                        break;
                    }
                }

                if (!hasMention) {
                    continue;
                }

                const link = item.matches(
                    "a[href*='/channels/']"
                )
                    ? item
                    : item.querySelector(
                        "a[href*='/channels/']"
                    );

                if (!link) {
                    continue;
                }

                result.push({
                    text: (
                        item.innerText
                        || item.textContent
                        || ''
                    ).trim(),

                    href: link.getAttribute('href') || ''
                });
            }

            return result;
        }
        """;

    public List<ChannelObservation> findMentionedTicketChannels(
            Page page,
            ServerType server
    ) {
        Object rawResult = page.evaluate(
                MENTIONED_CHANNELS_SCRIPT
        );

        if (!(rawResult instanceof List<?> rows)) {
            return List.of();
        }

        Map<String, ChannelObservation> observations =
                new LinkedHashMap<>();

        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) {
                continue;
            }

            String text = Objects.toString(
                    row.get("text"),
                    ""
            );

            String href = Objects.toString(
                    row.get("href"),
                    ""
            );

            if (text.isBlank() || href.isBlank()) {
                continue;
            }

            var parsedOptional =
                    TicketChannelParser.parse(
                            server,
                            text
                    );

            if (parsedOptional.isEmpty()) {
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
        }

        return List.copyOf(
                observations.values()
        );
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