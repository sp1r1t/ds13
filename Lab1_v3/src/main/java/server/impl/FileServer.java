package server.impl;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

import cli.Shell;
import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.DownloadFileResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.VersionResponse;
import model.DownloadTicket;
import server.IFileServer;
import server.impl.model.FileServerRequestTypes;
import server.impl.sockets.SimpleTCPMessagingConnection;
import util.ChecksumUtils;
import util.Config;
import util.files.FileManager;
import util.sockets.ITCPMessagingConnectionHandler;
import util.sockets.ITCPConnectionHandler;
import util.sockets.IUDPConnectionHandler;
import util.sockets.MessagingConnectionPool;
import util.sockets.TCPMessagingConnection;
import util.sockets.TCPSocketListener;
import util.sockets.UDPSocketSender;

public class FileServer implements IFileServer, Closeable,
		ITCPConnectionHandler, IUDPConnectionHandler,
		ITCPMessagingConnectionHandler {

	/*
	 * fileserver properties
	 */
	private final static String PROP_FILESERVER_ALIVE = "fileserver.alive";
	private final static String PROP_FILESERVER_DIR = "fileserver.dir";
	private final static String PROP_TCPPORT = "tcp.port";
	private final static String PROP_PROXYHOST = "proxy.host";
	private final static String PROXY_UPDPORT = "proxy.udp.port";

	/*
	 * # Alive period fileserver.alive=1000
	 */
	private int fileserver_alive;
	private String fileserver_dir;
	private int tcp_port;
	private String proxy_host;
	private int proxy_udp_port;

	private FileServerCLI fileServerCLI;

	private FileManager fileManager;

	private TCPSocketListener tcpSocketListener;
	private UDPSocketSender udpSocketSender;

	private boolean active = true;
	private int usage = 0;

	private MessagingConnectionPool connectionPool = new MessagingConnectionPool();

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			new FileServer(new Config("fs1"), new Shell("fs1", System.out,
					System.in));
		} else {
			new FileServer(new Config(args[0]), new Shell(args[0], System.out,
					System.in));
		}
	}

	public FileServer(Config config, Shell shell) throws IOException {
		/*
		 * Config
		 */
		fileserver_alive = config.getInt(PROP_FILESERVER_ALIVE);
		fileserver_dir = config.getString(PROP_FILESERVER_DIR);
		tcp_port = config.getInt(PROP_TCPPORT);
		proxy_host = config.getString(PROP_PROXYHOST);
		proxy_udp_port = config.getInt(PROXY_UPDPORT);

		/*
		 * CLI
		 */
		fileServerCLI = new FileServerCLI(this, shell);

		/*
		 * FileManager
		 */
		fileManager = new FileManager(fileserver_dir);

		/*
		 * Sockets
		 */
		tcpSocketListener = new TCPSocketListener(this, new ServerSocket(
				tcp_port));
		new Thread(tcpSocketListener).start();
		
		udpSocketSender = new UDPSocketSender(this, proxy_host, proxy_udp_port,
				fileserver_alive, getUDPMessageContent());
		new Thread(udpSocketSender).start();
		
		System.out.println(toString());
	}
	
	@Override
	public String toString() {
		return "FileServer [fileserver_alive=" + fileserver_alive + ", fileserver_dir=" + fileserver_dir
				+ ", tcp_port=" + tcp_port + ", proxy_host=" + proxy_host + ", proxy_udp_port=" + proxy_udp_port
				+ ", fileServerCLI=" + fileServerCLI + ", fileManager=" + fileManager + ", tcpSocketListener="
				+ tcpSocketListener + ", udpSocketSender=" + udpSocketSender + ", active=" + active + ", usage="
				+ usage + ", connectionPool=" + connectionPool + "]";
	}

	private byte[] getUDPMessageContent() {
		return new String("!alive " + tcp_port).getBytes();
	}

	/**
	 * Used in ComponentFactory
	 * 
	 * @return
	 */
	public FileServerCLI getFileServerCLI() {
		return fileServerCLI;
	}

	@Override
	public Response list() throws IOException {
		return new ListResponse(new HashSet<String>(fileManager.getFileNames()));
	}

	@Override
	public Response download(DownloadFileRequest request) throws IOException {
		DownloadTicket t = request.getTicket();
		if (fileManager.contains(t.getFilename())) {
			boolean isChecksumOK = ChecksumUtils.verifyChecksum(t.getUsername(), fileManager.getFile(t.getFilename()), fileManager.getVersion(t.getFilename()), t.getChecksum());
			if (!isChecksumOK) return new MessageResponse("Checksum failed");
			return new DownloadFileResponse(t, fileManager.getFileContent(t
					.getFilename()));
		}
		return new MessageResponse("File not found");
	}

	@Override
	public Response info(InfoRequest request) throws IOException {
		String filename = request.getFilename();
		System.out.println("InfoRequest " + filename);
		if (fileManager.contains(filename))
			return new InfoResponse(filename, fileManager.getFileSize(filename));
		return new MessageResponse("File does not exist");
	}

	@Override
	public Response version(VersionRequest request) throws IOException {
		String filename = request.getFilename();
		if (fileManager.contains(filename))
			return new VersionResponse(filename,
					fileManager.getVersion(filename));
		return new MessageResponse("File does not exist");
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		fileManager.storeFile(request.getFilename(), request.getContent());
		return new MessageResponse("Upload OK");
	}

	@Override
	public void close() throws IOException {
		active = false;
		tcpSocketListener.close();
		udpSocketSender.close();
//		fileServerCLI.close(); // closed from CLI - or else deadlock
		connectionPool.close();
	}

	/**
	 * Unused
	 */
	@Override
	@Deprecated
	public void handleNewUDPConnection(DatagramPacket p) {
	}

	@Override
	public boolean isUDPActive() {
		return active;
	}

	@Override
	public void handleNewTCPConnection(Socket s) {
		connectionPool.addMessagingConnection(new SimpleTCPMessagingConnection(this, s));
//		try {
////			connectionPool.addMessagingConnection(new TCPMessagingConnection(
////					this, s));
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}

	@Override
	public boolean isTCPActive() {
		return active;
	}

	@Override
	public synchronized Response handleRequest(Socket source, Object request) {
		System.out.println("Handle new request: " + request.getClass().getName());
		try {
			switch (FileServerRequestTypes.valueOf(request.getClass()
					.getSimpleName())) {
			case DownloadFileRequest:
				return download((DownloadFileRequest) request);
			case InfoRequest:
				return info((InfoRequest) request);
			case ListRequest:
				return list();
			case UploadRequest:
				return upload((UploadRequest) request);
			case VersionRequest:
				return version((VersionRequest) request);
			default:
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new MessageResponse("invalid command");
	}

}
