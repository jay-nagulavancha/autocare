import axios from 'axios';
import { getAuthApiBase } from '../config/runtimeEnv';

export const authClient = axios.create({
  baseURL: getAuthApiBase(),
});
