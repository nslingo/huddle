import type { ConfigContext, ExpoConfig } from '@expo/config';

import type { AppIconBadgeConfig } from 'app-icon-badge/types';

import 'tsx/cjs';

// adding lint exception as we need to import tsx/cjs before env.ts is imported
// eslint-disable-next-line perfectionist/sort-imports
import Env from './env';

const EXPO_ACCOUNT_OWNER = 'obytes';
const EAS_PROJECT_ID = 'c3e1075b-6fe7-4686-aa49-35b46a229044';

// The expo-font plugin resolves these with path.resolve(projectRoot, p), which
// is a literal join rather than Node resolution. A hardcoded 'node_modules/...'
// only works when the package sits in apps/app/node_modules — under pnpm's
// hoisted linker it lives in the workspace root instead. Resolving through Node
// returns an absolute path, which path.resolve passes through unchanged, so this
// is correct regardless of linker or workspace depth.
const interFont = (file: string): string =>
  require.resolve(`@expo-google-fonts/inter/${file}`);

const appIconBadgeConfig: AppIconBadgeConfig = {
  enabled: Env.EXPO_PUBLIC_APP_ENV !== 'production',
  badges: [
    {
      text: Env.EXPO_PUBLIC_APP_ENV,
      type: 'banner',
      color: 'white',
    },
    {
      text: Env.EXPO_PUBLIC_VERSION.toString(),
      type: 'ribbon',
      color: 'white',
    },
  ],
};

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: Env.EXPO_PUBLIC_NAME,
  description: `${Env.EXPO_PUBLIC_NAME} Mobile App`,
  owner: EXPO_ACCOUNT_OWNER,
  scheme: Env.EXPO_PUBLIC_SCHEME,
  slug: 'obytesapp',
  version: Env.EXPO_PUBLIC_VERSION.toString(),
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  newArchEnabled: true,
  updates: {
    fallbackToCacheTimeout: 0,
  },
  assetBundlePatterns: ['**/*'],
  ios: {
    supportsTablet: true,
    bundleIdentifier: Env.EXPO_PUBLIC_BUNDLE_ID,
    infoPlist: {
      ITSAppUsesNonExemptEncryption: false,
    },
  },
  experiments: {
    typedRoutes: true,
  },
  android: {
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#2E3C4B',
    },
    package: Env.EXPO_PUBLIC_PACKAGE,
  },
  web: {
    favicon: './assets/favicon.png',
    bundler: 'metro',
  },
  plugins: [
    [
      'expo-splash-screen',
      {
        backgroundColor: '#2E3C4B',
        image: './assets/splash-icon.png',
        imageWidth: 150,
      },
    ],
    [
      'expo-font',
      {
        ios: {
          fonts: [
            interFont('400Regular/Inter_400Regular.ttf'),
            interFont('500Medium/Inter_500Medium.ttf'),
            interFont('600SemiBold/Inter_600SemiBold.ttf'),
            interFont('700Bold/Inter_700Bold.ttf'),
          ],
        },
        android: {
          fonts: [
            {
              fontFamily: 'Inter',
              fontDefinitions: [
                {
                  path: interFont('400Regular/Inter_400Regular.ttf'),
                  weight: 400,
                },
                {
                  path: interFont('500Medium/Inter_500Medium.ttf'),
                  weight: 500,
                },
                {
                  path: interFont('600SemiBold/Inter_600SemiBold.ttf'),
                  weight: 600,
                },
                {
                  path: interFont('700Bold/Inter_700Bold.ttf'),
                  weight: 700,
                },
              ],
            },
          ],
        },
      },
    ],
    'expo-localization',
    'expo-router',
    ['app-icon-badge', appIconBadgeConfig],
    ['react-native-edge-to-edge'],
  ],
  extra: {
    eas: {
      projectId: EAS_PROJECT_ID,
    },
  },
});
