# Mock messages for device testing

These messages are synthetic. Paste **one message at a time** into **Settings → Backup & data → SMS & Gmail import → Paste an SMS or Gmail alert**, then tap **Detect transaction**. Use a test account whose number ends in `4920` and set the app currency to INR before saving. Captured time is used, not the date in the message. If a message was already tested, change its reference number to test it again; duplicate detection also remembers dismissed and imported alerts.

## Debit: INR 1,500.00

```text
Your A/c XX4920 has been debited by INR 1,500.00 on 29-Oct-2026. Info: Amazon Pay. UPI Ref 928374610123.
```

Expected: one expense candidate, amount `1500.00`, account suffix `4920`, payee `Amazon Pay`, UPI reference `928374610123`.

## Credit: INR 25,000.50

```text
INR 25,000.50 credited to account XX4920 from Demo Employer. UPI Ref 928374610124.
```

Expected: one income candidate, amount `25000.50`, account suffix `4920`.

## Refund: INR 700.00

```text
INR 700.00 refunded to card ending 4920 for Online Shop. Ref 928374610125.
```

Expected: one income candidate, amount `700.00`, account suffix `4920`. The payee can be entered during review if not extracted.

## Duplicate debit

```text
Debit alert. INR 1500.00 debited from A/c XX4920 at Amazon Pay. UPI Ref 928374610123.
```

Expected after the first debit: “Message already detected”; no additional candidate, even when arriving through a different supported source.

## Balance only — ignore

```text
Your account XX4920 balance is INR 5000.00.
```

Expected: ignored; no completed transaction action.

## OTP — ignore

```text
OTP 123456 for payment of INR 1500.00 using card XX4920. Do not share.
```

Expected: ignored; no expense or income.

## Failed transaction — ignore

```text
Payment failed. INR 1500.00 not debited from A/c XX4920. Ref 928374610126.
```

Expected: ignored; no expense or income.

## Future debit — ignore

```text
INR 1500.00 will be debited from account XX4920 tomorrow. Ref 928374610127.
```

Expected: ignored; no expense or income.

## Link / suspicious alert — ignore

```text
INR 1500.00 debited from account XX4920. Verify at https://example.invalid.
```

Expected: ignored; manual verification required.

## Notification capture

Grant notification access from the import screen. The listener processes only your default SMS app and Gmail. Pasting tests the same classifier/parser and inbox, but does not exercise Android notification delivery. Generic notifications sent by `adb shell cmd notification post` are intentionally excluded because they originate from the shell, not SMS or Gmail.

On an Android emulator with an SMS app, an incoming synthetic SMS can be injected with `adb -s emulator-5554 emu sms send ...`. This emulator command does not work on a physical Xiaomi phone. Do not use a real SMS send command or send email to another person for this test.
