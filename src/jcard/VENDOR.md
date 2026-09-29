# Bundled J-Card Template — provenance and licence

`src/jcard/` contains a **vendored copy** of the open-source
[J-Card Template](https://github.com/ed7n/jcard-template) web app. CassetteFlow serves it
from its own HTTP server at `/jcard` so both the desktop application and the web library
app can open a pre-filled card without depending on a third-party site.

## Upstream

| | |
|---|---|
| Repository | https://github.com/ed7n/jcard-template |
| Branch / ref | `master` |
| Commit | `ffa66b7` (tagged "Update 2 Revision 4", 2023-04-04) |
| Deployed site | https://ed7n.github.io/jcard-template/ |
| Vendored on | see the timestamp of this file in `git log` |

## What is ours and what is upstream

- **Upstream, verbatim:** everything under `src/jcard/res/`, plus `LICENSE` and
  `src/jcard/index.html` (with the three transforms below applied) and
  `res/styles/LICENSE-eden-online.txt`.
- **CassetteFlow's own files (not upstream, safe to edit):** `cf-bridge.js` and this
  `VENDOR.md`. Re-running the vendor script never touches them.

The only file that differs from upstream is `index.html`, and only in these three ways:

1. `https://ed7n.github.io/res/styles/…` → `res/styles/…` on the six portal stylesheets,
   so the page renders identically offline.
2. The **Switch Interface** section (a link to the sibling `1.html` variant, which is not
   bundled) was removed.
3. The **Offline Access / PWA** section was removed — no service worker and no manifest,
   so the app never caches itself into a phone home screen.

Upstream's credit and source links are left untouched, as the licences require.

Upstream files **not** bundled: `1.html` (the alternate interface), `pwa-assets.js`,
`pwa.*`, and the NET-Installer snippets.

## Licence

The vendored material carries two different licences — this matters if you redistribute
CassetteFlow:

- **BSD 2-Clause** — the J-Card Template application (`index.html`, `res/scripts/**`,
  `res/styles/{base,layout,application,input,form,nav,jcard,view,view-1}.css`).
  © 2017–2018 Blaine Murphy, © 2019–2023 Brendon Irwan / ED7 ENTERTAINMENT.
  Full text: `src/jcard/LICENSE`.
- **CC BY-SA 4.0** (EDEN Online Licence) — the six *portal* stylesheets that come from
  https://ed7n.github.io/res/styles/ and were copied in for offline use. Full notice:
  `src/jcard/res/styles/LICENSE-eden-online.txt`. CC BY-SA is a share-alike licence: these
  files stay under it, and attribution is preserved. If you would rather not ship
  share-alike content, re-vendor with `tools/fetch-jcard.ps1 -SkipSiteStyles` — the page
  then still works but pulls its portal styles from ed7n.github.io at runtime.
- **Freeware font** — `res/fonts/alte-haas-grotesk{,-bold}.ttf`, redistributable only
  together with `res/fonts/LICENSE`. Do not delete that file.

## How the card data gets in

Upstream accepts no card data via URL (its query parameters are presence-only flags such
as `?dark`). CassetteFlow therefore serves the page with a small script injected before
`</body>`:

- `window.CF_JCARD_DATA = { … }` when the page was requested as `/jcard?tape=0042`
  (optionally `&combine=1`, `&title=…`, `&artist=…`).
- `<script src="/jcard/cf-bridge.js">` always.

`cf-bridge.js` either decodes a `?data=<base64url json>` payload or uses the injected
object, then feeds it to the app through the app's own `#input-load-file` control so the
normal load path runs (validation, display refresh, download name). If the browser refuses
to construct a `File`/`DataTransfer`, it falls back to importing
`res/scripts/application-functions.mjs` and calling `populateDataSaves()` directly.

The JSON shape is CassetteFlow's `.jcard.json` (the same file the desktop dialog writes),
built by `CassetteFlow.buildJCardData(…)` so the desktop, `/api/jcard` and the web app can
never drift apart.

## Re-syncing with upstream

```powershell
pwsh -File tools/fetch-jcard.ps1                     # latest master, keep portal styles
pwsh -File tools/fetch-jcard.ps1 -SkipSiteStyles     # drop the CC BY-SA stylesheets
pwsh -File tools/fetch-jcard.ps1 -Ref <commit>       # pin a different revision
```

The script downloads `res/**`, `LICENSE` and the deployed `index.html`, applies the three
transforms, and **fails loudly** if a transform stops matching (which is what you want: it
means upstream changed shape and the transforms need reviewing). Diff the result before
committing, and update the commit hash in the table above.
