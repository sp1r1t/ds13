package proxy.impl.users;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.UserInfo;

public class UserManager {
	
	private Map<String, User> userMap = new HashMap<String, User>();
	
	public void addUser(User user) {
		userMap.put(user.getUsername(), user);
	}
	
	/**
	 * 
	 * @param port
	 * @return returns null if User was not found
	 */
	public User getUser(int port) {
		for (User u: userMap.values()) {
			if (u.getPort() == port)
				return u;
		}
		return null;
	}
	
	public boolean isLoggedIn(String username) {
		return userMap.get(username).isLoggedIn();
	}
	
	public List<UserInfo> getUserInfoList() {
		List<UserInfo> result = new ArrayList<UserInfo>();
		for (User u: userMap.values()) 
			result.add(u.getUserInfo());
		return result;
	}
	
	public boolean logIn(Socket source, String username, String password) {
		if (userMap.containsKey(username)) {
			User u = userMap.get(username);
			if (password.equals(u.getPassword())) {
				u.setLoggedIn(true);
				u.setPort(source.getPort());
				return true;
			}
		}
		return false;
	}
	
	public boolean logOut(String username) {
		User u = userMap.get(username);
		if (u == null) return false;
		u.setLoggedIn(false);
		return true;
	}
	
	public long buyCredits(String username, long credits) {
		User user = userMap.get(username);
		long currentCredits = user.getUserInfo().getCredits();
		user.setCredits(currentCredits+credits);
		return user.getUserInfo().getCredits();
	}

}
