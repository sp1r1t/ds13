package util;

import cli.Shell;
import client.IClientCli;
import client.impl.Client;
import proxy.IProxyCli;
import proxy.impl.ProxyServer;
import server.IFileServerCli;
import server.impl.FileServer;

/**
 * Provides methods for starting an arbitrary amount of various components.
 */
public class ComponentFactory {
	/**
	 * Creates and starts a new client instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	public IClientCli startClient(Config config, Shell shell) throws Exception {
		// create a new client instance (including a Shell) and start it
		return new Client(config, shell);
	}

	/**
	 * Creates and starts a new proxy instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	public IProxyCli startProxy(Config config, Shell shell) throws Exception {
		// create a new proxy instance (including a Shell) and start it
		return new ProxyServer(config, shell).getProxyServerCLI();
	}

	/**
	 * Creates and starts a new file server instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	@SuppressWarnings("resource")
	public IFileServerCli startFileServer(Config config, Shell shell) throws Exception {
		// create a new file server instance (including a Shell) and start it
		return new FileServer(config, shell).getFileServerCLI();
	}
}
