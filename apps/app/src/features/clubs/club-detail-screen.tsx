import type { ClubDetail, ClubLinkRef, ContactRef } from './api';

import { Stack, useLocalSearchParams } from 'expo-router';
import * as React from 'react';

import {
  ActivityIndicator,
  Button,
  FocusAwareStatusBar,
  Pressable,
  ScrollView,
  Text,
  View,
} from '@/components/ui';
import { openLinkInBrowser } from '@/lib/utils';
import { useClub } from './api';
import { ClubLogo } from './components/club-logo';

/** Display names for the `club_link_type` labels the API returns. */
const LINK_LABELS: Record<ClubLinkRef['type'], string> = {
  instagram: 'Instagram',
  facebook: 'Facebook',
  x: 'X',
  linkedin: 'LinkedIn',
  youtube: 'YouTube',
  tiktok: 'TikTok',
  discord: 'Discord',
  linktree: 'Linktree',
  website: 'Website',
};

const SCREEN_OPTIONS = { title: 'Club', headerBackTitle: 'Clubs' } as const;

/** Everything that isn't a digit or a leading `+`, stripped from a phone number for a `tel:` URL. */
const NON_DIAL_CHARS = /[^\d+]/g;

export function ClubDetailScreen() {
  // The `id` segment carries the club's `publicId` (uuid); the bigint id is never exposed.
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data, isPending, isError, error, refetch } = useClub({
    variables: { id },
  });

  if (isPending) {
    return (
      <View className="flex-1 justify-center">
        <Stack.Screen options={SCREEN_OPTIONS} />
        <FocusAwareStatusBar />
        <ActivityIndicator />
      </View>
    );
  }

  if (isError) {
    // A removed/unknown club 404s; anything else is likely transient, so offer a retry.
    const notFound = error?.response?.status === 404;
    return (
      <View className="flex-1 items-center justify-center p-6">
        <Stack.Screen options={SCREEN_OPTIONS} />
        <FocusAwareStatusBar />
        <Text className="pb-4 text-center">
          {notFound ? 'This club no longer exists.' : 'Couldn\'t load this club.'}
        </Text>
        {notFound ? null : <Button label="Try again" onPress={() => refetch()} />}
      </View>
    );
  }

  return (
    <View className="flex-1">
      <Stack.Screen options={SCREEN_OPTIONS} />
      <FocusAwareStatusBar />
      <ScrollView>
        <View className="gap-6 p-4">
          <Header club={data} />
          <Section title="About" body={data.description} />
          <Section title="Mission" body={data.mission} />
          <Section title="Goals" body={data.goals} />
          <Interests interests={data.interests} />
          <Contacts contacts={data.contacts} />
          <Links links={data.links} />
        </View>
      </ScrollView>
    </View>
  );
}

function Header({ club }: { club: ClubDetail }) {
  // Type · membership, dropping whichever the club didn't list.
  const meta = [club.clubType, club.membershipType].filter(Boolean).join(' · ');

  return (
    <View className="flex-row gap-4">
      <ClubLogo
        name={club.name}
        logoUrl={club.logoUrl}
        className="size-20 rounded-xl"
        textClassName="text-3xl"
      />
      <View className="flex-1 justify-center gap-1">
        <Text className="text-2xl font-bold" numberOfLines={3}>
          {club.name}
        </Text>
        {meta
          ? <Text className="text-gray-600 dark:text-gray-400">{meta}</Text>
          : null}
        {club.followerCount != null
          ? (
              <Text className="text-sm text-neutral-500">
                {club.followerCount.toLocaleString()}
                {' followers'}
              </Text>
            )
          : null}
        {club.status === 'inactive'
          ? (
              <View className="mt-1 self-start rounded-full bg-neutral-200 px-2 py-0.5 dark:bg-neutral-800">
                <Text className="text-xs text-neutral-600 dark:text-neutral-300">
                  Inactive
                </Text>
              </View>
            )
          : null}
      </View>
    </View>
  );
}

/** A titled block of body text; renders nothing when the field is null/empty. */
function Section({ title, body }: { title: string; body: string | null }) {
  if (!body?.trim()) {
    return null;
  }
  return (
    <View className="gap-2">
      <Text className="text-lg font-semibold">{title}</Text>
      <Text className="leading-relaxed text-gray-700 dark:text-gray-300">
        {body}
      </Text>
    </View>
  );
}

function Interests({ interests }: { interests: ClubDetail['interests'] }) {
  if (interests.length === 0) {
    return null;
  }
  return (
    <View className="gap-2">
      <Text className="text-lg font-semibold">Interests</Text>
      <View className="flex-row flex-wrap gap-2">
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
    </View>
  );
}

function Contacts({ contacts }: { contacts: ContactRef[] }) {
  if (contacts.length === 0) {
    return null;
  }
  return (
    <View className="gap-2">
      <Text className="text-lg font-semibold">Contact</Text>
      {contacts.map(contact => (
        <ContactRow key={`${contact.type}-${contact.value}`} contact={contact} />
      ))}
    </View>
  );
}

/** Email/phone contacts open the mail/dialer app; any other type is shown as plain text. */
function ContactRow({ contact }: { contact: ContactRef }) {
  const href = contactHref(contact);
  if (!href) {
    return (
      <Text className="text-gray-700 dark:text-gray-300">{contact.value}</Text>
    );
  }
  return (
    <Pressable onPress={() => openLinkInBrowser(href)}>
      <Text className="text-blue-600 dark:text-blue-400">{contact.value}</Text>
    </Pressable>
  );
}

function contactHref(contact: ContactRef): string | null {
  if (contact.type === 'email') {
    return `mailto:${contact.value.trim()}`;
  }
  if (contact.type === 'phone') {
    // tel: tolerates no spaces; keep leading + for international numbers.
    return `tel:${contact.value.replace(NON_DIAL_CHARS, '')}`;
  }
  return null;
}

function Links({ links }: { links: ClubLinkRef[] }) {
  if (links.length === 0) {
    return null;
  }
  return (
    <View className="gap-2">
      <Text className="text-lg font-semibold">Links</Text>
      {links.map(link => (
        <Pressable key={link.type} onPress={() => openLinkInBrowser(link.url)}>
          <Text className="text-blue-600 dark:text-blue-400">
            {LINK_LABELS[link.type]}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}
