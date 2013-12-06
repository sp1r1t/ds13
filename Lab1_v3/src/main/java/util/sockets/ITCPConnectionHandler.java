package util.sockets;

import java.net.Socket;

public interface ITCPConnectionHandler {
	
	public void handleNewTCPConnection(Socket s);
	
	public boolean isTCPActive();

}
