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
public class Client implements IClientCli {
    /**
     * private variables
     */
    // name of the client
    private String name;

    private Logger logger;

    private String downloadDir;

    private String proxy;

    private Integer tcpPort;

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
        Socket proxySocket = null;
        try {
            proxySocket = new Socket(proxy, tcpPort);

            PrintWriter out = 
                new PrintWriter(proxySocket.getOutputStream(), true);

            BufferedReader in = 
                new BufferedReader(
                    new InputStreamReader(proxySocket.getInputStream()));

            BufferedReader stdIn = 
                new BufferedReader(
                    new InputStreamReader(System.in));

            System.out.println("echo: " + in.readLine());
            out.println("hi");
            Thread.sleep(20);
            System.out.println("echo: " + in.readLine());
            out.println("bye\n");

            /*
              String userInput;
              while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                System.out.println("echo: " + in.readLine());
                }*/

            proxySocket.close();
        } catch(UnknownHostException x) {
            logger.info("Host not known.");
            return;
        } catch(IOException x) {
            logger.info("Caught IOException.");
        } catch(InterruptedException x) {
            logger.info("Caught Interrupted");
        }
        logger.info("Shutting down.");
    }

    public LoginResponse login(String username, String password)
        throws IOException {
        return null;
    }

    public Response credits() throws IOException {
        return null;
    }

    public Response buy(long credits) throws IOException {
        return null;
    }

    public Response list() throws IOException {
        return null;
    }

    public Response download(String filename) throws IOException {
        return null;
    }

    public MessageResponse upload(String filename) throws IOException {
        return null;
    }

    public MessageResponse logout() throws IOException {
        return null;
    }
    
    public MessageResponse exit() throws IOException {
        return null;
    }
}
