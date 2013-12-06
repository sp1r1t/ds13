package proxy.impl;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

import cli.Shell;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.DownloadTicketRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.UploadRequest;
import message.response.DownloadTicketResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.LoginResponse.Type;
import message.response.MessageResponse;
import proxy.IProxy;
import proxy.impl.fileservers.FileServerData;
import proxy.impl.fileservers.FileServerManager;
import proxy.impl.model.ProxyRequestTypes;
import proxy.impl.users.User;
import proxy.impl.users.UserManager;
import server.impl.FileServer;
import util.Config;
import util.files.FileManager;
import util.sockets.ITCPConnectionHandler;
import util.sockets.ITCPMessagingConnectionHandler;
import util.sockets.IUDPConnectionHandler;
import util.sockets.MessagingConnectionPool;
import util.sockets.TCPMessagingConnection;
import util.sockets.TCPSocketListener;
import util.sockets.UDPSocketListener;

public class ProxyServer implements Closeable, IProxy, ITCPMessagingConnectionHandler, ITCPConnectionHandler, IUDPConnectionHandler{
	
	/*
	 * proxy properties
	 */
	private final static String PROP_TCPPORT = "tcp.port";
	private final static String PROP_UDPPORT = "udp.port";
	private final static String PROP_FILESERVER_TIMEOUT = "fileserver.timeout";
	private final static String PROP_FILESERVER_CHECKPERIOD = "fileserver.checkPeriod";
	
	private int tcp_port;
	private int udp_port;
	private int fileserver_timeout;
	private int fileserver_checkperiod;
	
	/*
	 * user properties
	 */
	// TODO dynamically read names
//	private final static String PROP_ALICE_PASSWORD = "alice.password";
//	private final static String PROP_ALICE_CREDITS = "alice.credits";
//	private final static String PROP_BILL_PASSWORD = "bill.password";
//	private final static String PROP_BILL_CREDITS = "bill.credits";
	
	private UserManager userManager = new UserManager();
	
	private FileServerManager fileServerManager;
	
	private ProxyServerCLI proxyServerCLI;
	
	private TCPSocketListener tcpSocketListener;
	private UDPSocketListener udpSocketListener;
	
	private MessagingConnectionPool connectionPool = new MessagingConnectionPool();
	
	private boolean active = true;
	
