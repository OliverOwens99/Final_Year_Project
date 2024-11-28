import { useState } from 'react'
import SentimentAnalyzer from './components/SentimentAnalyser'
import './App.css'

function App() {
  const [count, setCount] = useState(0)

  return (
    <div>
      <SentimentAnalyzer/>
    </div>
  )
}

export default App
