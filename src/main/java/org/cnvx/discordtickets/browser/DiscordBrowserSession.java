package org.cnvx.discordtickets.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.cnvx.discordtickets.model.ServerType;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class DiscordBrowserSession
        implements AutoCloseable {

    private Playwright playwright;
    private BrowserContext browserContext;

    private Page skyblockPage;
    private Page kuudraPage;

    private final Map<ServerType, String> serverUrls = new EnumMap<>(ServerType.class);

    public void open(List<String> urls) {
        Objects.requireNonNull(
                urls,
                "Las URL de Discord son obligatorias."
        );

        if (urls.size() != 2) {
            throw new IllegalArgumentException(
                    "Se esperaban exactamente dos URL de Discord."
            );
        }

        serverUrls.clear();

        serverUrls.put(
                ServerType.SKYBLOCK_MANIACS,
                urls.get(0)
        );

        serverUrls.put(
                ServerType.KUUDRA_GANG,
                urls.get(1)
        );

        if (isHealthy()) {
            return;
        }

        close();

        Path profileDirectory = Path.of(
                System.getProperty("user.home"),
                ".discord-ticket-assistant",
                "chrome-profile"
        );

        playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel("chrome")
                        .setHeadless(false)
                        .setChromiumSandbox(true)
                        .setArgs(List.of(
                                "--disable-background-timer-throttling",
                                "--disable-backgrounding-occluded-windows",
                                "--disable-renderer-backgrounding"
                        ));

        browserContext = playwright
                .chromium()
                .launchPersistentContext(
                        profileDirectory,
                        options
                );

        List<Page> initialPages =
                browserContext.pages();

        if (initialPages.isEmpty()) {
            skyblockPage = browserContext.newPage();
        } else {
            skyblockPage = initialPages.getFirst();

            for (int index = 1;
                 index < initialPages.size();
                 index++) {

                initialPages.get(index).close();
            }
        }

        kuudraPage = browserContext.newPage();

        navigate(
                skyblockPage,
                urls.get(0)
        );

        navigate(
                kuudraPage,
                urls.get(1)
        );
    }

    public String expectedGuildId(
            ServerType server
    ) {
        Objects.requireNonNull(server);

        String url = serverUrls.get(server);

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "No existe una URL configurada para "
                            + server.displayName()
            );
        }

        String[] segments =
                URI.create(url)
                        .getPath()
                        .split("/");

        for (int index = 0;
             index < segments.length - 1;
             index++) {

            if ("channels".equals(segments[index])) {
                return segments[index + 1];
            }
        }

        throw new IllegalStateException(
                "La URL no contiene un ID de servidor: "
                        + url
        );
    }

    public Page replacePage(
            ServerType server
    ) {
        Objects.requireNonNull(server);

        ensureContextAvailable();

        String targetUrl = serverUrls.get(server);

        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException(
                    "No existe una URL configurada para "
                            + server.displayName()
            );
        }

        Page previousPage = rawPageFor(server);
        Page replacementPage = browserContext.newPage();

        try {
            navigate(
                    replacementPage,
                    targetUrl
            );

            replacementPage.waitForFunction(
                    """
                    () => document.querySelector(
                        "[data-list-item-id^='channels___']"
                    ) !== null
                    """,
                    null,
                    new Page.WaitForFunctionOptions()
                            .setTimeout(10_000)
            );

            setPage(
                    server,
                    replacementPage
            );

            if (previousPage != null
                    && !previousPage.isClosed()) {

                previousPage.close();
            }

            return replacementPage;

        } catch (RuntimeException exception) {
            try {
                if (!replacementPage.isClosed()) {
                    replacementPage.close();
                }
            } catch (RuntimeException ignored) {
                // Limpieza defensiva.
            }

            throw exception;
        }
    }

    private Page rawPageFor(
            ServerType server
    ) {
        return switch (server) {
            case SKYBLOCK_MANIACS -> skyblockPage;
            case KUUDRA_GANG -> kuudraPage;
        };
    }

    private void setPage(
            ServerType server,
            Page page
    ) {
        switch (server) {
            case SKYBLOCK_MANIACS ->
                    skyblockPage = page;

            case KUUDRA_GANG ->
                    kuudraPage = page;
        }
    }

    private void ensureContextAvailable() {
        if (playwright == null
                || browserContext == null
                || browserContext.isClosed()) {

            throw new IllegalStateException(
                    "El contexto automatizado de Chrome "
                            + "no está disponible."
            );
        }
    }

    public boolean isHealthy() {
        try {
            return playwright != null
                    && browserContext != null
                    && !browserContext.isClosed()
                    && skyblockPage != null
                    && !skyblockPage.isClosed()
                    && kuudraPage != null
                    && !kuudraPage.isClosed();

        } catch (RuntimeException exception) {
            return false;
        }
    }

    public Page skyblockPage() {
        ensureHealthy();
        return skyblockPage;
    }

    public Page kuudraPage() {
        ensureHealthy();
        return kuudraPage;
    }

    public Page pageFor(ServerType server) {
        Objects.requireNonNull(server);

        ensureHealthy();

        return switch (server) {
            case SKYBLOCK_MANIACS -> skyblockPage;
            case KUUDRA_GANG -> kuudraPage;
        };
    }

    private void navigate(
            Page page,
            String url
    ) {
        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(30_000)
        );
    }

    private void ensureHealthy() {
        if (!isHealthy()) {
            throw new IllegalStateException(
                    "La sesión automatizada de Chrome "
                            + "no se encuentra disponible."
            );
        }
    }

    @Override
    public void close() {
        try {
            if (browserContext != null
                    && !browserContext.isClosed()) {

                browserContext.close();
            }
        } catch (RuntimeException ignored) {
            // Chrome podría haber sido cerrado externamente.
        }

        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (RuntimeException ignored) {
            // La conexión con Playwright podría estar terminada.
        }

        skyblockPage = null;
        kuudraPage = null;
        browserContext = null;
        playwright = null;
    }
}