import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * The nlpBatch wrapper is a thin POST — all we verify is that it hits
 * the right path and returns the body. The Axios instance is mocked at
 * the client layer so these tests never touch the network.
 */
const { postMock } = vi.hoisted(() => ({ postMock: vi.fn() }));
vi.mock('@/api/client', () => ({
  default: { post: postMock },
}));

import { nlpBatchApi } from '@/api/nlpBatch';
import type { BatchParseRequest, BatchParseResponse } from '@/types/batch';

describe('nlpBatchApi.parse', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('POSTs the request body to /ai/batch-parse and unwraps .data', async () => {
    const response: BatchParseResponse = {
      mutations: [],
      confidence: 1.0,
      ambiguous_entities: [],
      proposed: true,
    };
    postMock.mockResolvedValueOnce({ data: response });

    const req: BatchParseRequest = {
      command_text: 'reschedule all tasks tomorrow',
      user_context: {
        today: '2026-04-23',
        timezone: 'UTC',
        tasks: [],
        habits: [],
        projects: [],
        medications: [],
      },
    };
    const result = await nlpBatchApi.parse(req);
    expect(postMock).toHaveBeenCalledWith('/ai/batch-parse', req);
    expect(result).toEqual(response);
  });
});
