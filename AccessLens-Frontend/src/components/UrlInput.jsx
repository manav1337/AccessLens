import { useState } from 'react';
import api from '../services/api';
import '../styles/components/urlinput.css';

function UrlInput({ onAnalysisComplete }) {
  const [url, setUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const analyzeWebsite = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await api.analyzeWebsite(url);

      if (!response.data) {
        throw new Error('Empty response from server');
      }

      onAnalysisComplete({ ...response.data, url });
    } catch (err) {
      console.error('Full error details:', {
        message: err.message,
        response: err.response?.data,
        status: err.response?.status,
      });

      setError(
        err.response?.data?.message ||
        err.response?.data ||
        'Failed to analyze website. Please check the URL and try again.'
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="url-input-container">
      <div className="input-group">
        <input
          type="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com"
          aria-label="Website URL to analyze"
          disabled={isLoading}
        />
        <button
          onClick={analyzeWebsite}
          disabled={!url || isLoading}
          aria-busy={isLoading}
        >
          {isLoading ? 'Analyzing...' : 'Analyze'}
        </button>
      </div>
      {error && (
        <p className="error-message" role="alert" aria-live="assertive">
          {error}
        </p>
      )}
    </div>
  );
}

export default UrlInput;
