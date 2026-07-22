import type { ClubSummary } from '../api';

import { Link } from 'expo-router';
import * as React from 'react';

import { Image, Pressable, Text, View } from '@/components/ui';

type Props = ClubSummary;

export function ClubCard({ publicId, name, logoUrl, blurb, interests }: Props) {
  return (
    <Link href={`/clubs/${publicId}`} asChild>
      <Pressable>
        <View className="m-2 overflow-hidden rounded-xl border border-neutral-300 bg-white dark:border-neutral-700 dark:bg-neutral-900">
          <View className="flex-row items-center gap-3 p-3">
            <ClubLogo name={name} logoUrl={logoUrl} />
            <Text className="flex-1 text-lg font-semibold" numberOfLines={2}>
              {name}
            </Text>
          </View>

          {blurb
            ? (
                <Text
                  numberOfLines={3}
                  className="px-3 leading-snug text-gray-600 dark:text-gray-400"
                >
                  {blurb}
                </Text>
              )
            : null}

          {interests.length > 0
            ? (
                <View className="flex-row flex-wrap gap-2 p-3">
                  {interests.map(interest => (
                    <View
                      key={interest.slug}
                      className="rounded-full bg-neutral-100 px-3 py-1 dark:bg-neutral-800"
                    >
                      <Text className="text-xs text-neutral-700 dark:text-neutral-300">
                        {interest.name}
                      </Text>
                    </View>
                  ))}
                </View>
              )
            : null}
        </View>
      </Pressable>
    </Link>
  );
}

/** Logo, or the club's first character when `logo_url` is null. */
function ClubLogo({ name, logoUrl }: Pick<ClubSummary, 'name' | 'logoUrl'>) {
  if (logoUrl) {
    return (
      <Image
        className="size-12 rounded-lg"
        contentFit="cover"
        source={{ uri: logoUrl }}
      />
    );
  }

  // Index by code point so an emoji or supplementary character isn't split.
  const initial = [...name][0] ?? '?';

  return (
    <View className="size-12 items-center justify-center rounded-lg bg-neutral-200 dark:bg-neutral-800">
      <Text className="text-lg font-semibold text-neutral-600 dark:text-neutral-300">
        {initial}
      </Text>
    </View>
  );
}
