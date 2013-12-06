package proxy.impl.fileservers;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import proxy.impl.users.User;
import util.ChecksumUtils;
import message.Request;
import message.Response;
import message.request.DownloadTicketRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.VersionResponse;
import model.DownloadTicket;
import model.FileServerInfo;

/**
 * Handles most of the communication between the proxy and the fileservers
 * @author rakaris
 *
 */
public class FileServerManager implements Runnable, Closeable {
	
	/*
	 *  	time in ms after which a fileserver is set offline
	 * 		fileserver.timeout=3000
	 */
	private int fileserver_timeout;

	/*
	 * period in ms to check for timeouts
	 * fileserver.checkPeriod=1000
	 */
	private int fileserver_checkperiod;
	
	private boolean watchFileServerStatus = true;
	
	private Map<Integer, FileServerData> fileServerMap = new ConcurrentHashMap<Integer, FileServerData>();

	public FileServerManager(int fileserver_timeout,
			int fileserver_checkperiod) {
		super();
		this.fileserver_timeout = fileserver_timeout;
		this.fileserver_checkperiod = fileserver_checkperiod;
	}
	
	public void handleIsAliveNotification(DatagramPacket p) {
		FileServerData fsd = fileServerMap.get(p.getPort()); 
		
		// !alive 1234
		String[] content = new String(p.getData()).trim().split(" ");
		String cmd = content[0];
		int tcpPort = Integer.valueOf(content[1]);
//		System.out.println(new String(p.getData()));
		
		if (!cmd.equals("!alive"))
			return;
		
		if (fsd != null) { // if server exists in list
			// set online status to true
			if (!fsd.isOnline()) fsd.setOnline(true);
			// reset time
			fsd.setOnlineTime(0);
			
		}
		else { // new: set online to true, time to 0, usage to 0
			fileServerMap.put(p.getPort(), new FileServerData(p.getAddress(), tcpPort, 0, true, 0, tcpPort, p.getAddress()));
		}
	}
	
	private void updateOnlineDuration(int duration) {
		for (FileServerData fsd: fileServerMap.values()) {
			int newValue = fsd.getOnlineTime() + duration;
			fsd.setOnlineTime(newValue);
			if (newValue >= fileserver_timeout) {
				fsd.setOnline(false); // go offline
			}
		}
	}
	
	public List<FileServerInfo> getFileServerList() {
		List<FileServerInfo> result = new ArrayList<FileServerInfo>();
		for (FileServerData fsd: fileServerMap.values())
			result.add(fsd.getFileServerInfo());
		return result;
	}
	
	public Collection<FileServerData> getFileServerData() {
		return fileServerMap.values();
	}
	
	public Set<String> getAllFileNames() { // TODO huebsch machen ?
		Set<String> fileList = new HashSet<String>();
		for (Set<String> s: getAllFileNamesWithServers().values())
			fileList.addAll(s);
		return fileList;
	}
	
	public Map<FileServerData, Set<String>> getAllFileNamesWithServers() {
		Map<FileServerData, Set<String>> map = new HashMap<FileServerData, Set<String>>();
		for (FileServerData fsd: fileServerMap.values()) {
			if (!fsd.isOnline()) continue;
			ListResponse lr = (ListResponse) sendTCPRequest(new ListRequest(), fsd.getiNetAddress(), fsd.getPort());
			map.put(fsd, lr.getFileNames());
		}
		return map;
	}
	
