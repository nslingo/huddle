import type { ClubDetail } from './api';

import * as React from 'react';

import { cleanup, screen, setup } from '@/lib/test-utils';
import { openLinkInBrowser } from '@/lib/utils';
import { useClub } from './api';
import { ClubDetailScreen } from './club-detail-screen';

// Stack.Screen only sets nav options (irrelevant here); the id normally comes from the route.
jest.mock('expo-router', () => ({
  Stack: { Screen: () => null },
  // eslint-disable-next-line react/no-unnecessary-use-prefix -- must match expo-router's hook name
  useLocalSearchParams: () => ({ id: 'club-uuid' }),
}));

// Drive the screen's branches directly by stubbing the query hook's return.
jest.mock('./api', () => ({ useClub: jest.fn() }));

// Assert on the side effect without opening a real URL.
jest.mock('@/lib/utils', () => ({ openLinkInBrowser: jest.fn() }));

const mockUseClub = useClub as unknown as jest.Mock;
const mockOpenLink = openLinkInBrowser as jest.Mock;

beforeEach(() => jest.clearAllMocks());
afterEach(cleanup);

const fullClub: ClubDetail = {
  publicId: 'club-uuid',
  slug: 'chess-club',
  name: 'Chess Club',
  status: 'active',
  logoUrl: null,
  description: 'We meet weekly to play chess.',
  mission: 'Grow chess at Cornell',
  goals: 'Win the Ivy tournament',
  clubType: 'Games',
  membershipType: 'Open',
  instagramHandle: 'cornellchess',
  followerCount: 1200,
  activityScore: 90,
  interests: [
    { slug: 'games', name: 'Games' },
    { slug: 'strategy', name: 'Strategy' },
  ],
  contacts: [
    { type: 'email', value: 'chess@cornell.edu' },
    { type: 'phone', value: '(607) 555-1234' },
  ],
  links: [
    { type: 'instagram', url: 'https://www.instagram.com/cornellchess' },
    { type: 'website', url: 'https://chess.cornell.edu' },
  ],
};

/** A success-state return for `useClub`, with `data` overridable per test. */
function success(overrides: Partial<ClubDetail> = {}) {
  return {
    data: { ...fullClub, ...overrides },
    isPending: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
  };
}

describe('clubDetailScreen', () => {
  it('shows a spinner while pending', () => {
    mockUseClub.mockReturnValue({ isPending: true });
    setup(<ClubDetailScreen />);
    expect(screen.getByTestId('club-detail-loading')).toBeOnTheScreen();
  });

  it('renders the header and every populated section', () => {
    mockUseClub.mockReturnValue(success());
    setup(<ClubDetailScreen />);

    expect(screen.getByText('Chess Club')).toBeOnTheScreen();
    expect(screen.getByText('Games · Open')).toBeOnTheScreen();
    // Formatted through toLocaleString; compute the expected string to stay locale-agnostic.
    expect(
      screen.getByText(`${(1200).toLocaleString()} followers`),
    ).toBeOnTheScreen();

    expect(screen.getByText('About')).toBeOnTheScreen();
    expect(screen.getByText('We meet weekly to play chess.')).toBeOnTheScreen();
    expect(screen.getByText('Mission')).toBeOnTheScreen();
    expect(screen.getByText('Grow chess at Cornell')).toBeOnTheScreen();
    expect(screen.getByText('Goals')).toBeOnTheScreen();
    expect(screen.getByText('Interests')).toBeOnTheScreen();
    expect(screen.getByText('Strategy')).toBeOnTheScreen();

    // An active club carries no status badge.
    expect(screen.queryByText('Inactive')).not.toBeOnTheScreen();
  });

  it('hides sections whose fields are empty', () => {
    mockUseClub.mockReturnValue(
      success({
        mission: null,
        goals: null,
        interests: [],
        contacts: [],
        links: [],
      }),
    );
    setup(<ClubDetailScreen />);

    expect(screen.getByText('About')).toBeOnTheScreen();
    expect(screen.queryByText('Mission')).not.toBeOnTheScreen();
    expect(screen.queryByText('Goals')).not.toBeOnTheScreen();
    expect(screen.queryByText('Interests')).not.toBeOnTheScreen();
    expect(screen.queryByText('Contact')).not.toBeOnTheScreen();
    expect(screen.queryByText('Links')).not.toBeOnTheScreen();
  });

  it('shows an Inactive badge for an inactive club', () => {
    mockUseClub.mockReturnValue(success({ status: 'inactive' }));
    setup(<ClubDetailScreen />);
    expect(screen.getByText('Inactive')).toBeOnTheScreen();
  });

  it('shows a terminal message with no retry on 404', () => {
    mockUseClub.mockReturnValue({
      isPending: false,
      isError: true,
      error: { response: { status: 404 } },
      refetch: jest.fn(),
    });
    setup(<ClubDetailScreen />);

    expect(screen.getByText('This club no longer exists.')).toBeOnTheScreen();
    expect(screen.queryByTestId('club-detail-retry')).not.toBeOnTheScreen();
  });

  it('offers a retry on a transient error', async () => {
    const refetch = jest.fn();
    mockUseClub.mockReturnValue({
      isPending: false,
      isError: true,
      error: { response: { status: 500 } },
      refetch,
    });
    const { user } = setup(<ClubDetailScreen />);

    expect(screen.getByText('Couldn\'t load this club.')).toBeOnTheScreen();
    await user.press(screen.getByTestId('club-detail-retry'));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('opens contacts and links in the right scheme', async () => {
    mockUseClub.mockReturnValue(success());
    const { user } = setup(<ClubDetailScreen />);

    await user.press(screen.getByRole('link', { name: 'chess@cornell.edu' }));
    expect(mockOpenLink).toHaveBeenLastCalledWith('mailto:chess@cornell.edu');

    // Non-dial characters are stripped for the tel: URL.
    await user.press(screen.getByRole('link', { name: '(607) 555-1234' }));
    expect(mockOpenLink).toHaveBeenLastCalledWith('tel:6075551234');

    await user.press(screen.getByRole('link', { name: 'Website' }));
    expect(mockOpenLink).toHaveBeenLastCalledWith('https://chess.cornell.edu');
  });
});
