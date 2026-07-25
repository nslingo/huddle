import * as React from 'react';

import { cleanup, render, screen } from '@/lib/test-utils';
import { ClubLogo } from './club-logo';

afterEach(cleanup);

describe('clubLogo', () => {
  it('shows the first letter when there is no logo', () => {
    render(<ClubLogo name="Chess Club" logoUrl={null} />);
    expect(screen.getByText('C')).toBeOnTheScreen();
  });

  it('renders no initial fallback when a logo url is present', () => {
    render(<ClubLogo name="Chess Club" logoUrl="https://cdn.example.com/c.png" />);
    // The fallback tile (and its letter) is only rendered in the null-logo branch.
    expect(screen.queryByText('C')).not.toBeOnTheScreen();
  });

  it('indexes the fallback by code point so an emoji is not split', () => {
    // A naive name[0] would yield a lone surrogate half; the first code point is the whole emoji.
    render(<ClubLogo name="😀 Club" logoUrl={null} />);
    expect(screen.getByText('😀')).toBeOnTheScreen();
  });

  it('falls back to ? for an empty name', () => {
    render(<ClubLogo name="" logoUrl={null} />);
    expect(screen.getByText('?')).toBeOnTheScreen();
  });
});
