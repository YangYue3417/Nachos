package nachos.threads;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
	return 0;
    }

    public static void exchSyncTest(){
//        a thread only returns from exchange when another thread synchronizes with it
    }

    public static void exchReternTest(){
//        exchange returns the exchanged values from the threads properly;
    }

    public static void nTo1Test(){
//        many threads can call exchange on the same tag,
//        and exchange will correctly pair them up and exchange their values;
    }

    public static void independenceTest(){
//        threads exchanging values on different tags operate independently of each other
    }

    public static void diffInstanceTest(){
//        threads exchanging values on different instances
//        of Rendezvous operate independently of each other.
    }
}
