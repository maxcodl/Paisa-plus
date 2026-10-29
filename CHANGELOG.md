# Changelog

## Unreleased

- In-app updates: check GitHub releases daily, weekly or monthly (beta optional), notify once per version, download with SHA-256 check and install through the system installer
- Message import settings: SMS and Gmail source switches, Wi-Fi-only model download
- Model download runs in the foreground with a progress notification
- Help for "Allow restricted settings" when notification access is greyed out on sideloaded installs
- Settings › SMS & AI: notification access, sources, scan past SMS (30–365 days, READ_SMS), on-device model
- Settings › Notifications: permission and per-type channel settings
- Add automatically (on by default): bank alerts post straight to the matching account (last digits), with payee, category from payee history, payment method and UPI app; duplicates from SMS + Gmail, manual or CSV entries are skipped; uncertain ones wait in Review; a notification for each
- Bank parser tuned on real ICICI / Federal / SBI Card alerts: card bill payments, "Acc", amounts glued to "credited", own-account transfers, reversal refunds, completed mandate debits, "card number ending with"; alerts with links now go to Review instead of being dropped

## 0.8.1

- Keep pages full size during system Back gestures instead of shrinking into a floating rectangle

## 0.8.0

- One-time Auto setup with optional model Download, Import ZIP and Skip choices
- Mirrored Settings navigation transitions, stable subpage headers and restored list position on Back
- Ignore repeated navigation taps while a Settings transition is running
- Offline SMS/Gmail alert review inbox and bank-format parser
- Optional downloadable INT4 model for experimental local drafts

## 0.7.0

- Home-screen widgets follow the app theme (accent or Monet, light and dark)
- Widget opacity setting
- Theme-aware widget picker previews (wallpaper accent on Android 12+)
- Glass widget cards: themed tint with sheen and rim
- Payees, card networks (Visa, Mastercard), balance modes
- More widgets: total balance, this month, budget summary, budget tracker, debts, upcoming, quick add
- Portuguese translation

## 0.6.0

- Accounts split into bank and credit tabs with card-style hero cards
- Account detail with add and edit transaction
- Profile page with photo
- Home top bar cleanup and shorter copy

## 0.5.1

- Statement CSV import ported from Paisa: AI prompt, preview, de-duplication, chunked commit
- Loans imported without auto-posting

## 0.5.0

- Settings: currency and locale formatting, appearance, accent colour, corner style, backgrounds, tab bar
- App lock with screen-capture protection
- Balance widget
- Onboarding

## 0.4.0

- Debts: lending and borrowing with partial payments, progress and due dates

## Earlier

- Native versioned JSON backup and restore of all data and settings, with restore from Paisa backups and daily auto-backup (keeps 7)
- Home: greeting, balance card with month-over-month change, overview grid, recent transactions
- Navigation: Home, Accounts, Reports and Search tabs; Transactions, Categories, Budgets, Loans and Recurring from More
- Shared component library and Material 3 theme with Outfit type scale
