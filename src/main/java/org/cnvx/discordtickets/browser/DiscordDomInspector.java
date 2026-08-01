package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;
import com.microsoft.playwright.Page;

import java.net.URI;
import java.util.*;

public final class DiscordDomInspector {

    private static final String PAGE_INSPECTION_SCRIPT = """
        expectedGuildId => {
            const result = [];

            const items = Array.from(
                document.querySelectorAll(
                    "[data-list-item-id^='channels___']"
                )
            );

            function hasMentionIndicator(item) {
                let current = item;

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
                        return true;
                    }

                    const combinedText = [
                        current.getAttribute('aria-label') || '',
                        current.getAttribute('title') || '',
                        current.innerText || ''
                    ].join(' ');

                    if (
                        /\\d+\\s+(?:menci[oó]n(?:es)?|mentions?)/i
                            .test(combinedText)
                    ) {
                        return true;
                    }
                }

                return false;
            }

            for (const item of items) {
                if (!hasMentionIndicator(item)) {
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

            function readGuildMentionCount(guildId) {
                if (!guildId) {
                    return 0;
                }

                const guildItem = document.querySelector(
                    `[data-list-item-id="guildsnav___${guildId}"]`
                );

                if (!guildItem) {
                    return 0;
                }

                const numericBadges =
                    guildItem.querySelectorAll(
                        '[class*="numberBadge"]'
                    );

                for (const badge of numericBadges) {
                    const text = (
                        badge.textContent || ''
                    ).trim();

                    if (/^\\d+$/.test(text)) {
                        return Number.parseInt(text, 10);
                    }
                }

                const combinedText = [
                    guildItem.getAttribute('aria-label') || '',
                    guildItem.getAttribute('title') || '',
                    guildItem.innerText || ''
                ].join(' ');

                const match = combinedText.match(
                    /(\\d+)\\s+(?:menci[oó]n(?:es)?|mentions?)/i
                );

                return match
                    ? Number.parseInt(match[1], 10)
                    : 0;
            }

            const pathSegments =
                window.location.pathname
                    .split('/')
                    .filter(Boolean);

            const currentGuildId =
                pathSegments.length >= 2
                && pathSegments[0] === 'channels'
                    ? pathSegments[1]
                    : '';

            return {
                rows: result,

                health: {
                    currentUrl: window.location.href,
                    currentGuildId,
                    readyState: document.readyState,
                    visibilityState:
                        document.visibilityState || '',
                    wasDiscarded:
                        Boolean(document.wasDiscarded),
                    renderedChannelItems: items.length,
                    guildMentionCount:
                        readGuildMentionCount(
                            expectedGuildId
                        )
                }
            };
        }
        """;

    public DiscordPageInspection inspect(
            Page page,
            ServerType server,
            String expectedGuildId
    ) {
        Object rawResult = page.evaluate(
                PAGE_INSPECTION_SCRIPT,
                expectedGuildId
        );

        if (!(rawResult instanceof Map<?, ?> root)) {
            return emptyInspection(page);
        }

        DiscordPageHealth health =
                readHealth(root, page);

        Object rawRows = root.get("rows");

        if (!(rawRows instanceof List<?> rows)) {
            return new DiscordPageInspection(
                    List.of(),
                    health
            );
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

        return new DiscordPageInspection(
                List.copyOf(observations.values()),
                health
        );
    }

    public List<ChannelObservation> findMentionedTicketChannels(
            Page page,
            ServerType server
    ) {
        String guildId = extractGuildId(page.url());

        return inspect(
                page,
                server,
                guildId
        ).observations();
    }

    private DiscordPageHealth readHealth(
            Map<?, ?> root,
            Page page
    ) {
        Object rawHealth = root.get("health");

        if (!(rawHealth instanceof Map<?, ?> health)) {
            return new DiscordPageHealth(
                    page.url(),
                    "",
                    "",
                    "",
                    false,
                    0,
                    0
            );
        }

        return new DiscordPageHealth(
                Objects.toString(
                        health.get("currentUrl"),
                        page.url()
                ),
                Objects.toString(
                        health.get("currentGuildId"),
                        ""
                ),
                Objects.toString(
                        health.get("readyState"),
                        ""
                ),
                Objects.toString(
                        health.get("visibilityState"),
                        ""
                ),
                Boolean.TRUE.equals(
                        health.get("wasDiscarded")
                ),
                integerValue(
                        health.get("renderedChannelItems")
                ),
                integerValue(
                        health.get("guildMentionCount")
                )
        );
    }

    private DiscordPageInspection emptyInspection(
            Page page
    ) {
        return new DiscordPageInspection(
                List.of(),
                new DiscordPageHealth(
                        page.url(),
                        "",
                        "",
                        "",
                        false,
                        0,
                        0
                )
        );
    }

    private int integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String extractGuildId(String url) {
        String path = URI.create(url).getPath();

        if (path == null) {
            return "";
        }

        String[] segments = path.split("/");

        /*
         * /channels/GUILD_ID/CHANNEL_ID
         */
        for (int index = 0;
             index < segments.length - 1;
             index++) {

            if ("channels".equals(segments[index])) {
                return segments[index + 1];
            }
        }

        return "";
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