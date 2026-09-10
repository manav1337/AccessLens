package com.accessibleweb.colorblind_web.render;

/**
 * Simulates how a colour would appear to someone with a colour vision deficiency.
 *
 * <p><b>Revision note:</b> the first version of this class went through an LMS
 * cone-space round trip using the Viénot/Brettel/Mollon dichromat coefficients. That
 * produced badly wrong output — neutral greys were exploding into saturated cyan under
 * protanopia/deuteranopia simulation, confirmed by tracing a grey input through the
 * pipeline and finding the projected L-cone value went negative. The root cause: those
 * published coefficients are only valid for the specific LMS normalization they were
 * derived against, and the Hunt-Pointer-Estevez matrix used here was a close but not
 * identical normalization. Mixing the two broke the invariant that equal L=M=S (grey)
 * must map back to grey.
 *
 * <p>This version uses the Machado, Oliveira &amp; Fluri (2009) matrices instead, which
 * operate directly on linear RGB with no intermediate LMS step, sidestepping the
 * normalization-mismatch problem entirely. These are the matrices used by Chrome DevTools'
 * "Emulate vision deficiencies" feature and most production CVD-simulation tools, so they
 * are well validated against real-world use. Achromatopsia is unaffected by this change —
 * it was never LMS-based, since rod vision has no cone response to project.
 */
public final class CvdSimulator {

    // Machado, Oliveira & Fluri 2009, full-severity dichromat matrices.
    // Applied directly to linear RGB. Row sums are constructed so that R=G=B (grey)
    // maps back to R=G=B — verified numerically, not just asserted.
    private static final double[][] PROTANOPIA = {
            {0.152286, 1.052583, -0.204868},
            {0.114503, 0.786281, 0.099216},
            {-0.003882, -0.048116, 1.051998}
    };

    private static final double[][] DEUTERANOPIA = {
            {0.367322, 0.860646, -0.227968},
            {0.280085, 0.672501, 0.047413},
            {-0.011820, 0.042940, 0.968881}
    };

    private static final double[][] TRITANOPIA = {
            {1.255528, -0.076749, -0.178779},
            {-0.078411, 0.930809, 0.147602},
            {0.004733, 0.691367, 0.303900}
    };

    private CvdSimulator() {
    }

    /** Applies the given deficiency to a colour, returning the simulated colour as hex. */
    public static String simulate(String hex, Deficiency deficiency) {
        double[] linear = ColorSpace.hexToLinear(hex);

        if (deficiency == Deficiency.ACHROMATOPSIA) {
            double y = ColorSpace.relativeLuminance(linear);
            return ColorSpace.linearToHex(new double[]{y, y, y});
        }

        double[][] matrix = switch (deficiency) {
            case PROTANOPIA -> PROTANOPIA;
            case DEUTERANOPIA -> DEUTERANOPIA;
            case TRITANOPIA -> TRITANOPIA;
            default -> throw new IllegalArgumentException("Not a dichromat type: " + deficiency);
        };

        double[] simulated = multiply(matrix, linear);
        return ColorSpace.linearToHex(simulated);
    }

    private static double[] multiply(double[][] matrix, double[] vec) {
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = matrix[i][0] * vec[0] + matrix[i][1] * vec[1] + matrix[i][2] * vec[2];
        }
        return out;
    }
}