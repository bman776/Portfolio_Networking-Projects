

/**
 * WebServer Class
 * 
 * Implements a multi-threaded web server
 * supporting non-persistent connections.
 * 
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.logging.*;

import java.net.*;


public class WebServer extends Thread {
	
	// global logger object, configures in the driver class
	private static final Logger logger = Logger.getLogger("WebServer");

    //server socket
    private ServerSocket srv_sock;
    //server ip address
    private InetAddress srv_ip;
    //server socket address (server ip + port number)
    private SocketAddress srv_addr;

    //The connection timeout value that will be passed on along to and adhered by all instantiated WebServerClientProcess'
    private int timeout;

    //the flag that is set to T when the server is directed to shutdown
    public static boolean shutdownRequested;

    //a very small timeout value set on server socket
    //which causes the server to periodically check if its host machine has requested to quit
    private final int SHUTDOWN_CHECKTIME = 100;

    //This array list will essentially store each currently executing WebServerClientProcess created whenever
    //a client connection is accepted and created by the webserver
    private ArrayList<WebServerClientProcess> clientProcess_Threads;
	
	
    /**
     * Constructor to initialize the web server
     * 
     * @param port 	    The server port at which the web server listens > 1024
     * @param timeout 	The timeout value for detecting non-resposive clients, in milli-second units
     * 
     */
	public WebServer(int port, int timeout){

        //initialize client thread list
        clientProcess_Threads = new ArrayList<WebServerClientProcess>();

        //initialize timeout value
        this.timeout = timeout;

        //Create server socket to listen for client requests
        try {
            srv_sock = new ServerSocket();
            srv_ip = InetAddress.getLocalHost();

            //DEBUGGING
            System.out.println("starting server @ IP: " + srv_ip.getHostAddress() + " (AKA "+ srv_ip.getCanonicalHostName() +")");
            System.out.println();

            srv_addr = new InetSocketAddress(srv_ip, port);
            srv_sock.bind(srv_addr);
        } catch (IOException e) {
            System.out.println("Error: could not create main server socket");
            e.printStackTrace();
            System.exit(1);
        }
        shutdownRequested = false;
        

    }

	
    /**
	 * Main web server method.
	 * The web server remains in listening mode 
	 * and accepts connection requests from clients 
	 * until the shutdown method is called.
	 *
     */
	public void run(){

        //set server shutdown checktime 
        
        try {
            srv_sock.setSoTimeout(SHUTDOWN_CHECKTIME);
        } catch (SocketException e) {
            System.out.println("Error: server socket timeout could not be set");
            e.printStackTrace();
            System.exit(1);
        }
        
        //DEBUGGING
        //System.out.println("Listening for client connections...");

        while (!shutdownRequested) {
            //listen for client connection requests

            try {
                //wait for client connection (blocking call - triggers SocketTimeout)
                Socket cli_sock = srv_sock.accept();

                //client connection recieved

                //begin running client process thread to deal with client connection 
                //(pass the created cli_sock to the thread)
                
                try {
                    WebServerClientProcess wscp = new WebServerClientProcess(Thread.currentThread(), cli_sock, timeout);
                    wscp.start();
                    clientProcess_Threads.add(wscp);
                } catch (WebServerClientProcessException e) {
                    System.out.println("Error: Could not initialize Web Server Client Process Object for Client Connection");
                    e.printStackTrace();
                    //let the main server thread keep running (keep listening for client connection requests)
                }
                

                //DEBUGGING
                //System.out.println("There are now " + clientProcess_Threads.size() + " active client threads");
            } catch (SocketTimeoutException e) {
                if (shutdownRequested) {
                    //begin shutdown

                    //we need to start closing any WebServerClientProcesses
                    //any WebServerClientProcess will either be
                    //  a) awaiting client GET request
                    //          will be waiting for client request during blocking read() call up to timeout val
                    //          (can't just wait for timeout value) 
                    //  b) in the middle of serving a client GET request
                    //          easy, just wait for *non-persistent http process of client process to finish

                    //we iterate through the list of client server processes and check thier status
                    //if they are busy serving a client GET then we wait for them to finish
                    //if they are blocked on a read() call waiting for a client GET then we close thier client socket
                    //      this triggers a socket exception in that client thread which we handle by throwing our own custom exception which
                    //      causes the client thread to shutdown (we wait for it to shutdown here before moving on to check the next client thread)                    
                    try {
                        for (WebServerClientProcess cpt : clientProcess_Threads) {
                            if (!cpt.isProcessingGET()) {
                                
                                //DEBUGGING
                                //System.out.println("Shutting down client thread...");

                                //close the client threads socket to client
                                cpt.get_cliSocket().close();
                            }
                            //else leave it to finish *non-persistent Http process
                            //either way process will be ended and we can join it to main server thread
                            cpt.join();
                        }
                    } catch (InterruptedException ie) {
                        System.out.println("Error: a problem occured during server shutdown, could not join a client process thread");
                        ie.printStackTrace();
                        System.exit(1);
                    } catch (IOException iie) {
                        System.out.println("Error: a problem occured during server shutdown, could not close the client socket");
                        iie.printStackTrace();
                        System.exit(1);
                    }
                    



                }
            } catch (IOException e) {
                System.out.println("Error: could not establish a client connection request");
            }
        }

        //DEBUGGING
        //System.out.println("No longer listening for client connections...");
    }
	

    /**
     * Signals the web server to shutdown.
	 *
     */
	public void shutdown(){
        //Called by ServerDriver whos listens for 'quit' at the command line ( waitForQuit() )
        //and upon reading so triggers this shutdown function
        //All we do here is toggle shutdownRequested to True
        //Then when WebServer checks this boolean during its regular period check it will detect
        //that a shutdown has been requested 
        shutdownRequested = true;
    }
	
}