import { useMemo, useState } from 'react';
import PreviewPane from './PreviewPane';
import '../styles/components/results.css';

const RATING_ORDER = { Fail: 0, AA: 1, AAA: 2 };
const FILTERS = ['all', 'fail', 'aa', 'aaa', 'indeterminate'];

function ratingClass(rating) {
  return rating ? rating.toLowerCase() : 'unknown';
}

function matchesFilter(sample, filter) {
  if (filter === 'all') return true;
  if (filter === 'indeterminate') return !sample.measurable;
  if (!sample.measurable) return false;
  return sample.rating?.toLowerCase() === filter;
}

function SummaryCard({ summary, pageTitle, elementsScanned, hasLangAttribute, truncated }) {
  const {
    totalSamples = 0,
    measurable = 0,
    indeterminate = 0,
    passingAA = 0,
    failingAA = 0,
    passRate,
  } = summary || {};

  return (
    <div className="result-card summary-card">
      <h3>Page overview</h3>
      {pageTitle && <p className="page-title">{pageTitle}</p>}

      <div className="summary-stats">
        <div className="stat">
          <span className="stat-value">{totalSamples}</span>
          <span className="stat-label">Distinct styles found</span>
        </div>
        <div className="stat stat-pass">
          <span className="stat-value">{passingAA}</span>
          <span className="stat-label">Pass AA</span>
        </div>
        <div className="stat stat-fail">
          <span className="stat-value">{failingAA}</span>
          <span className="stat-label">Fail AA</span>
        </div>
        {indeterminate > 0 && (
          <div className="stat stat-indeterminate">
            <span className="stat-value">{indeterminate}</span>
            <span className="stat-label">Indeterminate</span>
          </div>
        )}
      </div>

      {passRate !== null && passRate !== undefined && (
        <div className="pass-rate-bar" aria-hidden="true">
          <div className="pass-rate-fill" style={{ width: `${passRate}%` }} />
        </div>
      )}
      {passRate !== null && passRate !== undefined && (
        <p className="pass-rate-label">{passRate}% of measurable text passes WCAG AA</p>
      )}

      <ul className="page-flags">
        <li className={hasLangAttribute ? 'flag-ok' : 'flag-warn'}>
          {hasLangAttribute ? 'Page declares a language (lang attribute)' : 'Missing lang attribute on <html> — WCAG 3.1.1'}
        </li>
        <li className="flag-neutral">{elementsScanned} elements scanned on the rendered page</li>
        {truncated && (
          <li className="flag-warn">Sample limit reached — results may not cover the full page</li>
        )}
      </ul>
    </div>
  );
}

function SampleRow({ sample }) {
  const {
    selector,
    text,
    foreground,
    background,
    fontSizePx,
    bold,
    occurrences,
    largeText,
    measurable,
    contrastRatio,
    requiredAA,
    rating,
    note,
  } = sample;

  return (
    <li className={`sample-row rating-${ratingClass(measurable ? rating : 'indeterminate')}`}>
      <div className="sample-swatches" aria-hidden="true">
        <span className="swatch" style={{ backgroundColor: background }} />
        <span className="swatch swatch-text" style={{ backgroundColor: background, color: foreground }}>Aa</span>
      </div>

      <div className="sample-body">
        <p className="sample-text" title={text}>{text || <em>(no visible text captured)</em>}</p>
        <p className="sample-selector"><code>{selector}</code></p>
        <div className="sample-meta">
          <span>{Math.round(fontSizePx)}px{bold ? ' bold' : ''}</span>
          <span>{largeText ? 'Large text' : 'Normal text'}</span>
          {occurrences > 1 && <span>{occurrences} elements</span>}
          <span>{foreground} on {background}</span>
        </div>
        {!measurable && note && <p className="sample-note">{note}</p>}
      </div>

      <div className="sample-verdict">
        {measurable ? (
          <>
            <span className="ratio">{contrastRatio}:1</span>
            <span className={`badge badge-${ratingClass(rating)}`}>{rating}</span>
            <span className="required">needs {requiredAA}:1</span>
          </>
        ) : (
          <span className="badge badge-indeterminate">Unmeasurable</span>
        )}
      </div>
    </li>
  );
}

const DEFICIENCIES = [
  { key: 'protanopia', label: 'Protanopia' },
  { key: 'deuteranopia', label: 'Deuteranopia' },
  { key: 'tritanopia', label: 'Tritanopia' },
  { key: 'achromatopsia', label: 'Achromatopsia' },
];

