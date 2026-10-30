# paisa+

A native Android rewrite of [Paisa](https://github.com/h4h13/paisa), the open-source expense tracker by **Hemanth S** ([@h4h13](https://github.com/h4h13)), built with Kotlin and Jetpack Compose.

<p align="center">
  <img src="docs/screenshots/01-home.png" width="210" alt="Home">
  <img src="docs/screenshots/03-accounts.png" width="210" alt="Accounts">
  <img src="docs/screenshots/05-card.png" width="210" alt="Credit card">
  <img src="docs/screenshots/06-pay-bill.png" width="210" alt="Transfer and card bill payment">
</p>

## Credits

paisa+ is based on [Paisa](https://github.com/h4h13/paisa) by Hemanth S. The original app was written in Flutter. paisa+ ports its features and data model to native Compose and stays compatible with Paisa's JSON backups, so you can move your data across. All credit for the original idea, design, and app goes to the Paisa project.

## Features

- Accounts (bank, cash and credit cards), transactions, categories with budgets
- Transfers between your accounts and credit card bill payments, from the **+** menu or **Pay bill** on a card; any transaction can be turned into a transfer afterwards
- Loans and card EMIs, debts (lending/borrowing), recurring rules
- Monthly reports
- People: everyone you pay or get paid by, with the other names bank messages and payment apps use for them
- Statement CSV import
- Offline SMS, Gmail and banking-app notification parsing with a trained local model and review inbox; UPI and Pix app notices (Google Pay, PhonePe, Paytm, super money, Nubank and others) tag the app and the full payee name
- Past-SMS scan with a live log of what was done with each message and why
- Versioned JSON backup and restore, plus restore from Flutter Paisa backups
- App lock, home-screen widgets, Material You theming
- Everything stays on your device. No account and no cloud.

## Screenshots

Shown with sample data.

| | | |
|---|---|---|
| <img src="docs/screenshots/02-add-menu.png" width="250" alt="Add menu"><br>Add menu with Transfer | <img src="docs/screenshots/04-credit-cards.png" width="250" alt="Credit cards"><br>Credit cards | <img src="docs/screenshots/07-transactions.png" width="250" alt="Transactions"><br>Transactions |
| <img src="docs/screenshots/08-person.png" width="250" alt="Person"><br>A person and the names bank alerts use for them | <img src="docs/screenshots/09-reports.png" width="250" alt="Reports"><br>Reports | <img src="docs/screenshots/10-budgets.png" width="250" alt="Budgets"><br>Budgets |
| <img src="docs/screenshots/12-sms-ai.png" width="250" alt="SMS and AI settings"><br>SMS and banking-app sources | <img src="docs/screenshots/13-scan-log.png" width="250" alt="Scan log"><br>Scan log | <img src="docs/screenshots/15-make-transfer.png" width="250" alt="Make transfer"><br>Turn a transaction into a transfer |

The profile picture is from [DiceBear](https://www.dicebear.com/) (Notionists by Zoish, CC0).

## Build

Requires JDK 21 and Android SDK 37.

```sh
./gradlew installDebug       # debug build on a connected device
./gradlew testDebugUnitTest  # unit tests
```

For signed APKs/AABs, local `keystore.properties`, or an existing signing key in the manually triggered GitHub Actions workflow, see [release builds](docs/release-builds.md).

## Local message parsing

The one-time **Auto** setup lets you download the optional model, import its ZIP, or skip and use the built-in parser. You can change this later.

Open **Settings → SMS & AI** to enable notification access, pick the banking apps whose notifications are read, scan past SMS, or paste an INR banking alert. Review detected payments and choose an account/category before saving. This supports new notifications and past SMS, not full Gmail inbox sync. No message text is sent to a server. An [optional downloadable AI model](tools/message_model/optional-model.md) can produce experimental local drafts; the built-in parser works without it. See [model training and limitations](tools/message_model/README.md) and [mock messages for 50 Indian banks](tools/message_model/bank-mock-messages.md).

## License

GPL-3.0, same as the original Paisa. See [LICENSE](LICENSE).
