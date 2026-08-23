import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../api/authApi';
import { getStoredToken, setStoredAuth, clearStoredAuth } from '../api/client';
import type { AuthResponse, LoginRequest, RegisterRequest, UserRole } from '../types/auth';

export interface AuthUser {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
}

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<AuthResponse>;
  register: (data: RegisterRequest) => Promise<AuthResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const USER_STORAGE_KEY = 'commercecore_user';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const stored = localStorage.getItem(USER_STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const logout = useCallback(() => {
    clearStoredAuth();
    setToken(null);
    setUser(null);
  }, []);

  // Listen for unauthorized events triggered by API client interceptor
  useEffect(() => {
    const handleUnauthorized = () => {
      logout();
    };

    window.addEventListener('commercecore:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('commercecore:unauthorized', handleUnauthorized);
    };
  }, [logout]);

  // Background validation check on mount
  useEffect(() => {
    const validateCurrentSession = async () => {
      const currentToken = getStoredToken();
      if (!currentToken) return;

      try {
        const validation = await authApi.validateToken();
        if (validation && validation.valid === false) {
          logout();
        }
      } catch {
        // If backend is unreachable during local startup or test, maintain current state
      }
    };

    validateCurrentSession();
  }, [logout]);

  const login = async (data: LoginRequest): Promise<AuthResponse> => {
    setIsLoading(true);
    try {
      const response = await authApi.login(data);
      const authUser: AuthUser = {
        userId: response.userId,
        email: response.email,
        name: response.name,
        role: response.role,
      };

      setStoredAuth(response.accessToken, authUser);
      setToken(response.accessToken);
      setUser(authUser);
      return response;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegisterRequest): Promise<AuthResponse> => {
    setIsLoading(true);
    try {
      const response = await authApi.register(data);
      const authUser: AuthUser = {
        userId: response.userId,
        email: response.email,
        name: response.name,
        role: response.role,
      };

      setStoredAuth(response.accessToken, authUser);
      setToken(response.accessToken);
      setUser(authUser);
      return response;
    } finally {
      setIsLoading(false);
    }
  };

  const isAuthenticated = !!token && !!user;
  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated,
        isAdmin,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
