# Latest playtest fixes — 1.0.2-alpha.7

Role Bag uses all available columns and rows at a fixed readable token size. Script Builder, role assignment, bluff selection and the role catalog similarly calculate page capacity from effective GUI width/height. Role Bag's footer wraps on narrow screens. Changing GUI scale rebuilds the layout and clamps the current page.

Team-info previews open on the client thread. Poppy Grower identities withheld/released and Small Game: No Starting Info/ST Override are separate controls. Releasing Poppy Grower never lifts the below-seven-player restriction, and a small-game override never releases Poppy Grower identities. The exact private message preview and stale-plan/offline-recipient rejection remain.

Custom Scripts appears beneath Base 3 in Script Builder. Saved JSON files live in the server's `config/blood_on_the_sharktower/custom-scripts` folder and persist across resets/restarts. Select a file to load it into the builder; loading uses the same server script state as JSON upload. The library includes full custom definitions/metadata, with unsupported or malformed entries rejected rather than silently discarded.

Import from BotC Scripts supports HTTPS script pages (including explicit numeric versions and download links), `/api/script_ids/<id>/`, and `/api/scripts/<version-id>/` or `/json/` links. Page IDs resolve through the script-ID API to the selected/latest version; HTML is never scraped. Only botcscripts.com and www.botcscripts.com are allowed, including redirects. Downloads run off the game thread, use timeouts and a 1 MiB limit. Imported JSON is saved once, not fetched every game. Existing filenames or matching script names require Replace or Save Distinct Version. Replacement checks that the original file has not changed; distinct versions use exclusive creation. Pending imports are invalidated by reset, disconnect or loss of Storyteller control.

Simple Voice Chat's foreign-group nametag icon is suppressed during a synced Sharktower session, so it no longer advertises private-room membership. Speaking/mute/disconnect icons and the existing talking sidebar remain. This uses the pinned 26.3 SVC RenderEvents method; check the hook when upgrading SVC.

## Live retest

- Test GUI scales 1–4 and a narrow window: all buttons stay clickable and larger effective screens show more roles per page; toggle selections, resize on a later page, and distribute the same bag.
- Open Minion/Demon Info. Check the preview opens. In a small game with Poppy Grower, confirm only the appropriate independent override changes each restriction. Share and verify each player's private message.
- Put valid and malformed JSON files in the folder; refresh, select a valid file, edit its selection in Script Builder and Load Script. Full custom-role definitions must remain present.
- Import a page, an explicit version and a JSON API link; try duplicate Replace/Distinct/Cancel, unknown hosts, malformed JSON and a site failure. Restart/reset and verify saved files remain.
- Join different houses or private daytime rooms; outsiders must not see the group icon beside names. Confirm same-room audio, normal speaking indicators and the sidebar still work.
- During Setup, U and all hand prompts/icons remain off; start nominations and confirm speaking/voting hands return at the correct time.

Automated compilation/regressions and server startup do not replace graphical multiplayer/audio checks. BotC Scripts may reject requests with HTTP 403; the importer reports this and local JSON loading remains available.
