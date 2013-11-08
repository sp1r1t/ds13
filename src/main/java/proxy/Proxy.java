package proxy;

import message.Response;
import message.Request;
import message.request.*;
import message.response.*;

import model.DownloadTicket;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;

import java.net.*;

import cli.Command;
import cli.Shell;

import util.Config;

import proxy.User;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

/**
 * The Proxy
 */
public class Proxy implements IProxy{
    /**
     * member variables
     */
    // name of the server
    private String name;

    // stop signal
    private boolean stop = false;

    // logger
    private static Logger logger;

    // a list of all users
    private ArrayList<User> users;

    // list of logged in users
    private ArrayList<User> loggedIn;

    // a list of all fileservers
    private ArrayList<Integer> fileservers;

    // the proxy shell
    private Shell shell;

    // the thread pool
    private ExecutorService pool;

    // object input stream
    private ObjectInputStream ois;


    //* everything below is read from the config file *//

    // time interval after which a fileserver is set offline
    private Integer timeout;

    // period in ms to check for timeouts
    private Integer checkPeriod;

    // TCP port to listen for clients
    private Integer tcpPort;

    // UDP port to listen for keepAlive packages
    private Integer udpPort;

    /**
     * main function
     */
    public static void main(String[] args) {
        Proxy proxy = new Proxy("proxy");
        return;
    }

    /**
     * Constructor
     */
    public Proxy(String name) {
        // set name
        this.name = name;

        // set up logger
        logger = Logger.getLogger(Proxy.class);
        BasicConfigurator.configure();
        logger.debug("Logger is set up.");

        // read config
        String key = name;
        try {
            Config config = new Config(key);
            key = "tcp.port";
            tcpPort = config.getInt(key);
            key = "udp.port";
            udpPort = config.getInt(key);
            key = "fileserver.timeout";
            timeout = config.getInt(key);
            key = "fileserver.checkPeriod";
            checkPeriod = config.getInt(key);
        }
        catch (MissingResourceException x) {
            if(key == name) {
                logger.fatal("Config " + key + 
                             ".properties does not exist.");
            } else {
                logger.fatal("Key " + key + " is not defined.");
            }
            System.exit(1);
        }

        // create lists
        users = new ArrayList<User>();
        loggedIn = new ArrayList<User>();

        logger.info(name + " configured, starting services.");
        
        try {
            run();
        }
        catch (IOException x) {
            logger.info("Caught IOException");
        }
            
    }

    @Override
    public LoginResponse login(LoginRequest request) throws IOException {

        logger.debug("Got login request: " + request.getUsername()
                     + ":" + request.getPassword());

        LoginResponse resp = null;
        for(User u : users) {
            // search matching user
            if(u.getName().equals(request.getUsername()) &&
               u.getPassword().equals(request.getPassword())) {
                if(u.login()) {
                    // successfull
                    // create new session id
                    UUID sid = UUID.randomUUID();
                    u.setSid(sid);
                    resp = new LoginResponse(
                        LoginResponse.Type.SUCCESS, sid); 
                } else {
                    // already logged in
                    resp = new LoginResponse(
                        LoginResponse.Type.IS_LOGGED_IN);
                }
            }
        }
        if(resp == null) {
            // no user found or wrong creds
            resp = new LoginResponse(
                LoginResponse.Type.WRONG_CREDENTIALS);
        }

        return resp;
    }

    @Override
    public Response credits() throws IOException {
        return new CreditsResponse(0);
    }

    @Override
    public Response buy(BuyRequest credits) throws IOException {
        return new BuyResponse(0);
    }

    @Override
    public Response list() throws IOException {
        return new ListResponse(null);
    }

    @Override
    public Response download(DownloadTicketRequest request) throws IOException {
        return new DownloadTicketResponse(null);
    }

    @Override
    public MessageResponse upload(UploadRequest request) throws IOException {
        return new MessageResponse("");
    }

    @Override
    public MessageResponse logout() throws IOException {
        // this is handled in the client connection class
        // i can't do an argumentless logout with more than one user.
        return new MessageResponse("");
    }

