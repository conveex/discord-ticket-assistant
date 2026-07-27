package org.cnvx.discordtickets.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.cnvx.discordtickets.model.ServerType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiscordBrowserSession implements AutoCloseable {

    private Playwright playwright;
    private BrowserContext context;

    private Page skyblockPage;
    private Page kuudraPage;

    public void open(List<String> discordUrls) {
        if (discordUrls == null || discordUrls.size() != 2) {
            throw new IllegalArgumentException(
                    "Se esperaban exactamente dos direcciones de Discord."
            );
        }

        if (context != null) {
            throw new IllegalStateException(
                    "La sesión del navegador ya se encuentra abierta."
            );
        }

        Path profileDirectory = createProfileDirectory();

        playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel("chrome")
                        .setHeadless(false)
                        .setChromiumSandbox(true);

        context = playwright.chromium().launchPersistentContext(
                profileDirectory,
                options
        );

        context.setDefaultTimeout(10_000);
        preparePages(discordUrls);
    }

    private void preparePages(List<String> discordUrls) {
        List<Page> initialPages = new ArrayList<>(context.pages());

        if (initialPages.isEmpty()) {
            skyblockPage = context.newPage();
        } else {
            skyblockPage = initialPages.getFirst();

            /*
             * Cerramos páginas sobrantes restauradas por Chrome para que
             * la aplicación mantenga únicamente las dos que controla.
             */
            for (int index = 1; index < initialPages.size(); index++) {
                initialPages.get(index).close();
            }
        }

        kuudraPage = context.newPage();

        navigate(skyblockPage, discordUrls.get(0));
        navigate(kuudraPage, discordUrls.get(1));
    }

    private void navigate(Page page, String url) {
        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30_000)
        );
    }

    private Path createProfileDirectory() {
        Path profileDirectory = Path.of(
                System.getProperty("user.home"),
                ".discord-ticket-assistant",
                "chrome-profile"
        );

        try {
            return Files.createDirectories(profileDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible crear el perfil de Chrome en: "
                            + profileDirectory,
                    exception
            );
        }
    }

    public Page skyblockPage() {
        ensureOpened();
        return skyblockPage;
    }

    public Page kuudraPage() {
        ensureOpened();
        return kuudraPage;
    }

    private void ensureOpened() {
        if (context == null) {
            throw new IllegalStateException(
                    "La sesión del navegador no esttá abierta."
            );
        }
    }

    public Page pageFor(ServerType server) {
        ensureOpened();

        return switch (server) {
            case SKYBLOCK_MANIACS -> skyblockPage;
            case KUUDRA_GANG -> kuudraPage;
        };
    }

    @Override
    public void close() {
        if (context != null) {
            context.close();
            context = null;
        }

        if (playwright != null) {
            playwright.close();
            playwright = null;
        }

        skyblockPage = null;
        kuudraPage = null;
    }
}
