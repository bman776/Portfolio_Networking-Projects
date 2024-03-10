
//Brett Gattinger 30009390

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimerTask;

public class TimeoutRetransmitPacketTask extends TimerTask {

    private InetAddress srvIP;
    private int srv_UDP_portNum;
    private DatagramSocket UDPsock_to_srv;
    private FtpSegment ftpSendSeg;
    private DatagramPacket sendPkt;

    /**
     * Constructor for the TimeoutRetrasnmitPacket Task TimerTask class
     * 
     * @param srv_Name                  the hostname of the server
     * @param srv_UDP_portNum           the port number of the remote server UDP datagram socket
     * @param UDPsock_to_srv            the reference to the local UDP datagram socket meant to send packets to server (thread safe)
     * @param ftpSendSeg                the reference to the FtpSegment object to be retransmitted at timeout (not thread safe ~ must be value-copied)
     * @throws UnknownHostException 
     * 
     * DEV NOTE: UDP Datagram Sockets in Java are thread safe: 
     * https://stackoverflow.com/questions/16498223/is-datagramsocket-send-thread-safe#:~:text=Yes%2C%20it%20is%20thread%20safe.%20However%2C%20because%20network,But%20that%20has%20nothing%20to%20do%20with%20multithreading.
     * so they can be passed by reference here
     */
    public TimeoutRetransmitPacketTask(String srv_Name, int srv_UDP_portNum, DatagramSocket UDPsock_to_srv, FtpSegment ftpSendSeg) throws UnknownHostException {
        //use provided data to copy the packet to be retransmitted in case of timeout
        this.srvIP = InetAddress.getByName(srv_Name);
        this.srv_UDP_portNum = srv_UDP_portNum;
        this.UDPsock_to_srv = UDPsock_to_srv;
        this.ftpSendSeg = new FtpSegment(ftpSendSeg);
        this.sendPkt = FtpSegment.makePacket(ftpSendSeg, srvIP, srv_UDP_portNum);
    }

    @Override
    public void run() {
        //DEBUGGING
        System.out.println("timeout");
        System.out.println();

        //send DatagramPacket object over the UDP connection
        try {
            UDPsock_to_srv.send(sendPkt);
        } catch (IOException ioe) {
            System.out.println("Error: an error occured trying to send UDP datagram packet to server");
            ioe.printStackTrace();
            System.exit(1);
        }
        System.out.println("retx " + ftpSendSeg.getSeqNum());
        System.out.println();

    }
    
}
