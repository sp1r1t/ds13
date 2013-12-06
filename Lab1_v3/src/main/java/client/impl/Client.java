package client.impl;

import java.io.Closeable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import proxy.impl.ProxyServer;
import util.Config;
import util.files.FileManager;
import util.sockets.ITCPMessagingConnectionHandler;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadFileRequest;
import message.request.DownloadTicketRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import cli.Command;
import cli.Shell;
import client.IClientCli;

public class Client implements IClientCli, Closeable {

	/*
	 * client property names
	 */
	private static String PROP_DOWNLOADDIR = "download.dir";
	private static String PROP_PROXYHOST = "proxy.host";
	private static String PROP_PROXYTCPPORT = "proxy.tcp.port";

	/*
	 * client property values
	 */
	private String download_dir;
	private String proxy_host;
	private int proxy_tcp_port;

	/*
	 * Filemanager
	 */
	private FileManager fileManager;

	/*
	 * Shell
	 */
	private Shell clientShell = null;
	private Thread clientShellThread = null;

	/*
	 * Socket
	 */
	private Socket proxySocket = null;
	private ObjectOutputStream oos = null;
	private ObjectInputStream ois = null;

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			new Client(new Config("client"), new Shell("client", System.out, System.in));
		} else {
			new Client(new Config(args[0]), new Shell(args[0], System.out, System.in));
		}
	}

	public Client(Config config, Shell shell) throws IOException {
		/*
		 * Config
		 */
		download_dir = config.getString(PROP_DOWNLOADDIR);
		proxy_host = config.getString(PROP_PROXYHOST);
		proxy_tcp_port = config.getInt(PROP_PROXYTCPPORT);

		/*
		 * Filemanager
		 */
		fileManager = new FileManager(download_dir);

		/*
		 * Shell
		 */
		clientShell = shell;
		clientShell.register(this);
		clientShellThread = new Thread(clientShell);
		clientShellThread.start();
		
		System.out.println(toString());
	}

	@Override
	public String toString() {
		return "Client [download_dir=" + download_dir + ", proxy_host=" + proxy_host + ", proxy_tcp_port="
				+ proxy_tcp_port + ", fileManager=" + fileManager + ", clientShell=" + clientShell + ", proxySocket="
				+ proxySocket + ", oos=" + oos + ", ois=" + ois + "]";
	}

	@Override
	@Command
	public LoginResponse login(String username, String password) throws IOException {
		return (LoginResponse) sendRequestToProxy(new LoginRequest(username, password));
	}

	@Override
	@Command
	public Response credits() throws IOException {
		return sendRequestToProxy(new CreditsRequest());
	}

	@Override
	@Command
	public Response buy(long credits) throws IOException {
		return sendRequestToProxy(new BuyRequest(credits));
	}

	@Override
	@Command
	public Response list() throws IOException {
		return sendRequestToProxy(new ListRequest());
	}

	@Override
	@Command
	public Response download(String filename) throws IOException {
		
		// get ticket from proxy
		DownloadTicketRequest dtresq = new DownloadTicketRequest(filename);
		Response response = sendRequestToProxy(dtresq);
		// if response is e.g. messageresponse
		if (!(response instanceof DownloadTicketResponse))
			return response;
		
		// get download from fileserver
		DownloadFileRequest dfr_req = new DownloadFileRequest(((DownloadTicketResponse) response).getTicket());
		response = sendTCPRequest(dfr_req, dfr_req.getTicket().getAddress(), dfr_req.getTicket().getPort());
		if (!(response instanceof DownloadFileResponse))
			return response;
		
		// store file
		DownloadFileResponse dfr_resp = (DownloadFileResponse) response;
		fileManager.storeFile(dfr_resp.getTicket().getFilename(), dfr_resp.getContent());
		
		return dfr_resp;
	}

	@Override
	@Command
	public MessageResponse upload(String filename) throws IOException {
		if (!fileManager.contains(filename))
			return new MessageResponse("File not found");
		return (MessageResponse) sendRequestToProxy(new UploadRequest(filename, fileManager.getVersion(filename), fileManager.getFileContent(filename)));
	}

	@Override
	@Command
	public MessageResponse logout() throws IOException {
		MessageResponse response = (MessageResponse) sendRequestToProxy(new LogoutRequest());
		closeConnections();
		return response;
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {		// TODO message never sent
		close();
		return new MessageResponse("Client closed");
	}

	@Override
	@Command
	public void close() throws IOException {
		/*
		 * Shell
		 */
		clientShellThread.interrupt();
		clientShell.close();
		System.in.close();
		System.out.close();

		/*
		 * ProxyConnection
		 */
		closeConnections();
	}
	
	private void closeConnections() throws IOException {
		if (proxySocket != null) proxySocket.close();
		proxySocket = null;
		if (oos != null) oos.close();
		oos = null;
		if (ois != null) ois.close();
		ois = null;
	}

	/**
	 * Helper method for sending simple proxy requests
	 * 
	 * @param request
	 * @return
	 * @throws IOException
	 */
	public Response sendRequestToProxy(Request request) {
		try {
			if (proxySocket == null || oos == null || ois == null) {
				proxySocket = new Socket(proxy_host, proxy_tcp_port);
				oos = new ObjectOutputStream(proxySocket.getOutputStream());
				ois = new ObjectInputStream(proxySocket.getInputStream());
			}
		} catch (IOException e1) {
			System.out.println("Cannot connect to proxy: " + e1.getMessage());
			return null;
		}
		try {
			oos.writeObject(request);
			oos.flush();
			return (Response) ois.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Used for FileServer requests 
	 * TODO: refactor, optimize for multiple requests
	 * @param request
	 * @param address
	 * @param port
	 * @return
	 */
	protected Response sendTCPRequest(Request request, InetAddress address, int port) {
		Response response = new MessageResponse("Request failed");
		
		Socket s = null;
		ObjectOutputStream socketObjectOutputStream = null;
		ObjectInputStream socketObjectInputStream = null;
		try 
				{
			s = new Socket(address, port);
			socketObjectOutputStream = new ObjectOutputStream(s.getOutputStream());
			socketObjectInputStream = new ObjectInputStream(s.getInputStream());
			
			socketObjectOutputStream.writeObject(request);
			response = (Response) socketObjectInputStream.readObject();
		} 
		catch (IOException e) {
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		finally {
			try {
				socketObjectInputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socketObjectOutputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return response;
	}

}
