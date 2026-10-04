# Fresh physical notes — 1.0.2-alpha.6

The first successful Send Roles in fresh Setup replaces every carried book and quill (`WRITABLE_BOOK`) belonging to an assigned player with a new blank one in the same hotbar/main-inventory/offhand slot. Count is preserved; old pages and other item components are discarded. Players without a book are not given one. Signed written books and other items are preserved. The B notebook retains its existing full-reset lifecycle.

This happens once per reset generation. Sending Roles again during the same Setup, or changing/sending roles mid-game, does not erase notes again. Failed role validation never refreshes books. Assigned offline players are queued for refresh on their next full-state sync after joining; reset clears that queue. Replacement happens before the start snapshot is captured.

Live checks: write notes in hotbar, main-inventory and offhand books; Send Roles and verify fresh books remain in their slots. Include a signed book and unrelated items, which must survive. Write new notes and resend roles in Setup and later during Night; they must remain. Reset for the next game and Send Roles again; notes must clear. Verify an assigned reconnecting player and a player without a book.
