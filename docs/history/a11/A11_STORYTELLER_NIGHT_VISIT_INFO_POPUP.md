# A.11 — Storyteller Night Visit Info Popup

Adds an original-BOTB-style information card for the Storyteller whenever a
role visit is activated from the Night Order bar.

The card shows:
- role token and role name
- player name
- the first-night / other-night Storyteller instruction from the role data
- automatically calculated objective information where Sharktower can derive it
  unambiguously from the authoritative Grim
- Drunk YES/NO
- Poisoned YES/NO

Automatic true-info helpers currently include:
- Chef: evil neighbour pairs
- Empath: living evil neighbours
- Fortune Teller: marked Red Herring
- Grandmother: marked Grandchild + role
- Clockmaker: Demon-to-nearest-Minion distance
- Shugenja: closest evil direction
- Oracle: number of dead evil players

For triggered visits, the panel also identifies the source player when the
trigger targets somebody else (for example Barber/Hatter/Poppy Grower visits).

Important: the calculated line is the sober/healthy truth. If the status line
shows DRUNK or POISONED, the Storyteller still applies the relevant BOTC ruling
and should not blindly give the displayed true value.

The card is Storyteller-only and uses the Grimoire state already synchronised to
the Storyteller client. No extra hidden role information is sent to players.
