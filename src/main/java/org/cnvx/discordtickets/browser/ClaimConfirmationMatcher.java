package org.cnvx.discordtickets.browser;

import org.cnvx.discordtickets.util.UsernameNormalizer;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaimConfirmationMatcher {

    /*
     * Soporta:
     *
     * Your ticket was claimed by @[21] convecs_.
     *
     * @cliente, your ticket has been claimed by @convecs_.
     */
    private static final Pattern CLAIMED_BY_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "(?:"
                            + "your\\s+ticket\\s+"
                            + "(?:was|has\\s+been)\\s+"
                            + "claimed\\s+by"
                            + "|"
                            + "ticket\\s+has\\s+been\\s+claimed\\s+by"
                            + ")"
                            + "\\s+"
                            + "(.+?)"
                            + "(?:\\.|\\R|$)"
            );

    private ClaimConfirmationMatcher() {
    }

    public static Optional<String> extractClaimedBy(
            String confirmationText
    ) {
        if (confirmationText == null
                || confirmationText.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher =
                CLAIMED_BY_PATTERN.matcher(confirmationText);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String claimedBy = matcher.group(1).trim();

        return claimedBy.isBlank()
                ? Optional.empty()
                : Optional.of(claimedBy);
    }

    public static boolean matchesConfiguredUser(
            String visibleClaimedBy,
            String configuredMinecraftUsername
    ) {
        String normalizedVisible =
                normalizeVisibleClaimedBy(visibleClaimedBy);

        String normalizedMinecraftUsername =
                UsernameNormalizer
                        .normalize(configuredMinecraftUsername)
                        .toLowerCase(Locale.ROOT);

        return !normalizedMinecraftUsername.isBlank()
                && normalizedVisible.equals(
                normalizedMinecraftUsername
        );
    }

    public static String normalizeVisibleClaimedBy(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();

        /*
         * @convecs_          -> convecs_
         * @[22] convecs_     -> [22] convecs_
         */
        while (normalized.startsWith("@")) {
            normalized = normalized
                    .substring(1)
                    .trim();
        }

        /*
         * Elimina cero o más decoraciones iniciales:
         *
         * [22] convecs_          -> convecs_
         * [22] [MVP] convecs_    -> convecs_
         */
        while (true) {
            String withoutDecoration = normalized.replaceFirst(
                    "^\\[[^\\]]+]\\s*",
                    ""
            ).trim();

            if (withoutDecoration.equals(normalized)) {
                break;
            }

            normalized = withoutDecoration;
        }

        normalized = normalized.replaceFirst(
                "[\\s.,!;:]+$",
                ""
        );

        return normalized
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
