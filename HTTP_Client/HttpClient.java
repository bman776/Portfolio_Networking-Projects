
// Brett Gattinger
// 30009390

/**
 * HttpClient Class
 * 
 * CPSC 441
 * Assignment 2
 * 
 * 
 */


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.*;

public class HttpClient {

	private static final Logger logger = Logger.getLogger("HttpClient"); // global logger

    //constants used throughout program
    private final String STATUS_CODE_OK = "200";
    private final String HEADER_CONTENT_LEN = "Content-Length";

    //server address object
    private InetSocketAddress srv_addr;

    //server socket object
    private Socket srv_sock;

    //server socket output stream
    private InputStream srv_sock_in = null;
    private OutputStream srv_sock_out = null;

    //Http client class will save the hostname, portnum and path parsed from input url as attributes
    private String url_hostName;
    private String url_portNum;
    private String url_path;
    private String url_fileName;

    //buffer for server response
    private int resp_buff_size;

    //file writer object
    private FileOutputStream fw;





    /**
     * Default no-arg constructor
     */
	public HttpClient() {
		// nothing to do!
	}


    //MAIN FUNCTION ==============================================================================================
	
    /**
     * Downloads the object specified by the parameter url.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     */
	public void get(String url) {

        //parse url and store extracted hostname, portnumber and path to class attribute variables
        parse_url(url);

        //using extracted info from url establish connection with server
        est_SrvConn(url_hostName, Integer.parseInt(url_portNum));

        //using extracted info from url build Http GET request
        String http_GET_req = generate_HttpReq(url_hostName, url_path);

        //send generated http GET request to the provided hostname of server
        send_GETrequest(http_GET_req);

        //read response from server
        read_ServerResponse();

        
    }



    //===========================================================================================

    /**
     * Establishes connection to server using provide server name and port number
     * 
     * @param srv_name  the provided server name
     * @param srv_port  the provided port number
     */
    private void est_SrvConn(String srv_name, int srv_port) {

        //Create server socket address object
        InetAddress sA = null;
        try {
            sA = InetAddress.getByName(srv_name);
        } catch (UnknownHostException e) {
            System.out.println("Error: could not create server address object, host name could not be reconciled");
            e.printStackTrace();
            System.exit(1);
        }
        srv_addr = new InetSocketAddress(sA, srv_port);

        //Create socket and connect to server
        try {
            srv_sock = new Socket();
            srv_sock.connect(srv_addr);
        } catch (IOException e) {
            System.out.println("Error: could not create server socket object");
            e.printStackTrace();
            System.exit(1);
        }
        
        //DEBUGGING
        /*if (srv_sock.isConnected()) {
            System.out.println("Connection to server successful!");
            System.out.println();
        }*/
        

        //initialize server socket output stream object
        try {
            srv_sock_out = srv_sock.getOutputStream();
        } catch (IOException e) {
            System.out.println("Error: could not initialize output stream for server socket");
            e.printStackTrace();
            System.exit(1);
        }

        //initialzie server socket input stream object
        try {
            srv_sock_in =srv_sock.getInputStream();
        } catch (IOException e) {
            System.out.println("Error: could not initialize input stream for server socket");
            e.printStackTrace();
            System.exit(1);
        }

    }

