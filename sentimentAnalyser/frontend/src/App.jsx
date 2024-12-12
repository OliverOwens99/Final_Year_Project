import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import SentimentAnalyzer from './components/SentimentAnalyser';
import Login from './components/Login';
import Register from './components/Register';
import './App.css';

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />
  },
  {
    path: '/register',
    element: <Register />
  },
  {
    path: '/analyze',
    element: <SentimentAnalyzer />
  },
  {
    path: '/',
    element: <Navigate to="/login" replace />
  }
]);

function App() {
  return <RouterProvider router={router} />;
}

export default App;