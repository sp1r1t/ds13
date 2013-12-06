package proxy.impl.fileservers;

import java.net.InetAddress;

import model.FileServerInfo;

public class FileServerData {
	
	private final int port; // udp
	private int onlineTime;
	private FileServerInfo fileServerInfo;
	private final int tcpPort;
	private final InetAddress iNetAddress;
	
	public FileServerData(InetAddress address, int port, long usage, boolean online, int onlineTime, int tcpPort, InetAddress iNetAddress) {
		this.port = port;
		this.onlineTime = onlineTime;
		this.tcpPort = tcpPort;
		this.iNetAddress = iNetAddress;
		fileServerInfo = new FileServerInfo(address, port, usage, online);
	}

	public int getPort() {
		return port;
	}

	public int getOnlineTime() {
		return onlineTime;
	}

	public void setOnlineTime(int onlineTime) {
		this.onlineTime = onlineTime;
	}

	public FileServerInfo getFileServerInfo() {
		return fileServerInfo;
	}
	
	public boolean isOnline() {
		return fileServerInfo.isOnline();
	}
	
	public void setOnline(boolean online) {
		fileServerInfo = new FileServerInfo(fileServerInfo.getAddress(), fileServerInfo.getPort(), fileServerInfo.getUsage(), online);
	}
	
	public void setUsage(long usage) {
		fileServerInfo = new FileServerInfo(fileServerInfo.getAddress(), fileServerInfo.getPort(), usage, fileServerInfo.isOnline());
	}

	public int getTcpPort() {
		return tcpPort;
	}

	public InetAddress getiNetAddress() {
		return iNetAddress;
	}

	@Override
	public String toString() {
		return "FileServerData [port=" + port + ", onlineTime=" + onlineTime
				+ ", fileServerInfo=" + fileServerInfo + "]";
	}
	

}
