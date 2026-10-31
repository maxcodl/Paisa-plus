<p align="center">
  <img src="docs/icon.svg" width="96" alt="paisa+ icon">
</p>

<h1 align="center">paisa+</h1>

<p align="center">
  A native Android rewrite of <a href="https://github.com/h4h13/paisa-app">Paisa</a>, the open-source expense tracker.<br>
  Built with Kotlin and Jetpack Compose. Everything stays on your device.
</p>

<p align="center">
  <a href="https://github.com/maxcodl/Paisa-plus/releases/latest"><img src="https://img.shields.io/github/v/release/maxcodl/Paisa-plus?style=flat-square&color=944A02" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin, Jetpack Compose">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://github.com/maxcodl/Paisa-plus/releases/latest"><img src="https://img.shields.io/badge/Download-latest%20APK-944A02?style=for-the-badge&logo=android&logoColor=white" alt="Download latest APK"></a>
</p>

<p align="center">
  <img src="docs/screenshots/01-home.png" width="200" alt="Home">
  <img src="docs/screenshots/03-accounts.png" width="200" alt="Accounts">
  <img src="docs/screenshots/05-card.png" width="200" alt="Credit card">
  <img src="docs/screenshots/06-pay-bill.png" width="200" alt="Transfer and card bill payment">
</p>

## Overview

paisa+ tracks accounts, spending and budgets without an account or a server. It reads bank alerts on the phone, turns them into transactions you review, and keeps all of it in a local database. It stays compatible with Paisa's JSON backups, so existing data moves across.

## Features

| | |
|---|---|
| **Money** | Bank, cash and credit card accounts, transactions, categories with budgets, monthly reports |
| **Transfers** | Between your own accounts and for credit card bills, from the **+** menu or **Pay bill** on a card; any transaction can be turned into a transfer afterwards |
| **Commitments** | Loans and card EMIs, debts (lending and borrowing), recurring rules |
| **People** | Everyone you pay or get paid by, with the other names bank messages and payment apps use for them. Set a category for all of a person's transactions at once |
| **Messages** | Offline SMS, Gmail and banking-app notification parsing with a local model and a review inbox. UPI and Pix app notices (Google Pay, PhonePe, Paytm, super money, Nubank and others) tag the app and the full payee name |
| **Scanning** | Past-SMS scan with a live log of what was done with each message and why |
| **Data** | Statement CSV import, versioned JSON backup and restore, restore from Flutter Paisa backups |
| **App** | App lock, home-screen widgets, Material You theming, update notifications |

## Screenshots

Shown with sample data.

| | | |
|---|---|---|
| <img src="docs/screenshots/02-add-menu.png" width="250" alt="Add menu"><br>Add menu with Transfer | <img src="docs/screenshots/04-credit-cards.png" width="250" alt="Credit cards"><br>Credit cards | <img src="docs/screenshots/07-transactions.png" width="250" alt="Transactions"><br>Transactions |
| <img src="docs/screenshots/08-person.png" width="250" alt="Person"><br>A person and the names bank alerts use for them | <img src="docs/screenshots/09-reports.png" width="250" alt="Reports"><br>Reports | <img src="docs/screenshots/10-budgets.png" width="250" alt="Budgets"><br>Budgets |
| <img src="docs/screenshots/12-sms-ai.png" width="250" alt="SMS and AI settings"><br>SMS and banking-app sources | <img src="docs/screenshots/13-scan-log.png" width="250" alt="Scan log"><br>Scan log | <img src="docs/screenshots/15-make-transfer.png" width="250" alt="Make transfer"><br>Turn a transaction into a transfer |

The profile picture is from [DiceBear](https://www.dicebear.com/) (Notionists by Zoish, CC0).

## Privacy

No account, no analytics, no cloud. Transactions, accounts and settings live only on the device, and no message text is sent to a server. The network is used only for what you ask for: checking GitHub for app updates and downloading the optional AI model.

## Local message parsing

The one-time **Auto** setup lets you download the optional model, import its ZIP, or skip and use the built-in parser. You can change this later.

Open **Settings → SMS & AI** to enable notification access, pick the banking apps whose notifications are read, scan past SMS, or paste an INR banking alert. Review detected payments and choose an account and category before saving. This supports new notifications and past SMS, not full Gmail inbox sync. An [optional downloadable AI model](tools/message_model/optional-model.md) can produce experimental local drafts; the built-in parser works without it. Model source and reference: [banking-sms-json-parser-v8](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8). See [model training and limitations](tools/message_model/README.md) and [mock messages for 50 Indian banks](tools/message_model/bank-mock-messages.md).

## Build

Requires JDK 21 and Android SDK 37.

```sh
./gradlew installDebug       # debug build on a connected device
./gradlew testDebugUnitTest  # unit tests
```

For signed APKs/AABs, local `keystore.properties`, or an existing signing key in the manually triggered GitHub Actions workflow, see [release builds](docs/release-builds.md).

## Credits

paisa+ is based on [Paisa](https://github.com/h4h13/paisa) by **Hemanth S** ([@h4h13](https://github.com/h4h13)). The original app was written in Flutter; paisa+ ports its features and data model to native Compose. All credit for the original idea, design and app goes to the Paisa project.

Special thanks to Misfit ([@SriKousik](https://github.com/SriKousik)) for the notification AI. The model is based on [banking-sms-json-parser-v8](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8) on Hugging Face.

## License

GPL-3.0, same as the original Paisa. See [LICENSE](LICENSE).
