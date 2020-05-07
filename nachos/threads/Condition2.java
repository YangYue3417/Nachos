package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	private List<KThread> waitQueue = new LinkedList<KThread>();
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * re-acquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		waitQueue.add(KThread.currentThread());
		boolean status= Machine.interrupt().disable();
		
		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();
		
		Machine.interrupt().restore(status);

	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status= Machine.interrupt().disable();
		
		if(!waitQueue.isEmpty()) {
			KThread temp=waitQueue.remove(0);
			if(temp!=null) {
				boolean flag=ThreadedKernel.alarm.cancel(temp);
				if(!flag) {
					temp.ready();
				}
			}
		}
		
		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status= Machine.interrupt().disable();
		
		while(!waitQueue.isEmpty()) {
			wake();
		}
		
		Machine.interrupt().restore(status);
	}

    /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically re-acquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
        public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status= Machine.interrupt().disable();
		conditionLock.release();
		KThread temp=KThread.currentThread();
		// we need to add it inside the CV queue, cuz if someone wake it up, it will be pushed out of the CV queue
		waitQueue.add(temp);
		ThreadedKernel.alarm.waitUntil(timeout);
		// if timeout, the thread will still in the waitQueue, we need to push it out
		if(waitQueue.contains(temp)) {
			waitQueue.remove(temp);
		}
		conditionLock.acquire();
		Machine.interrupt().restore(status);
	}
        public static class InterlockTest {
            private static Lock lock;
            private static Condition2 cv;

            public static class Interlocker implements Runnable {
                public void run () {
                    lock.acquire();
                    for (int i = 0; i < 10; i++) {
                        System.out.println(KThread.currentThread().getName());
                        cv.wake();   // signal
                        cv.sleep();  // wait
                    }
                    lock.release();
                }
            }

            public InterlockTest () {
        		System.out.println("\n**********PingPong Test Start**********");
                lock = new Lock();
                cv = new Condition2(lock);

                KThread ping = new KThread(new Interlocker());
                ping.setName("ping");
                KThread pong = new KThread(new Interlocker());
                pong.setName("pong");

                ping.fork();
                pong.fork();

                // We need to wait for ping to finish, and the proper way
                // to do so is to join on ping.  (Note that, when ping is
                // done, pong is sleeping on the condition variable; if we
                // were also to join on pong, we would block forever.)
                // For this to work, join must be implemented.  If you
                // have not implemented join yet, then comment out the
                // call to join and instead uncomment the loop with
                // yields; the loop has the same effect, but is a kludgy
                // way to do it.
                ping.join();
                // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
            }
        }

        // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()


        
        // Place Condition2 test code inside of the Condition2 class.

        // Test programs should have exactly the same behavior with the
        // Condition and Condition2 classes.  You can first try a test with
        // Condition, which is already provided for you, and then try it
        // with Condition2, which you are implementing, and compare their
        // behavior.

        // Do not use this test program as your first Condition2 test.
        // First test it with more basic test programs to verify specific
        // functionality.

        public static void cvTest5() {
            final Lock lock = new Lock();
            // final Condition empty = new Condition(lock);
            final Condition2 empty = new Condition2(lock);
            final LinkedList<Integer> list = new LinkedList<>();

            KThread consumer = new KThread( new Runnable () {
                    public void run() {
                        lock.acquire();
                        while(list.isEmpty()){
                            empty.sleep();
                        }
                        Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                        while(!list.isEmpty()) {
                            // context swith for the fun of it
                            System.out.println("Removed " + list.removeFirst());
                            KThread.yield();
                        }
                        lock.release();
                    }
                });

            KThread producer = new KThread( new Runnable () {
                    public void run() {
                        lock.acquire();
                        for (int i = 0; i < 5; i++) {
                            list.add(i);
                            System.out.println("Added " + i);
                            // context swith for the fun of it
                            KThread.currentThread().yield();
                        }
                        empty.wake();
                        lock.release();
                    }
                });

            consumer.setName("Consumer");
            producer.setName("Producer");
            consumer.fork();
            producer.fork();

            // We need to wait for the consumer and producer to finish,
            // and the proper way to do so is to join on them.  For this
            // to work, join must be implemented.  If you have not
            // implemented join yet, then comment out the calls to join
            // and instead uncomment the loop with yield; the loop has the
            // same effect, but is a kludgy way to do it.
            consumer.join();
            producer.join();
            //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }

        public static void sleepTest1(){
//			verify that sleep blocks the calling thread;
		}

		public static void wakeTest1(){
//			wake wakes up at most one thread, even if multiple threads are waiting;
		}

		public static void wakeAllTest(){
//			wakeAll wakes up all waiting threads;
		}

		public static void callWOSyncLockTest(){
//			if a thread calls any of the synchronization methods
//			without holding the lock, Nachos asserts
		}

		public static void wakeLostTest(){
//			wake and wakeAll with no waiting threads have no effect,
//			yet future threads that sleep will still block
//			(i.e., the wake/wakeAll is "lost", which is in contrast to the semantics of semaphores).
		}

        public static void sleepForTest1(){
//			a thread that calls sleepFor will timeout and return after x ticks
//			if no other thread calls wake to wake it up


		}

		public static void sleepForTest2(){
//			a thread that calls sleepFor will wake up and return
//			if another thread calls wake before the timeout expires;
		}

		public static void sleepForTest3(){
//			sleepFor handles multiple threads correctly
//			(e.g., different timeouts, all are woken up with wakeAll)
		}

        public static void selfTest() {
    		System.out.println("\n**********Condition2 Test Start**********");
            new InterlockTest();
			System.out.println("\n**********Consumer-Producer Test Start**********");
            cvTest5();
        }

        private Lock conditionLock;
}
