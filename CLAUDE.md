# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Building
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore configuration)
./gradlew assembleRelease

# Install debug APK on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clean build
./gradlew clean

# Verify APK signature
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### Testing and Quality
```bash
# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Run specific test
./gradlew test --tests "com.nextjsclient.android.YourTestClass"

# Generate lint report
./gradlew lintDebug
```

## Architecture Overview

### MVVM Pattern
The app follows MVVM (Model-View-ViewModel) architecture:

- **Models** (`data/models/`): Data classes representing business entities
  - `ScamarkProduct`: Product with pricing, decisions, and article info
  - `ScamarkStats`: Dashboard statistics (products in/out, promos, clients)
  - `AvailableWeek`: Week navigation data

- **Repository** (`data/repository/FirebaseRepository`): 
  - Single source of truth for data operations
  - Handles Firebase Firestore queries with caching
  - Week-based data fetching with supplier filtering
  - Cache invalidation after 30 minutes

- **ViewModels** (`ui/*/ScamarkViewModel`):
  - Shared between fragments via `activityViewModels()`
  - Manages UI state with LiveData
  - Handles product filtering, search, and week selection
  - Coordinates data loading with loading states

- **Views** (Fragments):
  - `OverviewFragment`: Dashboard with supplier stats and Top SCA
  - `ScamarkFragment`: Product list with filters and search
  - Navigation via BottomNavigationView in MainActivity

### Navigation Flow
```
AuthActivity (login) 
    ↓
MainActivity (container)
    ├── OverviewFragment (dashboard)
    ├── ScamarkFragment (Anecoop)
    └── ScamarkFragment (Solagora)
```

### Data Flow with Preloading
When navigating from Overview to Scamark with filters:
1. OverviewFragment preloads supplier data
2. Stores in MainActivity cache with filter type
3. ScamarkFragment receives preloaded filtered data
4. Avoids redundant Firebase queries

### Key Components

**MainActivity**:
- Manages navigation between fragments
- Handles preloaded data cache for performance
- Manages biometric authentication overlay
- Coordinates theme changes per supplier
- Handles deep navigation with week/filter parameters

**UpdateManager**:
- Checks GitHub releases for updates
- Downloads and installs APKs
- Version comparison logic
- Shows update dialog with changelog

**BiometricManager**:
- Handles fingerprint/face authentication
- Shows lock overlay when app resumes
- Integrates with system biometric APIs

**SupplierThemeManager**:
- Dynamically changes app colors per supplier
- Updates status bar, navigation bar, FAB colors
- Smooth color transitions

## Firebase Integration

### Configuration
- `google-services.json` required in `app/` directory
- Firebase services: Auth, Firestore, Storage
- Fallback to offline mode if Firebase unavailable

### Data Structure
```
decisions/
  └── {year}/
      └── S{week}/
          └── {supplier}/
              └── products (array)
```

## APK Signing

### Production Keystore
- Keystore: `nextjs-client-release.keystore` (never commit)
- Properties: `keystore.properties` (never commit)
- GitHub Actions secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD

### Version Management
- Version code: Git commit count
- Version name: `{major}.{minor}.{patch}` or `{major}.{minor}.{patch}-dev+{commit}`
- CI builds: Includes GitHub run number

## Git Commit Standards

### Format
```
type: description courte et claire
```

Types: feat, fix, docs, style, refactor, test, chore

### Important Rules
- Never include Claude attribution in commits
- Use professional, concise messages
- Code in English, documentation in French

## Performance Considerations

### Caching Strategy
- Firebase data cached for 30 minutes per supplier/week
- Preloaded data passed between fragments
- Images cached with Glide

### Animation Performance
- Material 3 animations with hardware acceleration
- Careful use of `withEndAction` to chain animations
- Reset animation properties after completion

## UI/UX Guidelines

### Material Design 3
- Dynamic color theming per supplier
- Consistent elevation (cards: 4dp, header: 16dp)
- Expressive animations with FastOutSlowInInterpolator

### Fragment Lifecycle
- Use `viewLifecycleOwner` for observers
- Clean up bindings in `onDestroyView`
- Handle configuration changes with ViewModel

## Critical Files Never to Commit
- `*.keystore`
- `*.jks`  
- `keystore.properties`
- `keystore-passwords.txt`
- `google-services.json` (except in CI)