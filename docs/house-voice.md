# Physical house voice rooms — 1.0.2-alpha.5

Shared Night Chat is removed. Every seated player's configured house is its own hidden, isolated Simple Voice Chat room. Entering your own house moves you into that room; an ST inside the same house joins automatically. Leaving restores the normal outside route: proximity at Night, existing shared Day Chat during Day. House privacy continues through Dawn and nominations while a player is still inside. The morning phase change does not expose someone who has not left their house.

The current home-area detector remains the existing 8-block radius around `/bots setSeatHome <seat>`; no additional map markers are required. Ordinary players route only to their own house; STs select the closest configured occupied-seat house. Existing daytime private-area doorways and neighbour-whisper restrictions remain available. Physical houses take precedence over daytime areas; explicit manual ST private sessions take precedence over physical houses. Manual night sessions still end at Dawn, restoring the physical route. Night-order Visit and home teleport no longer send automatic private-chat invitations.

Voice reconnects reapply room routing, every server tick reconciles positions, and HUD labels show PRIVATE HOUSE CHAT. Full reset clears house membership even during Day. The old `/bots night` commands remain as routing diagnostics/refresh controls and cannot create a shared Night room. The former persisted shared Night group is removed on phase/lifecycle reconciliation.

## Live checks

1. Two players in separate homes cannot hear one another. A player outside hears normal outside proximity at Night, but no voice inside either house; verify both directions.
2. ST enters home A, speaks with A, leaves and visits B. A stays private; ST hears only the house being visited. Try the night-order Visit button and `/bots tpHome` without accepting an invitation.
3. Start Day with players still at home: their private audio remains isolated. Leave the home area: immediately join existing shared Day Chat. Re-enter home: private again. Repeat during nominations.
4. Verify no outside Day group/private doorway router overrides an occupied house. Exit a house near a configured private-area doorway and verify the normal area rules still apply.
5. Disconnect/reconnect Simple Voice Chat inside a house, then disconnect/reconnect Minecraft. Repeat at Night and Day, including a Dawn transition during reconnect. Verify no public audio leak or stuck house HUD.
6. Reset during Day and Night, then begin a fresh game. No stale shared Night group or manual/house membership should remain. Test proximity range, neighbouring whispers and ST distance exemption outside.

Automated regressions cover house boundary/nearest-ST choice, own-house restriction, privacy/HUD across Setup/Night/Day, Dawn persistence, Day-route ownership handoff, cached reconnect intent, disconnect/reset and absence of shared Night route codes. Client audio with real players still requires the checks above.
