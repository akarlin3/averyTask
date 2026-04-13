import apiClient from './client';

export interface ActivityLogSummary {
  id: number;
  user_id: number;
  user_email: string | null;
  project_id: number;
  action: string;
  entity_type: string | null;
  entity_id: number | null;
  entity_title: string | null;
  metadata_json: string | null;
  created_at: string;
}

export interface ActivityLogStats {
  total_logs: number;
  logs_today: number;
  logs_this_week: number;
  unique_users: number;
  top_actions: Array<{ action: string; count: number }>;
}

export interface PaginatedActivityLogs {
  items: ActivityLogSummary[];
  total: number;
  page: number;
  per_page: number;
  total_pages: number;
}

export interface ListActivityLogsParams {
  user_id?: number;
  action?: string;
  entity_type?: string;
  sort?: 'newest' | 'oldest';
  page?: number;
  per_page?: number;
}

export const adminActivityLogsApi = {
  list(params: ListActivityLogsParams = {}): Promise<PaginatedActivityLogs> {
    return apiClient
      .get('/admin/activity-logs', { params })
      .then((r) => r.data);
  },

  get(logId: number): Promise<ActivityLogSummary> {
    return apiClient
      .get(`/admin/activity-logs/${logId}`)
      .then((r) => r.data);
  },

  stats(): Promise<ActivityLogStats> {
    return apiClient
      .get('/admin/activity-logs/stats')
      .then((r) => r.data);
  },
};
