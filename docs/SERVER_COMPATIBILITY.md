# Client/server deployment

The release manifest identifies this as a client-side package. The dedicated server does not receive DistantHorizons-WDG, the runtime-bearing lwjgl3ify-wdg JAR, or Angelica solely for this feature. The packaged Java runtimes are embedded inside the client lwjgl3ify JAR, so no separate runtime payload belongs on the server.

GTNHLib and UniMixins may already be required by unrelated server mods. Audit them and their dependants; do not remove them blindly.

The server-connection smoke must use a safe copy or test instance of the normal Wargames server. The release-candidate client must join without FML missing-mod rejection, complete the normal handshake, move and load chunks, disconnect/reconnect, save/reopen its local DH database and avoid fatal networking errors. No Distant Horizons server installation is required and protocol compatibility remains unchanged.
