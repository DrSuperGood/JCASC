package com.hiveworkshop.labs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.hiveworkshop.blizzard.casc.ConfigurationFile;
import com.hiveworkshop.blizzard.casc.info.Info;
import com.hiveworkshop.blizzard.casc.storage.Storage;
import com.hiveworkshop.blizzard.casc.vfs.VirtualFileSystem;

public class NewCASCExtractLab {

	public static void main(String[] args) {
		//final var localAchiveFolder = Paths.get("C:\\Program Files (x86)\\StarCraft II\\SC2Data\\data");
		final var dataFolder = Paths.get("C:\\Program Files (x86)\\Warcraft III\\Data");
		final var extractFolder = Paths.get("G:\\WC3 maps\\CASC Extract");
		
		//final var localIndexFile = Paths.get("C:\\Program Files (x86)\\StarCraft II\\SC2Data\\data\\0000000139.idx");
		System.out.println("opening info");
		final var infoFile = dataFolder.resolveSibling(Info.BUILD_INFO_FILE_NAME);
		Info info = null;
		try {
			info = new Info(ByteBuffer.wrap(Files.readAllBytes(infoFile)));
		} catch (IOException e) {
			System.out.println("an exception occured");
			e.printStackTrace(System.out);
			System.out.println("fail");
			return;
		}
		
		System.out.println("extracting build configuration key");
		if (info.getRecordCount() < 1) {
			System.out.println("info contains no records");
			System.out.println("fail");
			return;
		}
		final var buildKeyField = "Build Key";
		final var fieldIndex = info.getFieldIndex(buildKeyField);
		if (fieldIndex == -1) {
			System.out.println("info missing field");
			System.out.println("fail");
			return;
		}
		final var buildKey = info.getField(0, fieldIndex);
		
		System.out.println("opening configuration");
		ConfigurationFile buildConfiguration = null;
		try {
			buildConfiguration = ConfigurationFile.lookupConfigurationFile(dataFolder, buildKey);
		} catch (IOException e) {
			System.out.println("an exception occured");
			e.printStackTrace(System.out);
			System.out.println("fail");
			return;
		}
		
		System.out.println("opening store");
		try (final var storage = new Storage(dataFolder, false, true)) {
			System.out.println("mounting VFS");
			final var vfs = new VirtualFileSystem(storage, buildConfiguration.getConfiguration());
			
			System.out.println("getting all paths");
			var allFilePaths = vfs.getAllFiles();
			
			final var startTime = System.nanoTime();
			/*final var repeatCount = 120;
			for (var i = 0 ; i < repeatCount ; i+= 1) {
				allFilePaths = vfs.getAllFilePaths();
			}*/
			
			final AtomicLong totalExtracted = new AtomicLong(0L);
			final var jobList = new ArrayList<Callable<?>>();
			for (var pathResult : allFilePaths) {
				final var filePath = pathResult.getPath();
				var outputPath = extractFolder.resolve(filePath);
				final var fileSize = pathResult.getFileSize();
				final var exists = pathResult.existsInStorage();
				if (exists && !pathResult.isTVFS()) {
					final var job = new Callable<Object>() {
						@Override
						public Object call() throws Exception {
							Files.createDirectories(outputPath.getParent());
							try (final var fileChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {
								final var fileBuffer = fileChannel.map(MapMode.READ_WRITE, 0L, fileSize);
								
								pathResult.readFile(fileBuffer);
								totalExtracted.addAndGet(fileSize);
							}
							
							final var resolveResult = vfs.resolvePath(pathResult.getPathFragments());
							if (resolveResult == null) {
								System.out.println("problem resolving results: " + filePath);
							}
							totalExtracted.addAndGet(fileSize);
							
							//pathResult.readFile(null);
							//totalExtracted.addAndGet(fileSize);
		
							return null;
						}
					};
					
					jobList.add(job);
					
					/*try {
						Files.createDirectories(outputPath.getParent());
						try (final var fileChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {
							final var fileBuffer = fileChannel.map(MapMode.READ_WRITE, 0L, fileSize);
							
							pathResult.readFile(fileBuffer);
							totalExtracted+= fileSize;
						}
					} catch (IOException e) {
						System.out.println("extract failed");
						e.printStackTrace(System.out);
					}*/
				}

				System.out.println(filePath + " : " + fileSize + " : " +  (exists ? "yes" : "no"));
			}
			
			/*System.out.println("testing file resolution");
			final var pathFragments = VirtualFileSystem.convertFilePath("war3.mpq\\units\\human\\arthas\\arthas.mdx");
			final var result = vfs.resolvePath(pathFragments);
			if (result != null) {
				final var extractFolder2 = Paths.get("G:\\WC3 maps\\CASC Extract Specific");
				final var outputPath = extractFolder2.resolve("testfile.mdx");
				Files.createDirectories(outputPath.getParent());
				try (final var fileChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {
					final var fileBuffer = fileChannel.map(MapMode.READ_WRITE, 0L, result.getFileSize());
					
					result.readFile(fileBuffer);
				}
				
				System.out.println("file resolved");
			}*/

			
			System.out.println("extracting files");
			final var executor = Executors.newFixedThreadPool(8);
			try {
				final var jobFutures = executor.invokeAll(jobList);
				for (final var jobFuture : jobFutures) {
					try {
						jobFuture.get();
					} catch (ExecutionException e) {
						System.out.println("error extracting file");
						e.printStackTrace();
					}
				}
			} catch (InterruptedException e) {
				System.out.println("interruption during execution");
				e.printStackTrace();
			} finally {
				executor.shutdownNow();
			}
			
			System.out.println("shuting down thread pool");
			try {
				executor.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				System.out.println("unable to shutdown executor");
				e.printStackTrace();
			}
			
			
			final var endTime = System.nanoTime();
			

			System.out.println("total path string count: " + allFilePaths.size());
			
			final var runtime = (endTime - startTime) / 1000000000d;
			System.out.println("running time to process all files: " + (long)runtime + "s");
			
			System.out.println("average process speed: " + (long)(totalExtracted.get() / runtime) + "B/sec");
			
			System.out.println("success");
		} catch (IOException e) {
			System.out.println("an exception occured");
			e.printStackTrace(System.out);
			System.out.println("fail");
			return;
		}
		
		System.out.println("end");
	}

}