    /**
     * Entry function for running the services
     */
    private void run() throws IOException {
        // read user config
        readUserConfig();

        // create thread pool
        pool = Executors.newFixedThreadPool(10);

        // give birth to shell thread and start it
        shell = new Shell(name, System.out, System.in);
        shell.register(new ProxyCli());
        logger.info("Starting the shell.");
        Future shellfuture = pool.submit(shell);

        // give birth to alive thread listener and start it
        KeepAliveListener keepAliveListener = new KeepAliveListener();
        logger.info("Starting to listen for keep alive messages.");
        pool.submit(keepAliveListener);

        // create client connection listener
        ClientConnectionListener CCL = new ClientConnectionListener();
        logger.info("Starting to listen for client connections.");
        pool.submit(CCL);

        // for now join shell
        try {
            shellfuture.get();
        } catch (InterruptedException x) {
            logger.info("Caught interrupt while waiting for shell.");
        } catch (ExecutionException x) {
            logger.info("Caught ExecutionExcpetion while waiting for shell.");
        }

        // clean up
        pool.shutdownNow();
        DatagramSocket aliveSocket = keepAliveListener.getAliveSocket();
        if(aliveSocket != null)
            aliveSocket.close(); // throws io exc in alive listener
        ServerSocket serverSocket = CCL.getServerSocket();
        if(serverSocket != null)
            serverSocket.close(); // throws io exc in ccl


        logger.info("Closing main");
    }

    /**
     * Read user information from the config file.
     */
    private int readUserConfig() {
        HashSet<String> usernames = new HashSet<String>();

        // setup
        String filename = new String("user.properties");
        Path file = Paths.get("build/" + filename); 
        Charset charset = Charset.forName("UTF-8");

        // process file, read properties
        try(BufferedReader reader = Files.newBufferedReader(file,charset)){
                // create pattern to match property lines
                Pattern p = Pattern.compile("^[a-z0-9]*[.].*");

                // read file
                logger.info("Reading user config.");
                while(reader.ready()) {
                    String line = reader.readLine();
                    Matcher m = p.matcher(line);
                    
                    // extract properties and values
                    if(m.matches())
                    {
                        Scanner scanner = new Scanner(line);
                        String username = scanner.findInLine("^[a-zA-Z0-9]*");
                        usernames.add(username);
                    }

                }
            }
        catch (IOException x) {
            logger.info("IOException: %s%n", x);
        }

        // create user db
        Iterator<String> it = usernames.iterator();
        Config config = new Config("user");
        try{
            while(it.hasNext()) {
                String username = it.next();
                logger.info("Adding user " + username);
                users.add(new User(username,
                                   config.getString(username + ".password"),
                                   config.getInt(username + ".credits")));
                // debug
                users.get(users.size() - 1).print();
            }
        }
        catch (Exception x) {
            logger.error("Your user config " +
                               "is corrupted. Make sure you have " +
                               "supplied all necessary variables.");
            return 1;
        }
        return 0;
    }

    private class KeepAliveListener implements Runnable {
        /** 
         * Member variables
         */
        private Logger logger;
        private DatagramSocket aliveSocket;

        /**
         * Constructor
         */
        public KeepAliveListener(){
            logger = Logger.getLogger(KeepAliveListener.class);
        }

        /**
         * run method
         */
        public void run() {
            // configure connection
            try {
                aliveSocket = new DatagramSocket(udpPort);
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                ByteBuffer wrapper;

                logger.info("Starting to listen for packets.");
                try{
                    while(true) {
                        aliveSocket.receive(packet);
                        String data = new String(packet.getData());
                        logger.info("FS data: " + data);
                    } 
                } catch (IOException x) {
                    logger.info("Interrupted. closing...");
                }
            }
            catch (IOException x) {
                logger.info("IO Exception thrown.");
            }
            if(aliveSocket != null)
                aliveSocket.close();
        }
        
        public DatagramSocket getAliveSocket() {
            return aliveSocket;
        }
    }

    private class ClientConnectionListener implements Runnable {
        Logger logger;
        ServerSocket serverSocket;

        public ClientConnectionListener() {
            logger = Logger.getLogger(ClientConnectionListener.class);
        }

