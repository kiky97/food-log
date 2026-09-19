import { mockApi } from './mockApi';
import type { DailySummary, FoodLog, SearchHit } from './types';

export interface Api {
  logText(userId: string, text: string): Promise<FoodLog>;
  logVoice(userId: string, audio: Blob, filename: string): Promise<FoodLog>;
  confirmItem(logId: string, index: number, foodId: string): Promise<FoodLog>;
  listLogs(userId: string, date?: string): Promise<FoodLog[]>;
  summary(userId: string, date?: string): Promise<DailySummary>;
  searchFoods(query: string): Promise<SearchHit[]>;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

/** The backend answers errors with RFC 9457 problem details; fall back to a generic message otherwise. */
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running on port 8080?');
  }
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    try {
      const problem = await response.json();
      if (typeof problem?.detail === 'string' && problem.detail) detail = problem.detail;
    } catch {
      /* body was not JSON */
    }
    throw new ApiError(response.status, detail);
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(response.status, 'The server sent an unexpected response. Is the /api proxy pointing at the backend?');
  }
}

function query(params: Record<string, string | undefined>): string {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) q.set(key, value);
  }
  return q.toString();
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const httpApi: Api = {
  logText: (userId, text) => request('/api/v1/logs', json({ userId, text })),

  logVoice(userId, audio, filename) {
    const form = new FormData();
    form.append('userId', userId);
    form.append('audio', audio, filename);
    return request('/api/v1/logs/voice', { method: 'POST', body: form });
  },

  confirmItem: (logId, index, foodId) =>
    request(`/api/v1/logs/${encodeURIComponent(logId)}/items/${index}/confirm`, json({ foodId })),

  listLogs: (userId, date) => request(`/api/v1/logs?${query({ userId, date })}`),

  summary: (userId, date) => request(`/api/v1/logs/summary?${query({ userId, date })}`),

  searchFoods: (q) => request(`/api/v1/foods/search?${query({ q, limit: '6' })}`),
};

/** `npm run dev:mock` swaps in an in-memory backend so the UI can be used without Spring Boot or MongoDB. */
export const api: Api = import.meta.env.MODE === 'mock' ? mockApi : httpApi;
