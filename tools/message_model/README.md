# Local bank-alert model

Run `python3 tools/message_model/train.py` from any directory to reproduce the checked-in Kotlin model and `evaluation.json`. Python's standard library is sufficient. Training is a development-time step; the app never downloads weights, calls a server, or trains on a user's messages.

The classifier combines two multinomial naive Bayes experts (a general-alert model and an expanded bank-format model), taking their maximum score. Both operate on lowercase ASCII word tokens with digit normalization, Laplace smoothing, equal class priors and an 0.8 acceptance threshold. Runtime tokenization matches the trainer and limits inference to 512 tokens. Its output is a transaction-text score, not a calibrated probability, fraud decision, or proof of sender authenticity.

`corpus.json` contains 1,088 independently authored synthetic training messages across 90 template families, plus 12 separately authored held-out examples. The original 288 examples train the general-alert expert; all 1,088 train the bank-format expert. This keeps expanded bank vocabulary from suppressing previously recognized general alerts. [The bank catalogue](bank-mock-messages.md) contains 300 regression cases across 50 banks; 12 banks use explicitly marked generic mocks without verified bank-specific grammar. These regression templates overlap training and are not a held-out accuracy benchmark. Numeric substitutions are augmentation, not independent samples. There are no real inbox messages, customer identities or downloaded third-party message bodies. Current scope is English INR alerts for Indian bank accounts/cards/UPI; unsupported and uncertain messages require manual entry.

The held-out model evaluation currently detects 6/6 positives and incorrectly accepts 1/6 negatives. This tiny synthetic check is not a real-world accuracy estimate. Strict parser exclusions are independently applied before the model: OTPs, authentication requests, URLs, failures, pending/future payments, bills, reversals, mixed currencies/directions and ambiguous amounts are rejected. Genuine alerts can also be rejected, especially messages containing bank links. Refunds with a clear credit action can be reviewed as income; reversals require manual reconciliation. Expand the corpus and test on consented, redacted, template-disjoint real-world data before considering unattended posting.

## Public research references

The implementation and corpus were authored for Paisa+, informed by public format/edge-case descriptions:

- [Indian Payment SMS Corpus, SplitEasy contributors](https://github.com/skyaara/indian-payment-sms-dataset): synthetic examples and distinctions between debits, credits, balances, failures and future events. This dataset was researched but not copied or used for training.
- [BankSMSParser](https://github.com/rahatsayyed/BankSMSParser/tree/30d1597685c4bccf4f1d4e7caf7ba1da2abece46): researched bank grammars for the expanded catalogue; pinned bank-level links and provenance are in the catalogue. No upstream implementation or full fixture bodies were copied.
- [Omoi SMS parser](https://github.com/abhirajsinha/omoi-sms-parser): documented rejection order for OTPs, bills and future debits. No source code or fixtures copied.
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService): opt-in notification capture lifecycle.

## App behavior

Settings → Backup & data → SMS & Gmail import lets users grant notification access or paste text. Capture processes only new, nongroup-summary notifications from the device's default SMS app and `com.google.android.gm`. It uses expanded notification text when available. This is not full Gmail mailbox synchronization, does not use Gmail OAuth and cannot read attachments, hidden notification content or earlier messages. No READ_SMS or RECEIVE_SMS permission is added. INTERNET is used only for the optional model download; SMS and email text is never sent to a server.

Candidate messages enter a Room inbox. The user reviews the original text, amount, direction, capture time, account and category, and can edit the payee. Account suffixes only suggest an unambiguous existing account. Captured notification time (or now for pasted messages) is the expense time; embedded dates are not interpreted. Incorrect amount/date/direction can be handled through manual transaction entry. The listener does not authenticate a bank sender and never posts to the ledger unattended.

Fingerprints deduplicate reference/account/amount/direction across channels; messages without references use normalized text plus UTC receipt date. This can collapse identical no-reference messages on the same day. Different wording without a shared reference may remain separate. Saving requires the app currency to be INR. Saving also rejects an existing same-account, same-amount/direction transaction within five minutes, so an already manually entered alert is not casually counted twice; legitimate similar payments can instead be entered manually. Saving, balance adjustment and inbox status update share one database transaction.

Up to 200 pending message bodies are retained. Imported/dismissed/overflow entries retain only parsed metadata and deduplication fingerprints, clearing original text. Inbox data is excluded from Paisa's JSON backups and cleared on backup restore. Confirmed expenses are backed up normally. Notification access can be revoked from the same Settings screen.

The parser regression suite lives in `app/src/test/java/com/paisaplus/compose/messages/BankMessageParserTest.kt`.

## Other model research

[Local evaluation of banking-sms-json-parser-v8](hf-model-assessment.md) records the Hugging Face model metadata, prompt assumptions and synthetic benchmark results. That generative model is not bundled in the app; an [optional INT4 pack](optional-model.md) enables local draft testing.
