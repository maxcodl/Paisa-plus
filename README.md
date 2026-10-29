# paisa+

A native Android rewrite of [Paisa](https://github.com/h4h13/paisa), the open-source expense tracker by **Hemanth S** ([@h4h13](https://github.com/h4h13)), built with Kotlin and Jetpack Compose.

## Credits

paisa+ is based on [Paisa](https://github.com/h4h13/paisa) by Hemanth S. The original app was written in Flutter. paisa+ ports its features and data model to native Compose and stays compatible with Paisa's JSON backups, so you can move your data across. All credit for the original idea, design, and app goes to the Paisa project.

## Features

- Accounts (bank and credit cards), transactions, categories with budgets
- Loans and card EMIs, debts (lending/borrowing), recurring rules
- Monthly reports
- Statement CSV import
- Offline SMS/Gmail notification parsing with a trained local model and review inbox
- Versioned JSON backup and restore, plus restore from Flutter Paisa backups
- App lock, home-screen widgets, Material You theming
- Everything stays on your device. No account and no cloud.

## Build

Requires JDK 21 and Android SDK 37.

```sh
./gradlew installDebug       # debug build on a connected device
./gradlew testDebugUnitTest  # unit tests
```

For signed APKs/AABs, local `keystore.properties`, or an existing signing key in the manually triggered GitHub Actions workflow, see [release builds](docs/release-builds.md).

## Local message parsing

The one-time **Auto** setup lets you download the optional model, import its ZIP, or skip and use the built-in parser. You can change this later.

Open **Settings → Backup & data → SMS & Gmail import** to enable notification access or paste an INR banking alert. Review detected payments and choose an account/category before saving. This supports new notifications, not full Gmail inbox sync. No message text is sent to a server. An [optional downloadable AI model](tools/message_model/optional-model.md) can produce experimental local drafts; the built-in parser works without it. See [model training and limitations](tools/message_model/README.md) and [mock messages for 50 Indian banks](tools/message_model/bank-mock-messages.md).

## License

GPL-3.0, same as the original Paisa. See [LICENSE](LICENSE).
