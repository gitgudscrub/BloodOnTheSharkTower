# 0.2.1 role model port notes

This slice was reconstructed from the supplied private-use 1.21.1-1.3.0 JAR.

Important 26.2 adaptation:
- Minecraft 26.2 uses `net.minecraft.resources.Identifier`; official role icon
  paths are preserved under the Sharktower namespace.
- The old client language singleton is not used from common code. `RoleText`
  reads the bundled `en_us.json` directly so the model remains dedicated-server safe.
- Custom URL texture resolution is deferred until the client/UI port.
- Packet codecs are deferred until the networking port.

Original behavior preserved here includes:
- 182 role enum entries
- `VILLAGE_IDIOT` and `LEGION` allowing setup duplicates
- normalized role IDs ignoring underscore, whitespace and hyphen
- custom role team/image/night/reminder/jinx parsing
- original separate non-player-character model for custom Fabled/Loric entries
- script metadata and custom definition parsing
- depth-64 / 32-extra-almanac parser safeguards; the original 16 MiB decompression limit remains for the networking codec slice
