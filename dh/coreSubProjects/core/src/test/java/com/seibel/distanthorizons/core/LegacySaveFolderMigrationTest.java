package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.file.structure.LegacySaveFolderMigration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class LegacySaveFolderMigrationTest
{
	@Rule public TemporaryFolder temp = new TemporaryFolder();
	private final byte[] databaseBytes = {3, 1, 4, 1, 5};

	@Test public void readableDimensionsCanReopenWithoutMovingThemselves() throws Exception
	{
		for (String dim : new String[]{"DIM5", "DIM2"})
		{
			Path folder = temp.newFolder(dim).toPath();
			Files.write(folder.resolve("DistantHorizons.sqlite"), databaseBytes);
			Files.write(folder.resolve(".dh-level-key"), ("00000001kk71e@" + dim + ":5").getBytes("UTF-8"));
			for (int i = 0; i < 3; i++)
			{
				LegacySaveFolderMigration.prepare(folder.toFile(), folder.toFile());
				assertArrayEquals(databaseBytes, Files.readAllBytes(folder.resolve("DistantHorizons.sqlite")));
				assertTrue(Files.exists(folder.resolve(".dh-level-key")));
			}
		}
	}

	@Test public void normalizedAliasesAreAlreadyTheSameFolder() throws Exception
	{
		Path folder = temp.newFolder("alias").toPath();
		Files.write(folder.resolve("DistantHorizons.sqlite"), databaseBytes);
		LegacySaveFolderMigration.prepare(folder.toFile(), folder.resolve(".").toFile());
		assertArrayEquals(databaseBytes, Files.readAllBytes(folder.resolve("DistantHorizons.sqlite")));
	}

	@Test public void distinctLegacyFolderMovesBeforeDestinationIsCreated() throws Exception
	{
		Path old = temp.newFolder("old").toPath();
		Files.write(old.resolve("DistantHorizons.sqlite"), databaseBytes);
		Files.write(old.resolve("DistantHorizons.sqlite-wal"), databaseBytes);
		Path target = temp.getRoot().toPath().resolve("new-parent/world");
		LegacySaveFolderMigration.prepare(target.toFile(), old.toFile());
		assertFalse(Files.exists(old));
		assertArrayEquals(databaseBytes, Files.readAllBytes(target.resolve("DistantHorizons.sqlite")));
		assertArrayEquals(databaseBytes, Files.readAllBytes(target.resolve("DistantHorizons.sqlite-wal")));
		LegacySaveFolderMigration.prepare(target.toFile(), old.toFile());
	}

	@Test public void existingTargetPreservesBothDatasetsWithoutMerging() throws Exception
	{
		Path old = temp.newFolder("old").toPath();
		Path target = temp.newFolder("current").toPath();
		Files.write(old.resolve("DistantHorizons.sqlite"), databaseBytes);
		Files.write(target.resolve("DistantHorizons.sqlite"), new byte[]{9, 8, 7});
		LegacySaveFolderMigration.prepare(target.toFile(), old.toFile());
		assertArrayEquals(databaseBytes, Files.readAllBytes(old.resolve("DistantHorizons.sqlite")));
		assertArrayEquals(new byte[]{9, 8, 7}, Files.readAllBytes(target.resolve("DistantHorizons.sqlite")));
	}

	@Test public void freshFolderIsCreatedAndRegularFilesAreRejected() throws Exception
	{
		Path root = temp.getRoot().toPath();
		LegacySaveFolderMigration.prepare(root.resolve("fresh").toFile(), root.resolve("missing").toFile());
		assertTrue(Files.isDirectory(root.resolve("fresh")));
		Path file = Files.write(root.resolve("file"), databaseBytes);
		try
		{
			LegacySaveFolderMigration.prepare(file.toFile(), root.resolve("missing").toFile());
			fail("A regular file cannot be used as a save directory");
		}
		catch (java.io.IOException expected) { }
		assertArrayEquals(databaseBytes, Files.readAllBytes(file));
	}
}