	public Response getDownloadTicketResponse(DownloadTicketRequest request, User user) { // TODO asynchron
		
		// find server by lowest usage
		// must have file
		Map<FileServerData, Set<String>> map = getAllFileNamesWithServers();
		FileServerData fsWithLowestUsage = null;
		for (Entry<FileServerData, Set<String>> e: map.entrySet()) {
			
			if (!e.getValue().contains(request.getFilename()))
				continue;
			
			// first run only
			if (fsWithLowestUsage == null) {
				fsWithLowestUsage = e.getKey();
				continue;
			}
			
			if (e.getKey().getFileServerInfo().getUsage() < fsWithLowestUsage.getFileServerInfo().getUsage())
				fsWithLowestUsage = e.getKey();
		}
		// check, ob datei vorhanden
		if (fsWithLowestUsage == null)
			return new MessageResponse("No such file was found on the fileservers");
		
		// get DownloadTicket from fileserver
		// InfoResponse
		Response infoResponse = sendTCPRequest(new InfoRequest(request.getFilename()), fsWithLowestUsage.getiNetAddress(), fsWithLowestUsage.getPort());
		if (!(infoResponse instanceof InfoResponse)) return infoResponse; 
		
		// VersionResponse
		Response versionResponse = sendTCPRequest(new VersionRequest(request.getFilename()), fsWithLowestUsage.getiNetAddress(), fsWithLowestUsage.getPort());
		if (!(versionResponse instanceof VersionResponse)) return versionResponse; 
		
		String checksum = ChecksumUtils.generateChecksum(user.getUsername(), request.getFilename(), ((VersionResponse) versionResponse).getVersion(), ((InfoResponse) infoResponse).getSize());

		// check, ob user sich den download leisten kann
		if (user.getUserInfo().getCredits() < ((InfoResponse) infoResponse).getSize())
			return new MessageResponse("Not enough credits available");
		
		// credits anpassen
		user.setCredits((user.getUserInfo().getCredits()-((InfoResponse) infoResponse).getSize()));
		
		// usage anpassen
		fsWithLowestUsage.setUsage(fsWithLowestUsage.getFileServerInfo().getUsage()+((InfoResponse) infoResponse).getSize());
		
		DownloadTicket dt = new DownloadTicket(user.getUsername(), request.getFilename(), checksum, fsWithLowestUsage.getiNetAddress(), fsWithLowestUsage.getPort()); 
		return new DownloadTicketResponse(dt);
	}
	
	// usage should be updated when proxy chooses download/upload for that server
//	private void updateUsageData() {
//	}
	
	public MessageResponse upload(UploadRequest request, User user) { // TODO asynchron
		// upload to all fileservers
		InfoResponse ir = null;
		for (FileServerData fsd: fileServerMap.values()) {
			if (!fsd.isOnline())
				continue;
			MessageResponse r = (MessageResponse) sendTCPRequest(request, fsd.getiNetAddress(), fsd.getPort());
			if (!r.getMessage().equals("Upload OK")) 
				return new MessageResponse("Upload failed");
			if (ir == null)
				ir = (InfoResponse) sendTCPRequest(new InfoRequest(request.getFilename()), fsd.getiNetAddress(), fsd.getPort());
			// update usage NOT ?
		}
		
		// increase user credits by double file size
		if (ir == null)
			return new MessageResponse("An error occured while uploading");
		user.setCredits(user.getUserInfo().getCredits()+(ir.getSize()*2));
		
		return new MessageResponse("Upload was successful");
	}

	@Override
	public void close() throws IOException {
		watchFileServerStatus = false;
	}

	@Override
	public void run() {
		while(watchFileServerStatus) {
			try {
				Thread.sleep(fileserver_checkperiod);
				updateOnlineDuration(fileserver_checkperiod);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
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
		System.out.println("Sending TCP request to: " + address + ":" + port + " " + request.getClass().getName() + " :: " + request.toString());
		
		Socket s = null;
		ObjectOutputStream socketObjectOutputStream = null;
		ObjectInputStream socketObjectInputStream = null;
		
		try {
			s = new Socket(address, port);
			socketObjectInputStream = new ObjectInputStream(s.getInputStream());
			socketObjectOutputStream = new ObjectOutputStream(s.getOutputStream());
			
			socketObjectOutputStream.writeObject(request);
			socketObjectOutputStream.flush();
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
		System.out.println("Reply ("+response.getClass().getName()+"): "+response.toString());
		return response;
	}
	
	
	
}