        /**
         * run method
         */
        public void run() {
            // start listening for connections
            logger.info("Creating server socket.");
            try {
                serverSocket = new ServerSocket(tcpPort);
            } 
            catch (IOException x) {
                logger.warn("Could not listen on port: " + tcpPort);
                return;
            }

            // accept connection
            try {
                for(int i = 1;; i = i+1) {
                    logger.debug("Waiting for " + i + ". client.");
                
                    Socket clientSocket = serverSocket.accept();
                    ClientConnection con = new ClientConnection(clientSocket);
                    pool.submit(con);
                }
            } catch (IOException x) {
                logger.info("Interrupted. Stopping...");
            }

            // cleanup
            try {
                serverSocket.close();
            } catch (IOException x) {
                logger.info("Caught IOException on closing socket");
            }
            logger.info("Shutting down.");
        }

        public ServerSocket getServerSocket() {
            return serverSocket;
        }
    }


    private class ClientConnection implements Runnable {
        /** 
         * member variables
         */
        private Socket clientSocket;
        private Logger logger;
        private User user;

        /** 
         * Constructor
         */
        public ClientConnection(Socket clientSocket) {
            this.clientSocket = clientSocket;
            logger = Logger.getLogger(ClientConnection.class);
        }

        /**
         * run method
         */
        public void run() {
            logger.debug(clientSocket.toString());
            try {
                // create streams
                ObjectInputStream ois = 
                    new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream oos = 
                    new ObjectOutputStream(clientSocket.getOutputStream());

                
                Request request = null;
                Response response = null;

                // listen for requests
                while(!Thread.interrupted()) {
                    // recieve request
                    Object o = ois.readObject();
                    
                    // LOGIN
                    if(o instanceof LoginRequest) {
                        if(user != null) {
                            response = new LoginResponse
                                (LoginResponse.Type.IS_LOGGED_IN);
                        } else {
                            LoginRequest lreq = (LoginRequest) o;
                            LoginResponse lresp = login(lreq);
                            if(lresp.getType() == LoginResponse.Type.SUCCESS) {
                                UUID sid = lresp.getSid();
                                user = getUserBySid(sid);
                            }
                            response = lresp;
                        }
                    }
                    // LOGOUT
                    else if (o instanceof LogoutRequest) {
                        if(user != null) {
                            logger.debug("Logging out user " + user.getName() +
                                         ".");
                            user.logout();
                            user = null;
                        }
                        response = new MessageResponse("Logged out.");
                    }
                    // TESTING REQUEST; cow says muh!!
                    else if (o instanceof String) {
                        if(user == null || user.getSid() == null) {
                            response = new MessageResponse("Ur not logged in.");
                        } else {
                            response = new MessageResponse("Ur in.");
                        }
                    }
                    
                    // send response back
                    if(response != null) {
                        oos.writeObject(response);
                    }
                }
            } catch (IOException x) {
                logger.info("Caught IOException.");
            } catch (Exception x) {
                logger.info("Caught Exception: "); 
                x.printStackTrace();
            }

            try {
                logger.debug("Closing socket.");
                clientSocket.close();
            } catch (IOException x) {
                logger.info("Caught IOException.");
            }
        }

        private User getUserBySid(UUID sid) {
            for(User u : users) {
                if(u.getSid() == sid) {
                    return u;
                }
            }
            return null;
        }
    }

    class ProxyCli implements IProxyCli {
        private Logger logger;

        public ProxyCli() {
            logger = Logger.getLogger(ProxyCli.class);
        }

        @Command
        public Response fileservers() throws IOException {
            System.out.println("TODO: list fileservers");
            return new FileServerInfoResponse(null);
        }

        @Command
        public Response users() throws IOException {
            for(User u : users) {
                u.print();
            }
            return new UserInfoResponse(null);
        }

        @Command
        public MessageResponse exit() throws IOException {
            logger.info("Exiting shell.");

            // close shell
            shell.close();
            
            // close System.in (blocking)
            System.in.close();

            return new MessageResponse("Shutdown proxy.");
        }

        @Command
        public void muh() throws IOException {
            System.out.println("muuuhhh");
        }
    }
}