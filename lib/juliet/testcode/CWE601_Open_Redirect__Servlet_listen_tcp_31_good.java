/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE601_Open_Redirect__Servlet_listen_tcp_31.java
Label Definition File: CWE601_Open_Redirect__Servlet.label.xml
Template File: sources-sink-31.tmpl.java
*/
/*
 * @description
 * CWE: 601 Open Redirect
 * BadSource: listen_tcp Read data using a listening tcp connection
 * GoodSource: A hardcoded string
 * Sinks:
 *    BadSink : place redirect string directly into redirect api call
 * Flow Variant: 31 Data flow: make a copy of data within the same method
 *
 * */

package juliet.testcases;

import juliet.support.*;

import javax.servlet.http.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

import java.util.logging.Level;

import java.net.URI;
import java.net.URISyntaxException;

public class CWE601_Open_Redirect__Servlet_listen_tcp_31_good extends AbstractTestCaseServlet
{
    /* uses badsource and badsink */
    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        goodG2B(request, response);
    }

    /* goodG2B() - use goodsource and badsink */
    private void goodG2B(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String dataCopy;
        {
            String data;

            /* FIX: Use a hardcoded string */
            data = "foo";

            dataCopy = data;
        }
        {
            String data = dataCopy;

            if (data != null)
            {
                /* This prevents \r\n (and other chars) and should prevent incidentals such
                 * as HTTP Response Splitting and HTTP Header Injection.
                 */
                URI uri;
                try
                {
                    uri = new URI(data);
                }
                catch (URISyntaxException exceptURISyntax)
                {
                    response.getWriter().write("Invalid redirect URL");
                    return;
                }
                /* POTENTIAL FLAW: redirect is sent verbatim; escape the string to prevent ancillary issues like XSS, Response splitting etc */
                response.sendRedirect(data);
                return;
            }

        }
    }

    /* Below is the main(). It is only used when building this testcase on
     * its own for testing or for building a binary to use in testing binary
     * analysis tools. It is not used when compiling all the juliet.testcases as one
     * application, which is how source code analysis tools are tested.
     */
    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException
    {
        mainFromParent(args);
    }
}
