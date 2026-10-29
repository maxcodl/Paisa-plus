# Physical Xiaomi test — 29 October 2026

Installed the debug APK over the existing app with `adb install -r`. Tested through the Android UI on the connected Xiaomi 24069PC21I; app data was not cleared.

## Mock-message checks

Used the messages in [mock-messages.md](mock-messages.md), pasted one at a time into Settings → Backup & data → SMS & Gmail import.

| Case | Observed result |
| --- | --- |
| Debit | INR 1,500.00 expense, account suffix 4920, payee Amazon Pay |
| Credit | INR 25,000.50 income, account suffix 4920 |
| Refund | INR 700.00 income; payee requires manual entry |
| Duplicate debit, changed wording / same reference | “Message already detected”; inbox count unchanged |
| Balance only | Ignored: no completed transaction action |
| OTP | Ignored |
| Failed payment | Ignored |
| Future payment | Ignored |
| Suspicious link | Ignored: manual verification required |

All nine cases passed. The debit, credit and refund examples remain as three pending review candidates. Force-stopped and reopened the app, then verified that all three pending candidates persisted. The phone was left on the import screen.

## Save and cleanup check

Created an isolated bank account named `Paisa parser test`, provider `Mock Bank`, number ending `4920`, starting balance zero. Entered a separate synthetic INR 1.25 debit with reference `928374619999` and payee `Parser Smoke Test`.

The account suffix was suggested correctly. After selecting Groceries and tapping Save transaction, the candidate left the review inbox, the account showed balance `-₹1.25`, and its transaction list showed one expense with `Groceries · UPI` and the reference in its note.

Deleted only that temporary test account and its one synthetic transaction. Verified the account list returned to the original Cash account with balance ₹0.00.

## Coverage boundary

This was an actual physical-device UI test of pasted-message parsing, review, deduplication and saving. No real SMS or email was sent. Android SMS/Gmail notification delivery was not exercised, and notification access was left disabled. Emulator SMS injection commands do not work on this physical phone.

## Expanded Canara check

Installed the updated build after the 50-bank corpus work. Pasted the user's Canara alert locally on the Xiaomi. The review card correctly showed INR 10,000.00 EXPENSE, suffix `730`, and the supplied merchant name. The separate INR 73,495.88 balance was not treated as the payment. This alert was left pending for review; no ledger transaction was saved. The underlying personal message is not copied into repository fixtures or Hugging Face benchmark inputs.

The expanded build passes 382 JVM tests, including all 300 synthetic bank-format cases. The model was retrained on 1,088 synthetic training rows. The synthetic catalogue is not a claim to support every live bank template.

## Optional-model release check

Synced implementation onto GitHub main at `8c16d26`, preserving its newer widget/settings changes. Added an optional ONNX INT4 model pack with pinned archive/file sizes and SHA-256 checks. The corrected release ZIP is 55,687,065 bytes and its installed files total 65,606,438 bytes. A stale-manifest packaging error was caught by the installer during an initial attempt and fixed in the exporter; the actual final release ZIP is now checked by a JVM test.

On the physical Xiaomi:

- Imported the final ZIP through Android's document picker; installer reported Installed / Ready for offline testing.
- Ran a synthetic HDFC debit through the local INT4 model. It returned a JSON draft with amount 1500.00, debit, account 1234 and date 2025-08-15. The review inbox count stayed at four and no ledger entry was created.
- Deleted the model; the card returned to Not installed.
- Tried Download model; the phone could not resolve github.com and the UI displayed the download error, leaving the model uninstalled. Public anonymous download success was not exercised: the repository is private and phone networking was unavailable.
- Reinstalled the minified ARM64 release APK, reimported the final pack and reran local generation successfully. This verifies that R8 preserves the ONNX runtime bindings. The model remains installed for the user to try.

389 JVM tests pass, with zero failures/errors/skips, including the exported model pack and GPT-2 tokenizer. Debug and minified release builds pass. Release APK sizes: ARM64 16,777,588 bytes; ARMv7 15,801,040 bytes; x86-64 18,663,048 bytes. Models remain outside APK and Git history. Notification access is currently enabled by the device user; no real incoming SMS/Gmail notification was generated during this test.

## Auto setup and Settings navigation — 0.8.0

Added a one-time device-local Auto introduction after onboarding, including for existing users on update. Download, Import ZIP and Skip choices are shown; widget quick-add launches bypass it. Download work is explicitly user-triggered. The intro preference is excluded from backup export/restore. The temporary optional model installation was deleted through its normal UI to check the not-installed setup layout; the user's account/transaction data was preserved.

On the connected Xiaomi, confirmed the startup screen layout shows the 55.7 MB download and all choices without clipping. The screen was dismissed and a force-stop/relaunch returned to Home. Settings subpages now use compact fixed titles and mirrored 260 ms navigation slides; the hub retains its large collapsing title. Captured and inspected the Back transition. Two rapid toolbar Back taps returned to the hub, and the hub's Settings title, Backup & data row and Home-screen widgets row returned to exactly their previous UI bounds. No double-pop occurred.

Synced the latest GitHub main widget/theme updates before the final 0.8.0 build; these changes remain intact.
