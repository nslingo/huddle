import type {
  GetNextPageParamFunction,
  GetPreviousPageParamFunction,
} from '@tanstack/react-query';

export type PaginateQuery<T> = {
  results: T[];
  count: number;
  next: string | null;
  previous: string | null;
};

type KeyParams = {
  [key: string]: any;
};
export const DEFAULT_LIMIT = 10;

export function getQueryKey<T extends KeyParams>(key: string, params?: T) {
  return [key, ...(params ? [params] : [])];
}

// for infinite query pages  to flatList data
export function normalizePages<T>(pages?: PaginateQuery<T>[]): T[] {
  return pages
    ? pages.reduce((prev: T[], current) => [...prev, ...current.results], [])
    : [];
}

const URL_PARAM_RE = /[?&]([^=#]+)=([^&#]*)/g;

// a function that accept a url and return params as an object
export function getUrlParameters(
  url: string | null,
): { [k: string]: string } | null {
  if (url === null) {
    return null;
  }
  // Module-scoped /g regex: reset lastIndex so a prior call's state can't skip matches.
  URL_PARAM_RE.lastIndex = 0;
  const params = {};
  let match;
  while ((match = URL_PARAM_RE.exec(url))) {
    if (match[1] !== null) {
      // @ts-expect-error - Dynamic key assignment
      params[match[1]] = match[2];
    }
  }
  return params;
}

export const getPreviousPageParam: GetNextPageParamFunction<
  unknown,
  PaginateQuery<unknown>
> = page => getUrlParameters(page.previous)?.offset ?? null;

export const getNextPageParam: GetPreviousPageParamFunction<
  unknown,
  PaginateQuery<unknown>
> = page => getUrlParameters(page.next)?.offset ?? null;
