# Readable multiplayer storage and log colours — 23 October 2026

Endpoint naming below is historical; see [the portable-folder update](PORTABLE_LOD_STORAGE_2026_10_25.md) for the current cross-platform names.

This follow-up supersedes the folder naming and protocol number in the earlier compatibility report.

On macOS/Linux the endpoint folder is the literal normalized host:port, for example `host.wargames.uk:27007`. There is no endpoint- prefix or percent-escaped dots. Windows cannot use literal colons in filenames, so only that platform escapes them. macOS Finder can display POSIX colons as slashes; the underlying path still contains a colon.

Protocol 18 transmits the actual server world name separately from its stable level key. Install this build on both client and server. The Forge 1.7.10 server resolves Bukkit worlds by exact native handle, independently of WorldBorder installation; the fallback is the actual chunk-save directory name. The client uses `endpoint/world-name/DistantHorizons.sqlite`. Server-provided level keys must remain enabled for this naming information. Empty legacy name information falls back to the previous dimension identifier, rather than guessing a world name.

The stable key is retained in `.dh-level-key`, not displayed as a directory. It prevents a newly replaced world with the same name from inheriting another world's terrain. A conflicting or unidentified existing target is refused and preserved, not merged, deleted or overwritten. One known old keyed child directory can move to its supplied world name before database opening. The previously escaped endpoint root can also move to the literal endpoint, with the existing conservative unique-candidate rule.

The reported NULL and Wargames Survival 2.0 roots contain multiple historical level identities. They are not evidence that the new endpoint build actively duplicates data. They cannot safely be combined with the current Earth database merely because they were found in the same instance. Current-connection logs identify `00000001kk71e@overworld:0`; the user identifies the current server world as `earth40`. The closed-pack migration preserves a separate original current-endpoint backup and archives old roots outside the active server-data directory. No SQLite tables are combined or discarded.

For colour-only LODs, the previous 1.7 representative colour always sampled the upward texture. An upright cherry log therefore coloured the entire coarse trunk from its pale end-grain. Native BlockLog states now sample a bark face, respecting X/Y/Z metadata and all-bark variants. The renderer still resolves the actual native icon for that face; no arbitrary brightness multiplier or global wood darkening is applied. This change applies to vanilla and modded BlockLog subclasses. Other blocks with contrasting top/side textures may still differ at coarse LOD distances and need an identified example before changing their policy. This is a representative-colour correction, not a visual certification of every block.

The Earth terrain boundary was also clarified after the earlier report: stored chunks include an empty margin. DH reads the configured WorldBorder rather than relying on any hardcoded dimensions. The actual terrain faces are not all 64-block aligned, so the earlier warning about a narrow rejected edge strip applies to the corrected border.

Validation includes the complete core test suite, Forge check and assemble, plus regression tests for literal endpoint paths, actual world-name packets, repeat-safe migration with unchanged database bytes, path traversal/control rejection, world-key collision protection and every legacy log-axis choice. Existing WorldBorder, water, snow and zoom checks remain in the suite.

Runtime follow-up: update the server as well as the client before reconnecting, check `host.wargames.uk:27007/earth40`, reconnect through a second menu with exactly the same address, and compare cherry bark beyond vanilla distance. No world reconversion or LOD database deletion is needed. Cached full-data LOD records retain block identities; new render meshes can use corrected representative colours.
