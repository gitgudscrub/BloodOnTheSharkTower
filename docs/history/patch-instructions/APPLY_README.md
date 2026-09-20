# Apply this patch over Blood on the Sharktower 0.9.1

Copy the contents of this archive into the root of your working 0.9.1 project and allow files to overwrite.

This changes only the Minecraft/Fabric/Gradle migration metadata plus the current-version README/log text. It intentionally does not redesign gameplay systems.

After applying, run:

```powershell
.\gradlew.bat clean runClient
```

If your wrapper has not yet been generated, run `bootstrap-gradle.bat` once first. See `MIGRATION_26.3.md` for the full smoke test.
