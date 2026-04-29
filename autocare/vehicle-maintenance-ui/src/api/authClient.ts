import axios from 'axios';
import { getAuthApiBase } from '../config/runtimeEnv';

export const authClient = axios.create();

authClient.interceptors.request.use((config) => {
  config.baseURL = getAuthApiBase();
  return config;
});
