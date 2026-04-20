# Libra

An Android app for the **Runtastic Libra** smart scale, whose official app was discontinued by Adidas/Runtastic.

## Features

- **Sync history** — download all measurements stored on the scale
- **New measurement** — step on the scale and save the result
- **Live weight** — view real-time weight without saving
- **Measurement history** — browse, copy, and delete past measurements
- **Charts** — visualise weight, body fat, muscle, and bone mass over time (7 days to all-time)
- **Import / Export** — backup and restore data as JSON or CSV; import from files shared via any app (Telegram, email, etc.)
- **Manual entry** — add measurements without the scale
- **User profile** — configure height, weight, date of birth, gender, and activity level used by the scale to calculate body composition
- **Unit support** — kg and lb
- **Language** — English and Italian

## Requirements

- Android 10 (API 29) or higher
- Bluetooth Low Energy
- A Runtastic Libra smart scale

## Building

```bash
JAVA_HOME=/path/to/jdk21 ./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech stack

- Kotlin, Jetpack Compose, Room, DataStore
- BLE via Android GATT API

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

This project is not affiliated with or endorsed by Adidas or Runtastic.
