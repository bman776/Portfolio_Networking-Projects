
/**
 * WebServerClientProcess Class
 * Spawned from main WebServer thread to deal with each individual client connection accepted by server
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;
import java.net.*;

public class WebServerClientProcess extends Thread {

    //reference to spwaning main WebServer thread
    private Thread serverParentThread;

    //client socket objects
    private Socket cli_sock = null;
    private OutputStream cli_sock_out = null;
    private InputStream cli_sock_in = null;

    //the timeout value
    private int timeout;

    //boolean flags to manage Web Server Client Process behaviour and shutdown
    private boolean timeout_exceeded;
    private boolean badRequest;
    private boolean notFound;
    private boolean processing_GET;
    private boolean error_occured;

    //file reader object
    private FileInputStream buf_fr = null;

    //read buffer
    byte[] read_buffer;

    /**
     * Constructor for WebServerClientProcess thread class
     * 
     * @param pt    reference to spawning parent thread
     * @param cs    reference to client socket object created by parent thread
     * @param t     timeout value for Http get requests that WebServerClientProcess will abide by
     */
    public WebServerClientProcess(Thread pt, Socket cs, int t) throws WebServerClientProcessException {
        //get reference to spawning server parent thread
        serverParentThread = pt;

        //get client socket created by spwaning server parent thread
        cli_sock = cs;

        //get timeout value
        timeout = t;
        
        //set thread flags
        timeout_exceeded = false;
        badRequest = false;
        notFound = false;
        processing_GET = false;
        error_occured = false;

        //initialize read buffer
        read_buffer = new byte[32*1024];

        //set timeout on client socket object passed from main server thread
        try {
            cli_sock.setSoTimeout(timeout);
        } catch (SocketException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: client socket timeout could not be set");
        }
        
        //get client socket output stream
        try {
            cli_sock_out = cli_sock.getOutputStream();
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: could not get output stream for client socket");
        }

        //get client socket input stream
        try {
            cli_sock_in = cli_sock.getInputStream();
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: could not get input stream for client socket");
        }

        //UNIT TESTING
        System.out.println("Client Thread created for client @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort());
        System.out.println();
    }
    
    



    /**
     * Waits for and reads the connected clients GET request
     * 
     * @throws WebServerClientProcessException if an error occurs while attempting to read Client's GET request
     */
    private void readClientGET() throws WebServerClientProcessException {

        //DEBUGGING
        //System.out.println("Waiting for client request...");
        
        //wait for and recieve client's Http GET request
        String Http_Req = "";
        String Http_Req_endDetect = "";
        Boolean Http_Req_read = false;
        int b;
        try {

            //DEBUGGING
            /*System.out.println("Client sockets timeout should be set to: ");
            System.out.println("=========================================");
            System.out.println(cli_sock.getSoTimeout());*/

            //wait for client get request
            //(blocking read call from client socket input stream that can potentially trigger timeout here)
            //(only way out of this loop is either by timeout or a client GET request is recieved)
            while ( !(Http_Req_read) && (b = cli_sock_in.read()) != -1) {
                //recieved bytes (presumably a client GET)
                //set flag to tell main server process to leave this client process alone if server shutdown initiated
                processing_GET = true;

                Http_Req = Http_Req + (char)(b);

                if (!(Http_Req_read)) {
                    if ( (char)(b) == '\r' || (char)(b) == '\n' ) {
                        Http_Req_endDetect = Http_Req_endDetect + (char)(b);
                    } else {
                        Http_Req_endDetect = "";
                    }

                    if (Http_Req_endDetect.equals("\r\n\r\n")) {
                        Http_Req_read = true;
                    }
                }
            }
        
        } catch (SocketException e) {
            //this exception will be thrown if WebServer Client process is waiting on Client GET and a shutdown is initiated
            //(main Web Server thread will iterate through all WebServerClientProcess Threads and any currently not engaged in
            //servicing a client GET request will have thier socket closed, triggering this exception)
            if (WebServer.shutdownRequested) {
                throw new WebServerClientProcessException("");
            }

        } catch (SocketTimeoutException e) { 
            //time period to recieve client request exceeded send Status code 408 to client and terminate connection
            clientTimeout();
            
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: a problem occured trying to read client Http Request");
        }

        //recieved a GET request from client

        //UNIT TESTING
        System.out.println("Client submitted get request: ");
        System.out.println("==============================");
        System.out.println(Http_Req);
        System.out.println();

        //serve Http GET request

        serveClientGET(Http_Req);
        
    }



    /**
     * Parses and serves the recieved client Http GET request
     * 
     * @param http_req  the recieved client GET request string
     */
    private void serveClientGET(String http_req) throws WebServerClientProcessException {

        //split the GET request by its lines
        String[] GET_lines = http_req.split("\r\n");

        //extract GET header and split it into its elements
        String GET_header = GET_lines[0];

        //DEBUGGING
        /*System.out.println("HEADER LINES: ");
        for (int i = 0; i < GET_lines.length; i++) {
            System.out.println(GET_lines[i]);
        }*/

        String[] GET_header_elems = GET_header.split(" ");

        //DEBUGGING
        /*System.out.println("HEADER ELEMENTS: ");
        for (int i = 0; i < GET_header_elems.length; i++) {
            System.out.println(GET_header_elems[i]);
        }*/

        //Check syntax of GET header
        if (GET_header_elems.length != 3) {
            clientBadRequest();
        }
        if (!GET_header_elems[0].equals("GET")) {
            clientBadRequest();
        }
        if (!GET_header_elems[2].equals("HTTP/1.1")) {
            clientBadRequest();
        }

        //extract GET header object-path and build local path to requested object
        String currWorkingDir = System.getProperty("user.dir"); 
		String separator = System.getProperty("file.separator");
        String req_obj_path = GET_header_elems[1].replace("/", separator);
        String full_obj_path = null;
        if (req_obj_path.equals(separator)){
            full_obj_path = currWorkingDir + req_obj_path + "index.html";
        } else {
            full_obj_path = currWorkingDir + req_obj_path;
        }

        File req_obj = new File(full_obj_path);
        if (req_obj.exists()) {
            //serve the requested object to client
            
            //DEBUGGING
            System.out.println("Fetching object...");

            //create file reader (file input stream)
            try {
                buf_fr = new FileInputStream(full_obj_path);
            } catch (FileNotFoundException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: an error occured while trying to read from the requested object file");
            }

            //create server OK response string
            Date currentDate = new Date();
            SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
            String currentDate_formatted = dateFormatter.format(currentDate);
            String serverName = "CPSC441_Assignment3_WebServer";
            String lastModified = dateFormatter.format(req_obj.lastModified());
            String contentLength = Long.toString(req_obj.length());
            String contentType = null; 
            try {
                contentType = Files.probeContentType(req_obj.toPath());
            } catch (IOException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: a problem occured trying to discern the file type of: " + full_obj_path);
            }
            String ConnectionVal = "close";
            String Http_resp_200 = 
                "HTTP/1.1 200 Ok\r\n"+
                "Server: " + serverName + "\r\n" +
                "Date: " + currentDate_formatted + "\r\n"+
                "Last-Modified: " + lastModified + "\r\n"+
                "Content-Length: " + contentLength + "\r\n"+
                "Content-Type: " + contentType + "\r\n"+
                "Connection: " + ConnectionVal + "\r\n" + 
                "\r\n";

            //Convert server timeout response string into bytes to send out over client socket
            byte[] OkResp_bytes = null; 
            try {
                OkResp_bytes = Http_resp_200.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: could not convert Http Server Object not found response into byte");
            }
            try {
                cli_sock_out.write(OkResp_bytes);
                cli_sock_out.flush();
            } catch (IOException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: a problem occured trying to send Object not found response to client");
            }

            System.out.println("Client requested object found, sending back Ok response: ");
            System.out.println("===========================================================================");
            System.out.println(Http_resp_200);
            System.out.println();
            
            //send requested object to client
            //int b is number of bytes read from input file and written out to client
            int b; 
            try {
                // read from file reader into buffer then write from buffer to client socket output stream
                while ( (b = buf_fr.read(read_buffer)) != -1) {
                    cli_sock_out.write(read_buffer, 0, b);
                    cli_sock_out.flush();
                }
            } catch (IOException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: a problem occured trying to read from: " + full_obj_path);
            }

            //close the file reader object
            try {
                buf_fr.close();
            } catch (IOException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: a problem occured trying to close buffered file reder object");
            }

            //close the client socket
            try {
                cli_sock.close();
            } catch (IOException e) {
                error_occured = true;
                throw new WebServerClientProcessException("Error: a problem occured trying to close client socket");
            }

            //DEBUGGING
            System.out.println("Client request successfully served");
            System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
            System.out.println();

        } else {
            //send back 404 Object not found
            clientObjNotFound();
        }
    }



    /**
     * Handles the returning of an Http 404 response to client that has submitted a GET request for a file object that cannot be found 
     *
     * @throws WebServerClientProcessException if an error occurs sending the 408 response back to client (error flag set)
     *                                         *purposfully throws this exception at the end of execution (notFound flag set)
     */
    private void clientObjNotFound() throws WebServerClientProcessException {
        //create server Bad Request response string
        Date currentDate = new Date();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
        String currentDate_formatted = dateFormatter.format(currentDate);
        String serverName = "CPSC441_Assignment3_WebServer";
        String ConnectionVal = "close";
        String Http_resp_404 = 
            "HTTP/1.1 404 Not Found\r\n"+
            "Server: " + serverName + "\r\n" +
            "Date: " + currentDate_formatted + "\r\n"+
            "Connection: " + ConnectionVal + "\r\n" + 
            "\r\n";

        //Convert server timeout response string into bytes to send out over client socket
        byte[] objNtFoundResp_bytes = null; 
        try {
            objNtFoundResp_bytes = Http_resp_404.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: could not convert Http Server Object not found response into byte");
        }

        //send out server timeout response bytes over client socket
        try {
            cli_sock_out.write(objNtFoundResp_bytes);
            cli_sock_out.flush();
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: a problem occured trying to send Object not found response to client");
        }

        //DEBUGGING
        System.out.println("Client requested object not found, sending back object not found response: ");
        System.out.println("===========================================================================");
        System.out.println(Http_resp_404);
        System.out.println();

        //begin stopping client process thread
        notFound = true;
        throw new WebServerClientProcessException("");
    }



    /**
     * Handles the returning of an Http 400 response to client that has submitted an invalid GET request
     *
     * @throws WebServerClientProcessException if an error occurs sending the 400 response back to client (error flag set)
     *                                         *purposfully throws this exception at the end of execution (bad request flag set)
     */
    private void clientBadRequest() throws WebServerClientProcessException {

        //create server Bad Request response string
        Date currentDate = new Date();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
        String currentDate_formatted = dateFormatter.format(currentDate);
        String serverName = "CPSC441_Assignment3_WebServer";
        String ConnectionVal = "close";
        String Http_resp_400 = 
            "HTTP/1.1 400 Bad Request\r\n"+
            "Server: " + serverName + "\r\n" +
            "Date: " + currentDate_formatted + "\r\n"+
            "Connection: " + ConnectionVal + "\r\n" + 
            "\r\n";

        //Convert server timeout response string into bytes to send out over client socket
        byte[] badReqResp_bytes = null; 
        try {
            badReqResp_bytes = Http_resp_400.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: could not convert Http Server Bad Request response into byte");
        }

        //send out server timeout response bytes over client socket
        try {
            cli_sock_out.write(badReqResp_bytes);
            cli_sock_out.flush();
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: a problem occured trying to send Bad Request response to client");
        }

        //DEBUGGING
        System.out.println("Client has sent bad request, sending back bad request response: ");
        System.out.println("================================================================");
        System.out.println(Http_resp_400);
        System.out.println();

        //begin stopping client process thread
        badRequest = true;
        throw new WebServerClientProcessException("");
    }




    /**
     * Handles the returning of an Http 408 response to client that has failed to submit a GET request within the alotted time 
     *
     * @throws WebServerClientProcessException if an error occurs sending the 408 response back to client (error flag set)
     *                                         *purposfully throws this exception at the end of execution (timeout flag set)
     */
    private void clientTimeout() throws WebServerClientProcessException {

        //create server Timeout response string
        Date currentDate = new Date();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
        String currentDate_formatted = dateFormatter.format(currentDate);
        String serverName = "CPSC441_Assignment3_WebServer";
        String ConnectionVal = "close";
        String Http_resp_408 = 
            "HTTP/1.1 408 Request Timeout\r\n"+
            "Server: " + serverName + "\r\n" +
            "Date: " + currentDate_formatted + "\r\n"+
            "Connection: " + ConnectionVal + "\r\n" + 
            "\r\n";

        //Convert server timeout response string into bytes to send out over client socket
        byte[] timeOutResp_bytes = null; 
        try {
            timeOutResp_bytes = Http_resp_408.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: could not convert Http Server Timeout response into byte");
        }

        //send out server timeout response bytes over client socket
        try {
            cli_sock_out.write(timeOutResp_bytes);
            cli_sock_out.flush();
        } catch (IOException e) {
            error_occured = true;
            throw new WebServerClientProcessException("Error: a problem occured trying to send timeout response to client");
        }

        //DEBUGGING
        System.out.println("Client has exceeded timeout limit, sending back timeout response: ");
        System.out.println("==================================================================");
        System.out.println(Http_resp_408);
        System.out.println();

        //begin stopping client process thread
        timeout_exceeded = true;
        throw new WebServerClientProcessException("");
    }











  






    //BASIC ACCESSOR FUNCTIONS

    //used by the main WebServer thread during shutdown to check if the Client process thread is currently serving a client GET 
    public boolean isProcessingGET() {
        return processing_GET;
    }

    //used by the main WebServer thread during shutdown to get access to the Client process thread's client socket so that it can close it
    public Socket get_cliSocket() {
        return this.cli_sock;
    }




    public void run() {
        try {
            readClientGET();
        } catch (WebServerClientProcessException e) {
            //WebServerClientProcessException only thrown within the execution of the WebServerClientProcess if 
            //   a) shutdown requested (main WebServer thread shutdown the client socket)
            //   b) a timeout occured
            //   c) an error occurred
            //an error or timeout flag should have been set before the exception was thrown
            if (WebServer.shutdownRequested) {
                //shutdown requested, let WebServerClientProcess thread die
                System.out.println("Main WebServer thread has initiated shutdown");
                //UNIT TESTING
                System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
                System.out.println();
            } else if (timeout_exceeded) {
                //client exceeded timeout, let WebServerClientProcess thread die
                System.out.println("Client has timed out");
                //UNIT TESTING
                System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
                System.out.println();
            } else if (badRequest) {
                //client sent bad request, let WebServerClientProcess thread die
                System.out.println("Client sent bad request");
                //UNIT TESTING
                System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
                System.out.println();
            } else if (notFound) {
                //client requested object not found, let WebServerClientProcess thread die
                System.out.println("Client requested object not found");
                //UNIT TESTING
                System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
                System.out.println();
            } else if (error_occured) {
                //error occured during execution, print error message and let WebServerClientProcess thread die
                System.out.println(e.getMessage());
                e.printStackTrace();
                System.out.println();
                //UNIT TESTING
                System.out.println("Client Thread  @: " + cli_sock.getInetAddress() + " on port: " + cli_sock.getPort() + " shutting down");
            }
        }
    }
}