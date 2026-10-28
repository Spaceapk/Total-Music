# 1.8.0 approved redesign — recovery checkpoint

Owner approved both concept boards on 2026-10-26. Implementation is authorized. Main integration and official publication are not authorized.

Remote main base: 3dee549dbcda996ac94865d7b999fdacb399ee4a. GitHub branch creation was denied with HTTP 403. The design branch exists only locally.

Shipped 1.7.0 source was recovered again from the release-attached source ZIP after the workspace reset. SHA-256: 589a6797d61c9b338d56b3ad8c3414a0a958555a2593e9e54d4809889c32d166. This patch includes the source recovery as well as the new work; apply to that exact main base.

The previous transient checkout was lost. Its earlier report of 1018 passed tests and one skipped test does NOT verify this re-created checkout. The re-created checkout has now been compiled and tested independently; see VALIDATION.md.

Re-created so far: independent editorial and gallery home/library presentations, collection pages, filtered search/selection, playlist actions, player presentations with existing session callbacks, queue, canvas artwork, own-cover mini-player, light/dark tokens, primary settings and Total EQ views, category-based advanced settings, custom song-action frames, and adaptive navigation with optional glass sampling. Original routes and multimedia services are retained.

Not finished: advanced compatibility routes, detailed visual parity, advanced settings/equalizer/player presentation parity, glass sampling validation, widgets/system-control review, actual screenshots and device performance. Do not mark this a complete UI rebirth or publish it. Four debug APKs were generated and their package, version and ZIP integrity were checked. No original-key-signed test delivery exists.

Infrastructure: use Java 21, Gradle 8.11.1, installed SDK 37.0 and required NDK. Java network configuration must use the current invocation's proxy. The Compose generated-resource ambiguity was avoided by compiling sharedUi non-incrementally; other modules remain incremental. Do not duplicate source directories or remove valid resource accessors to hide it.
