import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { BRAND } from './config/brand'
import './styles/index.css'

document.title = BRAND.pageTitle

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
