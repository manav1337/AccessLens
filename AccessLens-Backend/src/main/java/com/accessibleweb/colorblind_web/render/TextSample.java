package com.accessibleweb.colorblind_web.render;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One distinct text-styling combination found on the page.
 *
 * <p>Colours are already resolved by the browser: the cascade is applied, {@code var()} is
 * substituted, and any semi-transparent layers between the text and its nearest opaque
 * ancestor background have been alpha-composited. Both hex values are opaque and safe to
 * feed straight into a contrast calculation.
 *
 * <p>Samples are de-duplicated by styling signature, so {@code occurrences} tells you how
 * many elements shared this exact combination while {@code selector} points at the first one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextSample(
        String selector,
        String textSnippet,
        String foregroundHex,
        String backgroundHex,
        double fontSizePx,
        boolean bold,

        /*
         * True when a background-image or gradient sits behind this text. A single solid
         * colour cannot describe that, so any contrast verdict here is unreliable — report
         * it as indeterminate rather than passing or failing it.
         */
        boolean backgroundIndeterminate,

        int x,
        int y,
        int width,
        int height,
        int occurrences
) {

    /**
     * WCAG 2.1 large-text boundary: 18pt (24px) regular, or 14pt (18.66px) bold.
     * Large text is held to 3:1 at AA and 4.5:1 at AAA, instead of 4.5:1 and 7:1.
     */
    public boolean isLargeText() {
        return fontSizePx >= 24.0 || (bold && fontSizePx >= 18.66);
    }

    /** True when this sample can be given a trustworthy pass/fail verdict. */
    public boolean isMeasurable() {
        return !backgroundIndeterminate;
    }
}
