/*
 * @description Demonstrates use of unlock() more times than lock() in a Servlet that updates a shared variable.  Servlets are inherently multithreaded, so we don't need to actually start threads in the bad() and goodX() functions.
 * 
 * */
package juliet.testcases;

import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import juliet.support.AbstractTestCaseServlet;

public class CWE832_Unlock_Not_Locked__ReentrantLock_Servlet_01_good extends AbstractTestCaseServlet 
{
    private static final long serialVersionUID = 1L;

    /* bad(): Use unlock() on resource that is not locked */
    static private int intBad = 1;
    static private final ReentrantLock REENTRANT_LOCK_BAD = new ReentrantLock();

    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable 
    { return;}
 
    /* good1(): Use a ReentrantLock properly (use lock() once and unlock() once) */
    static private int intGood1 = 1;
    static private final ReentrantLock REENTRANT_LOCK_GOOD1 = new ReentrantLock();

    static public void helperGood1() 
    {
        REENTRANT_LOCK_GOOD1.lock();  /* Inserted lock here that was missing in bad() */
        /* good practice is to unlock() in a finally block, see
		 * http://download.oracle.com/javase/6/docs/api/java/util/concurrent/locks/ReentrantLock.html */
        try
        { 
            intGood1 = intGood1 * 2;
        } 
        finally 
        {
            REENTRANT_LOCK_GOOD1.unlock(); /* FIX: Only unlock() a lock that has been locked. */
        }
    }

    private void good1(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {        
        helperGood1();
        response.getWriter().write(intGood1);
    }  

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable 
    {
        good1(request, response);
    } 
}
