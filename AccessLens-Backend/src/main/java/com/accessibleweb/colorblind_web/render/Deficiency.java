package com.accessibleweb.colorblind_web.render;

/** The four colour vision deficiencies AccessLens simulates. */
public enum Deficiency {

    /** Missing/non-functional L (long-wavelength, "red") cones. Also reduces luminance. */
    PROTANOPIA,

    /** Missing/non-functional M (medium-wavelength, "green") cones. */
    DEUTERANOPIA,

    /** Missing/non-functional S (short-wavelength, "blue") cones. Rare; autosomal. */
    TRITANOPIA,

    /** No functioning cones — vision mediated by rods only. Full colour blindness. */
    ACHROMATOPSIA
}
