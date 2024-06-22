/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE113_HTTP_Response_Splitting__getCookies_Servlet_addHeaderServlet_41.java
Label Definition File: CWE113_HTTP_Response_Splitting.label.xml
Template File: sources-sinks-41.tmpl.java
*/
/*
 * @description
 * CWE: 113 HTTP Response Splitting
 * BadSource: getCookies_Servlet Read data from the first cookie using getCookies()
 * GoodSource: A hardcoded string
 * Sinks: addHeaderServlet
 *    GoodSink: URLEncode input
 *    BadSink : querystring to addHeader()
 * Flow Variant: 41 Data flow: data passed as an argument from one method to another in the same class
 *
 * */

package juliet.testcases;
import juliet.support.*;

import javax.servlet.http.*;


import java.net.URLEncoder;

public class CWE113_HTTP_Response_Splitting__getCookies_Servlet_addHeaderServlet_41_bad extends AbstractTestCaseServlet
{
    private void badSink(String data , HttpServletRequest request, HttpServletResponse response) throws Throwable
    {

        /* POTENTIAL FLAW: Input from file not verified */
        if (data != null)
        {
            response.addHeader("Location", "/author.jsp?lang=" + data);
        }

    }

    public void bad(HttpServletRequest request, HttpServletResponse response) throws Throwable
    {
        String data;

        data = ""; /* initialize data in case there are no cookies */

        /* Read data from cookies */
        {
            Cookie cookieSources[] = request.getCookies();
            if (cookieSources != null)
            {
                /* POTENTIAL FLAW: Read data from the first cookie value */
                data = cookieSources[0].getValue();
            }
        }

        badSink(data , request, response );
    }

    public void good(HttpServletRequest request, HttpServletResponse response) throws Throwable
    { return;}

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
