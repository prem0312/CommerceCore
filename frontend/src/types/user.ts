import type { UserRole } from './auth';

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export interface AddressRequest {
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

export interface AddressResponse {
  id: number;
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
  createdAt: string;
}

export interface UpdateProfileRequest {
  name: string;
  phone?: string;
}

export interface UpdateStatusRequest {
  status: UserStatus;
}

export interface UserProfileResponse {
  id: number;
  userId: number;
  name: string;
  email: string;
  phone: string | null;
  role: UserRole;
  status: UserStatus;
  addresses: AddressResponse[];
  createdAt: string;
  updatedAt: string;
}
