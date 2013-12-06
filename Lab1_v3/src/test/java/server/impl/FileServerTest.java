package server.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import util.Config;
import cli.Shell;

public class FileServerTest {
	
	FileServer fs;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		fs = new FileServer(new Config("fs1"), new Shell("fs1", System.out,
				System.in));
	}

	@After
	public void tearDown() throws Exception {
		fs.close();
	}	

	@Test
	public void test() throws UnknownHostException, IOException {
//		fail("Not yet implemented");
		
		Socket s = new Socket("localhost", 11922);
		assertTrue(s.isConnected());
	}

}
