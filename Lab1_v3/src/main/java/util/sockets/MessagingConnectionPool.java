package util.sockets;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MessagingConnectionPool implements Closeable {
	
	private ExecutorService executor;
	
	/**
	 * Initializes poolsize with a default size of 10
	 */
	public MessagingConnectionPool() {
		executor = Executors.newFixedThreadPool(10);
	}
	
	/**
	 * 
	 * @param size number of active threads
	 */
	public MessagingConnectionPool(int size) {
		executor = Executors.newFixedThreadPool(size);
	}
	
	/**
	 * Add a TCPMessagingConnection to the pool
	 * @param connection
	 */
	public void addMessagingConnection(IConnection connection) {
		try {
			executor.execute(connection);
		} catch (RejectedExecutionException e) {
			// sometimes thrown when ant test was started a 2nd time
		}
	}

	@Override
	public void close() throws IOException {
		executor.shutdownNow();
	} 

}
