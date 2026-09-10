package com.accessibleweb.colorblind_web.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders a page in real Chromium and extracts every distinct text-styling combination on it.
 *
 * <p>This replaces the old Jsoup + regex scraper. The difference is not incremental: because
 * the page runs in a real browser, the cascade, specificity, {@code var()} substitution,
 * alpha compositing and JavaScript rendering are all resolved for us. What comes back is what
 * a user would actually see, not what the stylesheets happen to declare.
 */
@Slf4j
@Service
public class PageRenderService {

    private final PlaywrightPool pool;
    private final ObjectMapper objectMapper;

    @Value("${accesslens.render.max-samples:400}")
    private int maxSamples;

    @Value("${accesslens.render.navigation-timeout-ms:30000}")
    private double navigationTimeoutMs;

    @Value("${accesslens.render.settle-delay-ms:1200}")
    private int settleDelayMs;

    @Value("${accesslens.render.viewport-width:1280}")
    private int viewportWidth;

    @Value("${accesslens.render.viewport-height:900}")
    private int viewportHeight;

    private String extractionScript;

    public PageRenderService(PlaywrightPool pool, ObjectMapper objectMapper) {
        this.pool = pool;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadScript() {
        try {
            extractionScript = new String(
                    new ClassPathResource("extract-samples.js").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load extract-samples.js from classpath", e);
        }
    }

    /** Renders {@code url} and returns its text samples. No screenshot. */
    public RenderResult render(String url) {
        return render(url, false);
    }

    /**
     * Renders {@code url} and returns its text samples.
     *
     * @param withScreenshot capture a full-page PNG as well — needed only for the
     *                       colour-vision-deficiency preview, and it roughly doubles render time
     */
    public RenderResult render(String url, boolean withScreenshot) {
        return pool.withBrowser(browser -> {
            BrowserContext context = null;
            try {
                context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(viewportWidth, viewportHeight)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                        .setLocale("en-US")
                        // Analysing motion-heavy pages is pointless and slow; this also makes
                        // renders far more deterministic between runs.
                        .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE));

                context.setDefaultTimeout(navigationTimeoutMs);
                Page page = context.newPage();

                blockHeavyResources(page, withScreenshot);

                Response response = page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(navigationTimeoutMs));

                if (response != null && response.status() >= 400) {
                    throw new RenderException("Origin returned HTTP " + response.status());
                }

                waitForSettle(page);

                String json = (String) page.evaluate(extractionScript, maxSamples);
                RenderResult result = objectMapper.readValue(json, RenderResult.class);

                if (result.truncated()) {
                    log.info("Sample cap of {} hit for {}", maxSamples, url);
                }
                log.info("Rendered {}: {} distinct samples from {} elements",
                        url, result.samples().size(), result.elementsScanned());

                if (withScreenshot) {
                    byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                    result = result.withScreenshot(png);
                }

                return result;

            } catch (PlaywrightException e) {
                throw new RenderException("Browser error rendering " + url + ": " + e.getMessage(), e);
            } catch (IOException e) {
                throw new RenderException("Could not parse extraction output for " + url, e);
            } finally {
                if (context != null) {
                    try {
                        context.close();
                    } catch (Exception e) {
                        log.warn("Context close failed", e);
                    }
                }
            }
        });
    }

    /**
     * Fonts and stylesheets must load — they decide layout and colour. Images, media and
     * trackers do not, and blocking them cuts render time substantially. When a screenshot
     * is requested images stay enabled, since the preview needs to look like the real page.
     */
    private void blockHeavyResources(Page page, boolean keepImages) {
        page.route("**/*", route -> {
            String type = route.request().resourceType();
            boolean drop = switch (type) {
                case "media", "websocket", "eventsource", "manifest" -> true;
                case "image", "font" -> !keepImages && type.equals("image");
                default -> false;
            };
            if (drop) {
                route.abort();
            } else {
                route.resume();
            }
        });
    }

    /**
     * Waits for the page to stop changing.
     *
     * <p>{@code networkidle} is the obvious choice but it never fires on pages holding open
     * analytics sockets or polling connections, so it is wrapped in a try and treated as
     * best-effort. The fixed delay afterwards covers client-side frameworks that paint one
     * frame after their data arrives.
     */
    private void waitForSettle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(8000));
        } catch (PlaywrightException e) {
            log.debug("networkidle never reached; continuing anyway");
        }

        // Lazy-loaded content often only mounts once it scrolls into view. One pass down the
        // page and back triggers most of it.
        try {
            page.evaluate("() => new Promise(resolve => {"
                    + "  const step = Math.max(window.innerHeight, 400);"
                    + "  let y = 0;"
                    + "  const timer = setInterval(() => {"
                    + "    window.scrollBy(0, step);"
                    + "    y += step;"
                    + "    if (y >= document.body.scrollHeight) {"
                    + "      clearInterval(timer);"
                    + "      window.scrollTo(0, 0);"
                    + "      resolve();"
                    + "    }"
                    + "  }, 100);"
                    + "})");
        } catch (PlaywrightException e) {
            log.debug("Scroll pass failed; continuing anyway");
        }

        page.waitForTimeout(settleDelayMs);
    }

    /** Convenience for callers that only want measurable samples. */
    public List<TextSample> measurableSamples(String url) {
        return render(url).samples().stream()
                .filter(TextSample::isMeasurable)
                .toList();
    }
}
