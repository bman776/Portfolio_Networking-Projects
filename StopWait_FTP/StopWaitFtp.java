
//Brett Gattinger 30009390

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.*;

import javax.swing.JPopupMenu.Separator;

public class StopWaitFtp {
	
	private static final Logger logger = Logger.getLogger("StopWaitFtp"); // global logger

	//port number for locally bound client UDP socket to server
	public static final int UDP_SRVSOCK_PORTNUM = 9876;
	
	//Remote server attributes
	private String srvName;
	private int srvPort;
	private InetAddress srvIP;
	private Socket TCPsock_to_srv;
	private DatagramSocket UDPsock_to_srv;
	private int srv_UDP_portNum;
	private int srv_init_seqNum;

	//File attributes
	private String fileName;
	private File ftp_File;
	private FileInputStream ftp_FileReader;
	private byte[] ftp_FileReader_buffer;
	private long ftp_FileLength;

	//File transmission attributes
	private int timeout;

	/**
	 * Constructor to initialize the program 
	 * 
	 * @param timeout		The time-out interval for the retransmission timer, in milli-seconds
	 */
	public StopWaitFtp(int timeout) {
		this.timeout = timeout;
	}



	/**
	 * Send the specified file to the remote server
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 */
	public void send(String serverName, int serverPort, String fileName) {

		//save the given server name, port number and file name in class attributes to be used later
		this.srvName = serverName;
		this.srvPort = serverPort;
		this.fileName = fileName;

		
		//TCP socket to server successfully created and connected
		createTCP_connToServer(serverName, serverPort);

		//Create local UDP socket
		createUDP_connToServer();

		//get path to file, establish a file input stream to it and get file length
		resolveFile(fileName);

		//perform TCP handshake
		handshake(UDP_SRVSOCK_PORTNUM, fileName, ftp_FileLength);

		//transfer file
		transferFile();




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
	 * Creates local connectionless UDP socket to send and recieve datagrams from the server
	 * 
	 * uses the class constant UDP_SRVSOCK_PORTNUM = 9876 as the port number
	 */
	public void createUDP_connToServer() {
		try {
			UDPsock_to_srv = new DatagramSocket(UDP_SRVSOCK_PORTNUM);
		} catch (SocketException se) {
			System.out.println("Error: could not create UDP socket to server; a problem occured trying to open the UDP socket or bind it to port number: " + UDP_SRVSOCK_PORTNUM);
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
	 * @param flN
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
		//File successfully found in the working directory and file input stream created to read from

		//initialize byte array buffer for file input stream object
		ftp_FileReader_buffer = new byte[FtpSegment.MAX_PAYLOAD_SIZE];

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

		//send local UDP socket port number
		try {
			int udp_srvSock_portNum = UDP_SRVSOCK_PORTNUM;
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
			srv_init_seqNum = TCP_dataIn.readInt();
		} catch (IOException ioe) {
			System.out.println("Error: unable to reciever servers initial sequence number; cannot complete TCP handshake");
			ioe.printStackTrace();
			System.exit(1);
		}


		//DEBUGGING
		System.out.println("TCP HANDSHAKE RESULT:");
		System.out.println("=====================");
		System.out.println("server UDP port number: " + srv_UDP_portNum);
		System.out.println("server initial sequence number: " + srv_init_seqNum);
		System.out.println();
	}


	
	/**
	 * transfers the given file over the UDP connection to the server
	 * 
	 * current version of this function assumes no packet loss (tested locally)
	 */
	public void transferFile() {
		//initialize Timer object
		

		//initialize sequence number
		int nxtSeqNum = srv_init_seqNum;
		int lastSeqNum = srv_init_seqNum;

		//initialize FtpSegment and DatagramPacket objects for UDP communication
		FtpSegment ftpSendSeg = null;
		DatagramPacket sendPkt = null;
		byte[] recvData = new byte[FtpSegment.MAX_SEGMENT_SIZE];
		DatagramPacket recvPkt = null;
		FtpSegment ftpRecvSeg = null;

		int file_bytesRead;
		try {
			while ((file_bytesRead = ftp_FileReader.read(ftp_FileReader_buffer)) != -1) {
				// set/reset lastSequence
				lastSeqNum = nxtSeqNum;

				//create FTPSegement object with data read in from file
				ftpSendSeg = new FtpSegment(nxtSeqNum, ftp_FileReader_buffer, file_bytesRead);
				//create DatagramPacket object to send FTPSegment over udp connection
				sendPkt = FtpSegment.makePacket(ftpSendSeg, srvIP, srv_UDP_portNum);

				//create retransmission TimerTask object and provide it with data to make its own copy of the segment
				// to be tretransmitted if timeout occurs
				Timer timeout_timer = new Timer();
				TimerTask timeout_retransmit = new TimeoutRetransmitPacketTask(this.srvName, srv_UDP_portNum, UDPsock_to_srv, ftpSendSeg);

				//send DatagramPacket object over the UDP connection
				try {
					UDPsock_to_srv.send(sendPkt);
				} catch (IOException ioe) {
					System.out.println("Error: an error occured trying to send UDP datagram packet to server");
					ioe.printStackTrace();
					System.exit(1);
				}
				//COSNOLE OUTPUT
				System.out.println("send " + ftpSendSeg.getSeqNum());
				System.out.println();

				//schedule packet retransmission timeout task
				timeout_timer.schedule(timeout_retransmit, this.timeout, this.timeout);

				//create Datagram packet object to recieve Ack segment from server
				recvPkt = new DatagramPacket(recvData, recvData.length);
				//(blocking call) wait to recieve Ack datagram from server
				//DEV NOTE:
				/* b/c timer task started just before here it [timer task] will continually wait 1 timeout period before retransmitting the currently sent packet
				 * to the server. That TimerTask thread will be responsible for contiunally retransmitting the current sendPkt over and over (once per every timeout interval)
				 * until the correct Ack is recieved here on this main thread (with correct ack being the sequence number of current sendPkt + 1), 
				 * at which point the main thread will cancel the timer task and move on to transmitting the next packet
				*/
				try {
					//wait to recieve appropraite Ack from server
					while (nxtSeqNum == lastSeqNum) {
						//(blocking call) waiting for ack (timeout_retransmit may be retransmitting packet in background while main thread waits here)
						UDPsock_to_srv.receive(recvPkt);
						//ACK response recieved from server

						//create FTPSegment object with datagram packet recieved from server (Ack from server) to evaluate its sequence number
						ftpRecvSeg = new FtpSegment(recvPkt);

						//COSNOLE OUTPUT
						System.out.println("ack " + ftpRecvSeg.getSeqNum());
						System.out.println();

						if (ftpRecvSeg.getSeqNum() == ftpSendSeg.getSeqNum()+1) {
							//cancel timeout retransmission task and update nxtSeqNum
							timeout_timer.cancel();
							nxtSeqNum = ftpRecvSeg.getSeqNum();
						}
						//else we ignore the ack and nxtSeqNum will still == lastSeqNum
						//and we go back to waiting for appropriate ack
					} 
					
				} catch (IOException ioe) {
					System.out.println("Error: an error occured trying to recieve Ack from server for last transmitted packet");
					ioe.printStackTrace();
					System.exit(1); 
				}
			}
		} catch (IOException ioe) {
			System.out.println("Error: an error occured trying to read data from the given input file: " + this.fileName);
			ioe.printStackTrace();
			System.exit(1); 
		}
	}

} // end of class