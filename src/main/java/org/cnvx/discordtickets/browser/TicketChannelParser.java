package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.model.ServerType;
import org.cnvx.discordtickets.model.TicketCategory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TicketChannelParser {

    /*
     * Ejemplos:
     *
     * 1 👹 | 10256
     * 1👹｜10256
     * ✅-1👹｜10272
     */
    private static final Pattern SKYBLOCK_PATTERN =
            Pattern.compile(
                    "(?:^|\\s|[-✅])"
                            + "([1-5])"
                            + "\\s*"
                            + "[^\\d\\r\\n]{0,24}"
                            + "[|｜]"
                            + "\\s*"
                            + "(\\d+)"
            );

    /*
     * Ejemplos:
     *
     * basic-6643
     * hot-6141
     * burning-8000
     */
    private static final Pattern KUUDRA_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "(basic|hot|burning|fiery|infernal)"
                            + "-"
                            + "(\\d+)"
            );

    private TicketChannelParser() {
    }

    public static Optional<ParsedTicketChannel> parse(
            ServerType server,
            String rawText
    ) {
        if (server == null || rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        return switch (server) {
            case SKYBLOCK_MANIACS -> parseSkyblock(rawText);
            case KUUDRA_GANG -> parseKuudra(rawText);
        };
    }

    private static Optional<ParsedTicketChannel> parseSkyblock(
            String rawText
    ) {
        Matcher matcher = SKYBLOCK_PATTERN.matcher(rawText);

        if (!matcher.find()) {
            return Optional.empty();
        }

        int categoryNumber =
                Integer.parseInt(matcher.group(1));

        TicketCategory category =
                TicketCategory.fromSkyblockNumber(categoryNumber);

        String ticketNumber = matcher.group(2);

        String matchedName = matcher.group()
                .trim()
                .replaceFirst("^-", "");

        return Optional.of(
                new ParsedTicketChannel(
                        category,
                        ticketNumber,
                        matchedName
                )
        );
    }

    private static Optional<ParsedTicketChannel> parseKuudra(
            String rawText
    ) {
        Matcher matcher = KUUDRA_PATTERN.matcher(rawText);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String prefix = matcher.group(1);
        String ticketNumber = matcher.group(2);

        TicketCategory category =
                TicketCategory.fromKuudraPrefix(prefix);

        return Optional.of(
                new ParsedTicketChannel(
                        category,
                        ticketNumber,
                        matcher.group()
                )
        );
    }
}