    /**
     * parses a given url string and extracts the hostname, portnumber, and pathname of the url
     * to class attributes url_hostName, url_portNum, url_path respectively
     * 
     * @param url   The provided String url to be parsed
     */
    private void parse_url(String url) {
        //function assumes url has syntax:
        //  http://hostname[:port]/[pathname]
        //  (where port and pathname optional)

        //split based on first colon
        String[] url_splitA = url.split(":", 2);
        //url_splitA[0] = "http"
        //url_splitA[1] = //hostname:port/path/name
        if ( url_splitA.length != 2) {
            System.out.println("Error: improperly formatted url, check the head of url for syntax errors");
            System.exit(1);
        }
        if ( !(url_splitA[0].equals("http")) ) {
            System.out.println("Error: Invalid application protocol detected, this program only accepts 'http'");
            System.exit(1);
        }

        //split based on first '//' after 'http:'
        String[] url_splitB = url_splitA[1].split("/", 3);
        //url_splitB[0] = ""
        //url_splitB[1] = ""
        //url_splitB[2] = hostname[:port][/pathname]
        if ( url_splitB.length != 3) {
            System.out.println("Error: improperly formatted url, check the body of url for syntax errors");
            System.exit(1);
        }

        //split based on first real file seperator '/'
        String[] url_splitC = url_splitB[2].split("/", 2);
        //url_splitC[0] = "hostname[:port]"
        //url_splitC[1] = "[/pathname]"

        /* DEV NOTE:
            url_splitC should have length of at least one and at most 2
            (i.e. there should at least be hostname[:port] and at most hostname[:port] + [/pathname])
        */

        //DEBUGGING
        /*for (int i = 0; i < url_splitC.length; i++) {
            System.out.println(url_splitC[i]);
        }*/

        if (url_splitC.length < 1 || url_splitC.length > 2) {
            System.out.println("Error: improperly formatted url, check the host or path name for syntax errors");
            System.exit(1); 
        }

        //extract hostname, port number (if given) 
        if (url_splitC[0].contains(":")) {
            //url has port specified
            url_hostName = url_splitC[0].split(":", 2)[0];
            url_portNum = url_splitC[0].split(":", 2)[1];

            //check for proper host name and port num
            //if (url_hostName is not valid ~ what makes a hostname invalid?)
            try {
                int portStringIsInt = Integer.parseInt(url_portNum);
            } catch (NumberFormatException e) {
                System.out.println("Error: invalid port number given, ensure the port number is a purely numeric value");
                System.exit(1); 
            }
        } else {
            //url has not specified a port
            //default to port 80
            url_hostName = url_splitC[0];
            url_portNum = "80";
        }
        
        if ( url_splitC.length == 2) {
            //pathname also provided
            //extract file path and filename
            url_path = "/" + url_splitC[1];
            String[] url_pathParts = url_splitC[1].split("/");
            if (url_pathParts.length > 1) {
                //filename provided
                url_fileName = url_pathParts[url_pathParts.length-1];
            } else {
                url_fileName = url_path;
            }
        } else {
            //only hostname was provided
            //default pathname to file separator and filename to empty string
            url_path = "/index.html";
            url_fileName = "";
        }

        //DEBUGGING
        /*System.out.println("Displaying results of url parse:");
        System.out.println("Hostname = " + url_hostName);
        System.out.println("Portnumber = " + url_portNum);
        System.out.println("path = " + url_path);
        System.out.println("file = " + url_fileName);
        System.out.println();*/
        
    }

    /**
     * Generates the full Http GET request to be sent to the target server using hostname and filepath
     * extracted from the program provided url by the parse_url function
     * 
     * @param hn
     * @param pn
     * @return
     */
    private String generate_HttpReq(String hn, String pn) {
        String httpReq = "";

        String httpReq_line =           "GET " + pn + " HTTP/1.1" + "\r\n";
        String hostHeader =             "Host: " + hn + "\r\n";
        String connHeader =             "Connection: close" + "\r\n";
        String termination =            "\r\n";

        httpReq = 
        httpReq_line + 
        hostHeader + 
        connHeader + 
        termination;

        //REQUIRED OUTPUT
        System.out.println();
        System.out.println("Displaying Generated Http GET request:");
        System.out.println("======================================");
        System.out.println(httpReq);

        return httpReq;
    }

    /**
     * sends the provided http request to the server
     * relies on class attribute srv_sock_out being initialized
     * 
     * @param httpReq
     */
    private void send_GETrequest(String httpReq) {

        //DEBUGGING
        /*if (srv_sock.isConnected()) {
            System.out.println("Sending Http GET request to server...");
            System.out.println();
        }*/
        
        //convert http GET request string to bytes
        byte[] getReq_b = null;
        try {
            getReq_b = httpReq.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            System.out.println("Error: could not create convert Http GET request into bytes");
            e.printStackTrace();
            System.exit(1);
        }
        
        //write http GET request into server socket output stream 
        try {
            srv_sock_out.write(getReq_b);
            srv_sock_out.flush();
        } catch (IOException e) {
            System.out.println("Error: a problem occured trying to write Http GET request to server");
            e.printStackTrace();
            System.exit(1);
        }

    }

