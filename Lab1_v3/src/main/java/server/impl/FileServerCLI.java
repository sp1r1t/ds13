package server.impl;

import java.io.Closeable;
import java.io.IOException;

import cli.Command;
import cli.Shell;
import message.response.MessageResponse;
import server.IFileServerCli;

/**
 * FileServer CommandLineInterface
 * @author rakaris
 *
 */
public class FileServerCLI implements IFileServerCli, Closeable {

	private FileServer fileServer;
	private Shell shell;
	private Thread shellThread;

	public FileServerCLI(FileServer fileServer, Shell shell) {
		super();
		this.fileServer = fileServer;
		this.shell = shell;
		shell.register(this);
		shellThread = new Thread(shell);
		shellThread.start();
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {
		close();
		fileServer.close();
		return new MessageResponse("Fileserver closed"); // TODO message never sent
	}

	@Override
	public void close() throws IOException {
		shellThread.interrupt();
		shell.close();
		System.in.close();
		System.out.close();
	}

}
