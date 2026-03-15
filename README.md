# Pic Browser - Android Local Photo Gallery

A modern Android photo gallery app built with Jetpack Compose.

## Features

- 📁 **Folder Navigation**: Browse photos by folders with a slide-out drawer
- 📂 **Custom Directories**: Add and browse any custom folders on your device
- 🖼️ **Grid View**: Adjustable grid size (2-6 columns) with separate settings for portrait/landscape
- 🔄 **Orientation Aware**: Auto-detects screen orientation - portrait defaults to 3 columns, landscape defaults to 5 columns
- 🔀 **Sorting**: Sort photos by date taken, date modified, file name, or file size (ascending/descending)
- 👆 **Photo Viewer**: Swipe between photos, pinch-to-zoom, double-tap to zoom in/out, quick fling for fast switching, smooth animations without flickering, tap to toggle menu bar, drag down to dismiss
- ❤️ **Favorites**: Mark and view favorite photos
- 🗑️ **Delete**: Remove photos with confirmation (supports both MediaStore and File API) - deleted photos immediately disappear from grid
- 📋 **Photo Details**: View file name, date taken, date modified, size, and resolution
- 🎬 **GIF Support**: Animated GIF playback (including hidden directories)
- 🎨 **Material Design 3**: Modern UI with dynamic theming
- 🔄 **Landscape Support**: Full orientation support with preserved state

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM + Repository pattern
- **Image Loading**: Coil (with GIF support)
- **Navigation**: Navigation Compose
- **Data Storage**: DataStore Preferences
- **Minimum SDK**: Android 10.0 (API 29)

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK with API 29 or higher
- JDK 17

### Building

1. Clone or download this project
2. Open in Android Studio
3. Sync Gradle files
4. Run on an emulator or physical device

### Permissions

The app requires:
- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 10-12)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/picbrowser/
│   │   ├── data/
│   │   │   ├── model/          # Data models (ImageItem, Folder)
│   │   │   └── repository/     # Data repositories
│   │   ├── ui/
│   │   │   ├── theme/           # Compose theming
│   │   │   ├── components/      # Reusable UI components
│   │   │   ├── screens/         # Screen composables
│   │   │   ├── viewmodel/       # ViewModels
│   │   │   └── navigation/      # Navigation graph
│   │   ├── MainActivity.kt
│   │   └── PicBrowserApplication.kt  # Coil configuration with GIF support
│   └── res/                     # Resources
```

## License

MIT License
