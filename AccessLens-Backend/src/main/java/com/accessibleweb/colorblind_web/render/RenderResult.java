package com.accessibleweb.colorblind_web.render;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Everything one page render produced.
 *
 * <p>The screenshot is kept separate from the JSON payload because it is only needed by the
 * colour-vision-deficiency preview, which transforms it pixel by pixel. Analysis of the
 * samples does not touch it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderResult(
        String pageUrl,
        String pageTitle,
        String lang,
        int elementsScanned,
        boolean truncated,
        List<TextSample> samples,
        byte[] screenshotPng
) {

    /** Copy of this result with a screenshot attached. */
    public RenderResult withScreenshot(byte[] png) {
        return new RenderResult(pageUrl, pageTitle, lang, elementsScanned, truncated, samples, png);
    }

    /**
     * Missing {@code lang} on the root element is WCAG 3.1.1 (Language of Page), a Level A
     * failure. It costs nothing to surface here since the renderer already has it.
     */
    public boolean hasLangAttribute() {
        return lang != null && !lang.isBlank();
    }
}
