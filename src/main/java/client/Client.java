package client;

import message.Response;
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

    private UUID sid;

    /**
     * main function
     */
    public static void main(String[] args) {
        Client client = new Client("client");
        client.run();
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
    }

    private void run() {
        // set up thread pool
        pool = Executors.newFixedThreadPool(10);

        // connect to proxy
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
            logger.info("Shutting down client.");
            return;
        } catch(IOException x) {
            logger.info("Coudln't connect to proxy.");
            logger.info("Shutting down client.");
            return;
        } 

        // set up shell
        shell = new Shell(name, System.out, System.in);
        shell.register(new ClientCli());
        logger.info("Starting the shell.");
        Future shellfuture = pool.submit(shell);

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

        logger.info("Shutting down client.");
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
                    if(resp.getType() == LoginResponse.Type.SUCCESS) {
                        sid = resp.getSid();
                        logger.debug("Got sid " + sid);
                    } else if (resp.getType() == 
                               LoginResponse.Type.WRONG_CREDENTIALS) {
                        logger.debug("Credentials are wrong.");
                    } else if (resp.getType() ==
                               LoginResponse.Type.IS_LOGGED_IN) {
                        logger.debug("Already logged in.");
                    }
                    return resp;
                }
                else {
                    logger.error("Login response corrupted.");
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
            LogoutRequest req = new LogoutRequest();
            oos.writeObject(req);

            MessageResponse resp = null;
            try {
                Object o = ois.readObject();
                if(o instanceof MessageResponse) {
                    resp = (MessageResponse) o;
                    logger.debug(resp.toString());
                }
                else {
                    logger.error("Logout response corrupted.");
                }
            } catch (ClassNotFoundException x) {
                logger.info("Class not found.");
                }
            return resp;
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
            //logger.debug("muuuh");
            // proxy test
            String muh = new String("muuuh");
            oos.writeObject(muh);
            try {
                Object o = ois.readObject();
                if(o instanceof MessageResponse) {
                    MessageResponse mresp = (MessageResponse) o;
                    logger.info(mresp.getMessage());
                }
            } catch (ClassNotFoundException x) {
                logger.info("Class not found.");
            }
        }
    }
}
