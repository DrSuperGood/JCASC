package com.hiveworkshop.blizzard.casc.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.hiveworkshop.blizzard.casc.ConfigurationFile;
import com.hiveworkshop.blizzard.casc.info.Info;
import com.hiveworkshop.blizzard.casc.nio.MalformedCASCStructureException;
import com.hiveworkshop.blizzard.casc.storage.Storage;
import com.hiveworkshop.blizzard.casc.vfs.VirtualFileSystem;

/**
 * A convenient access to locally stored Warcraft III data files. Intended for
 * use with CASC versions of Warcraft III including classic and Reforged.
 */
public class WarcraftIIICASC implements AutoCloseable {
	/**
	 * File system view for accessing files from file paths.
	 */
	public class FileSystem {
		/**
		 * Private constructor, currently not used.
		 */
		private FileSystem() {

		}

		/**
		 * Enumerate all file paths contained in this file system.
		 * <p>
		 * This operation might be quite slow.
		 * 
		 * @return A list containing all file paths contained in this file system.
		 * @throws IOException In an exception occurs when resolving files.
		 */
		public List<String> enumerateFiles() throws IOException {
			final var pathResults = vfs.getAllFiles();
			final var filePathStrings = new ArrayList<String>(pathResults.size());

			for (var pathResult : pathResults) {
				filePathStrings.add(pathResult.getPath());
			}

			return filePathStrings;
		}

		/**
		 * Test if the specified file path is a file.
		 * 
		 * @param filePath Path of file to test.
		 * @return True if path represents a file, otherwise false.
		 * @throws IOException In an exception occurs when resolving files.
		 */
		public boolean isFile(final String filePath) throws IOException {
			final var pathFragments = VirtualFileSystem.convertFilePath(filePath);
			try {
				final var resolveResult = vfs.resolvePath(pathFragments);
				return resolveResult.isFile();
			} catch (FileNotFoundException e) {
				return false;
			}
		}

		/**
		 * Test if the specified file path is available from local storage.
		 * 
		 * @param filePath Path of file to test.
		 * @return True if path represents a file inside local storage, otherwise false.
		 * @throws IOException In an exception occurs when resolving files.
		 */
		public boolean isFileAvailable(final String filePath) throws IOException {
			final var pathFragments = VirtualFileSystem.convertFilePath(filePath);
			final var resolveResult = vfs.resolvePath(pathFragments);
			return resolveResult.existsInStorage();
		}

		/**
		 * Test if the specified file path is a nested file system.
		 * <p>
		 * If true a file system can be resolved from the file path which files can be
		 * resolved from more efficiently than from higher up file systems.
		 * <p>
		 * Support for this feature is not yet implemented. Please resolve everything
		 * from the root.
		 * 
		 * @param filePath Path of file to test.
		 * @return True if file is a nested file system, otherwise false.
		 * @throws IOException In an exception occurs when resolving files.
		 */
		public boolean isNestedFileSystem(final String filePath) throws IOException {
			final var pathFragments = VirtualFileSystem.convertFilePath(filePath);
			try {
				final var resolveResult = vfs.resolvePath(pathFragments);
				return resolveResult.isTVFS();
			} catch (FileNotFoundException e) {
				return false;
			}
		}

		/**
		 * Fully read the file at the specified file path into memory.
		 * 
		 * @param filePath File path of file to read.
		 * @return Buffer containing file data.
		 * @throws IOException If an error occurs when reading the file.
		 */
		public ByteBuffer readFileData(final String filePath) throws IOException {
			final var pathFragments = VirtualFileSystem.convertFilePath(filePath);
			final var resolveResult = vfs.resolvePath(pathFragments);

			if (!resolveResult.isFile()) {
				throw new FileNotFoundException("the specified file path does not resolve to a file");
			} else if (!resolveResult.existsInStorage()) {
				throw new FileNotFoundException("the specified file is not in local storage");
			}

			final var fileBuffer = resolveResult.readFile(null);
			fileBuffer.flip();
			return fileBuffer;
		}
	}

