import axios from 'axios';

// In development, CRA's dev-server proxy (see package.json "proxy" field) forwards
// relative requests to the backend automatically, so no baseURL is needed there.
// In production, the frontend and backend are expected to be served under a shared
// path so relative requests still resolve correctly.
const api = axios.create({
  headers: {
    'Content-Type': 'application/json',
  },
});

export default {
  /**
   * Runs a full accessibility analysis on a URL.
   * Matches AccessibilityController's GET /api/accessibility endpoint.
   */
  analyzeWebsite(url, mode = 'default') {
    return api.get('/api/accessibility', { params: { url, mode } });
  },

  /**
   * Fetches a cleaned, renderable version of the target page for the preview pane.
   * Matches AccessibilityController's GET /api/accessibility/proxy endpoint.
   */
  fetchProxiedPage(url) {
    return api.get('/api/accessibility/proxy', {
      params: { url },
      responseType: 'text',
    });
  },
};
