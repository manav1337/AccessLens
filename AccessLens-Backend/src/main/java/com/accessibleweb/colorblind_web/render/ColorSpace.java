package com.accessibleweb.colorblind_web.render;

/**
 * Colour space conversions shared by the contrast engine and the CVD simulator.
 *
 * <p>Two colour spaces matter here and they are not interchangeable:
 * <ul>
 *   <li><b>Linear RGB</b> — light-proportional values. Required for luminance mixing,
 *       cone-response simulation, and relative luminance (WCAG contrast).</li>
 *   <li><b>CIELAB</b> — a perceptually-uniform space. Required for measuring how different
 *       two colours actually <i>look</i>, which Euclidean distance in sRGB or the linear
 *       space above does not represent (equal RGB distances do not mean equal perceived
 *       differences).</li>
 * </ul>
 *
 * <p>The previous version of this project computed a formula it called "Lab" that was
 * actually just {@code (luminance, r-g, b-avg(r,g))} — not a real transform, and the
 * thresholds built on it did not track anything meaningful. This class replaces it with
 * the standard sRGB &rarr; linear &rarr; XYZ &rarr; Lab pipeline against a D65 white point.
 */
public final class ColorSpace {

    // sRGB reference white, D65, 2-degree observer.
    private static final double WHITE_X = 95.047;
    private static final double WHITE_Y = 100.000;
    private static final double WHITE_Z = 108.883;

    private ColorSpace() {
    }

    public static int[] hexToRgb(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) {
            throw new IllegalArgumentException("Expected 6-digit hex color, got: " + hex);
        }
        return new int[]{
                Integer.parseInt(clean.substring(0, 2), 16),
                Integer.parseInt(clean.substring(2, 4), 16),
                Integer.parseInt(clean.substring(4, 6), 16)
        };
    }

    public static String rgbToHex(double[] rgb) {
        int r = clampByte(rgb[0]);
        int g = clampByte(rgb[1]);
        int b = clampByte(rgb[2]);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static int clampByte(double v) {
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }

    /** sRGB [0,255] channel to linear-light [0,1]. This is the WCAG 2.x gamma formula. */
    public static double srgbToLinear(double channel255) {
        double c = channel255 / 255.0;
        return (c <= 0.04045) ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Linear-light [0,1] back to sRGB [0,255]. Inverse of {@link #srgbToLinear}. */
    public static double linearToSrgb(double linear) {
        double c = (linear <= 0.0031308) ? linear * 12.92 : 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
        return c * 255.0;
    }

    public static double[] hexToLinear(String hex) {
        int[] rgb = hexToRgb(hex);
        return new double[]{
                srgbToLinear(rgb[0]),
                srgbToLinear(rgb[1]),
                srgbToLinear(rgb[2])
        };
    }

    public static String linearToHex(double[] linear) {
        return rgbToHex(new double[]{
                linearToSrgb(linear[0]),
                linearToSrgb(linear[1]),
                linearToSrgb(linear[2])
        });
    }

    /** WCAG relative luminance — the same weighted sum used for contrast ratio. */
    public static double relativeLuminance(double[] linearRgb) {
        return 0.2126 * linearRgb[0] + 0.7152 * linearRgb[1] + 0.0722 * linearRgb[2];
    }

    public static double contrastRatio(String hexA, String hexB) {
        double la = relativeLuminance(hexToLinear(hexA));
        double lb = relativeLuminance(hexToLinear(hexB));
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    // ---- sRGB -> XYZ -> Lab (D65), for perceptual colour-difference only ----

    private static double[] linearRgbToXyz(double[] linear) {
        double r = linear[0], g = linear[1], b = linear[2];
        // sRGB -> XYZ (D65), IEC 61966-2-1
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) * 100.0;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) * 100.0;
        return new double[]{x, y, z};
    }

    private static double labF(double t) {
        double delta = 6.0 / 29.0;
        return (t > delta * delta * delta)
                ? Math.cbrt(t)
                : (t / (3 * delta * delta) + 4.0 / 29.0);
    }

    /** sRGB hex to CIE L*a*b*, D65 white point. */
    public static double[] hexToLab(String hex) {
        double[] xyz = linearRgbToXyz(hexToLinear(hex));
        double fx = labF(xyz[0] / WHITE_X);
        double fy = labF(xyz[1] / WHITE_Y);
        double fz = labF(xyz[2] / WHITE_Z);
        double l = 116 * fy - 16;
        double a = 500 * (fx - fy);
        double b = 200 * (fy - fz);
        return new double[]{l, a, b};
    }

    /**
     * CIE76 colour difference — Euclidean distance in Lab space. A reasonable, simple
     * perceptual metric: roughly, ΔE below ~2.3 is imperceptible to most observers, and
     * above ~10 is clearly a different colour. CIEDE2000 is more accurate for edge cases
     * (particularly for saturated colours) but is substantially more complex; this is a
     * deliberate simplicity-over-precision tradeoff, documented here rather than hidden.
     */
    public static double deltaE76(String hexA, String hexB) {
        double[] labA = hexToLab(hexA);
        double[] labB = hexToLab(hexB);
        double dl = labA[0] - labB[0];
        double da = labA[1] - labB[1];
        double db = labA[2] - labB[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }
}
