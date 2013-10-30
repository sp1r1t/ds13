package proxy;

import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.UploadRequest;
import message.request.LoginRequest;
import message.request.BuyRequest;
import message.request.DownloadTicketRequest;
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

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;

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

/**
 * The Proxy
 */
public class Proxy implements IProxy{
    /**
     * private variables
     */
    // name of the server
    private String name;

    // stop signal
    private boolean stop = false;

    // udp socket for alive packages
    protected DatagramSocket aliveSocket;

    // tcp socket for client connections
    protected ServerSocket serverSocket;

    // a list of all users
    protected ArrayList<User> users;

    // a list of all fileservers
    protected ArrayList<Integer> fileservers;

    // a list of all threads;
    protected ArrayList<Thread> threads;

    // the proxy shell
    protected Shell shell;


    //* everything below is read from the config file *//

    // time interval after which a fileserver is set offline
    protected Integer timeout;

    // period in ms to check for timeouts
    private Integer checkPeriod;

    // TCP port to listen for clients
    protected Integer tcpPort;

    // UDP port to listen for keepAlive packages
    protected Integer udpPort;

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
            System.err.println("Proxy::Proxy: [Fatal] Config " + 
                               key + ".properties does not exist.");
            } else {
            System.err.println("Proxy::Proxy: [Fatal] Key " + 
                               key + " is not defined.");
            }
            System.exit(1);
        }

        // create lists
        users = new ArrayList<User>();
        threads = new ArrayList<Thread>();

        System.err.println(name + " configured, starting services.");
        
        try {
            run();
        }
        catch (IOException x) {
            System.err.println("Proxy::Proxy Ex: Caught IOException");
        }
            
    }

    @Override
    public LoginResponse login(LoginRequest request) throws IOException {
        return new LoginResponse(LoginResponse.Type.SUCCESS);
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
        return new MessageResponse("");
    }

    /**
     * Entry function for running the services
     */
    private void run() throws IOException {
        // read user config
        readUserConfig();

        // give birth to shell thread and start it
        shell = new Shell(name, System.out, System.in);
        shell.register(new ProxyCli());
        Thread shellThread = new Thread(shell);
        shellThread.setName("shellThread");
        System.err.println("Proxy::run: Starting the shell.");
        shellThread.start();

        // give birth to alive thread and start it
        Thread keepAliveListener = new Thread(new KeepAliveListenerThread());
        keepAliveListener.setName("keepAliveListener");
        threads.add(keepAliveListener);
        System.err.println("Proxy::run: Starting to listen for keep alive " +
                           "messages.");
        keepAliveListener.start();


/*
// start listening for connections
System.out.println("Creating server socket.");
try {
serverSocket = new ServerSocket(tcpPort);
} 
catch (IOException e) {
System.out.println("FileServer::run: Could not listen on port: " + tcpPort);
}

// accept connection
Socket clientSocket = null;
int i = 0;
while(!stop) {
System.out.println("Waiting for " + i + ". client.");
try {
clientSocket = serverSocket.accept();
} 
catch (IOException e) {
System.out.println("FileServer::run: Accept failed: " + tcpPort);
}
            
Thread con = new Thread(new ProxyConnection(clientSocket));
con.setName("ProxyConnection" + i);
con.start();
threads.add(con);
i = i + 1;
}

// stop threads
while(!threads.isEmpty()) {
Thread cur = threads.get(0);
if(cur != null) {
cur.interrupt();
}
threads.remove(0);
}

// close socket
serverSocket.close();*/
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
                System.err.println("Reading user config.");
                while(reader.ready()) {
                    String line = reader.readLine();
                    Matcher m = p.matcher(line);
                    
                    // extract properties and values
                    if(m.matches())
                    {
                        Scanner scanner = new Scanner(line);
                        String username = scanner.findInLine("^[a-z0-9]*");
                        usernames.add(username);
                    }

                }
            }
        catch (IOException x) {
            System.err.format("FileServer::readConfigFile: IOException: %s%n", x);
        }

        // create user db
        Iterator<String> it = usernames.iterator();
        Config config = new Config("user");
        try{
            while(it.hasNext()) {
                String username = it.next();
                System.out.println("Adding user " + username);
                users.add(new User(username,
                                   config.getString(username + ".password"),
                                   config.getInt(username + ".credits")));
                // debug
                users.get(users.size() - 1).print();
            }
        }
        catch (Exception x) {
            System.err.println("FileServer::readConfigFile: Error: Your user config " +
                               "is corrupted. Make sure you have " +
                               "supplied all necessary variables.");
            return 1;
        }
        return 0;
    }

    private class KeepAliveListenerThread implements Runnable {
        /**
         * Constructor
         */
        public KeepAliveListenerThread(){
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

                try{
                    while(true) {
                        aliveSocket.receive(packet);
                        wrapper = ByteBuffer.wrap(packet.getData());
                        Integer port = wrapper.getInt();
                        System.out.println("FS offset: " + packet.getOffset());
                        System.out.println("FS port: " + port);
                    } 
                } catch (IOException x) {
                    System.err.println("KeepAliveListenerThread::run: " +
                                       "Interrupted. closing...");
                }

                aliveSocket.close();

/*        // FS code
          String msg = new String(String.valueOf(tcpPort));
          byte[] buf = new byte[msg.length()];
          buf = msg.getBytes();
                
          InetAddress address = InetAddress.getByName(proxy);
                
          DatagramPacket packet = 
          new DatagramPacket(buf, buf.length, address, udpPort); 

          // send keep alive      
          while(!Thread.interrupted()){
          aliveSocket.send(packet);
          Thread.sleep(alivePeriod);
          }
          // close aliveSocket
          aliveSocket.close();

*/

            }
            catch (IOException x) {
                System.out.println("KeepAliveThread::run: Ex: IO Exception thrown.");
            }
        }
    }


    private class ProxyConnection implements Runnable {
        /** 
         * member variables
         */
        Socket clientSocket;

        /** 
         * Constructor
         */
        public ProxyConnection(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        /**
         * run method
         */
        public void run() {
            try{
                // talk
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader
                    (new InputStreamReader(clientSocket.getInputStream()));
                
                String inputLine, outputLine;
                
                // initiate conversation with client
                outputLine = "sup?";
                out.println(outputLine);
                
                while ((inputLine = in.readLine()) != null) {   
                    outputLine = "cool";
                    out.println(outputLine);
                    if (inputLine.equals("bye"))
                        break;
                }
                
                // clean up
                out.close();
                in.close();
                clientSocket.close();
            }
            catch (IOException x) {
                System.err.println("ProxyConnection::run: Ex: Caugth IOException");
            }
            catch (Exception x) {
                System.err.println("ProxyConnection::run: Ex: " + x.getMessage());
            }
        }
    }

    class ProxyCli implements IProxyCli {
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
            System.out.println("ProxyCli::exit: Exiting shell.");

            // close shell
            shell.close();
            
            // close System.in (blocking)
            System.in.close();

            // stop threads
            for(Thread t : threads) {
                t.interrupt();
            }

            // close sockets
            //serverSocket.close();
            aliveSocket.close();
            return new MessageResponse("Shutdown proxy.");
        }

        @Command
        public void muh() throws IOException {
            System.out.println("muuuhhh");
        }
    }
}