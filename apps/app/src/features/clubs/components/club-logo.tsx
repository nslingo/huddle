import * as React from 'react';

import { Image, Text, View } from '@/components/ui';

type Props = {
  name: string;
  logoUrl: string | null;
  /** Size + rounding classes for the logo box; defaults to the feed-card size. */
  className?: string;
  /** Text size class for the initial fallback. */
  textClassName?: string;
};

/**
 * A club's logo, or its first character on a neutral tile when `logoUrl` is null. Shared by the
 * feed card and the detail header; both need the same nullable-logo fallback.
 */
export function ClubLogo({
  name,
  logoUrl,
  className = 'size-12 rounded-lg',
  textClassName = 'text-lg',
}: Props) {
  if (logoUrl) {
    return (
      <Image className={className} contentFit="cover" source={{ uri: logoUrl }} />
    );
  }

  // Index by code point so an emoji or supplementary character isn't split.
  const initial = [...name][0] ?? '?';

  return (
    <View
      className={`items-center justify-center bg-neutral-200 dark:bg-neutral-800 ${className}`}
    >
      <Text
        className={`font-semibold text-neutral-600 dark:text-neutral-300 ${textClassName}`}
      >
        {initial}
      </Text>
    </View>
  );
}
