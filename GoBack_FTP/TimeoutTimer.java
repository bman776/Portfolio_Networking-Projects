//Brett Gattinger 30009390

import java.util.Timer;

/**
 * TimeoutTimer class responsible for sycnhronous, thread-safe management of a Timer object with which
 * to schedule and reschedule timeout packet retransmission tasks
 */
public class TimeoutTimer {
    //Timer object 
    private Timer timer;
    //timeout value
    private int rtoTimer;
    
    /**
     * Constructor for TimeoutTimer class
     * 
     * @param rtoTimer      timeout value for retransmission
     */
    public TimeoutTimer(int rtoTimer) {
        this.timer = new Timer(true);
        this.rtoTimer = rtoTimer;
    }

    /**
     * schedules a TimeoutRetransmitPacketTask to occur every timeout interval period
     * 
     * @param trpt  the TimeoutRetransmitPacketTask object to be executed at timeout
     */
    public synchronized void startTimer(TimeoutRestransmitPacketTask trpt) {
        this.timer = new Timer(true);
		this.timer.schedule(trpt, rtoTimer, rtoTimer);
	}

    /**
     * stops the timeout timer and cancels the timeout packet retransmission task
     */
	public synchronized void stoptimer() {
		this.timer.cancel();
	}
    
}
