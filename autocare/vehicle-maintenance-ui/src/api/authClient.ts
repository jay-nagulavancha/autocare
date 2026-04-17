import axios from 'axios';

export const authClient = axios.create({
  baseURL: import.meta.env.VITE_AUTH_API_URL ?? 'http://localhost:8080',
});
