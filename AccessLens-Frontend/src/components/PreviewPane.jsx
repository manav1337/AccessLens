import { useEffect, useState } from 'react';
import '../styles/components/preview.css';
import { generateErrorHtml } from '../utils/corsProxy';
import { getContrastingTextColor, adjustColorForAccessibility } from '../utils/colorUtils';
import CvdFilters, { CVD_FILTER_MARKUP } from './CvdFilters';

const DEFICIENCY_LABELS = {
  protanopia: 'protanopia',
  deuteranopia: 'deuteranopia',
  tritanopia: 'tritanopia',
  achromatopsia: 'achromatopsia',
};

function PreviewPane({ url, analysisData, accessibilitySettings, activeDeficiency }) {
  const [transformedHtml, setTransformedHtml] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Function to apply accessibility transformations
  const applyTransformations = (html) => {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');

    // Apply font size
    const fontSizeMap = {
      small: '14px',
      medium: '16px',
      large: '18px',
      'x-large': '20px'
    };
    doc.body.style.fontSize = fontSizeMap[accessibilitySettings.fontSize] || '16px';

    // Apply link highlighting
    if (accessibilitySettings.highlightLinks) {
      const links = doc.querySelectorAll('a');
      links.forEach(link => {
        link.style.backgroundColor = 'yellow';
        link.style.color = 'black';
        link.style.padding = '2px';
        link.style.fontWeight = 'bold';
      });
    }

    const bgColor = '#ffffff';
    const textColor = '#000000';

    // Create accessibility override styles
    const style = doc.createElement('style');
    style.textContent = `

    a {
      color: ${adjustColorForAccessibility(textColor, 'text')} !important;
      text-decoration: underline !important;
      font-weight: bold !important;
    }

    :focus {
      outline: 3px solid ${adjustColorForAccessibility(textColor, 'text')} !important;
      outline-offset: 2px !important;
    }
  `;
    doc.head.appendChild(style);

    return doc.documentElement.outerHTML;
  };

  const filterId = activeDeficiency && DEFICIENCY_LABELS[activeDeficiency]
    ? `cvd-${activeDeficiency}`
    : null;

  /**
   * Wraps the already-transformed HTML string so the currently active CVD filter
   * carries over into the new-tab window, not just the inline iframe. String
   * surgery on the body tag (rather than re-parsing with DOMParser again) keeps
   * this cheap and avoids a second transformation pass.
   */
  const withCvdFilter = (html) => {
    if (!filterId) return html;
    return html
      .replace(/<body([^>]*)>/i, `<body$1>${CVD_FILTER_MARKUP}<div class="cvd-filter-wrapper" style="filter:url(#${filterId});">`)
      .replace(/<\/body>/i, '</div></body>');
  };

  const openTransformedWebsite = () => {
    if (!transformedHtml) return;

    const newWindow = window.open('', '_blank');
    newWindow.document.write(withCvdFilter(transformedHtml));
    newWindow.document.close();

    // Apply text-to-speech if enabled
    if (accessibilitySettings.textToSpeech) {
      newWindow.addEventListener('DOMContentLoaded', () => {
        const speak = (text) => {
          const utterance = new SpeechSynthesisUtterance(text);
          utterance.lang = 'en-US';
          window.speechSynthesis.speak(utterance);
        };

        // Speak the page title
        speak(newWindow.document.title);

        // Speak when elements are focused
        newWindow.document.body.addEventListener('focus', (e) => {
          if (e.target.ariaLabel) {
            speak(e.target.ariaLabel);
          } else if (e.target.innerText) {
            speak(e.target.innerText);
          }
        }, true);
      });
    }
  };

  useEffect(() => {
    if (url && analysisData) {
      setLoading(true);
      setError(null);

      const fetchAndTransform = async () => {
        try {
          const proxyUrl = `/api/accessibility/proxy?url=${encodeURIComponent(url)}`;
          const response = await fetch(proxyUrl);

          if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Proxy error (${response.status}): ${errorText}`);
          }

          const html = await response.text();
          const transformed = applyTransformations(html);
          setTransformedHtml(transformed);
        } catch (error) {
          console.error('Error fetching content:', error);
          setError(error.message);
          setTransformedHtml(generateErrorHtml(error.message));
        } finally {
          setLoading(false);
        }
      };

      fetchAndTransform();
    } else {
      setTransformedHtml('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, analysisData, accessibilitySettings]);

  const openButtonLabel = filterId
    ? `Open accessible website in new tab (${DEFICIENCY_LABELS[activeDeficiency]} view)`
    : 'Open accessible website in new tab';

  return (
    <section className="preview-pane">
      <h2>Accessible website</h2>
      <CvdFilters />

      {loading && (
        <div className="loading-indicator">
          <div className="spinner"></div>
          <p>Applying accessibility transformations...</p>
        </div>
      )}

      {error && (
        <div className="error-message">
          <p>{error}</p>
        </div>
      )}

      {transformedHtml && !loading && (
        <div className="access-container">
          {filterId && (
            <p className="cvd-preview-label">
              Showing the live preview as it would appear under simulated {DEFICIENCY_LABELS[activeDeficiency]}.
              This uses a fast CSS approximation for visual intuition — the numbers in the report
              above are computed separately and are the authoritative source.
            </p>
          )}

          <div className="live-preview-frame-wrapper">
            <iframe
              title="Live accessible preview"
              srcDoc={transformedHtml}
              sandbox="allow-same-origin"
              className="live-preview-frame"
              style={filterId ? { filter: `url(#${filterId})` } : undefined}
            />
          </div>
          <p className="resize-hint">Drag the bottom-right corner to resize the preview.</p>

          <button
            className="open-website-btn"
            onClick={openTransformedWebsite}
            aria-label={openButtonLabel}
            disabled={loading}
          >
            {openButtonLabel}
          </button>

          <div className="preview-note">
            <p>This version includes:</p>
            <ul>
              <li>Enhanced color contrast</li>
              <li>Font size: {accessibilitySettings.fontSize}</li>
              <li>Link highlighting: {accessibilitySettings.highlightLinks ? 'On' : 'Off'}</li>
              <li>Text-to-speech: {accessibilitySettings.textToSpeech ? 'Enabled' : 'Disabled'}</li>
              {filterId && <li>Colour vision filter: {DEFICIENCY_LABELS[activeDeficiency]}</li>}
            </ul>
          </div>
        </div>
      )}

      {!transformedHtml && !loading && !error && (
        <p className="placeholder">Enter a URL to generate accessible version</p>
      )}
    </section>
  );
}

export default PreviewPane;
