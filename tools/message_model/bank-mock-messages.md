# Indian bank mock-message catalogue

50 banks; 300 synthetic cases. Each bank has debit, credit, OTP, failure, future-payment and balance-only cases.

These are independently authored mocks informed by public parser grammar, not messages supplied or certified by the banks. Banks marked **generic only** have no verified bank-specific template in this collection. Passing a mock does not establish support for all live templates. Regional, foreign-currency and other banks are not exhaustive.

Paste one alert at a time into **Settings → Backup & data → SMS & Gmail import**. Expected amounts exclude the balance. Change the reference before repeating a test. Never call or text a number in a mock; placeholder contact numbers are deliberately invalid.

Machine-readable expected results and all rejection cases: [bank-fixtures.json](bank-fixtures.json). Regenerate with `python3 tools/message_model/build_bank_corpus.py`, then retrain with `python3 tools/message_model/train.py`. The model evaluation keeps its original held-out examples separate; bank cases below are regression checks, not held-out model accuracy.

## Canara Bank

Public grammar reference: [Canara Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/CanaraBankParser.ts).

Debit — INR 10000.00; account suffix 730.

```text
Dear Customer, Acct XXX730 Dr. INR 10,000.00 on 29/10/26 to DEMO MERCHANT; UPI: 930000000000; Bal INR 73,495.88.Not you?SMS BLOCKUPI to 0000000000-CanaraBank
```

Credit — INR 10000.00; account suffix 730.

```text
Dear Customer, Acct XXX730 Cr. INR 10,000.00 on 29/10/26 from DEMO MERCHANT; UPI: 930000000001; Bal INR 83,495.88.-CanaraBank
```

## State Bank of India