function driftBadge(originallyPassed, stillPasses) {
  if (stillPasses) return { text: 'Passes', className: 'badge-aa' };
  if (originallyPassed) return { text: 'Newly fails', className: 'badge-newly-fail' };
  return { text: 'Still fails', className: 'badge-fail' };
}

function ConfusionPairRow({ pair }) {
  return (
    <li className="confusion-pair-row">
      <div className="confusion-swatches" aria-hidden="true">
        <span className="swatch" style={{ backgroundColor: pair.colorA }} />
        <span className="confusion-vs">vs</span>
        <span className="swatch" style={{ backgroundColor: pair.colorB }} />
      </div>
      <div className="confusion-body">
        <p className="confusion-colors">{pair.colorA} and {pair.colorB}</p>
        <p className="confusion-delta">
          Distinct today (&Delta;E {pair.originalDeltaE}) &rarr; hard to tell apart under this
          deficiency (&Delta;E {pair.simulatedDeltaE})
        </p>
      </div>
    </li>
  );
}

function CvdDriftRow({ row, originalSample, activeDeficiency }) {
  const sim = row.underSimulation[activeDeficiency];
  const originallyPassed = originalSample ? originalSample.rating !== 'Fail' : row.originalRatio >= 4.5;
  const badge = driftBadge(originallyPassed, sim.stillPasses);
  const text = originalSample?.text;

  return (
    <li className={`sample-row cvd-drift-row rating-${sim.stillPasses ? 'aa' : 'fail'}`}>
      <div className="sample-swatches" aria-hidden="true">
        <span
          className="swatch swatch-text"
          style={{ backgroundColor: sim.simulatedBackground, color: sim.simulatedForeground }}
        >
          Aa
        </span>
      </div>

      <div className="sample-body">
        <p className="sample-text" title={text}>{text || <em>(no visible text captured)</em>}</p>
        <p className="sample-selector"><code>{row.selector}</code></p>
        <div className="sample-meta">
          <span>Original {row.originalRatio}:1</span>
          <span>Simulated {sim.ratio}:1</span>
        </div>
      </div>

      <div className="sample-verdict">
        <span className={`badge ${badge.className}`}>{badge.text}</span>
      </div>
    </li>
  );
}

function CvdPanel({ cvd, samples, activeDeficiency, onDeficiencyChange }) {
  const originalBySelector = useMemo(() => {
    const map = {};
    samples.forEach((s) => { map[s.selector] = s; });
    return map;
  }, [samples]);

  const driftRows = cvd?.contrastDrift?.samples || [];

  const sortedRows = useMemo(() => {
    return driftRows.slice().sort((a, b) => {
      const simA = a.underSimulation[activeDeficiency];
      const simB = b.underSimulation[activeDeficiency];
      if (simA.stillPasses !== simB.stillPasses) return simA.stillPasses ? 1 : -1;
      return (b.originalRatio - simB.ratio) - (a.originalRatio - simA.ratio);
    });
  }, [driftRows, activeDeficiency]);

  const newlyFailingCount = useMemo(() => {
    return sortedRows.filter((row) => {
      const sim = row.underSimulation[activeDeficiency];
      const orig = originalBySelector[row.selector];
      const originallyPassed = orig ? orig.rating !== 'Fail' : row.originalRatio >= 4.5;
      return originallyPassed && !sim.stillPasses;
    }).length;
  }, [sortedRows, activeDeficiency, originalBySelector]);

  const confusionPairs = (cvd?.confusionPairs || []).filter(
    (p) => p.deficiency === activeDeficiency
  );

  if (driftRows.length === 0) {
    return null;
  }

  return (
    <div className="result-card cvd-card">
      <h3>Colour vision deficiency simulation</h3>
      <div className="filter-tabs" role="tablist" aria-label="Choose a deficiency to simulate">
        {DEFICIENCIES.map((d) => (
          <button
            key={d.key}
            type="button"
            role="tab"
            aria-selected={activeDeficiency === d.key}
            className={`filter-tab ${activeDeficiency === d.key ? 'active' : ''}`}
            onClick={() => onDeficiencyChange(d.key)}
          >
            {d.label}
          </button>
        ))}
      </div>

      {newlyFailingCount > 0 && (
        <p className="cvd-summary-line cvd-summary-warn">
          {newlyFailingCount} element{newlyFailingCount === 1 ? '' : 's'} that pass contrast for
          typical vision fail under simulated {DEFICIENCIES.find((d) => d.key === activeDeficiency)?.label.toLowerCase()}.
        </p>
      )}
      {newlyFailingCount === 0 && (
        <p className="cvd-summary-line cvd-summary-ok">
          No elements newly fail under simulated {DEFICIENCIES.find((d) => d.key === activeDeficiency)?.label.toLowerCase()}.
        </p>
      )}

      {confusionPairs.length > 0 && (
        <div className="cvd-confusion-alert">
          <strong>
            {confusionPairs.length} colour pair{confusionPairs.length === 1 ? '' : 's'} on this page
            become hard to tell apart under this deficiency
          </strong>
          <ul className="confusion-pair-list">
            {confusionPairs.map((pair, idx) => (
              <ConfusionPairRow key={idx} pair={pair} />
            ))}
          </ul>
        </div>
      )}

      <ul className="sample-list">
        {sortedRows.map((row, idx) => (
          <CvdDriftRow
            key={`${row.selector}-${idx}`}
            row={row}
            originalSample={originalBySelector[row.selector]}
            activeDeficiency={activeDeficiency}
          />
        ))}
      </ul>
    </div>
  );
}

