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

import java.io.IOException;
import java.io.BufferedReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.Charset;

/**
 * The Server
 */
public class FileServer implements IFileServer {
    /**
     * private variables
     */
    // name of the server
    private String name;

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


    public static void main(String[] args) {
        String name = new String("fs1");
        FileServer fs1 = new FileServer(name);
        if(fs1.readConfigFile() != 0) {
            System.err.println("FATAL: stopping server");
        }
        System.err.println(name + " configured, starting services.");

        return;
    }

    /**
     * Constructor
     */
    public FileServer(String name) {
        this.name = name;
    }

    /**
     * Read variables from the config file.
     * The file needs to be named <name>.properties
     */
    public int readConfigFile() {
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
                System.err.println("Reading config for " + name + ".");
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
                        System.err.println("  " + property + " = " +
                            value);
                    }

                }
            }
        catch (IOException x) {
            System.err.format("IOException: %s%n", x);
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
            System.err.println("Error: Your config file for the server " +
                               name + " is corrupted. Make sure you have " +
                               "supplied all necessary variables.");
            return 1;
        }
        return 0;
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

}