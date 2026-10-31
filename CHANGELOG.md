# Changelog
## 0.10.4

- Transactions look new everywhere: bigger rounded icons, the time and account under the name, and the category as a coloured tag
- Debts: Borrowing, Lending and Completed tabs, a net balance, days left, repayment progress and a payment history on each debt
- Loans: Loans, Card EMIs and Completed tabs, with installments paid, time remaining and late payments; loans can have a name
- The thin outline around tab switchers is gone
- The debts home-screen widget is removed
## 0.10.3

- An update now also shows as a notification when you open the app, once per version
- The update check runs every time the app comes to the front, not only on a cold start
- Closing the update dialog keeps it away for 6 hours; App updates still shows the release
## 0.10.2

- Set a category for everything a person has in one go from their page, or long-press rows to pick just some
- Privacy policy (short version: I don't want your data) and a Buy me a coffee page, both on the profile page; Telegram link included
## 0.10.1

- Fixed an account balance losing an update when confirming an alert in Review for an account whose last digits weren't saved yet (the balance was overwritten with an older copy); CSV import had the same flaw
- Balance check: the app remembers each account's balance against its transactions and, if one drifts without you editing the account, puts it back and tells you; it runs at start, after Review and after a scan
- Messages can stay longer and every message can be swiped away; the correction notice stays 12 seconds

## 0.10.0

- Transfer: new item in the + menu and Pay bill on a credit card save a transfer or card bill payment as one expense and one income; pick the banking app it was paid from
- Make transfer on any transaction marks it as a transfer and adds (or links) the matching entry on the other account
- Bank app payment method with iMobile, FedMobile, SBI Card, YONO, Axis Mobile, HDFC and Kotak
- People: each person lists the other names bank messages and payment apps use for them; add, edit or remove them. Scans save new names only when they look alike, and wrong ones from the first scan are cleaned up once
- Past-SMS scan: live progress and log while it runs, plus a Scan log screen to filter and copy
- Banking apps list shows app icons, ticked apps first
- Category chips on Add transaction use the same colours as the lists
- Credit card page: Edit is an icon so Add transaction no longer wraps
- Removed unused strings and imports
- README now has screenshots

## 0.9.9

- Fixed bank texts being missed when several arrived from one sender in a single notification: each message is now read on its own
- Fixed past-SMS scan marking new payments as duplicates of a different same-amount entry; saved entries are matched by account, amount, date and payee name
- Banking apps: pick which installed apps' notifications are read as bank alerts (Settings > Sources); iMobile, FedMobile, SBI Card and Axis Mobile are on by default
- Own-account transfers and credit card bill payments are added as Transfers, with no UPI app tag
- People: names bank alerts use for someone are remembered (from Review, rename and merge), shown on their page and used to match saved entries; included in backups
- UPI app notices (Google Pay, PhonePe, Paytm, super money, MobiKwik, Freecharge, Navi) with no matching bank alert go to Review after about 10 minutes; Amazon "payment successful" notices work the same way
- Amazon Pay Later (Axio) alerts are read into Review; new Mobile Recharge category
- Pix notices and apps for Brazil (Nubank, Mercado Pago, PicPay, PagBank, Inter) are recognised as hints only; their package names are unverified
- Backups keep the Banking apps selection

## 0.9.8
 - Added date seperation for account transactions


## 0.9.7

- Tapping an "Added" notification opens that transaction to edit; tapping a recurring reminder opens Recurring

## 0.9.6

- People: sort by name (A–Z) or by number of transactions

## 0.9.5

- People list is sorted A–Z

## 0.9.4

- Updates install without a confirmation prompt once Paisa+ has installed itself (Android 12+); the first in-app update still asks
- Optional "Install with Shizuku" (Settings › About › App updates): no prompt at all while Shizuku runs; falls back to the normal installer otherwise
- Name fields suggest names you have used (payees, people in Debts, recurring and loan names) as you type; picking a payee on a new transaction fills in its last category, account and payment method
- Profile › Manage › People: everyone you pay or get paid by (and people in Debts), searchable, with totals; tap to open their payee page
- Debug builds are named x.y.z-dev and can update to the release of the same number
- Debts: totals in visible cards (they vanished with Pure black on) and net in its own card
- App updates: the new version shows as one card with its changelog at full width, Install and On GitHub

## 0.9.3

- Updates: the app checks for a new version every time it opens (it used to skip if it had checked within the hour), and the changelog shows as a tidy list in the update dialog and in App updates
- Scan past SMS: 7-day and All ranges; a re-scan also fills in statement entries from alerts seen before
- Statement names cut at 10 characters ("Cochin Caf") take the alert's fuller name
- Very old alerts for accounts not in the app (before any of your records) no longer land in Review

## 0.9.2

- Scans reconcile with your statements: alerts before an account's statement start are skipped (already in the opening balance); alerts matching a statement row give it the real time, a readable payee, payment method and UPI app without changing balances; only unmatched ones go to Review
- UPI app notices (GPay, PhonePe, Paytm, CRED, super.money, BHIM) tag the matching bank alert with the app and full payee name
- Payee names cut off by bank SMS are completed from your saved payees
- Scan result shows how many were already in your records; Review has Dismiss all
- Update check on opening (unless updates are Off): new versions show their changelog with Install and Later; App updates shows the full notes
- Profile: About section with version, app updates and source code

## 0.9.1

- Review: payments already saved leave Review instead of getting stuck ("similar transaction exists"); opening Review clears them and adds ones that are now recognisable
- Accounts without saved digits are matched by bank name (an ICICI alert goes to your only ICICI account); confirming a review teaches the account its last digits so the next alert is added automatically
- Review redesigned: amount and direction up top, payee, two-line message (tap for more), account and category pre-filled as chips, paste moved to the top bar
- Transactions: import notes ("Reviewed SMS alert; Ref …") no longer clutter the list
- Alerts: ICICI card merchants ("…on MAKEMYTRIP INDI") and Federal card merchants after a terminal id are read; salary credits are named Salary, credit card bill payments "Credit card payment" (Transfer); bank codes like INF*INFT* are not used as names; ALL-CAPS names are title-cased; card alerts no longer guess Swipe
- Accounts: alerts without digits (SBI card payments) match by bank name
- Only alerts under 2 days old are added unattended; older ones, or any with a same-amount transaction within 3 days on any account, go to Review (statement rows carry posting dates and may sit on another card of the group)
- One-time repair removes auto-added entries that duplicated imported transactions (restorable from Recently deleted)
- Home: the bell badge no longer clips; more space around the greeting
- Home: bell with a count replaces Search; opens Payments to review (new alerts, plus auto-added payments missing a category, payment method or UPI app, with Edit and Looks right)
- Onboarding asks for notification access (with restricted-settings help), SMS access for past payments and a daily backup folder; each can be skipped

## 0.9.0

- In-app updates: check GitHub releases daily, weekly or monthly (beta optional), notify once per version, download with SHA-256 check and install through the system installer
- Message import settings: SMS and Gmail source switches, Wi-Fi-only model download
- Model download runs in the foreground with a progress notification
- Help for "Allow restricted settings" when notification access is greyed out on sideloaded installs
- Settings › SMS & AI: notification access, sources, scan past SMS (30–365 days, READ_SMS), on-device model
- Settings › Notifications: permission and per-type channel settings
- Add automatically (on by default): bank alerts post straight to the matching account (last digits), with payee, category from payee history, payment method and UPI app; duplicates from SMS + Gmail, manual or CSV entries are skipped; uncertain ones wait in Review; a notification for each
- Bank parser tuned on real ICICI / Federal / SBI Card alerts: card bill payments, "Acc", amounts glued to "credited", own-account transfers, reversal refunds, completed mandate debits, "card number ending with"; alerts with links now go to Review instead of being dropped
- In-app updates pick the APK for the phone's CPU (arm64-v8a, armeabi-v7a, x86_64)
- Releases are created automatically when the version changes, with notes from this changelog and the optional AI model pack attached

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
