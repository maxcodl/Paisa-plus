# Optional downloadable model

The one-time Auto startup screen offers Download model, Import ZIP and Skip for now. Dismissing it is remembered on this device; all model controls remain available in Settings.

The normal app includes the small built-in classifier and bank parser. The larger generative banking model is a separate optional download. It is **experimental**: see [the evaluation](hf-model-assessment.md) for incorrect amounts, dates, transaction directions and nontransaction detection.

Settings → Backup & data → SMS & Gmail import → Optional AI model offers Download model, Import ZIP, Delete model and a local draft test. Installing does not switch the ledger parser or enable unattended transaction posting. The generator only displays unverified text and has no repository/database write path.

## Hosting

The immutable `models-v1` GitHub release contains:

- `banking-v8-int4-v1.zip`: ONNX INT4 graph, GPT-2 vocabulary/merges, source attribution/model card, and a manifest with individual file sizes and SHA-256 hashes.
- `model-catalog.json`: model ID, exact archive size/hash, installed size and release URL.
- Release APKs per architecture (ARM64, ARMv7, x86-64) for testing this flow.

The catalogue is also pinned in `app/src/main/assets/model-catalog.json`. The client only accepts this exact archive checksum and ID, including when importing a ZIP. A future model update needs a versioned pack and an updated app catalogue. No executable code is downloaded.

Release downloads are anonymous. While the GitHub repository is private, the app reports that the asset is private/unavailable. Download the ZIP through an authenticated browser and use Import ZIP instead. The same Download button works when the repository becomes public; no GitHub token is embedded in the app.

## Export

Create an isolated Python environment with CPU PyTorch, transformers 4.54.0, onnx, onnxruntime 1.24.4, and onnxscript. Run:

```sh
python tools/message_model/export_optional_model.py
```

The script pins the upstream model revision, verifies its explicit GPT-2 forward pass against Transformers, exports a single-sequence last-token-logits graph, rewrites constant Gemm affine nodes as MatMul/Add, and quantizes constant MatMul and embedding Gather weights to symmetric INT4 blocks of 128. It validates finite output and vocabulary size with CPU ONNX Runtime. It writes the pack/catalogue to `build/optional-model/`, outside source control. Model weights are not included in the APK or Git history.

The exported model uses full-prefix inference without KV caching, limited to 384 context tokens, 96 generated tokens and a 60-second generation budget. Input is not truncated silently. Native inference runs off the main thread with two threads, closes tensors/sessions on completion, and checks cancellation between generation steps. The tokenizer uses original GPT-2 byte BPE. This first version prioritizes compatibility and bounded generation over maximum speed.

The ONNX runtime library is part of the app even when the model is absent; it adds native code to the APK. The model download itself does not grow the APK. Storage and RAM use are different; native inference needs more RAM than the model file size alone.

## Download/install behavior

Only a user action starts the WorkManager job. It waits for connectivity, reports progress, permits cancellation, and survives leaving the screen. An interrupted job can be retried; this version restarts downloads rather than resuming partial ranges. HTTPS downloads allow only GitHub release hosts and cap the byte count to the pinned archive size. No SMS/email text is passed to the downloader.

Installation checks free space and the archive hash, rejects nested/unexpected/duplicate entries, bounds expanded sizes, validates each file hash, and opens the ONNX graph before promoting the staging directory. Existing installations remain until a new pack is valid. Temporary archives/staging are cleaned on completion or error. A directory rename promotes a verified pack. Model data is stored in `noBackupFilesDir`, independent from user financial backups. Deleting it restores the disk space without touching transactions.

The download requires roughly 56 MB of network data and the installed pack about 66 MB of disk space. Installation also needs temporary disk space for archive + staging. Exact sizes are listed in the pinned catalogue.
