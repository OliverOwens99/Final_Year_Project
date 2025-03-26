import { useState, useEffect } from 'react';
import { PieChart, Pie, Cell, Legend } from 'recharts';
import { useNavigate } from 'react-router-dom';

function SentimentAnalyzer() {
  const [url, setUrl] = useState('');
  const [analyzer, setAnalyzer] = useState('lexicon');
  //const [analyzer, setAnalyzer] = useState('llm');
  const [model, setModel] = useState('gpt-3.5-turbo');
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [consoleMessage, setConsoleMessage] = useState('');
  const navigate = useNavigate();

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
        credentials: 'include', // Important for sending cookies
        body: JSON.stringify({
          url: url,
          analyzer_type: analyzer,
          model: model
        })
      });

      if (!response.ok) {
        if (response.status === 401) {
          // Redirect to login if not authenticated
          navigate('/login');
          return;
        }

        const errorData = await response.json();
        throw new Error(errorData.error || 'Server error');
      }

      const data = await response.json();
      console.log('Response data:', data);
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
    <div style={{ padding: '20px', maxWidth: '600px', margin: '0 auto' }}>
      <h1>Sentiment Analyser</h1>
      <p>Enter the link to the article you want to analyze and select the analysis module from the dropdown menu.</p>

      <div style={{ marginBottom: '20px' }}>
        <input
          type="text"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="Enter article URL"
          style={{ width: '100%', padding: '8px', marginBottom: '10px' }}
        />

        <select
          value={analyzer}
          onChange={(e) => setAnalyzer(e.target.value)}
          style={{ width: '100%', padding: '8px', marginBottom: '10px' }}
        >
          <option value="llm">LLM Analysis</option>
          <option value="transformer">Transformer Analysis</option>
          <option value="lexicon">Lexicon Analysis</option>
        </select>

        {/* Updated model selector dropdown with all models from TransformerAnalyzer.java */}
        {analyzer === 'transformer' && (
          <select
            value={model}
            onChange={(e) => setModel(e.target.value)}
            style={{ width: '100%', padding: '8px', marginBottom: '10px' }}
          >
            <option value="gpt-3.5-turbo">GPT-3.5 Turbo (OpenAI)</option>
            <option value="tiny-llama">TinyLlama-1.1B (Hugging Face)</option>
            <option value="deepseek-1.3b">DeepSeek-1.3B (Hugging Face)</option>
          </select>
        )}

        <button
          onClick={handleSubmit}
          disabled={loading}
          style={{
            width: '100%',
            padding: '8px',
            backgroundColor: loading ? '#cccccc' : '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: loading ? 'not-allowed' : 'pointer'
          }}
        >
          {loading ? 'Analyzing...' : 'Analyze'}
        </button>
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}
      {consoleMessage && <p>{consoleMessage}</p>}

      {results && (
        <div style={{ marginTop: '20px' }}>
          <h2>Analysis Results</h2>
          {(results.explanation || results.message) && (
            <div style={{ marginBottom: '20px' }}>
              <h3>Explanation:</h3>
              <p>{results.explanation || results.message}</p>
            </div>
          )}
          <PieChart width={400} height={400}>
            <Pie
              data={[
                { name: 'Left', value: parseFloat(results.left || results.leftPercentage || 0) },
                { name: 'Right', value: parseFloat(results.right || results.rightPercentage || 0) }
              ]}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({ name, value }) => `${name}: ${value.toFixed(1)}%`}
              outerRadius={80}
              fill="#8884d8"
            >
              <Cell fill="#0088FE" /> {/* Left - Blue */}
              <Cell fill="#FF8042" /> {/* Right - Orange */}
            </Pie>
            <Legend />
          </PieChart>
        </div>
      )}
    </div>
  );
}

export default SentimentAnalyzer;