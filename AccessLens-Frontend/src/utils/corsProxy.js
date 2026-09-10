/**
 * Renders a readable error message inside the preview iframe/window when the
 * backend proxy fails to fetch or transform the target page.
 */
export function generateErrorHtml(error) {
  return `
    <div class="error" style="
      background: #FEE2E2;
      color: #2D3748;
      padding: 1rem;
      border-radius: 4px;
      margin: 1rem 0;
      font-family: sans-serif;
    ">
      <strong>Web Accessibility Tool Error:</strong>
      <div>${error.message || error || 'Unknown error'}</div>
    </div>
  `;
}
