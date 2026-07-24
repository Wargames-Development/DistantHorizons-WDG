# Windows x64 release-candidate testing

Use a disposable profile on a Windows x64 machine. Record Windows version, CPU, GPU and driver, total RAM, Java allocation, Java 8 launcher parent, selected embedded `windows-x86_64` runtime from the exact Change 005 lwjgl3ify JAR, Java 21 child process, mod list, three-line RC warning, provenance, managed-updater log, main menu, disposable world creation, initial LOD generation, save/reopen, generated-LOD reload, FPS/frame-time observations, CPU/GPU load, normal Wargames modpack compatibility and normal shutdown.

The normal Windows x64 profile contains only the required mod JARs in `mods/`; it must not contain an external runtime ZIP or `lwjgl3ify/runtime/` directory. Windows ARM64 uses the optional manual extension archive and is outside the default testing-profile contract.

Run `scripts/Collect-Windows-RcEvidence.ps1` from PowerShell. It reads only operating-system, CPU, GPU/driver, memory, relevant Java process details and selected log lines. Review the output before sharing because unavoidable local paths may contain a Windows account directory name. Do not include launcher account databases, tokens, browser data, unrelated files or valuable worlds.

A successful structural profile ZIP is not called CurseForge-import verified until it has been imported into a disposable CurseForge profile and launched.
