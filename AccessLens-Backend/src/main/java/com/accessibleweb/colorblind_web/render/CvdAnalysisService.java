package com.accessibleweb.colorblind_web.render;

import java.util.*;

/**
 * Produces the two CVD-relevant findings this tool reports:
 *
 * <ol>
 *   <li><b>Contrast drift</b> — re-running the WCAG contrast formula on each text sample's
 *       colours after CVD simulation. Because WCAG contrast is luminance-based, this mostly
 *       moves for protanopia (which reduces perceived luminance from red) and achromatopsia
 *       (full desaturation). Deuteranopia and tritanopia largely preserve luminance, so this
 *       check alone under-detects their real impact — which is exactly why finding 2 exists.</li>
 *   <li><b>Confusion pairs</b> — every pair of distinct colours used on the page is compared
 *       before and after simulation using perceptual (Lab) distance. A pair that reads as
 *       clearly different today (ΔE76 above {@link #DISTINCT_THRESHOLD}) but collapses toward
 *       indistinguishable under simulation (ΔE76 below {@link #CONFUSABLE_THRESHOLD}) is a
 *       WCAG 1.4.1 (Use of Colour) risk: two UI states, categories, or chart series that a
 *       CVD user cannot tell apart, even though contrast ratio alone would report them as
 *       fine. This is the check a contrast-only tool cannot produce.</li>
 * </ol>
 */
public class CvdAnalysisService {

    private static final double DISTINCT_THRESHOLD = 15.0;
    private static final double CONFUSABLE_THRESHOLD = 8.0;

    /** Per-sample contrast ratio under each of the four deficiencies. */
    public Map<String, Object> simulateContrastDrift(List<TextSample> samples) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (TextSample sample : samples) {
            if (!sample.isMeasurable()) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("selector", sample.selector());
            row.put("originalRatio", round2(
                    ColorSpace.contrastRatio(sample.backgroundHex(), sample.foregroundHex())));

            Map<String, Object> perDeficiency = new LinkedHashMap<>();
            for (Deficiency d : Deficiency.values()) {
                String simFg = CvdSimulator.simulate(sample.foregroundHex(), d);
                String simBg = CvdSimulator.simulate(sample.backgroundHex(), d);
                double ratio = ColorSpace.contrastRatio(simBg, simFg);
                double threshold = sample.isLargeText() ? 3.0 : 4.5;

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("simulatedForeground", simFg);
                entry.put("simulatedBackground", simBg);
                entry.put("ratio", round2(ratio));
                entry.put("stillPasses", ratio >= threshold);
                perDeficiency.put(d.name().toLowerCase(), entry);
            }

            row.put("underSimulation", perDeficiency);
            rows.add(row);
        }

        return Map.of("samples", rows);
    }

    /**
     * Finds colour pairs on the page that are visually distinct today but become
     * confusable under at least one simulated deficiency.
     */
    public List<Map<String, Object>> findConfusionPairs(List<TextSample> samples) {
        Set<String> distinctColors = new LinkedHashSet<>();
        for (TextSample s : samples) {
            distinctColors.add(s.foregroundHex());
            distinctColors.add(s.backgroundHex());
        }
        List<String> colors = new ArrayList<>(distinctColors);

        List<Map<String, Object>> findings = new ArrayList<>();

        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                String colorA = colors.get(i);
                String colorB = colors.get(j);

                double originalDeltaE = ColorSpace.deltaE76(colorA, colorB);
                if (originalDeltaE < DISTINCT_THRESHOLD) continue; // not distinct to begin with

                for (Deficiency d : Deficiency.values()) {
                    String simA = CvdSimulator.simulate(colorA, d);
                    String simB = CvdSimulator.simulate(colorB, d);
                    double simulatedDeltaE = ColorSpace.deltaE76(simA, simB);

                    if (simulatedDeltaE < CONFUSABLE_THRESHOLD) {
                        Map<String, Object> finding = new LinkedHashMap<>();
                        finding.put("colorA", colorA);
                        finding.put("colorB", colorB);
                        finding.put("deficiency", d.name().toLowerCase());
                        finding.put("originalDeltaE", round2(originalDeltaE));
                        finding.put("simulatedDeltaE", round2(simulatedDeltaE));
                        findings.add(finding);
                    }
                }
            }
        }

        return findings;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
