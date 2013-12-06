package server.impl.sockets;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import message.Response;
import util.sockets.IConnection;
import util.sockets.ITCPMessagingConnectionHandler;

/**
 * Handles a simple tcp connection.
 * Receives a message, sends another back and closes the connection.
 * Mainly used for the fileserver.
 * @author rakaris
 *
 */
public class SimpleTCPMessagingConnection implements IConnection, Closeable {
	
	private ITCPMessagingConnectionHandler tcpMessagingConnectionHandler;
	
	private Socket socket;

	public SimpleTCPMessagingConnection(ITCPMessagingConnectionHandler tcpMessagingConnectionHandler, Socket s) {
		super();
		this.tcpMessagingConnectionHandler = tcpMessagingConnectionHandler;
		this.socket = s;
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}

	@Override
	public void run() {
		ObjectOutputStream socketObjectOutputStream = null;
		ObjectInputStream socketObjectInputStream = null;
		try {
			socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			socketObjectInputStream = new ObjectInputStream(socket.getInputStream());
			
			Object request = socketObjectInputStream.readObject();
			System.out.println("SimpleTCPMessagingConnection: received" + request);
			Response response = tcpMessagingConnectionHandler.handleRequest(socket, request);
			socketObjectOutputStream.writeObject(response);
			socketObjectOutputStream.flush();
		} catch (IOException e) {
//			e.printStackTrace();		// mostly socketexception when shutting down - ignore
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (socketObjectInputStream != null) socketObjectInputStream.close();
			} catch (IOException e1) {
//				e1.printStackTrace();
			}
			try {
				if (socketObjectOutputStream != null) socketObjectOutputStream.close();
			} catch (IOException e1) {
//				e1.printStackTrace();
			}
			try {
				if (socket != null) socket.close();
			} catch (IOException e) {
//				e.printStackTrace();
			}
		}
	}

}
