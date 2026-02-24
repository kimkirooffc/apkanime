# AniFlow Android

Anime streaming Android app with iOS-style UI.

## Prerequisites

1. **Android Studio** (recommended) or Android SDK CLI
2. **JDK 17**
3. **Android SDK** with:
   - Build Tools 34.0.0
   - Platform Tools
   - Android 34 (API level 34)

## Setup

### Option 1: Android Studio (Recommended)

1. Open Android Studio
2. Click "Open an Existing Project"
3. Select the `android-app` folder
4. Wait for Gradle sync to complete
5. Click Run (green play button)

### Option 2: Command Line

```bash
# Set environment variables
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Install required SDK packages (if using sdkmanager)
sdkmanager "platform-tools"
sdkmanager "build-tools;34.0.0"
sdkmanager "platforms;android-34"

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/aniflow/
│   │   │   ├── app/           # Application, MainActivity, Adapters
│   │   │   ├── model/         # Data models (Anime, Episode, StreamInfo)
│   │   │   ├── service/       # API client, Repository
│   │   │   └── ui/            # Activities, Fragments, Views
│   │   ├── res/
│   │   │   ├── layout/        # XML layouts
│   │   │   ├── drawable/      # Vector drawables, shapes
│   │   │   ├── values/        # Colors, strings, themes
│   │   │   └── menu/          # Bottom navigation menu
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## Features

- **iOS-style UI**: Glassmorphism, rounded cards, gradient accents
- **Dark/Light Theme**: System-aware theme switching
- **Anime Browse**: Trending, Top Rated, Recommendations
- **Search**: Find anime by title
- **Watchlist**: Save favorite anime
- **History**: Track watched anime
- **Video Player**: ExoPlayer integration for streaming
- **Download**: Offline episode download support
- **Notifications**: New episode alerts

## API

Uses Otakudesu API (https://otakudesu-api.vercel.app)

## Building Release APK

1. Create a keystore:
```bash
keytool -genkey -v -keystore aniflow.keystore -alias aniflow -keyalg RSA -keysize 2048 -validity 10000
```

2. Add to `gradle.properties`:
```properties
ANIFLOW_STORE_FILE=/path/to/aniflow.keystore
ANIFLOW_STORE_PASSWORD=your_password
ANIFLOW_KEY_ALIAS=aniflow
ANIFLOW_KEY_PASSWORD=your_password
```

3. Update `app/build.gradle` with signing config

4. Build:
```bash
./gradlew assembleRelease
```

## License

MIT License
