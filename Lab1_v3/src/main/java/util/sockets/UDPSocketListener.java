package util.sockets;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UDPSocketListener implements Runnable, Closeable {
	
	private IUDPConnectionHandler connectionHandler;
	
	private DatagramSocket datagramSocket;

	public UDPSocketListener(IUDPConnectionHandler connectionHandler,
			DatagramSocket datagramSocket) {
		super();
		this.connectionHandler = connectionHandler;
		this.datagramSocket = datagramSocket;
	}

	@Override
	public void close() throws IOException {
		datagramSocket.close();
	}

	@Override
	public void run() {
		DatagramPacket p;
		while (connectionHandler.isUDPActive()) {
			p = new DatagramPacket(new byte[32], 32);
			try {
				datagramSocket.receive(p);
				connectionHandler.handleNewUDPConnection(p);
			} 
			catch (SocketException e) {
				System.out.println(e.getMessage());
				return;
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
