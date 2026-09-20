# 0.9.1 — Base 3 Scripts

Adds a dedicated Base 3 picker to the Script Builder.

Bundled scripts:
- Trouble Brewing — velvet/dark red label — 22 roles
- Bad Moon Rising — orange/gold label — 25 roles
- Sects & Violets — purple label — 25 roles

The Base 3 JSON files live separately from custom scripts at:
`src/main/resources/data/blood_on_the_sharktower/scripts/base3/`

The client sends only the Base 3 key. The server loads the bundled JSON and
broadcasts the resulting Script through the existing full JSON/GZIP script sync.
This means Base 3 scripts work immediately with the Role Bag, Grimoire, role
assignment, and SEND ROLES workflow.
