package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	List<KThread> waitqueue=new LinkedList<KThread>();
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean threadstatus=Machine.interrupt().disable();
		long currenttime=Machine.timer().getTime();
	
		if(waitqueue.size()==0) {
			return;
		}
		else {
			for(int i=0; i<waitqueue.size(); i++) {
				//System.out.println("Who is finish: "+ waitqueue.get(i).getName()+ " index= "+ i);
				if(currenttime>=waitqueue.get(i).waketime) {
					waitqueue.get(i).ready();
					//System.out.println(waitqueue.get(i).getName());
					waitqueue.remove(i);
					// remove will shift the i+1th thread to i
					i--; 
				}
				else {
					// if thread(i) cannot be awakened, 
					// other threads behind it will not be awakened too.
					break; 
				}
			}
		}
		
		Machine.interrupt().restore(threadstatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		
		boolean threadstatus=Machine.interrupt().disable();
		//System.out.println("Who is waiting: "+KThread.currentThread().getName());
		long wakeTime = Machine.timer().getTime() + x;
		KThread tempthread=KThread.currentThread();
		tempthread.waketime=wakeTime;
		
		if(waitqueue.size()==0) {
			waitqueue.add(tempthread);
		}
		else {
			for(int i=0; i<waitqueue.size(); i++) {
				if(wakeTime<waitqueue.get(i).waketime) {
					waitqueue.add(i, KThread.currentThread());
					break;
				}
				if(i==waitqueue.size()-1){
					waitqueue.add(KThread.currentThread());
					break;
				}
			}
		}
		KThread.sleep();
		Machine.interrupt().restore(threadstatus);
		Machine.interrupt().enable();
	}
	

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
		return false;
	}
    /************************ TESTING *************************/
    private static class AlarmTestA implements Runnable{
		int counter=0;
		String name=null;
		AlarmTestA(int temp, String n){
			this.counter=temp;
			this.name=n;
		}
		public void run() {
			if(counter>10) {
				counter=10;
			}
			long sleeptime=50*counter;
			long time=Machine.timer().getTime();
			System.out.println("Now "+this.name+" works at: "+ time+" sleep "+sleeptime);
			ThreadedKernel.alarm.waitUntil(sleeptime);
			System.out.println(this.name+" works again at: "+ Machine.timer().getTime() +" acturally sleep: "+(Machine.timer().getTime()-time));
		}
		
	}
	
	public static void selfTest() {
		System.out.println("\n**********Alarm Test Start**********");
		testCase1();
		testCase2();
		testCase3();
		testCase4();
	}
	
	public static void testCase1() {
		System.out.println("\n**********Alarm TESTCASE 1**********");
		long time=20000;
		long time1=Machine.timer().getTime();
		System.out.println(KThread.currentThread().toString()+" works at: "+ Machine.timer().getTime()+" sleep "+time);
		ThreadedKernel.alarm.waitUntil(time);
		System.out.println(KThread.currentThread().toString()+" works again at: "+ Machine.timer().getTime() +" acturally sleep: "+(Machine.timer().getTime()-time1));
	}
	
	public static void testCase2() {
		System.out.println("\n**********Alarm TESTCASE 2**********");
		long time=10;
		for(int i=0;i<5;i++) {
			time=time*10;
			KThread temp=new KThread();
			temp.setName("Thread"+i);
			long time1=Machine.timer().getTime();
			System.out.println(temp.toString()+" works at: "+ time1+" sleep "+time);
			ThreadedKernel.alarm.waitUntil(time);
			System.out.println(temp.toString()+" works again at: "+ Machine.timer().getTime() +" acturally sleep: "+(Machine.timer().getTime()-time1));
		}
		
	}
	
	public static void testCase3() {
		System.out.println("\n**********Alarm TESTCASE 3**********");
		// Mutiple threads wait at different or same time
		for(int i=0;i<20;i++) {
			String name="Thread A"+i;
			KThread A = new KThread(new AlarmTestA(i, name));
			A.setName(name).fork();
		}

	}
	
	public static void testCase4() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
		    t0 = Machine.timer().getTime();
		    ThreadedKernel.alarm.waitUntil (d);
		    t1 = Machine.timer().getTime();
		    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}
        
}
