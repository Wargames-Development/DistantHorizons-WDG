# Portable multiplayer LOD folder names — 25 October 2026

Endpoint folders now use the same Windows-safe name on every OS:
`host.wargames.uk:27007` connects as before, but stores data under
`Distant_Horizons_server_data/host.wargames.uk.27007/earth40/`.
This supersedes the OS-dependent naming described in the 23 October report.

Only the host/port separator becomes a dot. IPv6 colons and other reserved
filename characters are escaped, including literal percent signs to avoid
encoding collisions. Windows device-name components are escaped too. The same
connection therefore uses the same label on macOS, Windows and Linux, including
when a pack is exported. World identity markers, SQLite contents, server address,
network protocol and world-name subdirectories do not change.

Before opening a database, the existing conservative migration now also considers
both prior readable endpoint names: literal `host:port` and Windows `host%3Aport`.
A single provable old root is moved without overwriting or merging. Illegal old
platform paths are skipped. Existing destinations and ambiguous multiple source
datasets remain untouched, retaining the established migration safeguards.

Release Testing installation requires the game fully closed. Keep the previous
jar and a byte-verified snapshot of the old endpoint directory outside the pack,
rename the endpoint directory, install exactly one new DH jar, and verify the
SQLite and `.dh-level-key` checksums remain unchanged. No terrain conversion,
LOD regeneration or server configuration change is needed for this client-side
folder selection fix.

Validation: all 109 core tests passed, including portable endpoint labels,
forbidden characters/device names, IPv6 and lossless migrations of prior roots.
Forge 1.7.10 check and assemble passed. These tests exercise naming for both OS
flags; they do not claim a live Minecraft run on every operating system.
