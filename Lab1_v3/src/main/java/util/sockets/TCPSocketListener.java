package util.sockets;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class TCPSocketListener implements Runnable, Closeable {
	
	private ITCPConnectionHandler connectionHandler;
	
	private ServerSocket serverSocket;

	public TCPSocketListener(ITCPConnectionHandler connectionHandler,
			ServerSocket serverSocket) {
		super();
		this.connectionHandler = connectionHandler;
		this.serverSocket = serverSocket;
	}

	@Override
	public void close() throws IOException {
		serverSocket.close();
	}

	@Override
	public void run() {
		Socket s;
		while (connectionHandler.isTCPActive()) {
			try {
				s = serverSocket.accept();	
				connectionHandler.handleNewTCPConnection(s);
			} catch (SocketException e) {
				System.out.println(e.getMessage());
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
