import { useState } from 'react';
import { PieChart, Pie, Cell, Legend } from 'recharts';

function SentimentAnalyzer() {
  const [url, setUrl] = useState('');
  const [analyzer, setAnalyzer] = useState('llm');
  const [model, setModel] = useState('gpt-3.5-turbo'); // Add this state
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [consoleMessage, setConsoleMessage] = useState('');

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);
    setConsoleMessage('');
    try {
      const response = await fetch('http://localhost:5000/analyze', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          url: url,
          analyzer_type: analyzer,
          model: model // Add this to send the model selection
        })
      });
      
      const data = await response.json();
      console.log('Response data:', data);
      setResults(data.results);
      setConsoleMessage(data.console_message);
    } catch (error) {
      setError('Error analyzing the article. Please try again.');
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

        {/* Add model selector dropdown that only shows when transformer is selected */}
        {analyzer === 'transformer' && (
          <select 
            value={model} 
            onChange={(e) => setModel(e.target.value)}
            style={{ width: '100%', padding: '8px', marginBottom: '10px' }}
          >
            <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
            <option value="gpt-4">GPT-4</option>
          </select>
        )}

        <button 
          onClick={handleSubmit}
          style={{ 
            width: '100%', 
            padding: '8px', 
            backgroundColor: '#007bff', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px' 
          }}
        >
          {loading ? 'Analyzing...' : 'Analyze'}
        </button>
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}
      {consoleMessage && <p>{consoleMessage}</p>}

      {results && (
        <div style={{ marginTop: '20px' }}>
          <PieChart width={400} height={400}>
            <Pie
              data={[
                { name: 'Left', value: results.left },
                { name: 'Right', value: results.right }
              ]}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({name, value}) => `${name}: ${value}%`}
              outerRadius={80}
              fill="#8884d8"
            >
              <Cell fill="#0088FE" />
              <Cell fill="#FF8042" />
            </Pie>
            <Legend />
          </PieChart>
        </div>
      )}
    </div>
  );
}

export default SentimentAnalyzer;