Public grammar reference: [State Bank of India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/SBIBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Your a/c XXXX4920 is debited by Rs.1,234.50 on 29/10/26 transfer to DEMO MERCHANT. Ref No 930000000100. Avl Bal Rs.42,875.25 -SBI
```

Credit — INR 1234.50; account suffix 4920.

```text
Your a/c XXXX4920 is credited by Rs.1,234.50 on 29/10/26 transfer from DEMO MERCHANT. Ref No 930000000101. Avl Bal Rs.48,875.25 -SBI
```

## Punjab National Bank

Public grammar reference: [Punjab National Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/PNBBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
PNB: Your a/c no XX4920 is debited for Rs 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. Ref 930000000200. Avl Bal Rs 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
PNB: Rs.1,234.50 credited to your A/c XX4920 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000000201. Avl Bal Rs 48,875.25.
```

## Bank of Baroda

Public grammar reference: [Bank of Baroda](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/BankOfBarodaParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Rs.1,234.50 Dr. from A/c XX4920 on 29-Oct-2026. Info: DEMO MERCHANT. UPI Ref 930000000300. Avl Bal Rs.42,875.25 -Bank of Baroda
```

Credit — INR 1234.50; account suffix 4920.

```text
Your A/c XX4920 credited with INR 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. UTR 930000000301. Avl Bal INR 48,875.25 -Bank of Baroda
```

## Bank of India

Public grammar reference: [Bank of India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/BankOfIndiaParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Rs.1,234.50 debited A/cXX4920 and credited to DEMO MERCHANT via UPI Ref No 930000000400 on 29Oct26. -BOI
```

Credit — INR 1234.50; account suffix 4920.

```text
Rs.1,234.50 credited to A/cXX4920 from DEMO MERCHANT via NEFT Ref No 930000000401 on 29Oct26. -BOI
```

## Union Bank of India

Public grammar reference: [Union Bank of India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/UnionBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
A/c *4920 Debited for Rs:1,234.50 on 29-10-2026 to DEMO MERCHANT. Mob Bk ref no 930000000500. Avl Bal Rs:42,875.25 -Union Bank
```

Credit — INR 1234.50; account suffix 4920.

```text
A/c *4920 Credited for Rs:1,234.50 on 29-10-2026 from DEMO MERCHANT. NEFT ref no 930000000501. Avl Bal Rs:48,875.25 -Union Bank
```

## Indian Bank

Public grammar reference: [Indian Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IndianBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Indian Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000000600. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Indian Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000000601. Avl Bal Rs. 48,875.25.
```

## Indian Overseas Bank

Public grammar reference: [Indian Overseas Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IndianOverseasBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Indian Overseas Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000000700. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Indian Overseas Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000000701. Avl Bal Rs. 48,875.25.
```

## Central Bank of India

Public grammar reference: [Central Bank of India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/CentralBankOfIndiaParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Central Bank: A/c XX4920 Debited by Rs.1,234.50 on 29-Oct-2026 to DEMO MERCHANT. Ref 930000000800. Bal Rs.42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Central Bank: A/c XX4920 Credited by Rs.1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000000801. Bal Rs.48,875.25.
```

## UCO Bank

Public grammar reference: [UCO Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/UCOBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
UCO Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000000900. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
UCO Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000000901. Avl Bal Rs. 48,875.25.
```

## Punjab & Sind Bank

Public grammar reference: [Punjab & Sind Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/PunjabSindBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
A/c No **4920 Debited with Rs 1,234.50--DEMO MERCHANT Ref 930000001000 (CLR BAL 42875.25CR)(29-10-2026 12:00:00)-Punjab&Sind Bank
```

Credit — INR 1234.50; account suffix 4920.

```text
A/c No **4920 Credited with Rs 1,234.50--DEMO MERCHANT Ref 930000001001 (CLR BAL 48875.25CR)(29-10-2026 12:00:00)-Punjab&Sind Bank
```

## Bank of Maharashtra

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Bank of Maharashtra: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000001100. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Bank of Maharashtra: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000001101. Avl Bal Rs. 48,875.25.
```

## HDFC Bank

Public grammar reference: [HDFC Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/HDFCBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Sent Rs.1,234.50 From HDFC Bank A/C *4920 To DEMO MERCHANT On 29/10/26 Ref 930000001200. Not You? SMS BLOCK UPI to 0000000000
```

Credit — INR 1234.50; account suffix 4920.

```text
HDFC Bank: Rs.1,234.50 deposited to a/c **4920 on 29-10-26. Info: DEMO MERCHANT. Ref 930000001201. Avl Bal Rs.48,875.25.
```

## ICICI Bank

Public grammar reference: [ICICI Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/ICICIBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
ICICI Bank Acct XX4920 debited for Rs 1,234.50 on 29-Oct-2026; DEMO MERCHANT credited. RRN 930000001300. Updated Bal Rs 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
ICICI Bank Acct XX4920 credited with Rs 1,234.50 from DEMO MERCHANT on 29-Oct-2026. RRN 930000001301. Updated Bal Rs 48,875.25.
```

## Axis Bank

Public grammar reference: [Axis Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/AxisBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
INR 1,234.50 debited from A/c no. XX4920 on 29-10-2026 at DEMO MERCHANT. Ref 930000001400. Avl bal INR 42,875.25 -Axis Bank
```

Credit — INR 1234.50; account suffix 4920.

```text
INR 1,234.50 credited to A/c no. XX4920 on 29-10-2026 from DEMO MERCHANT. Ref 930000001401. Avl bal INR 48,875.25 -Axis Bank
```

## Kotak Mahindra Bank

Public grammar reference: [Kotak Mahindra Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/KotakBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Sent Rs.1,234.50 from Kotak Bank AC X4920 to DEMO MERCHANT on 29-10-26. UPI Ref 930000001500. Avl Bal Rs.42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Received Rs.1,234.50 in your Kotak Bank AC X4920 from DEMO MERCHANT on 29-10-26. UPI Ref 930000001501. Avl Bal Rs.48,875.25.
```

## Yes Bank

Public grammar reference: [Yes Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/YesBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Yes Bank: INR 1,234.50 spent on card ending 4920 at DEMO MERCHANT on 29-Oct-2026. Ref 930000001600. Avl Lmt INR 1,42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Yes Bank: INR 1,234.50 refunded to card ending 4920 from DEMO MERCHANT on 29-Oct-2026. Ref 930000001601. Avl Lmt INR 1,48,875.25.
```

## IndusInd Bank

Public grammar reference: [IndusInd Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IndusIndBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Rs.1,234.50 debited from IndusInd Bank A/c XX4920 to DEMO MERCHANT. RRN:930000001700. Avl Bal Rs.42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
A/C *XX4920 credited by Rs 1,234.50 from DEMO MERCHANT. RRN:930000001701. Avl Bal Rs.48,875.25 -IndusInd Bank
```

## IDFC First Bank

Public grammar reference: [IDFC First Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IDFCFirstBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
IDFC First Bank Acct XX4920 debited for Rs 1,234.50 on 29-Oct-2026; DEMO MERCHANT credited. RRN 930000001800. Updated Bal Rs 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
IDFC First Bank Acct XX4920 credited with Rs 1,234.50 from DEMO MERCHANT on 29-Oct-2026. RRN 930000001801. Updated Bal Rs 48,875.25.
```

## Federal Bank

Public grammar reference: [Federal Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/FederalBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Debited Rs 1,234.50 from a/c XX4920 on 29OCT2026 via NEFT to DEMO MERCHANT.Ref 930000001900.Bal Rs 42,875.25.-Federal Bank
```

Credit — INR 1234.50; account suffix 4920.

```text
Credited Rs 1,234.50 to a/c XX4920 on 29OCT2026 via NEFT from DEMO MERCHANT.Ref 930000001901.Bal Rs 48,875.25.-Federal Bank
```

## South Indian Bank

Public grammar reference: [South Indian Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/SouthIndianBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
South Indian Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002000. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
South Indian Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002001. Avl Bal Rs. 48,875.25.
```

## Karnataka Bank

Public grammar reference: [Karnataka Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/KarnatakaBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Karnataka Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002100. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Karnataka Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002101. Avl Bal Rs. 48,875.25.
```

## IDBI Bank

Public grammar reference: [IDBI Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IDBIBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
IDBI Bank Acct XX4920 debited with Rs 1,234.50 on 29-Oct-2026 at DEMO MERCHANT. Ref 930000002200. Available Bal Rs 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
IDBI Bank Acct XX4920 credited with Rs 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002201. Available Bal Rs 48,875.25.
```

## DBS Bank

Public grammar reference: [DBS Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/DBSBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Your DBS Bank Acct XX4920 debited with INR 1,234.50 to DEMO MERCHANT on 29-Oct-2026. Ref 930000002300. Avl Bal INR 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Your DBS Bank Acct XX4920 credited with INR 1,234.50 from DEMO MERCHANT on 29-Oct-2026. Ref 930000002301. Avl Bal INR 48,875.25.
```

## Bandhan Bank

Public grammar reference: [Bandhan Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/BandhanBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Bandhan Bank: A/c XX4920 debited INR 1,234.50 towards DEMO MERCHANT on 29-Oct-2026. UPI Ref 930000002400. Avl Bal INR 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Bandhan Bank: A/c XX4920 credited INR 1,234.50 from DEMO MERCHANT on 29-Oct-2026. UPI Ref 930000002401. Avl Bal INR 48,875.25.
```

## City Union Bank

Public grammar reference: [City Union Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/CityUnionBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
City Union Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002500. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
City Union Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002501. Avl Bal Rs. 48,875.25.
```

## Dhanlaxmi Bank

Public grammar reference: [Dhanlaxmi Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/DhanlaxmiBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
INR 1,234.50 is debited from A/c XXXX4920 on 29-OCT-2026 to DEMO MERCHANT. UPI Ref 930000002600. Bal INR 42,875.25 -Dhanlaxmi Bank
```

Credit — INR 1234.50; account suffix 4920.

```text
INR 1,234.50 is credited to A/c XXXX4920 on 29-OCT-2026 from DEMO MERCHANT. UPI Ref 930000002601. Bal INR 48,875.25 -Dhanlaxmi Bank
```

## Jammu & Kashmir Bank

Public grammar reference: [Jammu & Kashmir Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/JKBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Jammu & Kashmir Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002700. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Jammu & Kashmir Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002701. Avl Bal Rs. 48,875.25.
```

## RBL Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
RBL Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002800. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
RBL Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002801. Avl Bal Rs. 48,875.25.
```

## Karur Vysya Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Karur Vysya Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000002900. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Karur Vysya Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000002901. Avl Bal Rs. 48,875.25.
```

## CSB Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
CSB Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003000. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
CSB Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000003001. Avl Bal Rs. 48,875.25.
```

## DCB Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
DCB Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003100. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
DCB Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000003101. Avl Bal Rs. 48,875.25.
```

## Tamilnad Mercantile Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Tamilnad Mercantile Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003200. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Tamilnad Mercantile Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000003201. Avl Bal Rs. 48,875.25.
```

## HSBC India

Public grammar reference: [HSBC India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/HSBCBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
HSBC India: INR 1,234.50 spent on card ending 4920 at DEMO MERCHANT on 29-Oct-2026. Ref 930000003300. Avl Lmt INR 1,42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
HSBC India: INR 1,234.50 refunded to card ending 4920 from DEMO MERCHANT on 29-Oct-2026. Ref 930000003301. Avl Lmt INR 1,48,875.25.
```

## Standard Chartered India

Public grammar reference: [Standard Chartered India](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/StandardCharteredBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Standard Chartered: INR 1,234.50 debited from your account 123xxxx4920 to DEMO MERCHANT on 29/10/2026. UTR 930000003400. Available Balance INR 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Standard Chartered: NEFT credit of INR 1,234.50 in your account 123xxxx4920 from DEMO MERCHANT on 29/10/2026. UTR 930000003401. Available Balance INR 48,875.25.
```

## AU Small Finance Bank

Public grammar reference: [AU Small Finance Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/AUBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
AU Bank: A/c XX4920 Dr INR 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003500. Bal INR 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
AU Bank: A/c XX4920 Cr INR 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. UPI Ref 930000003501. Bal INR 48,875.25.
```

## Equitas Small Finance Bank

Public grammar reference: [Equitas Small Finance Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/EquitasBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Equitas: INR 1,234.50 debited from A/c XX4920 to DEMO MERCHANT on 29-Oct-2026. Ref 930000003600. Bal INR 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Equitas: INR 1,234.50 credited to A/c XX4920 from DEMO MERCHANT on 29-Oct-2026. Ref 930000003601. Bal INR 48,875.25.
```

## Utkarsh Small Finance Bank

Public grammar reference: [Utkarsh Small Finance Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/UtkarshBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Utkarsh Small Finance Bank: INR 1,234.50 spent on card ending 4920 at DEMO MERCHANT on 29-Oct-2026. Ref 930000003700. Avl Lmt INR 1,42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Utkarsh Small Finance Bank: INR 1,234.50 refunded to card ending 4920 from DEMO MERCHANT on 29-Oct-2026. Ref 930000003701. Avl Lmt INR 1,48,875.25.
```

## Jana Small Finance Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Jana Small Finance Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003800. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Jana Small Finance Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000003801. Avl Bal Rs. 48,875.25.
```

## Suryoday Small Finance Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Suryoday Small Finance Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000003900. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Suryoday Small Finance Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000003901. Avl Bal Rs. 48,875.25.
```

## Ujjivan Small Finance Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
Ujjivan Small Finance Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004000. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Ujjivan Small Finance Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004001. Avl Bal Rs. 48,875.25.
```

## ESAF Small Finance Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
ESAF Small Finance Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004100. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
ESAF Small Finance Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004101. Avl Bal Rs. 48,875.25.
```

## Airtel Payments Bank

Public grammar reference: [Airtel Payments Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/AirtelPaymentsBankParser.ts).

Debit — INR 1234.50; account suffix unknown; reference provided.

```text
Rs. 1,234.50 debited from Airtel Payments Bank a/c to DEMO MERCHANT. Txn ID 930000004200. Bal Rs.42,875.25.
```

Credit — INR 1234.50; account suffix unknown; reference provided.

```text
Airtel Payments Bank a/c is credited with Rs.1,234.50 from DEMO MERCHANT. Txn ID: 930000004201. Bal Rs.48,875.25.
```

## India Post Payments Bank

Public grammar reference: [India Post Payments Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/IPPBParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
India Post Payments Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004300. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
India Post Payments Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004301. Avl Bal Rs. 48,875.25.
```

## Jio Payments Bank

Public grammar reference: [Jio Payments Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/JioPaymentsBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Jio Payments Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004400. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Jio Payments Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004401. Avl Bal Rs. 48,875.25.
```

## NSDL Payments Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
NSDL Payments Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004500. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
NSDL Payments Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004501. Avl Bal Rs. 48,875.25.
```

## Saraswat Co-operative Bank

Public grammar reference: [Saraswat Co-operative Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/SaraswatBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Saraswat Co-operative Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004600. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Saraswat Co-operative Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004601. Avl Bal Rs. 48,875.25.
```

## SVC Co-operative Bank

**Generic only:** synthetic common grammar; needs a consented bank-specific example.

Debit — INR 1234.50; account suffix 4920.

```text
SVC Co-operative Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004700. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
SVC Co-operative Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004701. Avl Bal Rs. 48,875.25.
```

## Greater Bank

Public grammar reference: [Greater Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/GreaterBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Your Account XXXX4920 had a DEBIT transaction of RS. 1,234.50 on 29-10-2026 at DEMO MERCHANT. Ref 930000004800. Available balance Rs.42,875.25 -GREATER BANK
```

Credit — INR 1234.50; account suffix 4920.

```text
Your Account XXXX4920 had a CREDIT transaction of RS. 1,234.50 on 29-10-2026 from DEMO MERCHANT. Ref 930000004801. Available balance Rs.48,875.25 -GREATER BANK
```

## Kerala Gramin Bank

Public grammar reference: [Kerala Gramin Bank](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46/src/banks/KeralaGraminBankParser.ts).

Debit — INR 1234.50; account suffix 4920.

```text
Kerala Gramin Bank: A/c XX4920 debited Rs. 1,234.50 on 29-Oct-2026 to DEMO MERCHANT. UPI Ref 930000004900. Avl Bal Rs. 42,875.25.
```

Credit — INR 1234.50; account suffix 4920.

```text
Kerala Gramin Bank: A/c XX4920 credited Rs. 1,234.50 on 29-Oct-2026 from DEMO MERCHANT. Ref 930000004901. Avl Bal Rs. 48,875.25.
```

## Sources and provenance

Grammar research: [BankSMSParser at 30d1597685c4bccf4f1d4e7caf7ba1da2abece46](https://github.com/rahatsayyed/BankSMSParser/blob/30d1597685c4bccf4f1d4e7caf7ba1da2abece46), by rahatsayyed and contributors, based on PennyWise parser-core. The upstream source is AGPL-3.0; no upstream source code or full fixture body is copied into this catalogue. Message templates here are authored for Paisa+ with synthetic names, identifiers, values and dates. Some grammar families and bank names are shared.

Canara formatting was also checked against the [public Canara demonstration](https://pennywise.zynth.dev/banks/canara-bank). The user-supplied Canara example informed the three-digit suffix and footer edge cases; its personal name, reference and contact number were replaced in repository fixtures.
