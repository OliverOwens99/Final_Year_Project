import { BrowserRouter as Router, Route, Switch } from 'react-router-dom';
import SentimentAnalyzer from './components/SentimentAnalyser';
import Login from './components/Login';
import Register from './components/Register';
import './App.css';

function App() {
  return (
    <Router>
      <Switch>
        <Route path="/login" component={Login} />
        <Route path="/register" component={Register} />
        <Route path="/" component={SentimentAnalyzer} />
      </Switch>
    </Router>
  );
}

export default App;