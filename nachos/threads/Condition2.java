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

        private Lock conditionLock;
}
