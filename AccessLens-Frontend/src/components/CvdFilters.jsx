/**
 * Single source of truth for the CVD filter definitions, as a raw markup string.
 *
 * Kept as a string (not JSX) so it can be reused two ways: rendered inline via
 * dangerouslySetInnerHTML for the live preview iframe's filter, AND spliced directly
 * into the plain HTML string written to the "open in new tab" window. Defining these
 * matrices in two places (once in JSX, once as a string) would risk the same kind of
 * silent divergence bug that hit the backend CVD engine earlier — one source fixes
 * that by construction.
 *
 * These are standard sRGB-space CVD approximation matrices (the same family browser
 * "emulate vision deficiencies" tools use), applied directly to gamma-encoded pixels
 * for speed. They are a fast visual approximation for intuition, not a pixel-exact
 * match to the backend's linear-space Machado-matrix numbers in the report.
 */
export const CVD_FILTER_MARKUP = `
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
  <defs>
    <filter id="cvd-protanopia" color-interpolation-filters="sRGB">
      <feColorMatrix type="matrix" values="
        0.567 0.433 0     0 0
        0.558 0.442 0     0 0
        0     0.242 0.758 0 0
        0     0     0     1 0" />
    </filter>
    <filter id="cvd-deuteranopia" color-interpolation-filters="sRGB">
      <feColorMatrix type="matrix" values="
        0.625 0.375 0   0 0
        0.7   0.3   0   0 0
        0     0.3   0.7 0 0
        0     0     0   1 0" />
    </filter>
    <filter id="cvd-tritanopia" color-interpolation-filters="sRGB">
      <feColorMatrix type="matrix" values="
        0.95 0.05  0     0 0
        0    0.433 0.567 0 0
        0    0.475 0.525 0 0
        0    0     0     1 0" />
    </filter>
    <filter id="cvd-achromatopsia" color-interpolation-filters="sRGB">
      <feColorMatrix type="matrix" values="
        0.2126 0.7152 0.0722 0 0
        0.2126 0.7152 0.0722 0 0
        0.2126 0.7152 0.0722 0 0
        0      0      0      1 0" />
    </filter>
  </defs>
</svg>
`;

/** Renders the hidden filter defs for use inside the React tree. */
function CvdFilters() {
  return <div dangerouslySetInnerHTML={{ __html: CVD_FILTER_MARKUP }} />;
}

export default CvdFilters;
