# Manual signed releases

The **Manual release build** workflow runs only through **Actions → Manual release build → Run workflow** (or `gh workflow run release.yml`). Pushes, tags, and pull requests do not start builds.

## Use a local keystore

Copy `keystore.properties.example` to `keystore.properties` in the repository root and fill in:

```properties
storeFile=release.jks
storePassword=your-store-password
keyAlias=your-existing-alias
keyPassword=your-key-password
```

Put `release.jks` in the repository root, or point `storeFile` at any existing JKS/PKCS12 keystore. Relative paths resolve from the repository root; absolute paths work too. Reuse the existing signing key and alias when updating an already distributed app. Keystore files and `keystore.properties` are ignored by Git.

```sh
bash gradlew assembleRelease -PrequireReleaseSigning=true
bash gradlew bundleRelease -PbundleBuild=true -PrequireReleaseSigning=true
```

Alternatively, provide the existing key directly by path, without a properties file:

```sh
export RELEASE_STORE_FILE=/absolute/path/to/existing-key.jks
export RELEASE_STORE_PASSWORD='your-store-password'
export RELEASE_KEY_ALIAS='your-existing-alias'
export RELEASE_KEY_PASSWORD='your-key-password'
bash gradlew assembleRelease -PrequireReleaseSigning=true
bash gradlew bundleRelease -PbundleBuild=true -PrequireReleaseSigning=true
```

Environment variables take priority over Gradle properties (`-PstoreFile=...`, etc.), then `keystore.properties`. All four values are required if any are supplied. Empty credentials or missing files fail with a configuration error. `-PrequireReleaseSigning=true` also fails when no release credentials are provided. Without that flag or any credentials, local builds retain the existing debug-key sideload fallback; the workflow always requires release signing.

Earlier preview APKs used the development debug certificate. A release signed with a different key cannot update those APKs in place; export a backup before uninstalling to switch keys. Continue using the same release key for future updates.

## Use the same existing key in GitHub Actions

In **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 contents of your existing `.jks` or `.keystore` file |
| `RELEASE_STORE_PASSWORD` | Keystore password (`storePassword`) |
| `RELEASE_KEY_ALIAS` | Existing alias (`keyAlias`) |
| `RELEASE_KEY_PASSWORD` | Key password (`keyPassword`) |

On Linux, generate the base64 file locally:

```sh
base64 -w 0 /path/to/existing-key.jks > /tmp/paisa-keystore.base64
```

Paste that file's contents into `RELEASE_KEYSTORE_BASE64`, then delete the temporary base64 file. Do not commit either the keystore or its base64 representation. The workflow restores the exact existing key to a private runner temporary file and deletes it at the end. It does not generate a new signing identity.

Run the workflow on the desired branch/tag. Leave **release_tag** empty to build downloadable workflow artifacts only. To publish, enter a **new** tag such as `v0.9.0`; update `versionCode` and `versionName` in `app/build.gradle.kts` beforehand. The tag does not change the app version. The optional **prerelease** switch defaults to true. Existing tags are rejected to preserve previously published builds.

The workflow uses JDK 21 and Android SDK 37, runs unit tests, then builds minified signed APKs for ARM64, ARMv7 and x86-64 plus an AAB. ABI APKs use `app/build/`; bundle builds disable APK splits and use isolated `build/app-bundle/` outputs so resource shrinking does not mix split APK resources into the bundle. Artifacts include SHA-256 checksums and both R8 mapping files, retained for 30 days. Optional GitHub releases include APKs, AAB and checksums. The optional ML model stays outside these builds.