	private User activeUser; // TODO auslagern
	private Socket activeSource; // TODO auslagern

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			new ProxyServer(new Config("proxy"), new Shell("proxy", System.out,
					System.in));
		} else {
			new ProxyServer(new Config(args[0]), new Shell(args[0], System.out,
					System.in));
		}
	}
	
	public ProxyServer(Config config, Shell shell) throws IOException {
		/*
		 * Config
		 */
		tcp_port = config.getInt(PROP_TCPPORT);
		udp_port = config.getInt(PROP_UDPPORT);
		fileserver_timeout = config.getInt(PROP_FILESERVER_TIMEOUT);
		fileserver_checkperiod = config.getInt(PROP_FILESERVER_CHECKPERIOD);
		
		/*
		 * UserManager
		 */
		Config config_user = new Config("user");
		
		Set<String> nameSet = new HashSet<String>();
		
		for (String s: ResourceBundle.getBundle("user").keySet()) {
			if (!s.trim().isEmpty()) 
				nameSet.add(s.substring(0,s.indexOf('.')));
		}
		
		for (String s: nameSet) {
			String password = config_user.getString(s+".password");
			int credits = config_user.getInt(s+".credits");
			userManager.addUser(new User(s, password, credits));
//			System.out.println("User added: " + s + " " + password + " " + credits);
		}
		
		/*
		 * FileServerManager
		 */
		fileServerManager = new FileServerManager(fileserver_timeout, fileserver_checkperiod);
		new Thread(fileServerManager).start();
		
		/*
		 * CLI
		 */
		proxyServerCLI = new ProxyServerCLI(this, shell);
		
		/*
		 * Sockets
		 */
		tcpSocketListener = new TCPSocketListener(this, new ServerSocket(tcp_port));
		new Thread(tcpSocketListener).start();
		
		udpSocketListener = new UDPSocketListener(this, new DatagramSocket(udp_port));
		new Thread(udpSocketListener).start();
		
		System.out.println(toString());
	}
	
	@Override
	public String toString() {
		return "ProxyServer [tcp_port=" + tcp_port + ", udp_port=" + udp_port + ", fileserver_timeout="
				+ fileserver_timeout + ", fileserver_checkperiod=" + fileserver_checkperiod + ", userManager="
				+ userManager + ", fileServerManager=" + fileServerManager + ", proxyServerCLI=" + proxyServerCLI
				+ ", tcpSocketListener=" + tcpSocketListener + ", udpSocketListener=" + udpSocketListener
				+ ", connectionPool=" + connectionPool + ", active=" + active + ", activeUser=" + activeUser
				+ ", activeSource=" + activeSource + "]";
	}

	@Override
	public void handleNewUDPConnection(DatagramPacket p) {
		fileServerManager.handleIsAliveNotification(p);
	}

	@Override
	public boolean isUDPActive() {
		return active;
	}

	@Override
	public void handleNewTCPConnection(Socket s) {
		try {
			connectionPool.addMessagingConnection(new TCPMessagingConnection(this, s));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isTCPActive() {
		return active;
	}

	@Override
	public synchronized Response handleRequest(Socket source, Object request) throws IOException {
		activeUser = userManager.getUser(source.getPort()); // TODO auslagern!!
		activeSource = source;
		if (activeUser == null || !activeUser.isLoggedIn()) {
			if (request instanceof LoginRequest) {
				return login((LoginRequest) request);
			}
		}
		else {
			// TODO auslagern
			switch (ProxyRequestTypes.valueOf(request.getClass().getSimpleName())) {
			case BuyRequest:
				return buy((BuyRequest) request);
			case CreditsRequest:
				return credits();
			case DownloadTicketRequest:
				return download((DownloadTicketRequest) request);
			case ListRequest:
				return list();
			case LogoutRequest:
				return logout();
			case UploadRequest:
				return upload((UploadRequest) request);
			default:
				return new MessageResponse("invalid command"); 
			}
			
		}
		return new MessageResponse("Login first");
	}

	@Override
	public LoginResponse login(LoginRequest request) throws IOException {
		boolean result = userManager.logIn(activeSource, request.getUsername(), request.getPassword());
		return new LoginResponse(result ? Type.SUCCESS : Type.WRONG_CREDENTIALS);
	}

	@Override
	public Response credits() throws IOException {
		return new MessageResponse("You have " + activeUser.getUserInfo().getCredits() + " credits left.");
	}

	@Override
	public Response buy(BuyRequest credits) throws IOException {
		return new MessageResponse("You have now " + userManager.buyCredits(activeUser.getUsername(), credits.getCredits()) + " credits.");
	}

	@Override
	public Response list() throws IOException {
		return new ListResponse(fileServerManager.getAllFileNames());
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException { // TODO verschieben nach filemanager
//		Set<String> allFileNames = fileServerManager.getAllFileNames();
//		if (!allFileNames.contains(request.getFilename()))
//			throw new FileNotFoundException("No such file was found on fileservers");
//		return new DownloadTicketResponse(fileServerManager.getDownloadTicket(request.getFilename(), activeUser));
		return fileServerManager.getDownloadTicketResponse(request, activeUser);
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		return fileServerManager.upload(request, activeUser);
	}

	@Override
	public MessageResponse logout() throws IOException {
		return userManager.logOut(activeUser.getUsername()) ? new MessageResponse("Successfully logged out.") : new MessageResponse("Logout unsuccessful");
	}

	@Override
	public void close() throws IOException {
		active = false;
		fileServerManager.close();
//		proxyServerCLI.close(); // closed from cli - or else deadlock
		tcpSocketListener.close();
		udpSocketListener.close();
		connectionPool.close();
	}

	public UserManager getUserManager() {
		return userManager;
	}

	public FileServerManager getFileServerManager() {
		return fileServerManager;
	}

	public ProxyServerCLI getProxyServerCLI() {
		return proxyServerCLI;
	}
	

}
