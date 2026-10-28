## Signing secrets

| Secret | Configured |
| --- | --- |
| `TOTAL_MUSIC_KEYSTORE_B64` / `KEYSTORE_BASE64` | **no** |
| `TOTAL_MUSIC_PASSWORD` / `KEYSTORE_PASSWORD` | **no** |

Missing: **TOTAL_MUSIC_KEYSTORE_B64 (or KEYSTORE_BASE64) TOTAL_MUSIC_PASSWORD (or KEYSTORE_PASSWORD)**.

Configure them at Settings → Secrets and variables → Actions →
New repository secret. The keystore is base64-encoded
(`base64 -w0 keystore.p12`); the password is the store password,
used for the key as well. Do not paste either into an issue, a
pull request, a commit or this conversation.
