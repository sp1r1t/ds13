package client;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
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

import client.IClientCli;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

/**
 * The Client
 */
public class Client {
    /**
     * private variables
     */
    // name of the client
    private String name;

    private Logger logger;

    private String downloadDir;

    private String proxy;

    private Integer tcpPort;

    private Shell shell;
    
    private ExecutorService pool;

    private Socket proxySocket;

    private PrintWriter out;
    private BufferedReader in;

    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private String username;
    private String password;

    /**
     * main function
     */
    public static void main(String[] args) {
        Client client = new Client("client");
        return;
    }

    /**
     * Constructor
     */
    public Client(String name) {
        // set name
        this.name = name;

        // set up logger
        logger = Logger.getLogger(Client.class);
        BasicConfigurator.configure();
        logger.debug("Logger is set up.");

        // read config
        String key = name;
        try {
            Config config = new Config(key);
            key = "download.dir";
            downloadDir = config.getString(key);
            key = "proxy.host";
            proxy = config.getString(key);
            key = "proxy.tcp.port";
            tcpPort = config.getInt(key);
        } catch (MissingResourceException x) {
            if(key == name) {
                logger.fatal("Config " + key + 
                             ".properties does not exist.");
            } else {
                logger.fatal("Key " + key + " is not defined.");
            }
            System.exit(1);
        }

        run();
    }

    private void run() {
        // set up thread pool
        pool = Executors.newFixedThreadPool(10);

        // set up shell
        shell = new Shell(name, System.out, System.in);
        shell.register(new ClientCli());
        logger.info("Starting the shell.");
        Future shellfuture = pool.submit(shell);

        proxySocket = null;
        try {
            proxySocket = new Socket(proxy, tcpPort);

            oos = new ObjectOutputStream(proxySocket.getOutputStream());
            ois = new ObjectInputStream(proxySocket.getInputStream());

            out = new PrintWriter(proxySocket.getOutputStream(), true);
            in =  new BufferedReader(
                    new InputStreamReader(proxySocket.getInputStream()));

        } catch(UnknownHostException x) {
            logger.info("Host not known.");
            return;
        } catch(IOException x) {
            logger.info("Caught IOException.");
        } 

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
        try {
            proxySocket.close();
        } catch (IOException x) {
            logger.info("Caught IOException.");
        }

        logger.info("Shutting down.");
    }

    class ClientCli implements IClientCli {
        private Logger logger;

        public ClientCli() {
            logger = Logger.getLogger(ClientCli.class);
        }

        @Command
        public LoginResponse login(String username, String password)
            throws IOException {
            logger.debug("started login command");
            logger.debug("username is " + username);
            logger.debug("password is " + password);

            LoginRequest req = new LoginRequest(username, password);
            oos.writeObject(req);

            LoginResponse resp;
            try {
                Object o = ois.readObject();
                if(o instanceof LoginResponse) {
                    resp = (LoginResponse) o;
                    logger.debug(resp.getType());
                    return resp;
                }
            } catch (ClassNotFoundException x) {
                logger.info("Class not found.");
            }
            return null;
        }

        @Command
        public Response credits() throws IOException {
            return null;
        }

        @Command
        public Response buy(long credits) throws IOException {
            return null;
        }

        @Command
        public Response list() throws IOException {
            return null;
        }
 
        @Command
        public Response download(String filename) throws IOException {
            return null;
        }

        @Command
        public MessageResponse upload(String filename) throws IOException {
            return null;
        }

        @Command
        public MessageResponse logout() throws IOException {
            return null;
        }
    
        @Command
        public MessageResponse exit() throws IOException {
            logger.info("Exiting shell.");

            // close shell
            shell.close();

            // close System.in
            System.in.close();

            return new MessageResponse("Shutdown client.");
        }

        @Command
        public void muh() throws IOException {
            logger.debug("muuuh");
            // proxy test
            System.out.println("echo: " + in.readLine());
            out.println("hi");
            System.out.println("echo: " + in.readLine());
            out.println("bye\n");
        }
    }
}
