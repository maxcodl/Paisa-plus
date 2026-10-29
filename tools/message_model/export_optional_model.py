#!/usr/bin/env python3
"""Export the pinned GPT-2 model as a verified optional ONNX INT4 pack."""
import argparse,hashlib,json,math,shutil,zipfile
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import QuantFormat
from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer,DefaultWeightOnlyQuantConfig
import torch
from transformers import AutoModelForCausalLM,AutoTokenizer

MODEL='rawsun00001/banking-sms-json-parser-v8'
REV='97ecb964b239469691549bef0647c5f2e957cdb6'
PACK_ID='banking-v8-int4-v1'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

# Explicit inference graph avoids transformer-library attention-mask export machinery.
# This matches GPT-2's eager, unpadded, single-sequence inference without a KV cache.
class LastLogits(torch.nn.Module):
 def __init__(self,model):super().__init__();self.t=model.transformer
 def forward(self,ids):
  t=self.t;n=ids.shape[1]
  x=t.wte(ids)+t.wpe(torch.arange(n,device=ids.device))
  for block in t.h:
   y=block.ln_1(x);q,k,v=block.attn.c_attn(y).chunk(3,dim=-1)
   def heads(z):return z.reshape(1,n,12,64).transpose(1,2)
   q,k,v=map(heads,(q,k,v))
   scores=(q@k.transpose(-1,-2))/8.0
   mask=torch.ones((n,n),dtype=torch.bool,device=ids.device).tril()
   scores=scores.masked_fill(~mask,-1e4)
   y=(torch.softmax(scores,dim=-1)@v).transpose(1,2).reshape(1,n,768)
   x=x+block.attn.c_proj(y)
   y=block.mlp.c_fc(block.ln_2(x))
   y=0.5*y*(1+torch.tanh(math.sqrt(2/math.pi)*(y+0.044715*y.pow(3))))
   x=x+block.mlp.c_proj(y)
  return torch.nn.functional.linear(t.ln_f(x[:,-1,:]),t.wte.weight)

def main():
 p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=Path('build/optional-model'));args=p.parse_args()
 out=args.out.resolve();out.mkdir(parents=True,exist_ok=True);pack=out/'pack';pack.mkdir(exist_ok=True)
 torch.set_num_threads(4)
 tokenizer=AutoTokenizer.from_pretrained(MODEL,revision=REV,trust_remote_code=False)
 model=AutoModelForCausalLM.from_pretrained(MODEL,revision=REV,trust_remote_code=False,torch_dtype=torch.float32).eval()
 graph=LastLogits(model).eval();ids=tokenizer('INR 125 debited from account XX4920. Ref 930000000000.|',return_tensors='pt').input_ids
 with torch.inference_mode():
  np.testing.assert_allclose(graph(ids).numpy(),model(ids,use_cache=False).logits[:,-1,:].numpy(),rtol=2e-3,atol=2e-3)
  torch.onnx.export(graph,(ids,),str(out/'fp32.onnx'),input_names=['input_ids'],output_names=['logits'],dynamic_axes={'input_ids':{1:'sequence'}},opset_version=21,dynamo=False,external_data=False)
 # GPT-2 Conv1D exports as Gemm. Rewrite these constant-weight affine nodes so
 # weight-only INT4 quantization covers the transformer, not just the embeddings/head.
 exported=onnx.load(str(out/'fp32.onnx'));nodes=[]
 for node in exported.graph.node:
  if node.op_type!='Gemm':nodes.append(node);continue
  attrs={a.name:onnx.helper.get_attribute_value(a) for a in node.attribute}
  assert attrs.get('alpha',1.0)==1.0 and attrs.get('beta',1.0)==1.0 and not attrs.get('transA',0) and not attrs.get('transB',0)
  middle=node.output[0]+'_matmul'
  nodes.append(onnx.helper.make_node('MatMul',list(node.input[:2]),[middle],name=node.name+'_matmul'))
  nodes.append(onnx.helper.make_node('Add',[middle,node.input[2]],list(node.output),name=node.name+'_bias'))
 del exported.graph.node[:];exported.graph.node.extend(nodes)
 onnx.save(exported,str(out/'fp32-matmul.onnx'))
 import logging
 logging.getLogger().setLevel(logging.WARNING)
 config=DefaultWeightOnlyQuantConfig(block_size=128,is_symmetric=True,accuracy_level=4,quant_format=QuantFormat.QOperator,op_types_to_quantize=('MatMul','Gather'),quant_axes=(('MatMul',0),('Gather',1)))
 quant=MatMulNBitsQuantizer(str(out/'fp32-matmul.onnx'),algo_config=config);quant.process();quant.model.save_model_to_file(str(pack/'model.onnx'),use_external_data_format=False)
 tokenizer.save_pretrained(out/'tokenizer')
 for name in ['vocab.json','merges.txt']:shutil.copy2(out/'tokenizer'/name,pack/name)
 (pack/'MODEL_CARD.md').write_text(f'# Optional banking SMS model\n\nSource: https://huggingface.co/{MODEL}\nRevision: {REV}\nLicense: MIT, as declared in the upstream model card. Author: rawsun00001.\n\nConverted for Paisa+ to ONNX INT4. This is an experimental text generator with observed amount, direction, date, OTP and JSON errors. Generated output is a draft and never changes account balances automatically.\n')
 session=ort.InferenceSession(str(pack/'model.onnx'),providers=['CPUExecutionProvider'])
 checks=[]
 for message in ['INR 125 debited from account XX4920. Ref 930000000000.|','OTP 246810 for INR 1500. Do not share.|']:
  x=tokenizer(message,return_tensors='np')['input_ids'].astype(np.int64)
  y=session.run(None,{'input_ids':x})[0]
  assert y.shape==(1,50257) and np.isfinite(y).all()
  checks.append(dict(input=message,first_token=int(np.argmax(y))))
 files={f.name:dict(bytes=f.stat().st_size,sha256=sha(f)) for f in sorted(pack.iterdir()) if f.name != "manifest.json"}
 manifest=dict(formatVersion=1,id=PACK_ID,name='Banking SMS V8 · INT4 (experimental)',sourceModel=MODEL,sourceRevision=REV,runtime='onnx-gpt2-bpe-v1',contextTokens=384,maxNewTokens=96,files=files)
 (pack/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 archive=out/(PACK_ID+'.zip')
 with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for f in sorted(pack.iterdir()):z.write(f,f.name)
 descriptor=dict(**{k:v for k,v in manifest.items() if k!='files'},downloadBytes=archive.stat().st_size,installedBytes=sum(f.stat().st_size for f in pack.iterdir()),sha256=sha(archive),url=f'https://github.com/maxcodl/Paisa-plus/releases/download/models-v1/{archive.name}')
 (out/'model-catalog.json').write_text(json.dumps(dict(formatVersion=1,models=[descriptor]),indent=2)+'\n')
 (out/'export-checks.json').write_text(json.dumps(checks,indent=2)+'\n')
 print(json.dumps(descriptor,indent=2))

if __name__=='__main__':main()
