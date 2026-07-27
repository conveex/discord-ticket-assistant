package org.cnvx.discordtickets.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Properties;

public record DiscordUrls (String skyblockManiacs, String kuudraGang){

    private static final String CONFIG_FILE = "/app.properties";

    public static DiscordUrls load() {
        Properties properties = new Properties();

        try (InputStream input = DiscordUrls.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "No se encontró el archivo" + CONFIG_FILE
                );
            }

            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible leer " + CONFIG_FILE,
                    exception
            );
        }

        String skyblock = requireDiscordUrl(
                properties,
                "discord.skyblock.url"
        );

        String kuudra = requireDiscordUrl(
                properties,
                "discord.kuudra.url"
        );

        return new DiscordUrls(skyblock, kuudra);
    }

    public List<String> asList() {
        return List.of(skyblockManiacs, kuudraGang);
    }

    private static String requireDiscordUrl(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la propiedad: " + key
            );
        }

        value = value.trim();
        URI uri;

        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "La propiedad " + key + " no contiene una URL válida.",
                    exception
            );
        }

        boolean validScheme = "https".equalsIgnoreCase(uri.getScheme());
        boolean validHost = "discord.com".equalsIgnoreCase(uri.getHost());
        boolean validPath = uri.getPath() != null
                && uri.getPath().startsWith("/channels/");

        if (!validScheme || !validHost || !validPath) {
            throw new IllegalStateException(
                    "La propiedad " + key
                            + " debe ser una URL de un canal de Discord."
            );
        }

        return value;
    }
}
