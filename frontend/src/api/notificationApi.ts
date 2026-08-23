import { apiClient } from './client';
import type { Page } from '../types/api';
import type { NotificationResponse } from '../types/notification';

export const notificationApi = {
  async getUserNotifications(userId: number, page = 0, size = 10): Promise<Page<NotificationResponse>> {
    const response = await apiClient.get<Page<NotificationResponse>>(`/api/v1/notifications/user/${userId}`, {
      params: { page, size },
    });
    return response.data;
  },

  async getAllUserNotifications(userId: number): Promise<NotificationResponse[]> {
    const response = await apiClient.get<NotificationResponse[]>(`/api/v1/notifications/user/${userId}/all`);
    return response.data;
  },
};
