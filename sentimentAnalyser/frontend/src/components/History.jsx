import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { PieChart, Pie, Cell, Legend } from 'recharts';

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
            // Redirect to login if not authenticated
            navigate('/login');
            return;
          }
          throw new Error('Failed to fetch history');
        }
        
        const data = await response.json();
        setHistory(data);
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

  if (loading) return <div style={{ textAlign: 'center', padding: '20px' }}>Loading history...</div>;
  if (error) return <div style={{ textAlign: 'center', padding: '20px', color: 'red' }}>{error}</div>;

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
      <h1>Your Analysis History</h1>
      
      {history.length === 0 ? (
        <p>You haven't analyzed any articles yet.</p>
      ) : (
        <div>
          {history.map((item, index) => (
            <div 
              key={index}
              style={{
                backgroundColor: '#f9f9f9',
                borderRadius: '8px',
                padding: '15px',
                marginBottom: '20px',
                boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
              }}
            >
              <h3 style={{ marginTop: 0 }}>
                <a 
                  href={item.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ color: '#007bff' }}
                >
                  {item.url}
                </a>
              </h3>
              
              <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between' }}>
                <div style={{ flex: '1', minWidth: '300px' }}>
                  <p><strong>Date analyzed:</strong> {new Date(item.date).toLocaleDateString()}</p>
                  <p><strong>Analyzer used:</strong> {item.analyzer_type}</p>
                  {item.model && <p><strong>Model:</strong> {item.model}</p>}
                  <p><strong>Analysis:</strong> {item.message}</p>
                  {item.explanation && <p><strong>Explanation:</strong> {item.explanation}</p>}
                </div>
                
                <div style={{ flex: '0 0 auto', width: '240px' }}>
                  <PieChart width={200} height={200}>
                    <Pie
                      data={[
                        { name: 'Left', value: parseFloat(item.left) || 50 },
                        { name: 'Right', value: parseFloat(item.right) || 50 },
                      ]}
                      dataKey="value"
                      cx="50%"
                      cy="50%"
                      outerRadius={60}
                      fill="#8884d8"
                      label
                    >
                      <Cell key="left" fill="#0000FF" />
                      <Cell key="right" fill="#FF0000" />
                    </Pie>
                    <Legend />
                  </PieChart>
                </div>
              </div>
              
              <button
                onClick={() => handleReAnalyze(item.url, item.analyzer_type, item.model)}
                style={{
                  backgroundColor: '#007bff',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  padding: '8px 15px',
                  cursor: 'pointer',
                  marginTop: '10px'
                }}
              >
                Re-analyze
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default History;