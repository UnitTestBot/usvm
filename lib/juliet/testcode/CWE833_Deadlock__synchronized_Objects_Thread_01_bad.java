/*
 * @description Demonstrates a deadlock caused by synchronizing on objects in a different order in different functions in multithreaded code that accesses shared variables.
 * 
 * */
package juliet.testcases;

import java.util.logging.Level;

import juliet.support.AbstractTestCase;
import juliet.support.IO;

public class CWE833_Deadlock__synchronized_Objects_Thread_01_bad extends AbstractTestCase 
{
    /* bad(): Synchronize on objects in different order in helperAddBad and helperMultiplyBad, causing a deadlock */
    static private int intBadNumber1 = 3;
    static private final Object BAD_NUMBER1_LOCK = new Object();

    static private int intBadNumber2 = 5;
    static private final Object BAD_NUMBER2_LOCK = new Object();

    static public void helperAddBad()
    {
        synchronized(BAD_NUMBER1_LOCK) 
        {
            try 
            { 
                Thread.sleep(1000); /* sleep for a bit to allow a context switch or the other thread to "catch up" */
            } 
            catch (InterruptedException exceptInterrupted) 
            {
                IO.logger.log(Level.WARNING, "Sleep Interrupted", exceptInterrupted);
            }
            
            synchronized(BAD_NUMBER2_LOCK) 
            {
                intBadNumber1 = intBadNumber1 + intBadNumber2;
            }
        }
    }

    static public void helperMultiplyBad()
    {
        /* FLAW: Synchronize on objects in the opposite order as in helperAddBad() */
        synchronized(BAD_NUMBER2_LOCK) 
        { 
            try 
            { 
                Thread.sleep(1000); /* sleep for a bit to allow a context switch or the other thread to "catch up" */
            } 
            catch (InterruptedException exceptInterrupted) 
            {
                IO.logger.log(Level.WARNING, "Sleep Interrupted", exceptInterrupted);
            }
            
            synchronized(BAD_NUMBER1_LOCK) 
            {
                intBadNumber1 = intBadNumber1 * intBadNumber2;
            }
        }
    }

    public void bad() throws Throwable 
    {
        /* Create threads */
        Thread threadOne = new Thread(new Runnable() 
        {
            public void run() 
            { 
                CWE833_Deadlock__synchronized_Objects_Thread_01_bad.helperAddBad(); 
            }
        });
        
        Thread threadTwo = new Thread(new Runnable() 
        {
            public void run() 
            { 
                CWE833_Deadlock__synchronized_Objects_Thread_01_bad.helperMultiplyBad(); 
            }
        });

        /* Start threads */
        threadOne.start();
        threadTwo.start();

        /* Wait for threads to finish (though they never will since they are deadlocked) */
        threadOne.join();
        threadTwo.join();

        /* write output */
        IO.writeLine(intBadNumber1);
    }

    /* good1() - Synchronize on objects in same order in helperAddGood1() and helperMultiplyGood1() */
    static private int intGood1Number1 = 3;
    static private final Object GOOD_NUMBER1_LOCK = new Object();

    static private int intGood1Number2 = 5;
    static private final Object GOOD_NUMBER2_LOCK = new Object();

    public void good() throws Throwable 
    { return;}

    /* Below is the main(). It is only used when building this testcase on 
     * its own for testing or for building a binary to use in testing binary 
     * analysis tools. It is not used when compiling all the juliet.testcases as one 
     * application, which is how source code analysis tools are tested. */ 
    public static void main(String[] args) 
            throws ClassNotFoundException, InstantiationException, IllegalAccessException 
    {
        mainFromParent(args);
    }
}
