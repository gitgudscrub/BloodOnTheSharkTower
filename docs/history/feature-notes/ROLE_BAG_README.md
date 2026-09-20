# Blood on the Sharktower 0.9.0 — Role Bag

Apply this patch over the final 0.8.2 baseline (including the circle-layout and
name-spacing hotfixes).

## Test

1. Launch with `./gradlew.bat runClient`.
2. Load a script and create test seats, e.g.:
   - `/bots testscript`
   - `/bots testseats 6`
   - `/bots storyteller claim`
3. Open the Grimoire with `R`.
4. Press **Role Bag**.
5. Select exactly 6 characters from the current script.
6. Press **Distribute Bag**.
7. Return to the Grimoire. The six selected characters should now be randomly
   distributed as pending roles.
8. Press **SEND ROLES** only when you want to commit them.

`Current Setup` copies the roles currently visible in the Storyteller Grimoire
back into the bag so they can be adjusted and re-distributed.
