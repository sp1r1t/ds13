package server;

import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.*;

import model.DownloadTicket;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;

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

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

/**
 * The Server
 */
public class FileServer implements IFileServer{
    /**
     * private variables
     */
    protected ServerSocket serverSocket;
    // name of the server
    private String name;

    // the thread pool
    protected ExecutorService pool;

    // logger
    private static Logger logger;

    // shell
    private Shell shell;

    // everything below is read from the config file

    // time interval to send alive packets
    private int alivePeriod;

    // file directory
    private String dir;

    // TCP port to listen for clients
    private int tcpPort;

    // address of the proxy
    private String proxy;

    // UDP port of the proxy to send alive packets to
    private int udpPort;

    /**
     * main function
     */
    public static void main(String[] args) {
        String name = new String("fs1");
        FileServer fs1 = new FileServer(name);
        return;
    }

    /**
     * Constructor
     */
    public FileServer(String name) {
        this.name = name;
        // set up logger
        logger = Logger.getLogger(FileServer.class);
        BasicConfigurator.configure();
        logger.debug("Logger is set up.");

        Config config = new Config(name);
        alivePeriod = config.getInt("fileserver.alive");
        dir = config.getString("fileserver.dir");
        tcpPort = config.getInt("tcp.port");
        proxy = config.getString("proxy.host");
        udpPort = config.getInt("proxy.udp.port");
        
/*        if(readConfigFile() != 0) {
            System.err.println("FATAL: stopping server");
            }*/
        logger.info(name + " configured, starting services.");
        
        try {
            run();
        }
        catch (IOException x) {
            logger.info("Ex: Caught IOException");
        }
            
    }

    @Override
    public Response list() throws IOException {
        return new ListResponse(new LinkedHashSet<String>());
    }

    @Override
    public Response download(DownloadFileRequest request) throws IOException {
        return new DownloadFileResponse(new DownloadTicket(), new byte[]{});
    }

    @Override
    public Response info(InfoRequest request) throws IOException {
        String filename = new String("filename.dummy");
        long size = 0;
        return new InfoResponse(filename, size);
    }

    @Override
    public Response version(VersionRequest request) throws IOException {
        String filename = new String("filename.dummy");
        int version = 0;
        return new VersionResponse(filename, version);
    }

    @Override
    public MessageResponse upload(UploadRequest request) throws IOException {
        String message = new String("Dummy Message.");
        return new MessageResponse(message);
    }


    /**
     * Entry function for running the services
     */
    private void run() throws IOException {
        // create executor service
        pool = Executors.newFixedThreadPool(10);

        // start thread to send keep_alive msgs
        logger.info("Starting KeepAlive");
        Thread keepAlive = new Thread
            (new KeepAlive(udpPort, proxy, alivePeriod, tcpPort));
        pool.submit(keepAlive);

        // start shell
        logger.info("Starting the shell.");
        shell = new Shell(name, System.out, System.in);
        shell.register(new FileServerCli());
        //Thread shellThread = new Thread(shell);
        FutureTask<String> shellFuture = new FutureTask<String>(shell, "future task askes: sup?");
        pool.submit(shellFuture);


        // start ProxyConnectionListener
        logger.info("Starting ProxyConnectionListener");
        Thread PCL = new Thread(new ProxyConnectionListener());
        pool.submit(PCL);


        // for now just join the shell. the main thread is kept alive to 
        // possibly implement respawning of unintenionally cancelled threads 
        // in the future.
        // e.g. if the keepAlive thread fails due to network problems this can
        // be handled here. or other maintainance stuff that may come.
        try {
            logger.debug(shellFuture.get());
        } catch (ExecutionException x) {
            logger.info(x.getMessage());
        } catch (InterruptedException x) {
            logger.info(x.getMessage());
        }
        
        // clean up threads
        pool.shutdownNow();
        serverSocket.close();
        
        logger.info("closing main");
    }

