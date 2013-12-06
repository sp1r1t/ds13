package util;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import util.files.FileManager;

public class FileManagerTest {
	
	static final String directoryPath = "files/client";
	
	static final String testFileName = "uzbnmipomion098764567ewf";
	
	FileManager fileManager;
	
	File testFile;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		File f = new File(directoryPath + "/" + testFileName);
		if (f.exists())
			f.delete();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void test() throws IOException {
		fileManager = new FileManager(directoryPath);
		fileManager.storeFile(testFileName, new byte[]{});
		assertTrue(fileManager.contains(testFileName));
		assertTrue(fileManager.getVersion(testFileName) == 0);
		fileManager.storeFile(testFileName, new byte[]{});
		assertTrue(fileManager.getVersion(testFileName) == 1);
	}

//	@Test
//	public void testFileManager() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetFile() {
//		fail("Not yet implemented");
//	}

//	@Test
//	public void testGetFiles() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetFileNames() {
//		for (String s: fileManager.getFileNames())
//			System.out.println(s);
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testContains() {
//		fail("Not yet implemented");
//	}

//	@Test
//	public void testStoreFile() {
//		fail("Not yet implemented");
//	}

//	@Test
//	public void testGetVersion() {
//		fail("Not yet implemented");
//	}

}
