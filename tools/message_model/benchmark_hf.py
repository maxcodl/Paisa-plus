import json,time,os,argparse,re
from pathlib import Path
from decimal import Decimal
os.environ['HF_HUB_DISABLE_TELEMETRY']='1'
import torch
from transformers import AutoTokenizer,AutoModelForCausalLM,StoppingCriteria,StoppingCriteriaList
HERE = Path(__file__).resolve().parent
options = argparse.ArgumentParser()
options.add_argument('--output', type=Path, default=HERE/'hf-benchmark-results.json')
args = options.parse_args()
MODEL='rawsun00001/banking-sms-json-parser-v8';REV='97ecb964b239469691549bef0647c5f2e957cdb6'
torch.set_num_threads(4)
tokenizer=AutoTokenizer.from_pretrained(MODEL,revision=REV,trust_remote_code=False,local_files_only=False)
model=AutoModelForCausalLM.from_pretrained(MODEL,revision=REV,trust_remote_code=False,torch_dtype=torch.float32,local_files_only=False).eval()
class EndObject(StoppingCriteria):
 def __init__(self,start):self.start=start
 def __call__(self,input_ids,scores,**kwargs):
  text=tokenizer.decode(input_ids[0,self.start:],skip_special_tokens=True)
  # Stop at the first structurally complete object, without repairing JSON numbers or values.
  start=text.find('{')
  if start<0:return False
  depth=0;quoted=False;escape=False
  for c in text[start:]:
   if escape:escape=False;continue
   if c=='\\' and quoted:escape=True;continue
   if c=='"':quoted=not quoted
   if not quoted:
    if c=='{':depth+=1
    elif c=='}':
     depth-=1
     if depth==0:return True
  return False

def generate(prompt):
 inputs=tokenizer(prompt,return_tensors='pt');start=inputs.input_ids.shape[1];t=time.perf_counter()
 with torch.inference_mode():out=model.generate(**inputs,max_new_tokens=180,do_sample=False,pad_token_id=tokenizer.eos_token_id,stopping_criteria=StoppingCriteriaList([EndObject(start)]))
 text=tokenizer.decode(out[0,start:],skip_special_tokens=True)
 try:obj,_=json.JSONDecoder().raw_decode(text[text.index('{'):])
 except (ValueError,json.JSONDecodeError):obj=None
 return text,obj,round(time.perf_counter()-t,3)
fixtures=json.load((HERE/'bank-fixtures.json').open())['fixtures']
# The model card omits its prompt. Probe a pipe suggested by the raw model continuation.
canara=next(x for x in fixtures if x['id']=='canara-bank-0')
for suffix in ['|','\n|']:
 text,obj,seconds=generate(canara['body']+suffix)
 print(json.dumps({'probe_suffix':repr(suffix),'completion':text,'parsed':obj,'seconds':seconds}),flush=True)
selected=[]
for name in ['Canara Bank','HDFC Bank','State Bank of India','ICICI Bank','Kotak Mahindra Bank','Bank of Baroda']:
 positives=[r for r in fixtures if r['bank']==name and r['expected']['bookable']]
 negatives=[r for r in fixtures if r['bank']==name and not r['expected']['bookable']]
 selected+=positives+negatives[:2]
# Check punctuation sensitivity, rather than only messages with grouped monetary amounts.
for row in list(selected):
 if row['expected']['bookable']:
  other = dict(row)
  other['id'] += '-ungrouped'
  other['body'] = re.sub(r'(?<=\d),(?=\d)', '', row['body'])
  selected.append(other)
selected += [
 dict(id='author-widget-debit',body='Sent Rs.1500.00 from HDFC Bank AC XX1234 to john@okicici on 15-Aug-25.UPI Ref 123456789012.', expected=dict(bookable=True,type='EXPENSE',amount_paise=150000,account_suffix='1234',date='2025-08-15')),
 dict(id='author-widget-credit',body='Rs.25000 credited to your SBI Bank a/c XX5678 via NEFT from beneficiary COMPANY LTD.', expected=dict(bookable=True,type='INCOME',amount_paise=2500000,account_suffix='5678',date=None)),
]
results=[]
for r in selected:
 raw,obj,seconds=generate(r['body']+'|');e=r['expected'];checks={}
 checks['valid_json']=isinstance(obj,dict)
 if isinstance(obj,dict):
  checks['transaction']=obj.get('is_transaction')==e['bookable']
  if e['bookable']:
   checks['direction']=str(obj.get('type','')).lower()==('debit' if e['type']=='EXPENSE' else 'credit')
   try:checks['amount']=Decimal(str(obj.get('amount')))*100==e['amount_paise']
   except:checks['amount']=False
   checks['account']=str(obj.get('last4'))==str(e['account_suffix'])
   if e.get('date', '2026-10-29') is not None: checks['date']=obj.get('date')==e.get('date', '2026-10-29')
 result=dict(id=r['id'],input=r['body'],expected=e,completion=raw,parsed=obj,checks=checks,seconds=seconds)
 results.append(result);print(json.dumps(result),flush=True)
 report=dict(model=MODEL,revision=REV,runtime='CPU float32, 4 threads; not Android latency',prompt='sms + |',max_new_tokens=180,decoding='greedy; stop at first balanced object; no JSON repair',cases=len(results),valid_json=sum(x['checks']['valid_json'] for x in results),all_checked_fields_correct=sum(all(x['checks'].values()) for x in results),results=results)
 args.output.open('w').write(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='results'}),flush=True)
