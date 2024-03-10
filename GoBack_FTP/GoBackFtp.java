//Brett Gattinger 30009390

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;

public class GoBackFtp {

	// global logger	
	private static final Logger logger = Logger.getLogger("GoBackFtp");

	//port number for locally bound client UDP socket to server
	public static final int UDP_RECEIVER_SRVSOCK_PORTNUM = 1234;
	public static final int UDP_SENDER_SRVSOCK_PORTNUM = 4567;

	private final int SEND_THREAD_FIN_CHECKTIME = 100;

	//file transmission attributes
	private int windowSize;
	private int rtoTimer;
	private TimeoutTimer timeoutTimer;
	private LinkedBlockingQueue<FtpSegment> transmissionQueue;

	//remote server attributes
	private String serverName;
	private int serverPort;
	private InetAddress srvIP;
	private Socket TCPsock_to_srv;
	private DatagramSocket UDPsock_to_srv;
	private int srv_UDP_portNum;
	private int srv_UDP_initSeqNum;

	//File reading attributes
	private String fileName;
	private File ftp_File;
	private FileInputStream ftp_FileReader;
	private long ftp_FileLength;
	



	/**
	 * Constructor to initialize the program 
	 * 
	 * @param windowSize	Size of the window for Go-Back_N in units of segments
	 * @param rtoTimer		The time-out interval for the retransmission timer
	 */
	public GoBackFtp(int windowSize, int rtoTimer){

		//save the provided transmission window size and transmission timeout as class attributes
		this.windowSize = windowSize;
		this.rtoTimer = rtoTimer;
	}


	/**
	 * Sends the specified file to the specified remote server by establishing and performing an initial TCP handshake with the server
	 * Then creating and running 
	 * 1. a sender thread responsible for reading from the given input file and sending read bytes as Datagram packets to the server via UDP
	 * 2. a receiver thread responsible for receiving Acks from the server corresponding to each Datagram packet sent by the sender thread
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 */
	public void send(String serverName, int serverPort, String fileName){
		
		//save the given server name, port number and file name in class attributes (just in case)
		this.serverName = serverName;
		this.serverPort = serverPort;
		this.fileName = fileName;

		//create TCP connection to server to perform handshake
		createTCP_connToServer(serverName, serverPort);

		//create UDP connection server to perform file transfer
		createUDP_connToServer();

		//resolve given file name
		resolveFile(fileName);

		//perform TCP handshake
		handshake(UDP_RECEIVER_SRVSOCK_PORTNUM, fileName, ftp_FileLength);

		//initialize timeout Timer object
		timeoutTimer = new TimeoutTimer(rtoTimer);

		//Initialize transmission queue and set its capcity to window size
		transmissionQueue = new LinkedBlockingQueue<FtpSegment>(windowSize);

		//Initialize FileSender and AckReceiver threads
		GoBackFtp_AckReceiver AckReceiver = new GoBackFtp_AckReceiver(Thread.currentThread(), serverName, srv_UDP_portNum, UDPsock_to_srv, timeoutTimer, transmissionQueue);
		GoBackFtp_FileSender FileSender = new GoBackFtp_FileSender(Thread.currentThread(), serverName, srv_UDP_portNum, srv_UDP_initSeqNum, UDPsock_to_srv, ftp_FileReader, windowSize, timeoutTimer, transmissionQueue);

		//start AckReceiver thread
		AckReceiver.start();
		//start FileSender thread
		FileSender.start();

		try {
			FileSender.join();
			AckReceiver.join();
			
		} catch (InterruptedException ie) {
			System.out.println("Error: A problem occured during termination of FileSender and AckReceiver threads");
			ie.printStackTrace();
			System.exit(1);
		}
		

		try {
			UDPsock_to_srv.close();
			TCPsock_to_srv.close();
			ftp_FileReader.close();
		} catch (IOException ioe) {
			System.out.println("Error: A problem occured while trying to close TCP and UDP socket to server");
			ioe.printStackTrace();
			System.exit(1);
		}
	
	}

