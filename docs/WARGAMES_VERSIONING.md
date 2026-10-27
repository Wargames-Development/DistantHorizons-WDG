# Wargames versioning

Use `major.minor.patch_Xbuild` for release jar names and reported versions.

- Major changes only when substantially changing the entire mod.
- Minor changes for a new feature; reset patch to zero.
- Patch changes for fixes and small changes.
- Build advances on every successful packaging invocation, independently of the three release components. Never reset the counter for a release bump.

EFR and DH automatically persist the next build number after successful packaging (including CI); failed builds, checks, configuration and dry runs do not consume it. Run one packaging invocation at a time in a checkout. Build through the repository root wrapper. Do not add branch names, git hashes, dirty markers or descriptive feature suffixes to release versions. Standard dev/sources classifiers identify different artifact types.

The three release components are deliberate edits in gradle.properties; the build does not guess feature versus fix from source changes. Configuration cache is disabled so a cached task graph cannot reuse a consumed build number.

WGMap keeps its existing version format and build policy. NTM keeps its upstream mod_version and mod_build_number untouched: only wdg_version is advanced manually, without a new automatic build counter. Leave disabled counters in other mods disabled.

## 27 October 2026 release validation

Released as `DistantHorizons-3.3.4_X2.jar`. Offline `check assemble` passed at X1; a second direct `jar` build produced X2 and persisted the counter. Both the jar's mcmod.info and Implementation-Version report the same WG version; generated ModInfo receives that version through the included build. Only the root wrapper controls the WG release; direct builds inside dh/ retain their upstream defaults. No DH gameplay/height or LOD storage code changed. No live modpack test was performed for this naming-only change.
