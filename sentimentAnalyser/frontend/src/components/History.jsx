import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { PieChart, Pie, Cell, Legend, ResponsiveContainer } from 'recharts';

function History() {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const response = await fetch('http://localhost:5000/history', {
          credentials: 'include'
        });
        
        if (!response.ok) {
          if (response.status === 401) {
            navigate('/login');
            return;
          }
          throw new Error('Failed to fetch history');
        }
        
        const data = await response.json();
        setHistory(Array.isArray(data) ? data : []);
      } catch (error) {
        console.error('Error fetching history:', error);
        setError('Failed to load history. Please try again later.');
      } finally {
        setLoading(false);
      }
    };
    
    fetchHistory();
  }, [navigate]);

  const handleReAnalyze = (url, analyzerType, model) => {
    navigate('/analyze', { state: { url, analyzerType, model } });
  };

  if (loading) return (
    <div className="loading-container">
      <div className="spinner"></div>
      <p>Loading your analysis history...</p>
    </div>
  );
  
  if (error) return (
    <div className="error-container">
      <h2>Error</h2>
      <p>{error}</p>
      <button onClick={() => window.location.reload()} className="btn-primary">Try Again</button>
    </div>
  );

  return (
    <div className="history-container">
      <div className="page-header">
        <h1>Analysis History</h1>
        <p className="subtitle">Your previous article analyses</p>
      </div>
      
      {history.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">📊</div>
          <h2>No analyses yet</h2>
          <p>You haven't analyzed any articles yet.</p>
          <button onClick={() => navigate('/analyze')} className="btn-primary">Analyse an Article</button>
        </div>
      ) : (
        <div className="history-list">
          {history.map((item, index) => (
            <div key={index} className="analysis-card">
              <div className="card-header">
                <h3 className="article-title">
                  <a 
                    href={item.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {item.url.replace(/^https?:\/\//, '').split('/')[0]}
                  </a>
                </h3>
                <div className="meta-info">
                  <span>Analysed on {new Date(item.date).toLocaleDateString()}</span>
                  <span className="separator">•</span>
                  <span>Method: {item.analyzer_type}</span>
                  {item.model && (
                    <>
                      <span className="separator">•</span>
                      <span>Model: {item.model}</span>
                    </>
                  )}
                </div>
              </div>
              
              <div className="results-section">
                <div className="text-analysis">
                  <h3>Analysis</h3>
                  <div className="message-box">
                    <p>{item.message}</p>
                  </div>
                  
                  {item.explanation && (
                    <div className="explanation-section">
                      <h3>Detailed Explanation</h3>
                      <div className="explanation-box">
                        <p>{item.explanation}</p>
                      </div>
                    </div>
                  )}
                </div>
                
                <div className="visualization-section">
                  <h3>Political Bias Distribution</h3>
                  <div className="chart-container">
                    <ResponsiveContainer width="100%" height={200}>
                      <PieChart>
                        <Pie
                          data={[
                            { name: 'Left', value: parseFloat(item.left) || 50 },
                            { name: 'Right', value: parseFloat(item.right) || 50 },
                          ]}
                          dataKey="value"
                          cx="50%"
                          cy="50%"
                          outerRadius={80}
                          fill="#8884d8"
                          label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                          labelLine={false}
                        >
                          <Cell key="left" fill="#0066cc" />
                          <Cell key="right" fill="#cc0000" />
                        </Pie>
                        <Legend verticalAlign="bottom" align="center" />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
              
              <div className="card-footer">
                <button
                  onClick={() => handleReAnalyze(item.url, item.analyzer_type, item.model)}
                  className="btn-primary"
                >
                  Re-analyse
                </button>
                <a href={item.url} target="_blank" rel="noopener noreferrer" className="source-link">
                  View Source Article
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default History;