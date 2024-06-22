/*
 * @description Demonstrates a deadlock caused by synchronized methods in objects that call one another in a Servlet.  Servlets are inherently multithreaded, so we don't need to actually start threads in the bad() and goodX() functions.  Implementation based on http://download.oracle.com/javase/tutorial/essential/concurrency/deadlock.html.
 * 
 * */
package juliet.testcases;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.logging.Level;

import juliet.support.IO;
import juliet.support.AbstractTestCaseServlet;

public class CWE833_Deadlock__synchronized_methods_Servlet_01_bad extends AbstractTestCaseServlet 
{
    private static final long serialVersionUID = 1L;

    /* Bad - Call to a synchronized method on another object while holding lock on this object */

    /* Create bad static objects */
    static CWE833_Deadlock__synchronized_methods_Servlet_01_bad objectBadFirst = new CWE833_Deadlock__synchronized_methods_Servlet_01_bad();
    static CWE833_Deadlock__synchronized_methods_Servlet_01_bad objectBadSecond = new CWE833_Deadlock__synchronized_methods_Servlet_01_bad();

    public synchronized void helperBowBad(CWE833_Deadlock__synchronized_methods_Servlet_01_bad bower) 
    {
        IO.writeLine("helperBowBad");
        
        try 
        { 
            Thread.sleep(1000); /* sleep for a bit to allow a context switch or the other thread to "catch up" */
        } 
        catch (InterruptedException exceptInterrupted) 
        {
            IO.logger.log(Level.WARNING, "Sleep Interrupted", exceptInterrupted);
        }
        
        /* FLAW: Call to a synchronized method on another object while holding lock on this object */
        bower.helperBowBackBad(this); 
    }

    public synchronized void helperBowBackBad(CWE833_Deadlock__synchronized_methods_Servlet_01_bad bower) 
    {
        IO.writeLine("helperBowBackBad");
    }

    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable 
    {
        /* Branch so that not all requests call the same method.  If a valid request and an invalid
         * one come in at the same time, a deadlock will result */
        if(request.isRequestedSessionIdValid()) 
        {
            objectBadFirst.helperBowBad(objectBadSecond);
        } 
        else 
        {
            objectBadSecond.helperBowBad(objectBadFirst);
        }
    }

    /* Good1 - Call to synchronized method on another object is made after giving up "lock" on this object */

    /* Create good1 static objects */
    static CWE833_Deadlock__synchronized_methods_Servlet_01_bad objectGood1First = new CWE833_Deadlock__synchronized_methods_Servlet_01_bad();
    static CWE833_Deadlock__synchronized_methods_Servlet_01_bad objectGood1Second = new CWE833_Deadlock__synchronized_methods_Servlet_01_bad();

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable 
    { return;} 
}
