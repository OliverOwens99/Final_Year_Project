import { useState } from 'react'
import reactLogo from './assets/react.svg'
import SentimentAnalyzer from './components/SentimentAnalyser'
import viteLogo from '/vite.svg'
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
