import type { ClubSummary } from './api';

import { FlashList } from '@shopify/flash-list';
import * as React from 'react';

import {
  ActivityIndicator,
  Button,
  EmptyList,
  FocusAwareStatusBar,
  Text,
  View,
} from '@/components/ui';
import { useClubs } from './api';
import { ClubCard } from './components/club-card';

export function ClubsScreen() {
  const {
    data,
    isPending,
    isError,
    refetch,
    isRefetching,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useClubs();

  // The envelope nests cards a page deep; flatten for the list.
  const clubs = React.useMemo(
    () => data?.pages.flatMap(page => page.content) ?? [],
    [data],
  );

  const renderItem = React.useCallback(
    ({ item }: { item: ClubSummary }) => <ClubCard {...item} />,
    [],
  );

  const onEndReached = React.useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (isError) {
    return (
      <View className="flex-1 items-center justify-center p-6">
        <FocusAwareStatusBar />
        <Text className="pb-4 text-center">Couldn't load clubs.</Text>
        <Button label="Try again" onPress={() => refetch()} />
      </View>
    );
  }

  return (
    <View className="flex-1">
      <FocusAwareStatusBar />
      <FlashList
        data={clubs}
        renderItem={renderItem}
        keyExtractor={item => item.publicId}
        onEndReached={onEndReached}
        onEndReachedThreshold={0.5}
        refreshing={isRefetching}
        onRefresh={refetch}
        ListEmptyComponent={<EmptyList isLoading={isPending} />}
        ListFooterComponent={
          isFetchingNextPage
            ? (
                <View className="py-4">
                  <ActivityIndicator />
                </View>
              )
            : null
        }
      />
    </View>
  );
}
