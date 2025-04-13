import { useState, useEffect } from 'react';
import { createBrowserRouter, RouterProvider, Navigate, Outlet, Link, useNavigate } from 'react-router-dom';
import SentimentAnalyzer from './components/SentimentAnalyser';
import Login from './components/Login';
import Register from './components/Register';
import History from './components/History';
import './App.css';

// Layout with built-in navigation bar
const AuthenticatedLayout = () => {
  const [username, setUsername] = useState('');
  const navigate = useNavigate();
  
  useEffect(() => {
    fetch('http://localhost:5000/check-auth', { credentials: 'include' })
      .then(res => res.json())
      .then(data => {
        if (data.authenticated) {
          setUsername(data.user);
        }
      })
      .catch(err => console.error('Error checking auth:', err));
  }, []);
  
  const handleLogout = () => {
    fetch('http://localhost:5000/logout', { 
      method: 'POST', 
      credentials: 'include' 
    })
    .then(() => navigate('/login'))
    .catch(err => console.error('Error logging out:', err));
  };

  return (
    <>
      {/* Modern navigation bar */}
      <nav className="app-navbar">
        <div className="navbar-brand">
          <h2>Political Bias Analyzer</h2>
        </div>
        <div className="navbar-links">
          <Link to="/analyze" className="nav-link">Analyser</Link>
          <Link to="/history" className="nav-link">History</Link>
        </div>
        <div className="navbar-user">
          {username && <span className="username">Welcome, {username}</span>}
          <button onClick={handleLogout} className="logout-button">Logout</button>
        </div>
      </nav>
      
      <main className="app-content">
        <Outlet />
      </main>
      
      <footer className="app-footer">
        <p>© 2025 Political Bias Analyser</p>
      </footer>
    </>
  );
};

// Routes remain the same
const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  { path: '/register', element: <Register /> },
  {
    element: <AuthenticatedLayout />,
    children: [
      { path: '/analyze', element: <SentimentAnalyzer /> },
      { path: '/history', element: <History /> }
    ]
  },
  { path: '/', element: <Navigate to="/login" replace /> }
]);

function App() {
  return <RouterProvider router={router} />;
}
export default App;