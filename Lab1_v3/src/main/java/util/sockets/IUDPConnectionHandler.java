package util.sockets;

import java.net.DatagramPacket;

public interface IUDPConnectionHandler {

	public void handleNewUDPConnection(DatagramPacket p);
	
	public boolean isUDPActive();
	
}
