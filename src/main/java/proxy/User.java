package proxy;

import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

class User {
    private Logger logger;

    private String username;
    private String password;
    private long credits;
    private boolean loggedIn = false;
    private UUID sid;

    public User(String username, String password, long credits) {
        logger = Logger.getLogger(User.class);
        sid = null;
        this.username = username;
        this.password = password;
        this.credits = credits;
    }

    String getName() {
        return username;
    }
    
    // TODO: make this unnecessary
    String getPassword() {
        return password;
    }

    long getCredits() {
        return credits;
    }
    
    boolean isLoggedIn() {
        return loggedIn;
    }

    UUID getSid() {
        return sid;
    }

    void setName(String username) {
        this.username = username;
    }

    void setCredits(long credits) {
        this.credits = credits;
    }
    
    void setPassword(String password) {
        this.password = password;
    }

    void setSid(UUID sid) {
        this.sid = sid;
    }

    boolean login() {
        if(loggedIn) {
            logger.warn("User " + username + " is already logged in.");
            return false;
        }
        loggedIn = true;
        return true;
    }

    void logout() {
        loggedIn = false;
        sid = null;
    }

    void print() {
        System.out.println("User " + username);
        System.out.println("  pw: " + password);
        System.out.println("  credits: " + credits);
    }
}