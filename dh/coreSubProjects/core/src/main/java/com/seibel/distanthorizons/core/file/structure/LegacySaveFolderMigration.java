package com.seibel.distanthorizons.core.file.structure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pre-2.3 folders must never replace or merge with an active dataset. */
public final class LegacySaveFolderMigration
{
	private LegacySaveFolderMigration() { }

	public static void prepare(File saveFolder, File pre23Folder) throws IOException
	{
		Path target = saveFolder.getCanonicalFile().toPath();
		Path legacy = pre23Folder.getCanonicalFile().toPath();
		boolean sameFolder = target.equals(legacy)
			|| (Files.exists(target) && Files.exists(legacy) && Files.isSameFile(target, legacy));

		// Readable world names (e.g. DIM5) can already be the pre-2.3 name.
		// Renaming a directory onto itself fails on Windows. Existing active
		// destinations also take precedence; leave any separate legacy data intact.
		if (!sameFolder && !Files.exists(target) && Files.exists(legacy))
		{
			if (!Files.isDirectory(legacy))
			{
				throw new IOException("Old DH save folder is not a directory: " + legacy);
			}
			Files.createDirectories(target.getParent());
			// Create the destination only AFTER migration. Do not replace data.
			Files.move(legacy, target);
		}
		Files.createDirectories(target);
	}
}
