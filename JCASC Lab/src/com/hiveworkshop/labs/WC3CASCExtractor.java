package com.hiveworkshop.labs;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC;

/**
 * A simple application for extracting a single file from the Warcraft III CASC
 * archive.
 * <p>
 * First argument is a file path to the Warcraft III install folder. An optional
 * second argument -p cam ne used to select a specific product. Then one of the
 * following flags can be used.
 * <p>
 * -l to list all files.
 * <p>
 * -e to extract a specific file. With -e a source file to extract and a
 * destination folder must be specified.
 * <p>
 * -b or -branch to list the active branch.
 * <p>
 * -bi or -buildinfo to retrieve a field from the active record of the build
 * information file which was mounted.
 */
public class WC3CASCExtractor {

	public static void main(String... args) {
		int pos = 0;
		if (args.length < 1) {
			System.out.println("No install path specified.");
			return;
		}

		final var installPathString = args[0];
		final var installPath = Path.of(installPathString);
		if (!Files.isDirectory(installPath)) {
			System.out.println("Install path is not a folder.");
			return;
		}
		pos+= 1;
		
		String product = WarcraftIIICASC.Product.w3.name();
		if (args.length - pos >= 2 && args[pos].equals("-p")) {
			product = args[pos + 1];
			pos+= 2;
		}

		System.out.println("Mounting.");
		try (final var dataSource = new WarcraftIIICASC(installPath, true, product)) {
			final var root = dataSource.getRootFileSystem();

			if (args.length - pos < 1) {
				System.out.println("No operation specified.");
				return;
			}
			final var operationString = args[pos];
			switch (operationString) {
			case "-l":
				System.out.println("Enumerating files.");
				final var filePaths = root.enumerateFiles();
				for (final var filePath : filePaths) {
					System.out.println(filePath);
				}
				break;
			case "-e":
				if (args.length < 4) {
					System.out.println("Not enough operands.");
					return;
				}

				final var sourceFilePathString = args[pos + 1];
				final var destinationFolderPathString = args[pos + 2];

				if (!root.isFile(sourceFilePathString)) {
					System.out.println("Specified file path does not exist.");
					return;
				} else if (!root.isFileAvailable(sourceFilePathString)) {
					System.out.println("Specified file is not available.");
					return;
				}

				final var destinationFilePath = Path.of(destinationFolderPathString, sourceFilePathString);
				final var destinationFolderPath = destinationFilePath.getParent();
				Files.createDirectories(destinationFolderPath);
				try (final var destinationChannel = FileChannel.open(destinationFilePath, StandardOpenOption.CREATE,
						StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
					System.out.println("Reading file data.");
					final var fileBuffer = dataSource.getRootFileSystem().readFileData(sourceFilePathString);
					System.out.println("Writing file data.");
					while (fileBuffer.hasRemaining()) {
						destinationChannel.write(fileBuffer);
					}
				}
				break;
			case "-b":
			case "-branch":
				System.out.println("Active branch: " + dataSource.getBranch());
				break;
			case "-bi":
			case "-buildinfo":
				if (args.length - pos < 2) {
					System.out.println("Not enough operands.");
					return;
				}

				final var fieldName = args[pos + 1];
				final var value = dataSource.getBuildInfo().getField(dataSource.getActiveRecordIndex(), fieldName);

				System.out.println("Field name: " + fieldName);
				System.out.println("Value: " + value);
				break;
			default:
				System.out.println("Unknown operation.");
				break;
			}

			System.out.println("Done.");
		} catch (IOException e) {
			System.out.println("An exception occured.");
			e.printStackTrace(System.out);
		} finally {
			System.out.println("Unmounted.");
		}

	}
}
