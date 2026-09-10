package com.accessibleweb.colorblind_web.render;

import com.accessibleweb.colorblind_web.render.PageRenderService;
import com.accessibleweb.colorblind_web.render.RenderResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PageRenderServiceTest {
    @Autowired
    PageRenderService renderer;

    @Test
    void extractsSamplesFromStaticPage() {
        RenderResult result = renderer.render("https://books.toscrape.com/");
        assertFalse(result.samples().isEmpty());
        result.samples().forEach(s -> {
            assertTrue(s.foregroundHex().matches("#[0-9a-f]{6}"));
            assertTrue(s.backgroundHex().matches("#[0-9a-f]{6}"));
            assertTrue(s.fontSizePx() > 0);
        });
    }

    @Test
    void findsBlackOnWhite() {
        RenderResult result = renderer.render("https://books.toscrape.com/");
        assertTrue(result.samples().stream()
                .anyMatch(s -> s.backgroundHex().equals("#ffffff")));
    }
}