package util.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages file storage and versioning
 * @author rakaris
 *
 */
public class FileManager {
	
	private String directoryPath;
	private File storageDirectory;
	
	private Map<String, Integer> fileVersionMap = new HashMap<String, Integer>();
	
	public FileManager(String directoryPath) throws IOException {
		this.directoryPath = directoryPath;
		storageDirectory = new File(directoryPath);
		if (!storageDirectory.isDirectory()) 
			throw new IOException("Not a directory: "+directoryPath);
		
		/*
		 * Fill versionmap with data
		 */
		for (String s: getFileNames())
			fileVersionMap.put(s, 0);
	}
	
	/**
	 * Constructs a file from filename (and directory name)
	 * @param file
	 * @return
	 */
	public File getFile(String file) {
		return new File(directoryPath + "/" + file);
	}
	
	public byte[] getFileContent(String filename) throws IOException {
		File f = getFile(filename);
		if (f.exists()) {
			FileInputStream fin = new FileInputStream(f);
			byte fileContent[] = new byte[(int)f.length()];
			fin.read(fileContent);
			fin.close();
			return fileContent;
		}
		return new byte[]{};
	}
	
	public long getFileSize(String filename) {
		File f = getFile(filename);
		if (f.exists())
			return f.length();
		return -1L;
	}
	
	public List<File> getFiles() {
		return Arrays.asList(storageDirectory.listFiles());
	}
	
	public List<String> getFileNames() {
		List<String> result = new ArrayList<String>();
		for (File f: storageDirectory.listFiles()) // TODO redundant
			result.add(f.getName());
		return result;
	}
	
	/**
	 * Checks if file exists
	 * @param filename
	 * @return
	 */
	public boolean contains(String filename) {
		File f = getFile(filename);
		return f.exists();
	}
	
	public File storeFile(String filename, byte[] content) throws IOException {
		File f = getFile(filename);
		if (!f.exists()) {
			f.createNewFile();
		}
		FileOutputStream fos = new FileOutputStream(f);
		fos.write(content);
		fos.flush();
		fos.close();
		
		incrementVersionOrInsertNew(filename);
		
		System.out.println("File stored: " + f.getAbsolutePath());
		return f;
	}
	
	public Integer getVersion(String filename) {
		return fileVersionMap.get(filename);
	}
	
	private Integer incrementVersionOrInsertNew(String filename) {
		if (fileVersionMap.containsKey(filename)) {
			return fileVersionMap.put(filename, getVersion(filename)+1);
		}
		else
			return fileVersionMap.put(filename, 0);
	}

}
