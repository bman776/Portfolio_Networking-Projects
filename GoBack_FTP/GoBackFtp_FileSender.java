import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * FileSender thread class responsible for reading bytes from the input file and transmitting them to the server
 * via the given connectionelss UDP socket to the server
 */
public class GoBackFtp_FileSender extends Thread {

	private static final int EOF = -1;
    
    //reference to spawning parent thread
    private Thread parentThread;

    //thread status attributes
    public static AtomicBoolean finishedSending;
	public static AtomicBoolean beganSending;

	//remote server attributes
	private String serverName;
	private InetAddress serverIP;
	private DatagramSocket UDPsock_to_srv;
	private int srv_UDP_portNum;
	private int srv_UDP_initSeqNum;

    //File reading attributes
	private String fileName;
	private FileInputStream ftp_FileReader;
	private byte[] ftp_FileReader_buffer;

	//Packet transmission attributes
	private LinkedBlockingQueue<FtpSegment> transmissionQueue;
	private int windowSize;
	private TimeoutTimer timeoutTimer;

	/**
	 * Constructor for GoBackFtp_FileSender Thread class
	 * 
	 * @param parentThread			reference to spawning parent thread (main program thread)
	 * @param serverName			string hostname of the server 
	 * @param srv_UDP_portNum		port number of remote server's UDP socket
	 * @param srv_UDP_initSeqNum	initial sequence number used by server and given during initial TCP handshake
	 * @param UDPsock_to_srv		reference to the local UDP socket being used to transmit segments to the server
	 * @param ftp_FileReader		reference to the FileInputStream object from which bytes of the input file can be read
	 * @param windowSize			capacity (window size) of the transmission queue 
	 * @param timeoutTimer			reference to the timeoutTimer object responsible for triggering segment retransmission at timeout
	 * @param transmissionQueue		reference to the transmission queue which holds a copy of all in-flight segments
	 */
    public GoBackFtp_FileSender(Thread parentThread, String serverName, int srv_UDP_portNum,  int srv_UDP_initSeqNum, DatagramSocket UDPsock_to_srv, FileInputStream ftp_FileReader, int windowSize, TimeoutTimer timeoutTimer, LinkedBlockingQueue<FtpSegment> transmissionQueue) {
		
		//get reference to spwaning parent thread
		this.parentThread = parentThread;

		//create server IP address from given server host name
		this.serverName = serverName;
		try {
			this.serverIP = InetAddress.getByName(serverName);
		} catch (UnknownHostException uhe) {
			System.out.println("Error: FileSender Thread could not resilve server IP address from server name\n");
			uhe.printStackTrace();
			System.exit(1);
		}
		
		//get reference to UDP socket to server as well as remote server's UDP port number and initial sequence number
		this.UDPsock_to_srv = UDPsock_to_srv;
		this.srv_UDP_portNum = srv_UDP_portNum;
		this.srv_UDP_initSeqNum = srv_UDP_initSeqNum;

		//get reference to FileInputStream from file to be transmitted
		this.ftp_FileReader = ftp_FileReader;

		//initialize file reader buffer to Ftp segment payload size
		ftp_FileReader_buffer = new byte[FtpSegment.MAX_PAYLOAD_SIZE];

		//get reference to transmission queue plus its capacity limit (transmission window size) 
		this.windowSize = windowSize;
		this.transmissionQueue = transmissionQueue;

		//get reference to timeout timer object responsible for triggering retransmission
		this.timeoutTimer = timeoutTimer;

		//Initialize atomic boolean attributes
		beganSending = new AtomicBoolean(false);		//set to true when FileSender thread begins transmitting very first segment
		finishedSending = new AtomicBoolean(false);	//set to true when FileSender has read all bytes from input file and encapsulated them in segments

    }





	/**
	 * Transmits the bytes of the given input file to the server via connectionless UDP socket
	 */
	private void transmitFile() {

		//initialize sequence number counter variable
		int nxtSeqNum = srv_UDP_initSeqNum;

		//initialize FtpSegment and DatagramPacket objects for UDP communication
		FtpSegment ftpSendSeg = null;
		DatagramPacket sendPkt = null;

		//begin transmitting input file
		int file_bytesRead;
		try {
			while ( (file_bytesRead = ftp_FileReader.read(ftp_FileReader_buffer)) != EOF ) {

				//encapsulate data read in from file in Ftp segment
				ftpSendSeg = new FtpSegment(nxtSeqNum, ftp_FileReader_buffer, file_bytesRead);

				//encapsulate Ftp segment in Datagram packet to send over UDP connection
				sendPkt = FtpSegment.makePacket(ftpSendSeg, serverIP, srv_UDP_portNum);

				//add segment to transmission queue (waiting if necessary)
				while (true) {
					try {
						transmissionQueue.add(ftpSendSeg);
	
						break;
					} catch (IllegalStateException ise) {
	
					}
				}

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

				

				//signal to AckReceiver thread that file transmission has begun and start initial timeoutTimer
				// (if not done so already)
				if (!(beganSending.get())) {
					beganSending.set(true);
					synchronized(timeoutTimer) {
						timeoutTimer.startTimer(new TimeoutRestransmitPacketTask(serverName, srv_UDP_portNum, UDPsock_to_srv, transmissionQueue));
					}
				}

				//increment to next sequence number
				nxtSeqNum++;

			}
			finishedSending.set(true);
			
			
		} catch (IOException ioe) {
			System.out.println("Error: an error occured trying to read data from the given input file: " + this.fileName);
			ioe.printStackTrace();
			System.exit(1); 
		}
	}


	public void run() {
		transmitFile();

		//DEBUGGING
		//System.out.println("FileSender finished");	
	}

}