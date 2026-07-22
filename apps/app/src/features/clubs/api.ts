import type { AxiosError } from 'axios';
import { createInfiniteQuery } from 'react-query-kit';
import { client } from '@/lib/api';

// Types — mirror the API DTOs in apps/api.

/** `com.huddle.interest.dto.InterestRef` */
export type InterestRef = {
  slug: string;
  name: string;
};

/**
 * `com.huddle.club.dto.ClubSummaryResponse`. `blurb` is the server-truncated
 * `coalesce(description, mission)` and is null when neither field has text;
 * `logo_url` is a nullable column. `interests` is capped at 3 server-side.
 */
export type ClubSummary = {
  publicId: string;
  name: string;
  logoUrl: string | null;
  blurb: string | null;
  interests: InterestRef[];
};

/** `com.huddle.common.PageResponse<T>` */
export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

/** Server caps `size` at 100. */
const PAGE_SIZE = 20;

// Hooks
type ClubsResponse = PageResponse<ClubSummary>;
type ClubsVariables = void;

/**
 * Paginated club discovery feed. Keyed `['clubs']` — deliberately a sibling of the
 * detail key, not a prefix of it: React Query filters partial-match, so nesting the
 * detail under `['clubs', {id}]` would make invalidating the feed also evict every
 * cached club detail.
 */
export const useClubs = createInfiniteQuery<
  ClubsResponse,
  ClubsVariables,
  AxiosError,
  number
>({
  queryKey: ['clubs'],
  fetcher: (_variables, { pageParam }) =>
    client
      .get('clubs', { params: { page: pageParam, size: PAGE_SIZE } })
      .then(response => response.data),
  initialPageParam: 0,
  getNextPageParam: lastPage => (lastPage.hasNext ? lastPage.page + 1 : undefined),
});
