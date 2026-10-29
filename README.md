# paisa+

A native Android rewrite of [Paisa](https://github.com/h4h13/paisa), the open-source expense tracker by **Hemanth S** ([@h4h13](https://github.com/h4h13)), built with Kotlin and Jetpack Compose.

## Credits

paisa+ is based on [Paisa](https://github.com/h4h13/paisa) by Hemanth S. The original app was written in Flutter. paisa+ ports its features and data model to native Compose and stays compatible with Paisa's JSON backups, so you can move your data across. All credit for the original idea, design, and app goes to the Paisa project.

## Features

- Accounts (bank and credit cards), transactions, categories with budgets
- Loans and card EMIs, debts (lending/borrowing), recurring rules
- Monthly reports
- Statement CSV import
- Versioned JSON backup and restore, plus restore from Flutter Paisa backups
- App lock, home-screen widgets, Material You theming
- Everything stays on your device. No account and no cloud.

## Build

Requires JDK 17 and the Android SDK.

```sh
./gradlew installDebug       # debug build on a connected device
./gradlew testDebugUnitTest  # unit tests
```

## License

GPL-3.0, same as the original Paisa. See [LICENSE](LICENSE).
