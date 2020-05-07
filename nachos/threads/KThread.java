package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * 
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 * 	public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	public long waketime=0;
	private static ThreadQueue joinQueue = ThreadedKernel.scheduler.newThreadQueue(true);
	public boolean joinornot=false;
	/**
	 * Get the current thread.
	 * 
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		if (currentThread != null) {
			tcb = new TCB();
		}
		else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @param name the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 * 
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to
	 * the <tt>fork</tt> method) and the other thread (which executes its
	 * target's <tt>run</tt> method).
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: "
				+ target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 * 
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 */
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();

		Machine.autoGrader().finishingCurrentThread();

		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;
		currentThread.status = statusFinished;
		// since the joinqueue will only have 0/1 thread
		// we only need to let it call ready() if it is not null
		
		KThread temp=joinQueue.nextThread();
		if(temp!=null) temp.ready();
		KThread.sleep();
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 * 
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 * 
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 * 
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 */
	public void join() {
		// A calls B.join() which means A is the currentthread, B is this in join() method.
		// we need to set a joinQueue and a flag joinornot for each thread to record the thread who is waiting for him
		// and the times they call join() (which should be 0/1 ture/flase)
		
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != currentThread);
		Lib.assertTrue(joinornot==false);
		if(this.status==statusFinished) return;
		boolean threadstatus=Machine.interrupt().disable();
		joinornot=true;
		//System.out.println("The current thread is: "+ currentThread.getName()+ " The join thread is: "+ this.getName());
		//joinQueue.acquire(this);
		joinQueue.waitForAccess(currentThread);
		KThread.sleep();// sleep will always sleep the current thread no need to call currentThread.sleep()

		//this.joinornot=true;

		Machine.interrupt().restore(threadstatus);

	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 * 
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					KThread.yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 * 
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 * 
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 * 
	 * @param finishing <tt>true</tt> if the current thread is finished, and
	 * should be destroyed by the new thread.
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
				+ " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i
						+ " times");
				currentThread.yield();
			}
		}

		private int which;
	}

	/**
	 * Tests whether this module is working.
	 */
	/************************ TESTING *************************/
	private static class JoinTestA implements Runnable {
		KThread jointhread;
		JoinTestA(KThread thread){
			jointhread=thread;
		}
		public void run() {
			System.out.println("Before join, the time now is: "+ Machine.timer().getTime());
			jointhread.join();
			System.out.println("After join the thread "+ jointhread.name+ " the time now is: "+ Machine.timer().getTime());
		}
	}
	
	private static class JoinTestB implements Runnable{
		public void run() {
			System.out.println("The joined thread is running");
		}
	}
	
	private static class JoinTestC implements Runnable{
		public void run() {
			long sleeptime=1000;
			long temp=Machine.timer().getTime();
			System.out.println("C thread is running, the time now is: "+ temp+ " C thread should wait "+ sleeptime +" tickets");
			ThreadedKernel.alarm.waitUntil(sleeptime);
			System.out.println("the time now is: "+ Machine.timer().getTime()+" C has been waiting for "+ (Machine.timer().getTime()-temp)+ " tickets");
			
		}
		
	}
	
	private static class JoinTestD implements Runnable{
		KThread temp1;
		KThread temp2;
		JoinTestD(KThread thread1, KThread thread2){
			this.temp1=thread1;
			this.temp2=thread2;
		}
		public void run() {
			System.out.println("Before " + temp1.getName()+ " join, the time now is: "+ Machine.timer().getTime());
			temp1.join();
			System.out.println("After join the thread "+ temp1.name+ " the time now is: "+ Machine.timer().getTime());
			
			System.out.println("Before " + temp2.getName()+ " join, the time now is: "+ Machine.timer().getTime());
			temp2.join();
			System.out.println("After join the thread "+ temp2.name+ " the time now is: "+ Machine.timer().getTime());
		}
	}
	
	public static void selfTest() {
		System.out.println("\n**********Join Test Start**********");
//		Lib.debug(dbgThread, "Enter KThread.selfTest");
//		new KThread(new PingTest(0)).setName("forked thread").fork();
//		new PingTest(0).run();
		
		testCase1();
		testCase2();
		testCase3();
		testCase4();
		testCase5();
		//testCase6();
	}
	
	public static void testCase1() {
		System.out.println("\n**********Join TESTCASE 1**********");
	    Lib.debug(dbgThread, "Join Once");
	    KThread B = new KThread(new JoinTestB());
	    B.setName("Thread B");
	    KThread A = new KThread(new JoinTestA(B));
	    A.setName("Thread A");
	    A.fork();
	    B.fork();
	    ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase2() {
		System.out.println("\n**********Join TESTCASE 2**********");
		Lib.debug(dbgThread, "Join and Wait");
		KThread C = new KThread(new JoinTestC());
		KThread A2 = new KThread(new JoinTestA(C));
		A2.setName("Thread A2").fork();
		C.setName("Thread C").fork();
		ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase3() {
		System.out.println("\n**********Join TESTCASE 3**********");
		Lib.debug(dbgThread, "Mutiple Join");
		// Since D will be finished after B1, when time comes to B2, the D's status is finish, so B2.join() will return directly.
		KThread B1 = new KThread(new JoinTestB());
		B1.setName("Thread B1");
		KThread B2 = new KThread(new JoinTestA(B1));
		B2.setName("Thread B2");
		KThread A3 = new KThread(new JoinTestA(B2));
		A3.setName("Thread D");
		A3.fork();
		B2.fork();
		B1.fork();
		ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase4() {
		System.out.println("\n**********Join TESTCASE 4**********");
		Lib.debug(dbgThread, "Make thread finish before join");
		KThread B3 = new KThread(new JoinTestB());
		KThread A4 = new KThread(new JoinTestA(B3));
		B3.setName("Thread B3").fork();
		ThreadedKernel.alarm.waitUntil(20000);
		A4.setName("Thread A4").fork();
		ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase5() {
		System.out.println("\n**********Join TESTCASE 5**********");
		Lib.debug(dbgThread, "Join and Join");
		KThread B4 = new KThread(new JoinTestB());
		KThread B5 = new KThread(new JoinTestB());
		KThread D = new KThread(new JoinTestD(B4, B5));
		D.setName("Thread D").fork();
		B4.setName("Thread B4").fork();
		B5.setName("Thread B5").fork();
		ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase6() {
		// will cause assertion error
		System.out.println("\n**********Join TESTCASE 5**********");
		Lib.debug(dbgThread, "Join one thread");
		KThread B4 = new KThread(new JoinTestB());
		KThread B5 = B4;
		KThread D = new KThread(new JoinTestD(B4, B5));
		D.setName("Thread D").fork();
		B4.setName("Thread B4").fork();
		B5.setName("Thread B4-1").fork();
		ThreadedKernel.alarm.waitUntil(2000);
	}
	
	public static void testCase7() {
		
	}

	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 * 
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;

	private static final int statusReady = 1;

	private static final int statusRunning = 2;

	private static final int statusBlocked = 3;

	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;

	private String name = "(unnamed thread)";

	private Runnable target;

	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;

	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;

	private static ThreadQueue readyQueue = null;

	private static KThread currentThread = null;

	private static KThread toBeDestroyed = null;

	private static KThread idleThread = null;
}
