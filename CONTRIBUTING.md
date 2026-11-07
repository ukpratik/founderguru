# Contributing to FounderGuru

Thanks for helping. Bug reports, source suggestions, accessibility feedback and pull requests are all welcome: open an issue at <https://github.com/ukpratik/founderguru/issues>. For anything larger than a small fix, please open an issue first so we can agree on the approach.

## Suggesting a source
Open an issue with the publisher's name, its RSS/Atom feed URL and the region. We add a source when it is mostly startup or VC news (or general enough that the startup keyword filter works for it), has a free feed over HTTPS with at least a title, link and date per item, publishes at least weekly, and serves its feed to apps (no Cloudflare challenge). Sources are added in releases, by editing `app/src/main/assets/sources.json` (bump `listVersion`, update `CatalogLoaderTest`, add a trimmed real fixture under `core/src/test/resources/feeds/`).

## Rules that keep the app private, small and maintainable
1. **No tracking, no backend.** No analytics, crash SDKs, ads or Google Play Services. The only network hosts are the feed hosts, `hnrss.org` and the image hosts in the feeds. `scripts/check-permissions.sh` and `scripts/check-no-google.sh` fail the build otherwise.
2. **Licences.** Dependencies must be FOSS and GPL-3.0-compatible (Apache-2.0, MIT, BSD, OFL). The AboutLibraries strict licence gate fails the release build otherwise. Explain each new dependency in the pull request.
3. **Architecture.** `:core` is pure Kotlin with no Android dependency (fetching, parsing, tagging, blending) and is tested on the JVM. `:app` is Compose and Material 3, with colours and type from the design tokens in `ui/theme`. Lens rules change only with the measurement rules in `product/06-build/build-plan.md` (labelled sets first, gate on both sets) and a `TAGGER_VERSION` bump.
4. **Tests with the code**, and `scripts/ci.sh` green before pushing; it is exactly what CI runs.
5. **Accessibility.** Every interactive element has a TalkBack label, touch targets are at least 48 dp, text meets 4.5:1 contrast in light and dark (`ContrastTest`), and screens work at 200% system text.
6. **Source headers.** Start each new source file with `// SPDX-License-Identifier: GPL-3.0-or-later`.
7. **Commits.** Conventional commits (`feat:`, `fix:`, `build:`, `docs:` …), small and focused.

By contributing you agree that your contribution is licensed under GPL-3.0-or-later.
