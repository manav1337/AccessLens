package com.accessibleweb.colorblind_web.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * A fixed pool of Chromium instances.
 *
 * <p><strong>Why each worker owns a thread.</strong> Playwright for Java is not thread-safe:
 * a {@code Playwright} object and every object it creates must be used from the thread that
 * created it. Tomcat hands each HTTP request to an arbitrary worker thread, so calling a
 * shared {@code Browser} directly from a controller is a race waiting to happen — usually
 * showing up as intermittent {@code TargetClosedError}s under load rather than a clean crash.
 *
 * <p>The fix is to pin each browser to its own single-thread executor and submit work to it.
 * Request threads never touch Playwright objects; they hand over a lambda and block on a
 * {@link Future}. A {@link Semaphore} caps how many renders are in flight so a burst of
 * traffic queues instead of spawning browsers until the container runs out of memory.
 */
@Slf4j
@Component
public class PlaywrightPool implements AutoCloseable {

    @Value("${accesslens.render.pool-size:2}")
    private int poolSize;

    @Value("${accesslens.render.headless:true}")
    private boolean headless;

    @Value("${accesslens.render.acquire-timeout-seconds:60}")
    private int acquireTimeoutSeconds;

    private final List<Worker> workers = new ArrayList<>();
    private final BlockingQueue<Worker> available = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;

    @PostConstruct
    void start() {
        log.info("Starting Playwright pool: size={} headless={}", poolSize, headless);
        for (int i = 0; i < poolSize; i++) {
            Worker worker = new Worker(i, headless);
            worker.boot();
            workers.add(worker);
            available.add(worker);
        }
        log.info("Playwright pool ready");
    }

    /**
     * Borrows a browser, runs {@code job} on that browser's own thread, and returns it.
     *
     * @throws RenderException if no browser frees up in time, or the job fails
     */
    public <T> T withBrowser(Function<Browser, T> job) {
        if (closed) throw new RenderException("Render pool is shut down");

        Worker worker;
        try {
            worker = available.poll(acquireTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException("Interrupted while waiting for a browser", e);
        }
        if (worker == null) {
            throw new RenderException("All browsers busy; timed out after " + acquireTimeoutSeconds + "s");
        }

        try {
            return worker.submit(job);
        } finally {
            // Always return the worker, even after a failure — a crashed browser is
            // rebuilt lazily on next use rather than leaking a slot from the pool.
            available.offer(worker);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        closed = true;
        log.info("Shutting down Playwright pool");
        for (Worker worker : workers) {
            worker.shutdown();
        }
        workers.clear();
        available.clear();
    }

    /** One Chromium instance plus the single thread that is allowed to talk to it. */
    private static final class Worker {

        private final int id;
        private final boolean headless;
        private final ExecutorService executor;

        private Playwright playwright;
        private Browser browser;

        Worker(int id, boolean headless) {
            this.id = id;
            this.headless = headless;
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "playwright-worker-" + id);
                t.setDaemon(true);
                return t;
            });
        }

        void boot() {
            runOnOwnThread(() -> {
                createBrowser();
                return null;
            });
        }

        <T> T submit(Function<Browser, T> job) {
            return runOnOwnThread(() -> {
                if (browser == null || !browser.isConnected()) {
                    log.warn("Browser {} disconnected; restarting", id);
                    disposeQuietly();
                    createBrowser();
                }
                return job.apply(browser);
            });
        }

        private <T> T runOnOwnThread(Callable<T> callable) {
            try {
                return executor.submit(callable).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RenderException("Render interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RenderException re) throw re;
                throw new RenderException("Render failed: " + cause.getMessage(), cause);
            }
        }

        private void createBrowser() {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(List.of(
                            // Required in most containers: Chromium's sandbox needs kernel
                            // capabilities that Docker drops by default.
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu"
                    )));
            log.info("Browser {} launched", id);
        }

        private void disposeQuietly() {
            try {
                if (browser != null) browser.close();
            } catch (Exception ignored) {
                // Already dead — nothing useful to do.
            }
            try {
                if (playwright != null) playwright.close();
            } catch (Exception ignored) {
                // Same.
            }
            browser = null;
            playwright = null;
        }

        void shutdown() {
            try {
                executor.submit(() -> {
                    disposeQuietly();
                    return null;
                }).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Browser {} did not shut down cleanly", id, e);
            }
            executor.shutdownNow();
        }
    }
}
