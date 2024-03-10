//Brett Gattinger 30009390

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * TimeoutRetransmitPacketTask class responsible for performing segment retransmission of transmission queue whenever a timeout occurs
 */
public class TimeoutRestransmitPacketTask extends TimerTask {
    
    //File retransmission attributes
    private InetAddress serverIP;
    private int srv_UDP_portNum;
    private DatagramSocket UDPsock_to_srv;
    private LinkedBlockingQueue<FtpSegment> transmissionQueue;

    /**
     * Constructor for TimeoutRetransmitPacketTask
     * 
     * @param serverName            string hostname of the server 
     * @param srv_UDP_portNum       port number of remote server's UDP socket
     * @param UDPsock_to_srv        reference to the local UDP socket being used to transmit segments to the server
     * @param transmissionQueue     reference to the transmission queue which holds a copy of all in-flight segments
     */
    public TimeoutRestransmitPacketTask(String serverName, int srv_UDP_portNum, DatagramSocket UDPsock_to_srv, LinkedBlockingQueue<FtpSegment> transmissionQueue) {
        try {
            this.serverIP = InetAddress.getByName(serverName);
        } catch (UnknownHostException uhe) {
            System.out.println("Error: Cannot create retransmission timeout task; server IP address could not be found\n");
			uhe.printStackTrace();
			System.exit(1);
        }
        
        this.UDPsock_to_srv = UDPsock_to_srv;
        
        this.srv_UDP_portNum = srv_UDP_portNum;
        this.transmissionQueue = transmissionQueue;
    }

    public void run() {

        //COSNOLE OUTPUT
        System.out.println("timeout");
        System.out.println();

        //initialize FtpSegment and DatagramPacket objects for UDP communication
		FtpSegment ftpSendSeg = null;
		DatagramPacket sendPkt = null;

        //create iterator to iterate through transmission queue
        Iterator<FtpSegment> transmissionQueueIterator = transmissionQueue.iterator();

        //iterate through transmission queue (starting at oldest in flight packets) and retransmit them
        while (transmissionQueueIterator.hasNext()) {

            //get next in-flight segment in transmission queue
            ftpSendSeg = transmissionQueueIterator.next();

            //encapsulate Ftp segment in Datagram packet to send over UDP connection
            sendPkt = FtpSegment.makePacket(ftpSendSeg, serverIP, srv_UDP_portNum);

            //retransmit Ftpsegment
            try {
                UDPsock_to_srv.send(sendPkt);
            } catch (IOException ioe) {
                System.out.println("Error: an error occured trying to send UDP datagram packet to server during retransmission timeout task");
                ioe.printStackTrace();
                System.exit(1);
            }
            //COSNOLE OUTPUT
			System.out.println("retx " + ftpSendSeg.getSeqNum());
			System.out.println();
        }
    }


}
