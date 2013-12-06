package proxy.impl;

import java.io.Closeable;
import java.io.IOException;

import cli.Command;
import cli.Shell;
import message.Response;
import message.response.FileServerInfoResponse;
import message.response.MessageResponse;
import message.response.UserInfoResponse;
import proxy.IProxyCli;

public class ProxyServerCLI implements IProxyCli, Closeable {
	
	private ProxyServer proxyServer;
	private Shell shell;
	private Thread shellThread;

	public ProxyServerCLI(ProxyServer proxyServer, Shell shell) {
		super();
		this.proxyServer = proxyServer;
		this.shell = shell;
		shell.register(this);
		shellThread = new Thread(shell);
		shellThread.start();
	}

	@Override
	public void close() throws IOException {
		shellThread.interrupt();
		shell.close();
		System.in.close();
		System.out.close();
	}

	@Command
	@Override
	public Response fileservers() throws IOException {
		return new FileServerInfoResponse(proxyServer.getFileServerManager().getFileServerList());
	}

	@Command
	@Override
	public Response users() throws IOException {
		return new UserInfoResponse(proxyServer.getUserManager().getUserInfoList());
	}

	@Command
	@Override
	public MessageResponse exit() throws IOException {
		close();
		proxyServer.close();
		return new MessageResponse("Fileserver closed"); // TODO message never sent
	}

}
