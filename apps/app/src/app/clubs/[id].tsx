import { Stack, useLocalSearchParams } from 'expo-router';
import * as React from 'react';

import { FocusAwareStatusBar, Text, View } from '@/components/ui';

/**
 * Placeholder until slice 4 builds `GET /api/clubs/{publicId}` and the real detail
 * screen. Exists so feed cards navigate somewhere and the routing is testable.
 *
 * The `id` segment carries the club's `publicId` (uuid) — the bigint `id` is never
 * exposed by the API. The segment is named `id` because route filenames must be
 * kebab-case, and `[public-id]` would force `params['public-id']` at every call site.
 */
export default function ClubDetailScreen() {
  const { id: publicId } = useLocalSearchParams<{ id: string }>();

  return (
    <View className="flex-1 justify-center p-6">
      <Stack.Screen options={{ title: 'Club', headerBackTitle: 'Clubs' }} />
      <FocusAwareStatusBar />
      <Text className="text-center text-gray-600 dark:text-gray-400">
        Club detail lands in slice 4.
      </Text>
      <Text className="pt-2 text-center text-xs text-neutral-500">{publicId}</Text>
    </View>
  );
}
