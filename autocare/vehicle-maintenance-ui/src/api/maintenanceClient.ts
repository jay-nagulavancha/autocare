import axios from 'axios';

export const maintenanceClient = axios.create({
  baseURL: import.meta.env.VITE_MAINTENANCE_API_URL ?? 'http://localhost:8081',
});

// Request interceptor — attach JWT
maintenanceClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401
maintenanceClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('jwt');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
