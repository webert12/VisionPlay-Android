# Vision Play TV — Xtream API prototype

Android app prototype using Kotlin, AndroidX Media3 and Xtream Codes-compatible `player_api.php`.

## Connect
Enter the server base URL, username and password. The app checks `player_api.php`, then requests live categories/streams, VOD categories/streams, and series categories/series independently.

## Notes
- Credentials are kept in memory only in this prototype; they are not saved to disk or printed to logs.
- Prefer HTTPS. HTTP sends credentials and stream URLs without transport encryption.
- Catalogs are parsed as streaming JSON, with a cap of 3,000 items per section to reduce memory pressure.
- Series episodes are requested separately with `get_series_info` when a series is selected.
- The API and stream URL conventions vary between providers. If the provider uses a custom API or path, the implementation may need adjustment.
- Only use services and streams you are authorized to access.
- GitHub Actions builds a debug APK for testing; this is not a Play Store release build.
