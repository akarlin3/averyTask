import { describe, it, expect, vi, beforeEach } from 'vitest';

const { postMock } = vi.hoisted(() => ({ postMock: vi.fn() }));
vi.mock('@/api/client', () => ({ default: { post: postMock } }));

import { aiApi } from '@/api/ai';

describe('aiApi.eisenhowerClassifyText', () => {
  beforeEach(() => postMock.mockReset());

  it('POSTs the request body and unwraps .data', async () => {
    postMock.mockResolvedValueOnce({
      data: { quadrant: 'Q2', reason: 'Important, not urgent' },
    });
    const res = await aiApi.eisenhowerClassifyText({
      title: 'Prepare board deck',
      description: 'Needs data from sales',
      due_date: '2026-05-02',
      priority: 3,
    });
    expect(postMock).toHaveBeenCalledWith('/ai/eisenhower/classify_text', {
      title: 'Prepare board deck',
      description: 'Needs data from sales',
      due_date: '2026-05-02',
      priority: 3,
    });
    expect(res.quadrant).toBe('Q2');
  });

  it('accepts a minimal payload', async () => {
    postMock.mockResolvedValueOnce({ data: { quadrant: 'Q4', reason: '' } });
    await aiApi.eisenhowerClassifyText({ title: 'Read newsletter' });
    expect(postMock).toHaveBeenCalledWith('/ai/eisenhower/classify_text', {
      title: 'Read newsletter',
    });
  });
});
