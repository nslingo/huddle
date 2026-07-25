import type { ClubSummary, PageResponse } from './api';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import * as React from 'react';

import { client } from '@/lib/api';
import { useClub, useClubs } from './api';

jest.mock('@/lib/api', () => ({ client: { get: jest.fn() } }));

const mockGet = client.get as jest.Mock;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

afterEach(() => jest.clearAllMocks());

describe('useClub', () => {
  it('requests the club by public id and returns the payload', async () => {
    const detail = { publicId: 'club-uuid', name: 'Chess Club' };
    mockGet.mockResolvedValue({ data: detail });

    const { result } = renderHook(
      () => useClub({ variables: { id: 'club-uuid' } }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGet).toHaveBeenCalledWith('clubs/club-uuid');
    expect(result.current.data).toEqual(detail);
  });
});

describe('useClubs', () => {
  const emptyCard: ClubSummary = {
    publicId: 'a',
    name: 'A',
    logoUrl: null,
    blurb: null,
    interests: [],
  };

  function page(overrides: Partial<PageResponse<ClubSummary>>): PageResponse<ClubSummary> {
    return {
      content: [emptyCard],
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
      hasNext: false,
      ...overrides,
    };
  }

  it('fetches the first page with page 0 and the fixed size', async () => {
    mockGet.mockResolvedValue({ data: page({ hasNext: false }) });

    const { result } = renderHook(() => useClubs(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGet).toHaveBeenCalledWith('clubs', { params: { page: 0, size: 20 } });
  });

  it('derives the next page param from hasNext, then stops', async () => {
    mockGet
      .mockResolvedValueOnce({ data: page({ page: 0, hasNext: true }) })
      .mockResolvedValueOnce({ data: page({ page: 1, hasNext: false }) });

    const { result } = renderHook(() => useClubs(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.hasNextPage).toBe(true);

    await act(async () => {
      await result.current.fetchNextPage();
    });

    // hasNext:true -> requests page 1; the page-1 response reports hasNext:false -> paging ends.
    expect(mockGet).toHaveBeenLastCalledWith('clubs', { params: { page: 1, size: 20 } });
    // React Query flushes observer updates on a batched timer, so poll for the final flag.
    await waitFor(() => expect(result.current.hasNextPage).toBe(false));
  });
});
