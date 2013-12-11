package proxy;

import java.util.Date;
import java.util.Calendar;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

class FileServer {
    private Logger logger;

    private String host;
    private Integer port;
    private long usage;
    private Integer tcpPort;
    private boolean online;
    private Date lastAlive;

    public FileServer(String host, Integer port,  Integer tcpPort) {
        logger = Logger.getLogger(FileServer.class);
        this.host = host;
        this.port = port;
        this.usage = 0;
        this.tcpPort = tcpPort;
        online = true;
        Calendar cal = Calendar.getInstance();
        this.lastAlive = cal.getTime();
    }

    public String getHost() {
        return host;
    }
    
    public Integer getPort() {
        return port;
    }

    public long getUsage() {
        return usage;
    }

    public Integer getTcpPort() {
        return tcpPort;
    }

    public boolean isOnline() {
        return online;
    }

    public Date getLastAlive() {
        return lastAlive;
    }

    public void setOnline() {
        this.online = true;
    }
    
    public void setOffline() {
        this.online = false;
    }

    public void setUsage(long usage) {
        this.usage = usage;
    }

    public void setLastAlive(Date lastAlive) {
        this.lastAlive = lastAlive;
    }
    
    public void print() {
        logger.debug("FileServer " + host + ":" + port);
        logger.debug("  usage: " + usage);
    }
}