	/**
	 * Creates TCP socket and establishes TCP connection to the server
	 * 
	 * (sets the TCPsock_to_srv class attribute)
	 * 
	 * @param srvN name of the server (DNS resolvable to the IP address of the server)
	 * @param srvP port number of the server
	 */
	public void createTCP_connToServer(String srvN, int srvP) {
		//create and open TCP socket to server
		try {
			TCPsock_to_srv = new Socket();
			srvIP = InetAddress.getByName(srvN);
			System.out.println(srvIP);
			TCPsock_to_srv.connect(new InetSocketAddress(srvIP, srvP));
		} catch (UnknownHostException uhe) {
			System.out.println("Error: Cannot create TCP socket to server; server IP address could not be found\n");
			uhe.printStackTrace();
			System.exit(1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Error: Cannot create TCP socket to server; port number outside range of valid port values\n");
			iae.printStackTrace();
			System.exit(1);
		} catch (IOException ioe) {
			System.out.println("Error: Cannot create TCP socket to server; a problem occured while trying to connect the server socket object to the provided ip address and port number\n");
			ioe.printStackTrace();
			System.exit(1);
		}
	}





	/**
	 * Creates connectionless UDP socket to the server and sets a 100 milisecond socket timeout on it
	 * 
	 * (sets the UDPsock_to_srv class attribute)
	 */
	public void createUDP_connToServer() {
		//create UDP socket to server for AckReceiver thread
		try {
			UDPsock_to_srv = new DatagramSocket(GoBackFtp.UDP_RECEIVER_SRVSOCK_PORTNUM);
		} catch (SocketException se) {
			System.out.println("Error: could not create UDP socket to server; a problem occured trying to open the UDP socket or bind it to port number: " + GoBackFtp.UDP_RECEIVER_SRVSOCK_PORTNUM);
			se.printStackTrace();
			System.exit(1);
		}

		//set 100-milisecond timeout on AckReceiver's UDP socket to server
		try {
			UDPsock_to_srv.setSoTimeout(SEND_THREAD_FIN_CHECKTIME);
		} catch (SocketException se) {
			System.out.println("Error: socket timeout on AckReceiver thread's UDP port could not be set");
			se.printStackTrace();
			System.exit(1);
		}
	}





	/**
	 * checks if the file exists in the working directory establishes a buffered file input stream to read from it
	 * also retrieves the files length to be communicated to server during TCP handshake
	 * 
	 * (initializes the ftp_FileReader class attribute)
	 * (initializes the ftp_FileReader_buffer class attribute)
	 * (initializes the ftp_FileLength class attribute)
	 * 
	 * @param flN	the given string filename
	 */
	public void resolveFile(String flN) {
		String currWorkingDir = System.getProperty("user.dir");
		String fileDir_delimeter = System.getProperty("file.separator");
		String full_filePath = currWorkingDir + fileDir_delimeter + flN;
		ftp_File = new File(full_filePath);
		try {
			ftp_FileReader = new FileInputStream(ftp_File);
		} catch (FileNotFoundException fnfe) {
			//File could not be found within the programs working directory
			System.out.println("Error: could not find the given file; ensure the file exists within the working directory of the program");
			fnfe.printStackTrace();
			System.exit(1);
		}
		//File successfully found in the working directory and file input stream created to read from it

		//get file length
		ftp_FileLength = ftp_File.length();
	}



	

	/**
	 * Performs TCP handshake with server which consists of:
	 * 		1. sending the local UDP socket port number
	 * 		2. sending the name of the file to be transfered as a UTF encoded string
	 *		3. sending the length of the file to be transfered
	 *		4. recieving the server's UDP socket port number
	 *		5. recieving the initial sequence number used by the server
	 * 
	 * 
	 * @param client_portNum
	 * @param fileName
	 * @param fileLength
	 */
	public void handshake(int client_portNum, String fileName, long fileLength) {
		//create data output and input streams to perform handshake
		DataOutputStream TCP_dataOut = null;
		try {
			TCP_dataOut = new DataOutputStream(TCPsock_to_srv.getOutputStream());
		} catch (IOException ioe) {
			System.out.println("Error: unable to get TCP server socket output stream; cannot perform TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}
		DataInputStream TCP_dataIn = null;
		try {
			TCP_dataIn = new DataInputStream(TCPsock_to_srv.getInputStream());
		} catch (IOException ioe) {
			System.out.println("Error: unable to get TCP server socket input stream; cannot perform TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}

		//Perform send part of handshake

		//send local UDP reciever socket port number
		try {
			int udp_srvSock_portNum = UDP_RECEIVER_SRVSOCK_PORTNUM;
			TCP_dataOut.writeInt(udp_srvSock_portNum);
			TCP_dataOut.flush();
		} catch (IOException ioe) {
			System.out.println("Error: unable to send local UDP socket port number; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}
		//send UTF encoded filename
		try {
			TCP_dataOut.writeUTF(fileName);
			TCP_dataOut.flush();
		} catch (IOException ioe) {
			System.out.println("Error: unable to send UTF-encoded file name; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}
		//send file length
		try {
			TCP_dataOut.writeLong(fileLength);
			TCP_dataOut.flush();
		} catch (IOException ioe) {
			System.out.println("Error: unable to send file length; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}

		//Perform recieve part of handshake

		//get servers UDP port number
		try {
			srv_UDP_portNum = TCP_dataIn.readInt();
		} catch (IOException ioe) {
			System.out.println("Error: unable to reciever servers UDP port number; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}

		//get servers initial sequence number
		try {
			srv_UDP_initSeqNum = TCP_dataIn.readInt();
		} catch (IOException ioe) {
			System.out.println("Error: unable to reciever servers initial sequence number; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}


		//CONSOLE OUTPUT
		System.out.println("TCP HANDSHAKE RESULT:");
		System.out.println("=====================");
		System.out.println("server UDP port number: " + srv_UDP_portNum);
		System.out.println("server initial sequence number: " + srv_UDP_initSeqNum);
		System.out.println();
	}
	


} // end of class