import axios, { type AxiosInstance, type AxiosResponse, AxiosError } from 'axios';
import { generateUUID } from '../utils/idempotency';
import type { ApiErrorResponse } from '../types/api';

const TOKEN_STORAGE_KEY = 'commercecore_token';
const USER_STORAGE_KEY = 'commercecore_user';

export const getStoredToken = (): string | null => {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
};

export const setStoredAuth = (token: string, user: unknown): void => {
  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  } catch {
    // ignore storage quota errors
  }
};

export const clearStoredAuth = (): void => {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } catch {
    // ignore storage errors
  }
};

const baseURL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';

export const apiClient: AxiosInstance = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Request Interceptor: Attach JWT Bearer Token & Correlation ID
apiClient.interceptors.request.use(
  (config) => {
    // Attach JWT if present
    const token = getStoredToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Attach Distributed Correlation ID for End-to-End Tracing
    if (!config.headers['X-Correlation-Id']) {
      config.headers['X-Correlation-Id'] = generateUUID();
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Handle Global 401 & Normalize Errors
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response) {
      const status = error.response.status;

      // When encountering 401 on protected route, trigger session invalidation event
      if (status === 401) {
        clearStoredAuth();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('commercecore:unauthorized'));
        }
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Normalizes backend Spring Boot ErrorResponse and Axios errors into a readable string message.
 */
export function extractApiErrorMessage(error: unknown, fallbackMessage = 'An unexpected error occurred.'): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiErrorResponse>;
    if (axiosError.response?.data) {
      const data = axiosError.response.data;
      if (data.message) {
        return data.message;
      }
      if (data.error) {
        return `${data.error} (${axiosError.response.status})`;
      }
    }
    if (axiosError.message) {
      if (axiosError.message.includes('Network Error')) {
        return 'Unable to reach API Gateway at http://localhost:8080. Please ensure the backend is running.';
      }
      return axiosError.message;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallbackMessage;
}
