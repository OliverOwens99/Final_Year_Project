import { useState, useEffect } from 'react';
import { PieChart, Pie, Cell, Legend } from 'recharts';
import { useNavigate, useLocation } from 'react-router-dom';

function SentimentAnalyzer() {
  const location = useLocation();
  const navigate = useNavigate();
  
  // Initialize state with values from navigation, if available
  const [url, setUrl] = useState(location.state?.url || '');
  const [analyzer, setAnalyzer] = useState(location.state?.analyzerType || 'lexicon');
  const [model, setModel] = useState(location.state?.model || 'mistral-7b');
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [consoleMessage, setConsoleMessage] = useState('');

  // Check authentication when component mounts
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await fetch('http://localhost:5000/check-auth', {
          credentials: 'include' // Important for sending cookies
        });

        const data = await response.json();
        if (!response.ok || !data.authenticated) {
          navigate('/login');
        }
      } catch (err) {
        console.error('Authentication check failed:', err);
        navigate('/login');
      }
    };

    checkAuth();
  }, [navigate]);

  const handleSubmit = async () => {
    if (!url.trim()) {
      setError('Please enter a URL to analyze');
      return;
    }

    setLoading(true);
    setError(null);
    setConsoleMessage('');

    try {
      const response = await fetch('http://localhost:5000/analyze', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          url: url,
          analyzer_type: analyzer,
          model: model
        })
      });

      if (!response.ok) {
        if (response.status === 401) {
          navigate('/login');
          return;
        }
        const errorData = await response.json();
        throw new Error(errorData.error || 'Server error');
      }

      const data = await response.json();
      setResults(data.results);
      setConsoleMessage(data.console_message || 'Analysis complete');
    } catch (error) {
      setError(error.message || 'Error analyzing the article. Please try again.');
      console.error('Error:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="sentiment-analyzer-container">
      <div className="page-header">
        <h1>Political Bias Analyser</h1>
        <p className="subtitle">Analyse the political leaning of any online article</p>
      </div>

      <div className="analyzer-form">
        <div className="card">
          <div className="card-body">
            <div className="form-group">
              <label htmlFor="url-input">Article URL</label>
              <input
                id="url-input"
                type="text"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="Enter article URL (e.g., https://www.example.com/article)"
              />
            </div>

            <div className="analyzer-options">
              <div className="form-group">
                <label htmlFor="analyzer-select">Analysis Method</label>
                <select
                  id="analyzer-select"
                  value={analyzer}
                  onChange={(e) => setAnalyzer(e.target.value)}
                >
                  <option value="llm">BERT Pre-trained Political Analyser</option>
                  <option value="transformer">Transformer Analysis</option>
                  <option value="lexicon">Lexicon Analysis</option>
                </select>
              </div>

              {analyzer === 'transformer' && (
                <div className="form-group">
                  <label htmlFor="model-select">AI Model</label>
                  <select
                    id="model-select"
                    value={model}
                    onChange={(e) => setModel(e.target.value)}
                  >
                    <option value="mistral-7b">Mistral 7B Instruct</option>
                    <option value="gemma-2b-it">Google Gemma 2B-IT</option>
                    <option value="llama-2-7b">Meta Llama 2 7B Chat</option>
                    <option value="deepseek-chat">DeepSeek 7B Chat</option>
                  </select>
                </div>
              )}
            </div>

            {error && <div className="error-message">{error}</div>}
            {consoleMessage && !error && <div className="success-message">{consoleMessage}</div>}

            <button 
              onClick={handleSubmit} 
              disabled={loading}
              className={loading ? "btn-secondary" : "btn-primary"}
            >
              {loading ? 'Analysing...' : 'Analyse Article'}
            </button>
          </div>
        </div>

        {results && (
          <div className="results-container card">
            <div className="card-header">
              <h2>Analysis Results</h2>
            </div>
            <div className="card-body">
              <div className="results-section">
                <div className="text-analysis">
                  {results.message && (
                    <div className="message-box">
                      <h3>Political Leaning</h3>
                      <p>{results.message}</p>
                    </div>
                  )}
                  
                  {results.explanation && (
                    <div className="explanation-section">
                      <h3>Detailed Explanation</h3>
                      <div className="explanation-box">
                        <p>{results.explanation}</p>
                      </div>
                    </div>
                  )}
                </div>
                
                <div className="visualization-section">
                  <h3>Political Bias Distribution</h3>
                  <div className="chart-container">
                    <PieChart width={200} height={200}>
                      <Pie
                        data={[
                          { name: 'Left', value: parseFloat(results.left) || 50 },
                          { name: 'Right', value: parseFloat(results.right) || 50 },
                        ]}
                        dataKey="value"
                        cx="50%"
                        cy="50%"
                        outerRadius={80}
                        fill="#8884d8"
                        label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                      >
                        <Cell key="left" fill="#0066cc" />
                        <Cell key="right" fill="#cc0000" />
                      </Pie>
                      <Legend />
                    </PieChart>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default SentimentAnalyzer;