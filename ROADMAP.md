# Paisa+ roadmap

Checklist of planned features, open checks and known gaps. Tick items as they ship; add new ones at the bottom of their section.

## Next features

- [ ] **Auto-suggest while adding a transaction.** Payee names are already stored on every transaction (`ExpenseEntity.name`, grouped by `payeeKey`). Suggest matching names as you type, and when one is picked, prefill that payee's last category, account and payment method (UPI app too).
- [ ] Search and filter by payment method and UPI app (Search screen and Transactions filters).
- [ ] Search inside notes.
- [ ] Search: "Show more" past the first 300 results (the header already counts them all).
- [ ] Payee page: optional per-payee note or photo; set category for all of a payee's transactions at once.
- [ ] CSV import: optional `note` column.
- [ ] Material icons on the payment-method chips (UPI, tap, swipe, insert) and in rows.
- [ ] Categories page: decide whether the "This month" total should leave out Transfers (it includes them today).
- [x] Widget: "Spent this month" respects "Exclude transfers from totals" (uses `monthTotals` like Home).
- [x] Widgets redraw when a setting that affects them changes (hide amounts, currency, balance modes, month start, exclude transfers).
- [x] More widgets (Flutter Paisa had the first three), each with a real preview and description in the picker:
  - [x] Total balance 1×1: total balance only; amount shrinks to fit.
  - [x] Budget tracker 2×2: overall spent vs budget this month with a bar, left / over by (`budgets()`).
  - [x] Budget summary 3×2: every budget category with a progress bar, over-budget in red; scrolls.
  - [x] Upcoming 3×2: recurring rules, open loans / EMIs and the card bill, soonest first; scrolls.
  - [x] Debts 2×2: owed to you / you owe (outstanding), next one due.
  - [x] Quick add 1×1: one tap to the add sheet.
  - [x] This month 2×2: net, income, expense, spent-vs-income bar (Glance rows cap at 10 children, so no daily bars).
- [x] Reports: Spend summary card (month total, six-month bars, tap a bar to pick the month, dashed average).
- [x] Reports: account filter chips (All, Bank, Credit cards, each card group, each account) applied to the whole page.
- [x] Widget customisation (Settings › Home-screen widgets): style App colours (accent / Monet, light / dark) or Nothing (flat monochrome, red accent), opacity, glass sheen, Add expense button on the balance widget; corners follow the launcher radius.
- [x] Widgets reload data and colours on every refresh (running Glance sessions used to keep stale values); Nothing style uses the wallpaper (Monet) neutral like Nothing widgets, accent Nothing red or wallpaper.
- [x] Overview tile 2×2 (any overview card or balance page, ‹ › per widget) and Overview grid 4×3 (all overview cards, like Home).
- [x] Widgets adapt to the real cell size (short cells: slimmer Add button, then a round + by the title; compact overview tile; This month drops the bar).
- [x] Pure black keeps Monet-tinted cards (was fixed greys), so the app matches Nothing-style widgets.
- [x] With wallpaper colours, app backgrounds and cards use the system Monet neutrals for every colour style (matches launcher and Nothing widgets); Nothing-style widget accent defaults to wallpaper.
- [ ] Widgets: per-widget setup screen when placed (balance pages per widget, style per widget).
- [x] Settings reorganised: hub (profile, Money & totals, Appearance ×3, Home & widgets, Security & data, About) with sub-pages; dropdowns for number format, language, lock after; colour-style carousel with live previews.

## Verify on the phone

- [ ] ICICI joint card after importing v18: "Group payment" chips on payment rows, both cards under "ICICI combined".
- [ ] Mastercard logo (no Mastercard card in the data yet).
- [ ] Portuguese: Home tile plurals ("1 cartão" / "5 cartões"), CSV error messages, payment-method labels.
- [ ] Editing a note and a payment method on an existing transaction; Undo after delete keeps both.
- [ ] Rename and merge on the payee page.
- [ ] Settings › Data › Daily backup: turn on, pick a folder, "Back up now".
- [x] Widget "Spent this month" shows ₹1,84,365.69 for October (was ₹2,34,391.89 with Transfers).
- [ ] New widgets in the picker (previews, descriptions, Portuguese labels); place each, check sizes, tap targets, "Hide amounts" masking.

## Polish

- [ ] Bump `versionName` (still 0.7.0) before the next shared release.
- [ ] Default category names and the "Cash" account are still English (stored in data; Transfer/Salary logic and CSV matching rely on the English names).
- [ ] Payment-method label translations: review "Aproximação" / "Passar cartão" wording with a Brazilian speaker.

## Known limits

- The dark strip under the gesture handle on Nothing OS is the system navigation-bar overlay (a translucent Taskbar window drawn over every app), not app content. The app draws edge to edge beneath it.
- CSV parse errors are produced in English by the parser (tests pin the wording) and translated on the import screen by matching that wording; changing a message in `parseStatementCsv` needs the matching `csvError` pattern updated.
- The "ICICI combined" shared-payment rule is keyed on the group name (`SHARED_PAYMENT_GROUPS`); renaming that group in the CSV turns it off silently.