    /**
     * Read variables from the config file.
     * The file needs to be named <name>.properties
     */
    private int readConfigFile() {
        HashMap<String,String> properties = new HashMap<String,String>();

        // setup
        String filename = new String(name + ".properties");
        Path file = Paths.get("build/" + filename); 
        Charset charset = Charset.forName("UTF-8");

        // process file, read properties
        try(BufferedReader reader = Files.newBufferedReader(file,charset)){
                // create pattern to match property lines
                Pattern p = Pattern.compile("^[a-z0-9.]*=[a-zA-Z0-9/]*");

                // read file
                logger.info("Reading config for " + name + ".");
                while(reader.ready()) {
                    String line = reader.readLine();
                    Matcher m = p.matcher(line);
                    
                    // extract properties and values
                    if(m.matches())
                    {
                        Scanner scanner = new Scanner(line).useDelimiter("=");
                        String property = scanner.next();
                        String value = scanner.next();
                        properties.put(property,value);
                        logger.info("  " + property + " = " +
                            value);
                    }

                }
            }
        catch (IOException x) {
            System.err.format("FileServer::readConfigFile: IOException: %s%n", x);
        }

        // save properties
        try{
            alivePeriod = Integer.parseInt(properties.get("fileserver.alive"));
            dir = properties.get("fileserver.dir");
            tcpPort = Integer.parseInt(properties.get("tcp.port"));
            proxy = properties.get("proxy.host");
            udpPort = Integer.parseInt(properties.get("proxy.udp.port"));
        }
        catch (Exception x) {
            logger.error("Your config file for the server " +
                               name + " is corrupted. Make sure you have " +
                               "supplied all necessary variables.");
            return 1;
        }
        return 0;
    }

    private boolean testFileExists(String filename) {
        Path path = Paths.get(dir,filename);
        return Files.exists(path);
    }

    private boolean testEnoughCredits(String filename, int credits) {
        try {
            Path path = Paths.get(dir,filename);
            return (credits >= Files.size(path));
        }
        catch (IOException x){
            logger.info("IOException caught");
            return false;
        }
    }

    /**
     * clean exit
     */
    public void cleanExit() throws IOException {
        // close sockets
/*        if(serverSocket != null)
            serverSocket.close();
        if(clientSocket != null)
        clientSocket.close();*/
    }

    
    private class KeepAlive implements Runnable {
        /**
         * member variables
         */
        // logger
        private Logger logger;

        // udp socket for alive packages
        private DatagramSocket aliveSocket;

        // config params
        private int udpPort;
        private String proxy;
        private int alivePeriod;
        private int tcpPort;

        /**
         * Constructor
         */
        public KeepAlive(int udpPort, String proxy, 
                               int alivePeriod, int tcpPort){
            // init logger
            logger = Logger.getLogger(KeepAlive.class);

            // init params
            this.udpPort = udpPort;
            this.proxy = proxy;
            this.alivePeriod = alivePeriod;
            this.tcpPort = tcpPort;
        }

        /**
         * run method
         */
        public void run() {
            try {
                // configure connection
                aliveSocket = new DatagramSocket();
                
                String msg = new String(String.valueOf(tcpPort));
                byte[] buf = new byte[msg.length()];
                buf = msg.getBytes();


                InetAddress address = InetAddress.getByName(proxy);
                
                DatagramPacket packet = 
                    new DatagramPacket(buf, buf.length, address, udpPort); 
                
                try {
                    // send keep alive      
                    logger.debug("Starting to send keep alive messages...");
                    while(true){
                        aliveSocket.send(packet);
                        Thread.sleep(alivePeriod);
                    }
                } catch (InterruptedException x) {
                    logger.info("Recieved interrupt. Closing...");
                }

                // close aliveSocket
                aliveSocket.close();
            }
            catch (IOException x) {
                logger.info("IO Exception thrown.");
            }
        }

    }

    private class ProxyConnectionListener implements Runnable {
        Logger logger;
        //ServerSocket serverSocket;

        public ProxyConnectionListener() {
            logger = Logger.getLogger(ProxyConnectionListener.class);
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
                    Thread con = new Thread(new ProxyConnection(clientSocket));
                    con.setName("ProxyConnection" + i);
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
    }

    private class ProxyConnection implements Runnable {
        /** 
         * member variables
         */
        Socket clientSocket;
        Logger logger;

        /** 
         * Constructor
         */
        public ProxyConnection(Socket clientSocket) {
            this.clientSocket = clientSocket;
            logger = Logger.getLogger(ProxyConnection.class);
        }

        /**
         * run method
         */
        public void run() {
            logger.info("Starting Connection.");
            try (
                PrintWriter out = new PrintWriter
                    (clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader
                    (new InputStreamReader(clientSocket.getInputStream()))
                )
            {
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
            } catch (IOException x) {
                logger.info("Caught IOException");
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException x) {
                    logger.info("Caught IOException while closing socket.");
                }
            }
            
            logger.info("Closed connection.");
        }
    }

    class FileServerCli implements IFileServerCli {
        private Logger logger;
        
        public FileServerCli() {
            logger = Logger.getLogger(FileServerCli.class);
        }

        @Command
        public MessageResponse exit() throws IOException {
            logger.info("Exiting shell.");
            
            // close shell
            shell.close();
            
            // close Syste.in (blocking)
            System.in.close();
            
            return null;
        }

        @Command
        public void muh() throws IOException {
            System.out.println("muuuhhh");
        }
    }
}