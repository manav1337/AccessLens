package com.accessibleweb.colorblind_web.service;

import com.accessibleweb.colorblind_web.render.CvdAnalysisService;
import com.accessibleweb.colorblind_web.render.PageRenderService;
import com.accessibleweb.colorblind_web.render.TextSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Turns a page's {@link TextSample} list into a WCAG contrast report.
 *
 * <p>This replaces the old single bg/text guess. Every distinct text-styling combination
 * the renderer found gets its own verdict, using the correct large-text threshold from
 * WCAG 1.4.3 / 1.4.6 instead of one flat number applied to everything on the page.
 */
@Service
public class ColorService {

    private final PageRenderService renderService;

    private final CvdAnalysisService cvdAnalysisService = new CvdAnalysisService();

    @Autowired
    public ColorService(PageRenderService renderService) {
        this.renderService = renderService;
    }

    public Map<String, Object> analyzeAccessibility(String url, String mode) {
        String analysisMode = (mode == null) ? "default" : mode.toLowerCase();
        boolean isStrictMode = "strict".equals(analysisMode);

        var render = renderService.render(url);
        List<TextSample> samples = render.samples();

        List<Map<String, Object>> perSample = new ArrayList<>();
        int passAA = 0, failAA = 0, indeterminate = 0;

        for (TextSample s : samples) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("selector", s.selector());
            entry.put("text", s.textSnippet());
            entry.put("foreground", s.foregroundHex());
            entry.put("background", s.backgroundHex());
            entry.put("fontSizePx", s.fontSizePx());
            entry.put("bold", s.bold());
            entry.put("occurrences", s.occurrences());
            entry.put("largeText", s.isLargeText());

            if (!s.isMeasurable()) {
                entry.put("measurable", false);
                entry.put("note", "Background is an image or gradient; contrast cannot be reliably computed");
                indeterminate++;
                perSample.add(entry);
                continue;
            }

            try {
                double ratio = calculateWCAGContrast(s.backgroundHex(), s.foregroundHex());
                double aaThreshold = s.isLargeText() ? 3.0 : (isStrictMode ? 5.0 : 4.5);
                double aaaThreshold = s.isLargeText() ? 4.5 : 7.0;
                String rating = ratio >= aaaThreshold ? "AAA" : (ratio >= aaThreshold ? "AA" : "Fail");

                entry.put("measurable", true);
                entry.put("contrastRatio", Math.round(ratio * 100) / 100.0);
                entry.put("requiredAA", aaThreshold);
                entry.put("requiredAAA", aaaThreshold);
                entry.put("rating", rating);

                if ("Fail".equals(rating)) failAA++; else passAA++;
            } catch (IllegalArgumentException e) {
                // A malformed hex value shouldn't be possible given the extraction script,
                // but one bad sample must never take down the whole report.
                entry.put("measurable", false);
                entry.put("note", "Could not parse color value: " + e.getMessage());
                indeterminate++;
            }

            perSample.add(entry);
        }

        int measurableCount = passAA + failAA;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSamples", samples.size());
        summary.put("measurable", measurableCount);
        summary.put("indeterminate", indeterminate);
        summary.put("passingAA", passAA);
        summary.put("failingAA", failAA);
        summary.put("passRate", measurableCount == 0 ? null
                : Math.round((100.0 * passAA / measurableCount) * 10) / 10.0);

        List<TextSample> measurableSamples = samples.stream()
                .filter(TextSample::isMeasurable)
                .toList();

        Map<String, Object> colorVisionDeficiency = new LinkedHashMap<>();
        colorVisionDeficiency.put("contrastDrift", cvdAnalysisService.simulateContrastDrift(measurableSamples));
        colorVisionDeficiency.put("confusionPairs", cvdAnalysisService.findConfusionPairs(measurableSamples));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysisMode", analysisMode);
        result.put("pageTitle", render.pageTitle());
        result.put("elementsScanned", render.elementsScanned());
        result.put("truncated", render.truncated());
        result.put("hasLangAttribute", render.hasLangAttribute());
        result.put("summary", summary);
        result.put("samples", perSample);
        result.put("colorVisionDeficiency", colorVisionDeficiency);

        return result;
    }

    public Map<String, Object> analyzeAccessibility(String url) {
        return analyzeAccessibility(url, "default");
    }

    // ---- WCAG contrast math (unchanged — this part was always correct) ----

    private double calculateWCAGContrast(String hex1, String hex2) {
        double l1 = calculateRelativeLuminance(hexToRgb(hex1));
        double l2 = calculateRelativeLuminance(hexToRgb(hex2));
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    private int[] hexToRgb(String hex) {
        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;
        if (cleanHex.length() != 6) {
            throw new IllegalArgumentException("Expected 6-digit hex color, got: " + hex);
        }
        return new int[]{
                Integer.parseInt(cleanHex.substring(0, 2), 16),
                Integer.parseInt(cleanHex.substring(2, 4), 16),
                Integer.parseInt(cleanHex.substring(4, 6), 16)
        };
    }

    private double calculateRelativeLuminance(int[] rgb) {
        double r = rgb[0] / 255.0, g = rgb[1] / 255.0, b = rgb[2] / 255.0;
        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
