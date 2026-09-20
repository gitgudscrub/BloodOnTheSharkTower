# A.11 — Player Personal Grim: Drunk / Marionette Believed Roles

Extends the Drunk/Marionette secondary role-box system to ordinary players' private Grimoire notes.

- If a player privately assigns `Drunk` or `Marionette` to another player, the second `?` role box appears beside that token.
- Clicking the second box lets the player privately note what role they think that Drunk/Marionette believes they are.
- Drunk believed roles are limited to Townsfolk.
- Marionette believed roles are limited to Townsfolk/Outsiders.
- These notes are completely client-local and are never sent to the server or Storyteller.
- Changing the guessed true role away from Drunk/Marionette clears the local believed-role note.
- The optional world-role icons use the same local notebook state, so a player can also see their own Drunk/Marionette + believed-role pair above that player's head.
- Final reveal suppresses perceived-role notes and shows the revealed true role normally.

This patch also preserves the Storyteller `Game End` button below `Send Home` in AssignRolesScreen.