    /**
     * reads and parses the response from the server
     * relies on class attribute srv_sock_in being initialized
     */
    private void read_ServerResponse() {
        //initialize server socket input stream

        //DEBUGGING
        /*if (srv_sock.isConnected()) {
            System.out.println("Reading server response...");
            System.out.println();
        }*/
        
        //Get server Http response head
        String Http_responseHead = "";
        String detector = "";
        Boolean responseHeadRead = false;
        int b;
        try {
            while ( !(responseHeadRead) &&  ((b = srv_sock_in.read()) != -1)) {
                
                Http_responseHead = Http_responseHead + (char)(b);

                if (!responseHeadRead) {
                    if ( (char)b == '\r' || (char)b == '\n') {
                        detector = detector + (char)b;
                    } else {
                        detector = "";
                    }
                    if (detector.equals("\r\n\r\n")) {

                        //DEBUGGING
                        //System.out.println("===========================================");
                        //System.out.println("end of Server Http response header reached!");
                        //System.out.println("===========================================");
                        //System.out.println();
                        responseHeadRead = true;
                    }
                }
                
            }

            //REQUIRED OUTPUT
            System.out.println("Retrieved Http response head:");
            System.out.println("=============================");
            System.out.println(Http_responseHead);
            System.out.println();

        } catch (IOException e) {
            System.out.println("Error: a problem occured trying to read server Http Response Header");
            e.printStackTrace();
            System.exit(1);
        }

        String[] Http_responseHeadLines = Http_responseHead.split("\r\n");

        //DEBUGGING
        /*System.out.println("Extracted Http response header lines\n==================");
        for (int i = 0; i < Http_responseHeadLines.length; i++) {
            System.out.println(Http_responseHeadLines[i]);
        }
        System.out.println();*/

        String StatusLine = Http_responseHeadLines[0];
        String responseProtocol = StatusLine.split(" ")[0];
        String responseStatusCode = StatusLine.split(" ")[1];
        String responseStatusPhrase = StatusLine.split(" ")[2];

        //DEBUGGING
        /*System.out.println("Extracted Http response status line\n==================");
        System.out.println(responseProtocol);
        System.out.println(responseStatusCode);
        System.out.println(responseStatusPhrase);
        System.out.println();*/

        int ContentLength = -1;
        for (int i = 0; i < Http_responseHeadLines.length; i++) {
            if (Http_responseHeadLines[i].split(":")[0].equals("Content-Length")) {
                ContentLength = Integer.parseInt(Http_responseHeadLines[i].split(":")[1].trim());
            }
        }

        //DEBUGGING
        /*System.out.println("Extracted Content Length value\n==================");
        System.out.println(ContentLength);
        System.out.println();*/


        if (responseStatusCode.equals("200")) {
            //download body (data) of server Http response
            
            //create buffered file writer
            try {
                if (!(url_fileName.equals(""))) {
                    fw = new FileOutputStream(url_fileName);
                } else {
                    fw = new FileOutputStream("index.html");
                }
            } catch (FileNotFoundException e) {
                System.out.println("Error: file to hold downloaded server response could not be created");
                e.printStackTrace();
                System.exit(1);
            }
            
            //Get Server Http response body (i.e. data)
            byte[] responseBodyBuffer = new byte[32*1024];
            int bytesRead;
            try {
                while ( ((bytesRead = srv_sock_in.read(responseBodyBuffer)) != -1) && (ContentLength != 0)) {
                    fw.write(responseBodyBuffer,0,bytesRead);
                    fw.flush();
                    ContentLength -= bytesRead;

                    //DEBUGGING
                    //String incoming = new String(responseBodyBuffer);
                    //System.out.print(incoming);
                }
            } catch (IOException e) {
                System.out.println("Error: a problem occured trying to read server Http Response Body");
                e.printStackTrace();
                System.exit(1);
            }

        } 

    }

}
