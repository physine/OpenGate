# OpenGate

OpenGate is an Android application that automatically calls your gate when you arrive home. It uses location services to detect when you're within a specified radius of your home location and initiates the gate call automatically.

## Features

- **Automatic Gate Calling**: Automatically calls your gate when you arrive within the specified radius
- **Customizable Settings**:
  - Set your gate phone number
  - Define your home location
  - Adjust the detection radius (50-500 meters)
- **Background Operation**: Works in the background to monitor your location
- **Battery Efficient**: Uses FusedLocationProviderClient for optimized location updates
- **Modern UI**: Built with Material 3 design principles and Jetpack Compose

## Requirements

- Android 10.0 (API level 29) or higher
- Location permissions
- Phone call permissions
- Battery optimization exemption

## Setup

1. Clone the repository:
```bash
git clone https://github.com/physine/OpenGate.git
```

2. Open the project in Android Studio

3. Build and run the application

4. On first launch, grant the required permissions:
   - Location permission
   - Phone call permission
   - Battery optimization exemption

## Usage

1. **Set Gate Number**:
   - Enter your gate's phone number in the settings
   - This is the number that will be called when you arrive

2. **Set Home Location**:
   - Tap "Set Home Location" to set your current location as home
   - This is the reference point for the radius detection

3. **Adjust Radius**:
   - Use the slider to set the detection radius (50-500 meters)
   - The gate will be called when you enter this radius

4. **Save Settings**:
   - Tap "Save Settings" to apply your changes
   - The service will start monitoring your location

## How It Works

1. The app runs a foreground service that monitors your location
2. When you enter the specified radius around your home location:
   - The app automatically calls your gate number
   - A notification is shown to confirm the call
3. The service continues running in the background to monitor your location

## Technical Details

- Built with Kotlin and Jetpack Compose
- Uses FusedLocationProviderClient for efficient location updates
- Implements a foreground service for reliable background operation
- Follows Material 3 design guidelines
- Supports Android 10+ features for phone calls

## Permissions

The app requires the following permissions:
- `ACCESS_FINE_LOCATION`: For precise location tracking
- `CALL_PHONE`: To make phone calls to the gate
- `READ_PHONE_STATE`: To check phone state for calls
- `FOREGROUND_SERVICE`: For background location monitoring
- `WAKE_LOCK`: To ensure reliable operation

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License.