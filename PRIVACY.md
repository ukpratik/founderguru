# Privacy policy

FounderGuru · applies to version 1.0.0 and later · updated 2026-11-07

**Short version:** FounderGuru has no server, no account, no ads, no analytics and no crash reporting. Nothing about you is sent to the developer. The app downloads public news feeds and images directly from the publishers, like a browser would.

## What leaves your phone
- **Feed requests.** To show stories, the app downloads the public RSS/Atom feed of each source that is switched on, directly from the publisher's server, plus `hnrss.org` (a third-party proxy that publishes the Show HN feed). The full list is in the [README](README.md#sources) and in the app under Settings › About › Sources. The request carries the app's name and version (`FounderGuru/1.0.0 (+https://github.com/ukpratik/founderguru)`) and standard caching headers. It carries no identifier, region, saved story or search.
- **Images.** Thumbnails are loaded from the image hosts named in the feeds (usually the publisher's own servers or their image CDN).
- **Opening a story.** The story opens in your browser or a Custom Tab, and from then on the publisher's own privacy policy applies.
- **Sharing.** Only when you tap Share: the headline, link and an app link go to the app you pick.
- **Feedback.** Only when you tap Send feedback, which opens the GitHub issue tracker in your browser. Copy diagnostics copies the app version, Android version, settings such as theme and digest, and the status of each source to your clipboard; it never includes your region, saved stories, URLs or searches, and nothing is sent unless you paste it somewhere.

Every server you connect to sees your IP address and the time of the request, as with any web request. FounderGuru does not control those servers.

## What stays on your phone
Downloaded stories (pruned after 14 days unless saved), your saved stories, your region, the stories you opened (to grey out visited headlines), the time of your last visit, and your settings. Searches are never stored. If Android backup is on, Android may copy the app's database and settings to your own Google account; the app itself never uploads anything. Uninstalling the app deletes everything.

## Permissions
- **Internet** and **network state**: to download feeds and to show the offline state.
- **Notifications** (Android 13+): asked for only when you choose a weekly or daily digest. Saying no keeps the digest off.
- **Run at startup** and **prevent sleep** (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`): used by Android's WorkManager to keep the background refresh and the digest scheduled. Background refresh can be turned off in Settings.

No location, contacts, storage, camera, microphone or advertising ID.

## Children
FounderGuru is a general news reader and collects no personal data from anyone, including children.

## Changes and contact
Changes to this policy are made in this file, in the public repository, and noted in the release changelog. Questions: open an issue at <https://github.com/ukpratik/founderguru/issues>.