	/**
	 * Name of the CASC data folder used by Warcraft III.
	 */
	private static final String WC3_DATA_FOLDER_NAME = "Data";

	/**
	 * Warcraft III build information.
	 */
	private final Info buildInfo;

	/**
	 * Detected active build information record.
	 */
	private final int activeInfoRecord;

	/**
	 * Warcraft III build configuration.
	 */
	private final ConfigurationFile buildConfiguration;

	/**
	 * Warcraft III CASC data folder path.
	 */
	private final Path dataPath;

	/**
	 * Warcraft III local storage.
	 */
	private final Storage localStorage;

	/**
	 * TVFS file system to resolve file paths.
	 */
	private final VirtualFileSystem vfs;
	
	/**
	 * Known Warcraft III products.
	 */
	public enum Product {
		/**
		 * Warcraft III current release.
		 */
		w3,
		/**
		 * Warcraft III public test realm.
		 */
		w3t;
	}
	
	/**
	 * Construct an interface to the CASC local storage used by the first active
	 * build of Warcraft III. Can be used to read data files from the local storage.
	 * <p>
	 * The active build record is used for local storage details.
	 * <p>
	 * Install folder is the Warcraft III installation folder where the
	 * <code>.build.info</code> file is located. For example
	 * <code>C:\Program Files\Warcraft III</code>.
	 * <p>
	 * Memory mapped IO can be used instead of conventional channel based IO. This
	 * should improve IO performance considerably by avoiding excessive memory copy
	 * operations and system calls. However it may place considerable strain on the
	 * Java VM application virtual memory address space. As such memory mapping
	 * should only be used with large address aware VMs.
	 * 
	 * @param installFolder    Warcraft III installation folder.
	 * @param useMemoryMapping If memory mapped IO should be used to read file data.
	 * @throws IOException If an exception occurs while mounting.
	 */
	public WarcraftIIICASC(final Path installFolder, final boolean useMemoryMapping) throws IOException {
		this(installFolder, useMemoryMapping, (String)null);
	}
	
	/**
	 * Construct an interface to the CASC local storage used by Warcraft III. Can be
	 * used to read data files from the local storage.
	 * <p>
	 * The active build record is used for local storage details.
	 * <p>
	 * Install folder is the Warcraft III installation folder where the
	 * <code>.build.info</code> file is located. For example
	 * <code>C:\Program Files\Warcraft III</code>.
	 * <p>
	 * Memory mapped IO can be used instead of conventional channel based IO. This
	 * should improve IO performance considerably by avoiding excessive memory copy
	 * operations and system calls. However it may place considerable strain on the
	 * Java VM application virtual memory address space. As such memory mapping
	 * should only be used with large address aware VMs.
	 * 
	 * @param installFolder    Warcraft III installation folder.
	 * @param useMemoryMapping If memory mapped IO should be used to read file data.
	 * @param product          Product of build.
	 * @throws IOException If an exception occurs while mounting.
	 */
	public WarcraftIIICASC(final Path installFolder, final boolean useMemoryMapping, final Product product) throws IOException {
		this(installFolder, useMemoryMapping, product.name());
	}

