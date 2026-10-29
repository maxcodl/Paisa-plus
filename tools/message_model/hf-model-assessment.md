# banking-sms-json-parser-v8 assessment

Checked locally on 29 October 2026. Recommendation: retain it as a research baseline; do not use its generated amounts/directions to create ledger entries in this state. It is not bundled into Paisa+.

## Repository metadata

- [Model repository](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8/tree/main), pinned revision `97ecb964b239469691549bef0647c5f2e957cdb6`.
- [Model card](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8/blob/main/README.md) declares MIT licensing, English/Indian banking support, 60,000 training and 6,000 validation examples. These training/coverage claims are the author's; the repository does not publish the dataset, training procedure, numerical evaluation or working usage instructions.
- [Configuration](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8/blob/main/config.json): GPT2LMHeadModel, 6 layers, 768 embedding dimensions, 12 attention heads, 1,024-token context. Hugging Face metadata reports 81,912,576 parameters.
- [Model artifact](https://huggingface.co/rawsun00001/banking-sms-json-parser-v8/blob/main/model.safetensors): 163,832,712 bytes (163.8 MB / 156.2 MiB), F16 weights. Runtime activations, caches and tokenizer memory are additional. No Android/ONNX/TFLite export is supplied.

## Local benchmark

Model loading and inference ran on this workstation, with no SMS sent to a hosted inference service. Inputs were synthetic fixtures and the model card's two published examples, not the user's personal SMS. Runtime: PyTorch CPU float32, four threads; Transformers 4.54.0. These timings are not Android measurements.

The card's Usage section is empty and tokenizer configuration has no chat template. Initial probes tried raw SMS, `SMS: … JSON:`, `Input: … Output:` and a heading format. Raw completions suggested a pipe delimiter; the recorded benchmark uses `sms + "|"`, greedy decoding, at most 180 generated tokens, and stops at the first balanced object. Leading non-JSON text is skipped before JSON decoding; numeric values and JSON syntax are not repaired. This prompt is inferred, not author-confirmed.

[Machine-readable results](hf-benchmark-results.json) and [reproduction script](benchmark_hf.py) cover 38 cases: debit/credit/OTP/failure across six banks (24), ungrouped-number variants of the 12 positives, and the author's two model-card examples.

| Check | Result |
| --- | --- |
| Valid JSON | 24/38 |
| All checked fields correct | 1/38 |
| Original grouped-amount subset | 10/24 valid JSON, 0/24 fully correct |
| Ungrouped-amount subset | 12/12 valid JSON, 0/12 fully correct (dates also checked) |
| Author's model-card examples | 2/2 valid JSON, 1/2 fully correct |
| Median CPU generation time | About 0.56 seconds per case; excludes initial loading |

Specific failures:

- Canara `Dr. INR 10,000.00` produced `"amount":10,000.00`, invalid JSON, and date `2025-10-26` rather than `2026-10-29`.
- Canara `Cr.` was reported as debit, including in the ungrouped variant where JSON was valid.
- OTP `246810` became a transaction amount of `246810.50` and `is_transaction:true`.
- Failed payments were marked as transactions; some amounts lost leading digits.
- The author's salary-credit example says `Rs.25000`; the model generated `25000.79`.

This is a small adversarial synthetic smoke test, not a general accuracy estimate. All 12 ungrouped positives failed the complete check partly because of dates; that statistic alone is not an amount-only failure rate. A different, author-confirmed prompt could change performance. However, the observed monetary and OTP/failure mistakes are sufficient to keep generated financial fields out of the ledger.

## Possible use

The architecture is small enough to investigate quantization and on-device export, but the present weights need a documented prompt, correct JSON/decimal constraints, balanced negative training and independent bank/template holdouts first. Even then, deterministic amount/ownership validation, failure/OTP exclusions and review should remain. This model does not authenticate the SMS sender.

To reproduce in an isolated Python environment, install CPU PyTorch, `transformers==4.54.0` and safetensors, then run:

```sh
python tools/message_model/benchmark_hf.py
```

This downloads the pinned model/tokenizer for local inference and overwrites the benchmark results file. Model weights and Python dependencies are not app dependencies.
