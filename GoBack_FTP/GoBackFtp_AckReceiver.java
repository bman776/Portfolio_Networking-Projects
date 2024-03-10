import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GoBackFtp_AckReceiver extends Thread {
    
    //reference to spawning parent thread
    private Thread parentThread;

	private final int SEND_THREAD_FIN_CHECKTIME = 100;

	//remote server attributes
	private String serverName;
	private DatagramSocket UDPsock_to_srv;
	private int srv_UDP_portNum;

    //Packet transmission attributes
	private TimeoutTimer timeoutTimer;
	private LinkedBlockingQueue<FtpSegment> transmissionQueue;

	/**
	 * Constructor for GoBackFtp_Ackreciever Thread class
	 * 
	 * @param parentThread			reference to spawning parent thread (main program thread)
	 * @param serverName			string hostname of the server 
	 * @param srv_UDP_portNum		port number of remote server's UDP socket
	 * @param UDPsock_to_srv		reference to the local UDP socket being used to transmit segments to the server
	 * @param timeoutTimer			reference to the timeoutTimer object responsible for triggering segment retransmission at timeout
	 * @param transmissionQueue		reference to the transmission queue which holds a copy of all in-flight segments
	 */
    public GoBackFtp_AckReceiver(Thread parentThread, String serverName, int srv_UDP_portNum, DatagramSocket UDPsock_to_srv, TimeoutTimer timeoutTimer, LinkedBlockingQueue<FtpSegment> transmissionQueue) {
        
		//get reference to spwaning parent thread
		this.parentThread = parentThread;

		//get reference to string hostname of server
		this.serverName = serverName;

		//get reference to the local UDP socket being used to transmit segments to the server
        this.UDPsock_to_srv = UDPsock_to_srv;

		//get remote server's UDP socket port number
		this.srv_UDP_portNum = srv_UDP_portNum;

		//get reference to transmission queue and timeoutTimer object
		this.transmissionQueue = transmissionQueue;
		this.timeoutTimer = timeoutTimer;
		
    }

	/**
	 * Listens for Datagram packets encapsulating Acks from the server 
	 */
	private void listenForAcks() {

		//wait for FileSender to start sending segments
		while(!(GoBackFtp_FileSender.beganSending.get())) {
			//wait
		}

		//initialize FtpSegment and DatagramPacket objects for UDP communication
		byte[] recvData = new byte[FtpSegment.MAX_SEGMENT_SIZE];
		DatagramPacket recvPkt = null;
		FtpSegment ftpRecvSeg = null;

		//Begin listening for Acks for as long as FileSender thread is sending segments to the server and the transmission queue is not empty
		while ( !(GoBackFtp_FileSender.finishedSending.get()) || !(transmissionQueue.isEmpty()) ) {

			try {
				
				//initialize a datagram packet object to store received Ack from server 
				recvPkt = new DatagramPacket(recvData, recvData.length);

				//BLOCKING CALL ~ wait to recieve Ack from server
				//===============================================
				UDPsock_to_srv.receive(recvPkt);	
				//===============================================
				
				//Ack recieved from server
				//extract Ftp segment from received datagram packet 
				ftpRecvSeg = new FtpSegment(recvPkt);
				//COSNOLE OUTPUT
				System.out.println("ack " + ftpRecvSeg.getSeqNum());
				System.out.println();

				//check sequence number of Ack received against that of oldest in-flight segment in transmission queue (i.e at head of queue)
				if (!transmissionQueue.isEmpty() && transmissionQueue.peek().getSeqNum() < ftpRecvSeg.getSeqNum()) {

					//cancel retransmission timer
					timeoutTimer.stoptimer();

					//update transmission queue (cumulative Ack update)
					while ( !transmissionQueue.isEmpty() && (transmissionQueue.peek().getSeqNum() < ftpRecvSeg.getSeqNum())) {
						transmissionQueue.poll();
					}

					//restart transmission timer if transmission queue not empty
					if (!transmissionQueue.isEmpty()) {
						synchronized(timeoutTimer) {
							timeoutTimer.startTimer(new TimeoutRestransmitPacketTask(serverName, srv_UDP_portNum, UDPsock_to_srv, transmissionQueue));
						}
					}
					
				}
			} catch (SocketTimeoutException ste) {
				/*
				 * catching socket timeout exception here breaks us out of blocking .receive() call
				 * allowing execution to return to condition check in while loop head
				 */
			} catch (IOException ioe) {
				System.out.println("Error: an error occured trying to recieve Ack from server");
				ioe.printStackTrace();
				System.exit(1); 
			}
		}
		
	}

	public void run() {
		listenForAcks();

		//DEBUGGING
		//System.out.println("AckReceiver finished");	
	}
}