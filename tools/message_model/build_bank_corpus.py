#!/usr/bin/env python3
"""Author synthetic bank-format variants and regression fixtures; never downloads private SMS."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
REV = '30d1597685c4bccf4f1d4e7caf7ba1da2abece46'
SOURCE = f'https://github.com/rahatsayyed/BankSMSParser/blob/{REV}/src/banks/'
# Templates are independently authored mock alerts informed by the linked grammar references.
# Supplemental banks explicitly use generic mocks where no actual template was verified.
BANKS = [
('Canara Bank','CanaraBank','canara'),
('State Bank of India','SBIBank','sbi'),
('Punjab National Bank','PNBBank','pnb'),
('Bank of Baroda','BankOfBaroda','bob'),
('Bank of India','BankOfIndia','boi'),
('Union Bank of India','UnionBank','union'),
('Indian Bank','IndianBank','common'),
('Indian Overseas Bank','IndianOverseasBank','common'),
('Central Bank of India','CentralBankOfIndia','central'),
('UCO Bank','UCOBank','common'),
('Punjab & Sind Bank','PunjabSindBank','psb'),
('Bank of Maharashtra',None,'common'),
('HDFC Bank','HDFCBank','hdfc'),
('ICICI Bank','ICICIBank','beneficiary'),
('Axis Bank','AxisBank','axis'),
('Kotak Mahindra Bank','KotakBank','kotak'),
('Yes Bank','YesBank','card'),
('IndusInd Bank','IndusIndBank','rrn'),
('IDFC First Bank','IDFCFirstBank','beneficiary'),
('Federal Bank','FederalBank','federal'),
('South Indian Bank','SouthIndianBank','common'),
('Karnataka Bank','KarnatakaBank','common'),
('IDBI Bank','IDBIBank','idbi'),
('DBS Bank','DBSBank','dbs'),
('Bandhan Bank','BandhanBank','bandhan'),
('City Union Bank','CityUnionBank','common'),
('Dhanlaxmi Bank','DhanlaxmiBank','dhanlaxmi'),
('Jammu & Kashmir Bank','JKBank','common'),
('RBL Bank',None,'common'),
('Karur Vysya Bank',None,'common'),
('CSB Bank',None,'common'),
('DCB Bank',None,'common'),
('Tamilnad Mercantile Bank',None,'common'),
('HSBC India','HSBCBank','card'),
('Standard Chartered India','StandardCharteredBank','neft'),
('AU Small Finance Bank','AUBank','au'),
('Equitas Small Finance Bank','EquitasBank','equitas'),
('Utkarsh Small Finance Bank','UtkarshBank','card'),
('Jana Small Finance Bank',None,'common'),
('Suryoday Small Finance Bank',None,'common'),
('Ujjivan Small Finance Bank',None,'common'),
('ESAF Small Finance Bank',None,'common'),
('Airtel Payments Bank','AirtelPaymentsBank','airtel'),
('India Post Payments Bank','IPPB','common'),
('Jio Payments Bank','JioPaymentsBank','common'),
('NSDL Payments Bank',None,'common'),
('Saraswat Co-operative Bank','SaraswatBank','common'),
('SVC Co-operative Bank',None,'common'),
('Greater Bank','GreaterBank','greater'),
('Kerala Gramin Bank','KeralaGraminBank','common'),
]
# {amount}, {account}, {ref}, {merchant}, {bank} are synthetic values, not customer data.
TEMPLATES = {
'common': ('{bank}: A/c XX{account} debited Rs. {amount} on 29-Oct-2026 to {merchant}. UPI Ref {ref}. Avl Bal Rs. 42,875.25.',
           '{bank}: A/c XX{account} credited Rs. {amount} on 29-Oct-2026 from {merchant}. Ref {ref}. Avl Bal Rs. 48,875.25.'),
'canara': ('Dear Customer, Acct XXX{account} Dr. INR {amount} on 29/10/26 to {merchant}; UPI: {ref}; Bal INR 73,495.88.Not you?SMS BLOCKUPI to 0000000000-CanaraBank',
           'Dear Customer, Acct XXX{account} Cr. INR {amount} on 29/10/26 from {merchant}; UPI: {ref}; Bal INR 83,495.88.-CanaraBank'),
'sbi': ('Your a/c XXXX{account} is debited by Rs.{amount} on 29/10/26 transfer to {merchant}. Ref No {ref}. Avl Bal Rs.42,875.25 -SBI',
        'Your a/c XXXX{account} is credited by Rs.{amount} on 29/10/26 transfer from {merchant}. Ref No {ref}. Avl Bal Rs.48,875.25 -SBI'),
'pnb': ('PNB: Your a/c no XX{account} is debited for Rs {amount} on 29-Oct-2026 to {merchant}. Ref {ref}. Avl Bal Rs 42,875.25.',
        'PNB: Rs.{amount} credited to your A/c XX{account} on 29-Oct-2026 from {merchant}. Ref {ref}. Avl Bal Rs 48,875.25.'),
'bob': ('Rs.{amount} Dr. from A/c XX{account} on 29-Oct-2026. Info: {merchant}. UPI Ref {ref}. Avl Bal Rs.42,875.25 -Bank of Baroda',
        'Your A/c XX{account} credited with INR {amount} on 29-Oct-2026 from {merchant}. UTR {ref}. Avl Bal INR 48,875.25 -Bank of Baroda'),
'boi': ('Rs.{amount} debited A/cXX{account} and credited to {merchant} via UPI Ref No {ref} on 29Oct26. -BOI',
        'Rs.{amount} credited to A/cXX{account} from {merchant} via NEFT Ref No {ref} on 29Oct26. -BOI'),
'union': ('A/c *{account} Debited for Rs:{amount} on 29-10-2026 to {merchant}. Mob Bk ref no {ref}. Avl Bal Rs:42,875.25 -Union Bank',
          'A/c *{account} Credited for Rs:{amount} on 29-10-2026 from {merchant}. NEFT ref no {ref}. Avl Bal Rs:48,875.25 -Union Bank'),
'central': ('Central Bank: A/c XX{account} Debited by Rs.{amount} on 29-Oct-2026 to {merchant}. Ref {ref}. Bal Rs.42,875.25.',
            'Central Bank: A/c XX{account} Credited by Rs.{amount} on 29-Oct-2026 from {merchant}. Ref {ref}. Bal Rs.48,875.25.'),
'hdfc': ('Sent Rs.{amount} From HDFC Bank A/C *{account} To {merchant} On 29/10/26 Ref {ref}. Not You? SMS BLOCK UPI to 0000000000',
         'HDFC Bank: Rs.{amount} deposited to a/c **{account} on 29-10-26. Info: {merchant}. Ref {ref}. Avl Bal Rs.48,875.25.'),
'beneficiary': ('{bank} Acct XX{account} debited for Rs {amount} on 29-Oct-2026; {merchant} credited. RRN {ref}. Updated Bal Rs 42,875.25.',
                '{bank} Acct XX{account} credited with Rs {amount} from {merchant} on 29-Oct-2026. RRN {ref}. Updated Bal Rs 48,875.25.'),
'axis': ('INR {amount} debited from A/c no. XX{account} on 29-10-2026 at {merchant}. Ref {ref}. Avl bal INR 42,875.25 -Axis Bank',
         'INR {amount} credited to A/c no. XX{account} on 29-10-2026 from {merchant}. Ref {ref}. Avl bal INR 48,875.25 -Axis Bank'),
'kotak': ('Sent Rs.{amount} from Kotak Bank AC X{account} to {merchant} on 29-10-26. UPI Ref {ref}. Avl Bal Rs.42,875.25.',
          'Received Rs.{amount} in your Kotak Bank AC X{account} from {merchant} on 29-10-26. UPI Ref {ref}. Avl Bal Rs.48,875.25.'),
'card': ('{bank}: INR {amount} spent on card ending {account} at {merchant} on 29-Oct-2026. Ref {ref}. Avl Lmt INR 1,42,875.25.',
         '{bank}: INR {amount} refunded to card ending {account} from {merchant} on 29-Oct-2026. Ref {ref}. Avl Lmt INR 1,48,875.25.'),
'rrn': ('Rs.{amount} debited from IndusInd Bank A/c XX{account} to {merchant}. RRN:{ref}. Avl Bal Rs.42,875.25.',
        'A/C *XX{account} credited by Rs {amount} from {merchant}. RRN:{ref}. Avl Bal Rs.48,875.25 -IndusInd Bank'),
'federal': ('Debited Rs {amount} from a/c XX{account} on 29OCT2026 via NEFT to {merchant}.Ref {ref}.Bal Rs 42,875.25.-Federal Bank',
            'Credited Rs {amount} to a/c XX{account} on 29OCT2026 via NEFT from {merchant}.Ref {ref}.Bal Rs 48,875.25.-Federal Bank'),
'idbi': ('IDBI Bank Acct XX{account} debited with Rs {amount} on 29-Oct-2026 at {merchant}. Ref {ref}. Available Bal Rs 42,875.25.',
         'IDBI Bank Acct XX{account} credited with Rs {amount} on 29-Oct-2026 from {merchant}. Ref {ref}. Available Bal Rs 48,875.25.'),
'dbs': ('Your DBS Bank Acct XX{account} debited with INR {amount} to {merchant} on 29-Oct-2026. Ref {ref}. Avl Bal INR 42,875.25.',
        'Your DBS Bank Acct XX{account} credited with INR {amount} from {merchant} on 29-Oct-2026. Ref {ref}. Avl Bal INR 48,875.25.'),
'bandhan': ('Bandhan Bank: A/c XX{account} debited INR {amount} towards {merchant} on 29-Oct-2026. UPI Ref {ref}. Avl Bal INR 42,875.25.',
            'Bandhan Bank: A/c XX{account} credited INR {amount} from {merchant} on 29-Oct-2026. UPI Ref {ref}. Avl Bal INR 48,875.25.'),
'dhanlaxmi': ('INR {amount} is debited from A/c XXXX{account} on 29-OCT-2026 to {merchant}. UPI Ref {ref}. Bal INR 42,875.25 -Dhanlaxmi Bank',
              'INR {amount} is credited to A/c XXXX{account} on 29-OCT-2026 from {merchant}. UPI Ref {ref}. Bal INR 48,875.25 -Dhanlaxmi Bank'),
'neft': ('Standard Chartered: INR {amount} debited from your account 123xxxx{account} to {merchant} on 29/10/2026. UTR {ref}. Available Balance INR 42,875.25.',
         'Standard Chartered: NEFT credit of INR {amount} in your account 123xxxx{account} from {merchant} on 29/10/2026. UTR {ref}. Available Balance INR 48,875.25.'),
'au': ('AU Bank: A/c XX{account} Dr INR {amount} on 29-Oct-2026 to {merchant}. UPI Ref {ref}. Bal INR 42,875.25.',
       'AU Bank: A/c XX{account} Cr INR {amount} on 29-Oct-2026 from {merchant}. UPI Ref {ref}. Bal INR 48,875.25.'),
'equitas': ('Equitas: INR {amount} debited from A/c XX{account} to {merchant} on 29-Oct-2026. Ref {ref}. Bal INR 42,875.25.',
            'Equitas: INR {amount} credited to A/c XX{account} from {merchant} on 29-Oct-2026. Ref {ref}. Bal INR 48,875.25.'),
'airtel': ('Rs. {amount} debited from Airtel Payments Bank a/c to {merchant}. Txn ID {ref}. Bal Rs.42,875.25.',
           'Airtel Payments Bank a/c is credited with Rs.{amount} from {merchant}. Txn ID: {ref}. Bal Rs.48,875.25.'),
'psb': ('A/c No **{account} Debited with Rs {amount}--{merchant} Ref {ref} (CLR BAL 42875.25CR)(29-10-2026 12:00:00)-Punjab&Sind Bank',
        'A/c No **{account} Credited with Rs {amount}--{merchant} Ref {ref} (CLR BAL 48875.25CR)(29-10-2026 12:00:00)-Punjab&Sind Bank'),
'greater': ('Your Account XXXX{account} had a DEBIT transaction of RS. {amount} on 29-10-2026 at {merchant}. Ref {ref}. Available balance Rs.42,875.25 -GREATER BANK',
            'Your Account XXXX{account} had a CREDIT transaction of RS. {amount} on 29-10-2026 from {merchant}. Ref {ref}. Available balance Rs.48,875.25 -GREATER BANK'),
}
NEGATIVES = [
'{bank}: OTP 246810 to authorize INR {amount} on A/c XX{account}. Ref {ref}.',
'{bank}: INR {amount} payment failed. A/c XX{account} not debited. Ref {ref}.',
'{bank}: INR {amount} will be debited from A/c XX{account} tomorrow. Ref {ref}.',
'{bank}: Available balance INR {amount} in A/c XX{account}. Ref {ref}.',
]

def build():
    base = [r for r in json.loads((HERE/'corpus.json').read_text()) if not r.get('bank')]
    fixtures, catalog = [], []
    for i,(bank,parser,style) in enumerate(BANKS):
        slug = bank.lower().replace(' & ', '-').replace(' ', '-').replace('&','')
        source = SOURCE+parser+'Parser.ts' if parser else None
        basis = 'public-parser-grammar' if parser else 'generic-synthetic-only'
        catalog.append(dict(bank=bank, basis=basis, source=source, style=style))
        for direction,template in enumerate(TEMPLATES[style]):
            for variant in range(4):
                values=dict(bank=bank, account='731' if style=='canara' else '6428',amount=['1,250.25','725.50','12,000.00','89.25'][variant],ref=str(920000000000+i*100+direction*10+variant),merchant=['Demo Bazaar','Sample Cafe','Test Employer','Mock Books'][variant])
                base.append(dict(split='train',bank=bank,family=f'bank-{style}-{direction}',label=1,text=template.format(**values)))
            values=dict(bank=bank,account='730' if style=='canara' else '4920',amount='10,000.00' if style=='canara' else '1,234.50',ref=str(930000000000+i*100+direction),merchant='DEMO MERCHANT')
            expected=dict(bookable=True,amount_paise=1000000 if style=='canara' else 123450,type='EXPENSE' if direction==0 else 'INCOME',account_suffix=None if style=='airtel' else values['account'],reference=values['ref'])
            if style in ('canara','beneficiary','boi','sbi','pnb','bob','hdfc','axis','kotak','rrn','federal','idbi','dbs','dhanlaxmi','neft','au','equitas','greater','card'):
                expected['merchant']='DEMO MERCHANT'
            fixtures.append(dict(id=f'{slug}-{direction}',bank=bank,basis=basis,source=source,kind='debit' if direction==0 else 'credit',body=template.format(**values),expected=expected))
        for j,template in enumerate(NEGATIVES):
            for variant in range(2):
                values=dict(bank=bank,account='6428',amount=['725.50','12,000.00'][variant],ref=str(920000000000+i*100+j*10+variant))
                base.append(dict(split='train',bank=bank,family=f'bank-negative-{j}',label=0,text=template.format(**values)))
            values=dict(bank=bank,account='4920',amount='1,234.50',ref=str(940000000000+i*100+j))
            fixtures.append(dict(id=f'{slug}-negative-{j}',bank=bank,basis=basis,source=source,kind=['otp','failed','future','balance'][j],body=template.format(**values),expected=dict(bookable=False)))
    (HERE/'corpus.json').write_text(json.dumps(base,indent=2)+'\n')
    (HERE/'bank-fixtures.json').write_text(json.dumps(dict(description='Synthetic regression mocks, not a held-out accuracy benchmark or official bank templates.',banks=catalog,fixtures=fixtures),indent=2)+'\n')
    out=['# Indian bank mock-message catalogue','',f'{len(BANKS)} banks; {len(fixtures)} synthetic cases. Each bank has debit, credit, OTP, failure, future-payment and balance-only cases.','',
         'These are independently authored mocks informed by public parser grammar, not messages supplied or certified by the banks. Banks marked **generic only** have no verified bank-specific template in this collection. Passing a mock does not establish support for all live templates. Regional, foreign-currency and other banks are not exhaustive.','',
         'Paste one alert at a time into **Settings → SMS & Gmail import**. Expected amounts exclude the balance. Change the reference before repeating a test. Never call or text a number in a mock; placeholder contact numbers are deliberately invalid.','',
         'Machine-readable expected results and all rejection cases: [bank-fixtures.json](bank-fixtures.json). Regenerate with `python3 tools/message_model/build_bank_corpus.py`, then retrain with `python3 tools/message_model/train.py`. The model evaluation keeps its original held-out examples separate; bank cases below are regression checks, not held-out model accuracy.','']
    for bank in catalog:
        out += ['## '+bank['bank'],'',('Public grammar reference: ['+bank['bank']+']('+bank['source']+').' if bank['source'] else '**Generic only:** synthetic common grammar; needs a consented bank-specific example.'),'']
        for row in [r for r in fixtures if r['bank']==bank['bank'] and r['expected']['bookable']]:
            e=row['expected'];out += [('Debit' if e['type']=='EXPENSE' else 'Credit')+f" — INR {e['amount_paise']/100:.2f}; account suffix {e['account_suffix'] or 'unknown; reference provided'}.",'','```text',row['body'],'```','']
    out += ['## Sources and provenance','',f'Grammar research: [BankSMSParser at {REV}]({SOURCE.rsplit("/src/",1)[0]}), by rahatsayyed and contributors, based on PennyWise parser-core. The upstream source is AGPL-3.0; no upstream source code or full fixture body is copied into this catalogue. Message templates here are authored for Paisa+ with synthetic names, identifiers, values and dates. Some grammar families and bank names are shared.','',
            'Canara formatting was also checked against the [public Canara demonstration](https://pennywise.zynth.dev/banks/canara-bank). The user-supplied Canara example informed the three-digit suffix and footer edge cases; its personal name, reference and contact number were replaced in repository fixtures.','']
    (HERE/'bank-mock-messages.md').write_text('\n'.join(out))
    print(f'{len(BANKS)} banks, {len(fixtures)} fixtures, {sum(r["split"]=="train" for r in base)} training rows')

if __name__=='__main__':build()
