package org.cnvx.discordtickets.util;

public final class UsernameNormalizer {

    private UsernameNormalizer() {

    }

    public static String normalize(String rawUsername) {
        if (rawUsername == null) {
            return "";
        }

        String normalized = rawUsername.trim();

        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }

        return normalized;
    }
}
