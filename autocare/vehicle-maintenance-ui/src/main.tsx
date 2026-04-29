import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import App from './App';
import ServiceVersionsPanel from './components/ServiceVersionsPanel';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
        <ServiceVersionsPanel />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);
