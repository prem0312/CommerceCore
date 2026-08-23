export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';
export type NotificationStatus = 'SENT' | 'PENDING' | 'FAILED';

export interface NotificationResponse {
  id: number;
  userId: number;
  channel: NotificationChannel;
  template: string;
  recipient: string;
  subject: string;
  content: string;
  status: NotificationStatus;
  eventId: string;
  correlationId: string;
  createdAt: string;
}
