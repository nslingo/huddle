import type { AxiosError } from 'axios';
import { createInfiniteQuery, createQuery } from 'react-query-kit';
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

/** Postgres `club_status`, serialized as its lowercase label. */
export type ClubStatus = 'active' | 'inactive';

/** Postgres `club_link_type`, serialized as its lowercase label. */
export type ClubLinkType
  = | 'instagram'
    | 'facebook'
    | 'x'
    | 'linkedin'
    | 'youtube'
    | 'tiktok'
    | 'discord'
    | 'linktree'
    | 'website';

/**
 * `com.huddle.club.dto.ContactRef`. `type` is free-form — ingestion derives it from the
 * CampusGroups label, so it's usually `email` or `phone` but isn't guaranteed to be.
 */
export type ContactRef = {
  type: string;
  value: string;
};

/** `com.huddle.club.dto.ClubLinkRef` */
export type ClubLinkRef = {
  type: ClubLinkType;
  url: string;
};

/**
 * `com.huddle.club.dto.ClubDetailResponse`. Every descriptive field is nullable in `V1`;
 * `interests` / `contacts` / `links` are always present (empty, never null), and `interests`
 * is uncapped here, unlike the feed's ≤3. No `blurb`: the detail screen renders the full
 * `description`/`mission` instead.
 */
export type ClubDetail = {
  publicId: string;
  slug: string;
  name: string;
  status: ClubStatus;
  logoUrl: string | null;
  description: string | null;
  mission: string | null;
  goals: string | null;
  clubType: string | null;
  membershipType: string | null;
  instagramHandle: string | null;
  followerCount: number | null;
  activityScore: number | null;
  interests: InterestRef[];
  contacts: ContactRef[];
  links: ClubLinkRef[];
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

type ClubVariables = { id: string };

/**
 * One club's full detail, by `publicId`. Keyed `['club', { id }]` — a sibling of the feed's
 * `['clubs']`, deliberately not nested under it (see `useClubs`): React Query matches keys by
 * prefix, so nesting would make a feed invalidation also evict every cached detail.
 *
 * A 404 (unknown/removed club) surfaces as an `AxiosError` for the screen to handle.
 */
export const useClub = createQuery<ClubDetail, ClubVariables, AxiosError>({
  queryKey: ['club'],
  fetcher: variables =>
    client.get(`clubs/${variables.id}`).then(response => response.data),
});
