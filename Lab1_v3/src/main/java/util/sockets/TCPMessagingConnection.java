package util.sockets;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import message.Response;

public class TCPMessagingConnection implements Closeable, IConnection {
	
	private ITCPMessagingConnectionHandler tcpMessagingConnectionHandler;
	private Socket socket;
	private ObjectOutputStream socketObjectOutputStream;
	private ObjectInputStream socketObjectInputStream;
	
	private boolean active = true;
	
	public TCPMessagingConnection(ITCPMessagingConnectionHandler tpcMessagingConnectionHandler,
			Socket socket) throws IOException {
		super();
		this.tcpMessagingConnectionHandler = tpcMessagingConnectionHandler;
		this.socket = socket;
		socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
		socketObjectInputStream = new ObjectInputStream(socket.getInputStream());
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@Override
	public String toString() {
		return "TCPMessagingConnection [socket=" + socket + ", active=" + active
				+ "]";
	}

	@Override
	public void close() throws IOException {
		setActive(false);
		socketObjectInputStream.close();
		socketObjectOutputStream.close();
		socket.close();
	}

	@Override
	public void run() {
//		System.out.println("New TCPMessagingConnection established with: " + socket.getInetAddress() +":"+socket.getPort());
		Object request;
		Response response;
		while (active) {
			try {
				request = socketObjectInputStream.readObject();
				response = tcpMessagingConnectionHandler.handleRequest(socket, request);
				socketObjectOutputStream.writeObject(response);
				socketObjectOutputStream.flush();
			} catch (EOFException e) {
				try {
					close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				break;
			} catch (SocketException e) {
				try {
					close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				break;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}

}
