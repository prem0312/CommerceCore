export type UserRole = 'USER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  role: UserRole;
  name: string;
}

export interface TokenValidationResponse {
  valid?: boolean;
  userId?: number;
  email?: string;
  role?: UserRole;
  sub?: string;
  exp?: number;
  iat?: number;
  error?: string;
}
