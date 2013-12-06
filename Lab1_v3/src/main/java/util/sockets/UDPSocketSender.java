package util.sockets;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UDPSocketSender implements Runnable, Closeable {
	
	/*
	 * Class is being misused here. Don't use handleNewUDPConnection method
	 */
	private IUDPConnectionHandler connectionHandler;
	
	private DatagramSocket datagramSocket;
	
	private String destinationAddress;
	private int destinationPort;
	private int interval;
	private byte[] content; 

	public UDPSocketSender(IUDPConnectionHandler connectionHandler,
			String destinationAddress, int destinationPort, int interval, byte[] content) throws SocketException {
		super();
		this.connectionHandler = connectionHandler;
		this.destinationAddress = destinationAddress;
		this.destinationPort = destinationPort;
		this.interval = interval;
		this.content = content;
		datagramSocket = new DatagramSocket();
	}

	@Override
	public void close() throws IOException {
		datagramSocket.close();
	}

	@Override
	public void run() {
		DatagramPacket p;
		try {
			p = new DatagramPacket(content, content.length, InetAddress.getByName(destinationAddress), destinationPort);
			while (connectionHandler.isUDPActive()) {
				try {
					datagramSocket.send(p);
					Thread.sleep(interval);
				}
				catch (SocketException e) {
					System.out.println(e.getMessage());
				}
				catch (IOException e) {
					e.printStackTrace();
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
	}

}
