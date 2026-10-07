# Reminder picker

Generic reminders can be placed on any player. They have no source character.
The Script tab uses the current script's official reminder labels plus custom
characters' `reminders` and `remindersGlobal` fields. Duplicate labels from the
same character are listed once. The Placed tab can remove any existing reminder;
all three tabs have pages instead of limiting removal to six reminders.

Character identity and the affected target are distinct. Tinker Death is available
only on the Tinker, and the server enforces this restriction. Own-character status
markers such as Slayer No Ability are similarly filtered. Effects such as Poisoner
Poisoned, Monk Safe and Butler Master can be placed on other players. Custom
reminder labels default to allowing other targets unless they use a known
own-character status label; generic markers remain available for unusual cases.
Reminders are notes and do not change death or ability state.

Official labels are sourced from The Pandemonium Institute's character data:
https://github.com/ThePandemoniumInstitute/botc-release/blob/main/resources/data/roles.json
The bundled catalog contains reminder labels only. Target restrictions are maintained
in ReminderCatalog; new abilities may need additional target rules.
