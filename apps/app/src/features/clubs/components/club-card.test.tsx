import type { ClubSummary } from '../api';

import * as React from 'react';

import { cleanup, render, screen } from '@/lib/test-utils';
import { ClubCard } from './club-card';

// The card wraps its content in an expo-router <Link href> (asChild). Replace it with a plain
// view that surfaces the href on a testID so we can both render the content and assert the target.
jest.mock('expo-router', () => {
  const { View } = require('react-native');
  return {
    Link: ({ href, children }: { href: string; children: React.ReactNode }) => (
      <View testID="club-link" accessibilityLabel={String(href)}>
        {children}
      </View>
    ),
  };
});

afterEach(cleanup);

const baseClub: ClubSummary = {
  publicId: 'abc-123',
  name: 'Chess Club',
  logoUrl: null,
  blurb: 'We play chess weekly.',
  interests: [
    { slug: 'games', name: 'Games' },
    { slug: 'strategy', name: 'Strategy' },
  ],
};

function renderCard(overrides: Partial<ClubSummary> = {}) {
  return render(<ClubCard {...baseClub} {...overrides} />);
}

describe('clubCard', () => {
  it('renders the name, blurb, and interest chips', () => {
    renderCard();
    expect(screen.getByText('Chess Club')).toBeOnTheScreen();
    expect(screen.getByText('We play chess weekly.')).toBeOnTheScreen();
    expect(screen.getByText('Games')).toBeOnTheScreen();
    expect(screen.getByText('Strategy')).toBeOnTheScreen();
  });

  it('links to the club detail route by public id', () => {
    renderCard();
    expect(screen.getByTestId('club-link').props.accessibilityLabel).toBe(
      '/clubs/abc-123',
    );
  });

  it('shows the initial fallback when there is no logo', () => {
    renderCard({ logoUrl: null });
    expect(screen.getByText('C')).toBeOnTheScreen();
  });

  it('omits the blurb when it is null', () => {
    renderCard({ blurb: null });
    expect(screen.queryByText('We play chess weekly.')).not.toBeOnTheScreen();
    // The rest of the card still renders.
    expect(screen.getByText('Chess Club')).toBeOnTheScreen();
  });

  it('renders no chips when the club has no interests', () => {
    renderCard({ interests: [] });
    expect(screen.queryByText('Games')).not.toBeOnTheScreen();
  });
});
