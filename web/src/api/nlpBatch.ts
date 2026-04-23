import apiClient from './client';
import type { BatchParseRequest, BatchParseResponse } from '@/types/batch';

/**
 * Wrapper around `POST /ai/batch-parse`. The endpoint is stateless — the
 * caller supplies a fresh user context snapshot so the model knows which
 * entity IDs it can reference. The response is always a preview; the
 * server never writes. Commit happens client-side after user approval.
 */
export const nlpBatchApi = {
  parse(data: BatchParseRequest): Promise<BatchParseResponse> {
    return apiClient.post('/ai/batch-parse', data).then((r) => r.data);
  },
};