	/**
	 * Construct an interface to the CASC local storage used by Warcraft III. Can be
	 * used to read data files from the local storage.
	 * <p>
	 * The active build record is used for local storage details.
	 * <p>
	 * Install folder is the Warcraft III installation folder where the
	 * <code>.build.info</code> file is located. For example
	 * <code>C:\Program Files\Warcraft III</code>.
	 * <p>
	 * Memory mapped IO can be used instead of conventional channel based IO. This
	 * should improve IO performance considerably by avoiding excessive memory copy
	 * operations and system calls. However it may place considerable strain on the
	 * Java VM application virtual memory address space. As such memory mapping
	 * should only be used with large address aware VMs.
	 * 
	 * @param installFolder    Warcraft III installation folder.
	 * @param useMemoryMapping If memory mapped IO should be used to read file data.
	 * @param product          Product identifier string of build or null for first
	 *                         declared build.
	 * @throws IOException If an exception occurs while mounting.
	 */
	public WarcraftIIICASC(final Path installFolder, final boolean useMemoryMapping, final String product)
			throws IOException {
		final var infoFilePath = installFolder.resolve(Info.BUILD_INFO_FILE_NAME);
		buildInfo = new Info(ByteBuffer.wrap(Files.readAllBytes(infoFilePath)));

		final var recordCount = buildInfo.getRecordCount();
		if (recordCount < 1) {
			throw new MalformedCASCStructureException("build info does not contain any records");
		}

		var selectedBuilds = IntStream.range(0, recordCount);

		// filter selection for active only
		final var activeFieldIndex = buildInfo.getFieldIndex("Active");
		if (activeFieldIndex == -1) {
			throw new MalformedCASCStructureException("build info does not contain \"Active\" field");
		}
		selectedBuilds = selectedBuilds
				.filter(build -> Integer.parseInt(buildInfo.getField(build, activeFieldIndex)) == 1);

		// filter selection for product
		if (product != null) {
			final var productFieldIndex = buildInfo.getFieldIndex("Product");
			if (productFieldIndex == -1) {
				throw new MalformedCASCStructureException("build info does not contain \"Product\" field");
			}
			selectedBuilds = selectedBuilds
					.filter(build -> buildInfo.getField(build, productFieldIndex).equals(product));
		}

		final var selectedBuild = selectedBuilds.findFirst();
		if (selectedBuild.isEmpty()) {
			throw new IOException("no matching build");
		}
		activeInfoRecord = selectedBuild.getAsInt();

		// resolve build configuration file
		final var buildKeyFieldIndex = buildInfo.getFieldIndex("Build Key");
		if (buildKeyFieldIndex == -1) {
			throw new MalformedCASCStructureException("build info contains no build key field");
		}
		final var buildKey = buildInfo.getField(activeInfoRecord, buildKeyFieldIndex);

		// resolve data folder
		dataPath = installFolder.resolve(WC3_DATA_FOLDER_NAME);
		if (!Files.isDirectory(dataPath)) {
			throw new MalformedCASCStructureException("data folder is missing");
		}

		// resolve build configuration file
		buildConfiguration = ConfigurationFile.lookupConfigurationFile(dataPath, buildKey);

		// mounting local storage
		localStorage = new Storage(dataPath, false, useMemoryMapping);

		// mounting virtual file system
		VirtualFileSystem vfs = null;
		try {
			vfs = new VirtualFileSystem(localStorage, buildConfiguration.getConfiguration());
		} finally {
			if (vfs == null) {
				// storage must be closed to prevent resource leaks
				localStorage.close();
			}
		}
		this.vfs = vfs;
	}

	@Override
	public void close() throws IOException {
		localStorage.close();
	}

	/**
	 * Returns the active record index of the build information. This is the index
	 * of the record that is mounted.
	 * 
	 * @return Active record index of build information.
	 */
	public int getActiveRecordIndex() {
		return activeInfoRecord;
	}

	/**
	 * Returns the active branch name which is currently mounted.
	 * <p>
	 * This might reflect the locale that has been cached to local storage.
	 * 
	 * @return Branch name.
	 * @throws IOException If no branch information is available.
	 */
	public String getBranch() throws IOException {
		// resolve branch
		final var branchFieldIndex = buildInfo.getFieldIndex("Branch");
		if (branchFieldIndex == -1) {
			throw new MalformedCASCStructureException("build info contains no branch field");
		}
		return buildInfo.getField(activeInfoRecord, branchFieldIndex);
	}

	/**
	 * Returns the build information of the archive.
	 * 
	 * @return Build information.
	 */
	public Info getBuildInfo() {
		return buildInfo;
	}

	/**
	 * Get the root file system of Warcraft III. From this all locally stored data
	 * files can be accessed.
	 * 
	 * @return Root file system containing all files.
	 */
	public FileSystem getRootFileSystem() {
		return new FileSystem();
	}
}
