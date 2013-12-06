package util.sockets;

import java.io.IOException;
import java.net.Socket;

import message.Response;

public interface ITCPMessagingConnectionHandler {
	
	public Response handleRequest(Socket source, Object request) throws IOException;

}