function AccessibilityResults({ data }) {
  const [filter, setFilter] = useState('all');
  const [accessibilitySettings, setAccessibilitySettings] = useState({
    fontSize: 'medium',
    highlightLinks: false,
    textToSpeech: false,
  });
  const [activeDeficiency, setActiveDeficiency] = useState('protanopia');

  const samples = data.samples || [];

  const filteredSamples = useMemo(() => {
    return samples
      .filter((s) => matchesFilter(s, filter))
      .slice()
      .sort((a, b) => {
        if (a.measurable !== b.measurable) return a.measurable ? 1 : -1;
        const ra = RATING_ORDER[a.rating] ?? 1;
        const rb = RATING_ORDER[b.rating] ?? 1;
        if (ra !== rb) return ra - rb;
        return (b.occurrences || 0) - (a.occurrences || 0);
      });
  }, [samples, filter]);

  const counts = useMemo(() => {
    const base = { all: samples.length, fail: 0, aa: 0, aaa: 0, indeterminate: 0 };
    samples.forEach((s) => {
      if (!s.measurable) {
        base.indeterminate += 1;
      } else if (s.rating === 'Fail') {
        base.fail += 1;
      } else if (s.rating === 'AA') {
        base.aa += 1;
      } else if (s.rating === 'AAA') {
        base.aaa += 1;
      }
    });
    return base;
  }, [samples]);

  return (
    <div className="results-container">
      <div className="results-grid">
        <section className="analysis-results">
          <h2>Accessibility analysis</h2>

          <SummaryCard
            summary={data.summary}
            pageTitle={data.pageTitle}
            elementsScanned={data.elementsScanned}
            hasLangAttribute={data.hasLangAttribute}
            truncated={data.truncated}
          />

          <div className="result-card samples-card">
            <div className="samples-header">
              <h3>Text and background pairs</h3>
              <div className="filter-tabs" role="tablist" aria-label="Filter results by rating">
                {FILTERS.map((f) => (
                  <button
                    key={f}
                    type="button"
                    role="tab"
                    aria-selected={filter === f}
                    className={`filter-tab ${filter === f ? 'active' : ''}`}
                    onClick={() => setFilter(f)}
                  >
                    {f === 'all' ? 'All' : f.toUpperCase()} ({counts[f]})
                  </button>
                ))}
              </div>
            </div>

            {filteredSamples.length === 0 ? (
              <p className="placeholder">No results in this category.</p>
            ) : (
              <ul className="sample-list">
                {filteredSamples.map((sample, idx) => (
                  <SampleRow key={`${sample.selector}-${idx}`} sample={sample} />
                ))}
              </ul>
            )}
          </div>

          <CvdPanel
            cvd={data.colorVisionDeficiency}
            samples={samples}
            activeDeficiency={activeDeficiency}
            onDeficiencyChange={setActiveDeficiency}
          />
        </section>

        <PreviewPane
          url={data.url}
          analysisData={data}
          activeDeficiency={activeDeficiency}
          accessibilitySettings={{
            ...accessibilitySettings,
            onFontSizeChange: (size) => setAccessibilitySettings((prev) => ({ ...prev, fontSize: size })),
            onHighlightLinksChange: (checked) => setAccessibilitySettings((prev) => ({ ...prev, highlightLinks: checked })),
            onTextToSpeechChange: (checked) => setAccessibilitySettings((prev) => ({ ...prev, textToSpeech: checked })),
          }}
        />
      </div>
    </div>
  );
}

export default AccessibilityResults;
