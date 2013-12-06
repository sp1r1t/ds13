package proxy.impl.users;

import model.UserInfo;

/**
 * TODO: optimize
 * @author rakaris
 *
 */
public class User {
	
	private final String username;
	private final String password;
	private UserInfo userInfo;
	private int port;
	
	public User(String username, String password, long credits) {
		super();
		this.username = username;
		this.password = password;
		userInfo = new UserInfo(username, credits, false);
	}

	public boolean isLoggedIn() {
		return userInfo.isOnline();
	}

	public void setLoggedIn(boolean loggedIn) {
		userInfo = new UserInfo(username, userInfo.getCredits(), loggedIn);
	}

	public UserInfo getUserInfo() {
		return userInfo;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
	
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public void setCredits(long credits) {
		userInfo = new UserInfo(username, credits, userInfo.isOnline());
	}

	@Override
	public String toString() {
		return "User [username=" + username + ", password=" + password
				+ ", userInfo=" + userInfo + ", port=" + port + "]";
	}

	

}
