package nachos.threads;

import nachos.machine.*;

import java.util.HashMap;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    private nachos.threads.Lock lock;
    private HashMap<Integer, Integer> tag_val;
    private HashMap<Integer, nachos.threads.Condition> tag_con;

    public Rendezvous () {
        lock = new nachos.threads.Lock();
        tag_val = new HashMap<Integer, Integer>();
        tag_con = new HashMap<Integer, nachos.threads.Condition>();
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
        lock.acquire();
        // System.out.println("Exchanging...  thread_id = " + nachos.threads.KThread.currentThread().getName());
        boolean is_exchanged = true;
        if (!tag_val.containsKey(tag)) {
            is_exchanged = true;
            tag_val.put(tag, value);
            Condition con = new Condition(lock);
            tag_con.put(tag, con);
            con.sleep();
        }
        else {
            is_exchanged = false;
            Condition con = tag_con.get(tag);
            con.wake();
        }

        int val = tag_val.get(tag);
        tag_val.put(tag, value);
//        System.out.println("Thread_id: " + nachos.threads.KThread.currentThread().getName() + ", is_exchanged: " + is_exchanged);
        if (is_exchanged) {
            tag_val.remove(tag);
            tag_con.remove(tag);
        }
        lock.release();

        return val;
    }

    // Test case starting here ...
    public static void testCase1() {
        System.out.println("\n**********Rendezvous TESTCASE 1**********");
        System.out.println("Exchange returns the exchanged values from the threads properly.");
        final Rendezvous p = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 1;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 2, "Expecting " + 2 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received value " + ans);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 1, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received " + ans);
            }
        });
        t2.setName("t2");

        t1.fork();
        t2.fork();

        t1.join();
        t2.join();
    }

    public static void testCase2() {
        System.out.println("**********Rendezvous TESTCASE 2**********");
        System.out.println("many threads can call exchange on the same tag, and exchange will correctly pair them up and exchange their values.");
        final Rendezvous p = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 1;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 2, "Expecting " + 2 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received value: " + ans);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 1, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received value: " + ans);
            }
        });
        t2.setName("t2");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 3;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 4, "Expecting " + 4 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received value: " + ans);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 4;
                System.out.println("Thread " + KThread.currentThread().getName() + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 3, "Expecting " + 3 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", received value: " + ans);
            }
        });
        t4.setName("t4");

        t1.fork();
        t2.fork();

        t1.join();
        t2.join();

        t3.fork();
        t4.fork();

        t3.join();
        t4.join();
    }

    public static void testCase3() {
        System.out.println("**********Rendezvous TESTCASE 3**********");
        System.out.println("Threads exchanging values on different tags operate independently of each other.");
        final Rendezvous p = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 1;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 3, "Expecting " + 3 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", received value: " + ans);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int val = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 4, "Expecting " + 4 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", received value: " + ans);
            }
        });
        t2.setName("t2");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 3;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 1, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", received value: " + ans);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int val = 4;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", original value: " + val);
                int ans = p.exchange (tag, val);
                Lib.assertTrue (ans == 2, "Expecting " + 2 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", received value: " + ans);
            }
        });
        t4.setName("t4");

        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();

        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }

    public static void testCase4() {
        System.out.println("**********Rendezvous TESTCASE 4**********");
        System.out.println("Threads exchanging values on different instances of Rendezvous operate independently of each other.");
        final Rendezvous p1 = new Rendezvous();
        final Rendezvous p2 = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 1;
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 1" + ", original value: " + val);
                int ans = p1.exchange (tag, val);
                Lib.assertTrue (ans == 3, "Expecting " + 3 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 1" + ", received value: " + ans);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 2" + ", original value: " + val);
                int ans = p2.exchange (tag, val);
                Lib.assertTrue (ans == 4, "Expecting " + 4 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 2" + ", received value: " + ans);
            }
        });
        t2.setName("t2");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 3;
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 1" + ", original value: " + val);
                int ans = p1.exchange (tag, val);
                Lib.assertTrue (ans == 1, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 1" + ", received value: " + ans);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 4;
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 2" + ", original value: " + val);
                int ans = p2.exchange (tag, val);
                Lib.assertTrue (ans == 2, "Expecting " + 2 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", instance: 2" + ", received value: " + ans);
            }
        });
        t4.setName("t4");

        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();

        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }

    public static void testCase5() {
        System.out.println("\n**********Rendezvous TESTCASE 5**********");
        System.out.println("A thread only returns from exchange when another thread synchronizes with it.");
        final Rendezvous p = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int val = 1;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", original value: " + val);
                int ans = p.exchange (tag, val);
//                Lib.assertTrue (ans == 2, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 0" + ", received value " + ans);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int val = 2;
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", original value: " + val);
                int ans = p.exchange (tag, val);
//                Lib.assertTrue (ans == 1, "Expecting " + 1 + ", but received: " + ans);
                System.out.println("Thread " + KThread.currentThread().getName() + ", tag: 1" + ", received " + ans);
            }
        });
        t2.setName("t2");

        t1.fork();
        t2.fork();

        t1.join();
        t2.join();
    }

    public static void selfTest() {
        System.out.println("\n**********Start Rendezvous Testing**********");
        testCase1();
        testCase2();
        testCase3();
        testCase4();
//        testCase5();
    }
}
