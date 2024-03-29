//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00018:
/**
 * OWASP Benchmark v1.2
 *
 * <p>This file is part of the Open Web Application Security Project (OWASP) Benchmark Project. For
 * details, please see <a
 * href="https://owasp.org/www-project-benchmark/">https://owasp.org/www-project-benchmark/</a>.
 *
 * <p>The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, version 2.
 *
 * <p>The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * @author Dave Wichers
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/sqli-00/BenchmarkTest00018")
public class BenchmarkTest0001864 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

if (response == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (((Number)serialVersionUID).intValue() != 2) {
               throw new AssertionError("Fail");
            }

doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // some code
        response.setContentType("text/html;charset=UTF-8");

        String param = "";
        java.util.Enumeration<String> headers = request.getHeaders("BenchmarkTest00018");

        if (headers != null && headers.hasMoreElements()) {
            param = headers.nextElement(); // just grab first element
        }

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";

if (param == null) {
         param = "Default";
      }

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

}

// URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().
        param = java.net.URLDecoder.decode(param, "UTF-8");

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);
            org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if ((count & '耀') != 0) {

if ((count & 65536) != 0) {
         count = 17;

if ((count & 536870912) != 0) {
         count = 62;
      }

}

if ((count & 4194304) != 0) {
         count = 55;
      }

count = 48;

if ((count & 2) != 0) {
         count = 34;
      }

}

if ((count & 8) != 0) {
         param = "4";
      }

if (count < 2 * count) {
            ++count;
         } else {
            count *= 2;

if ((count & 8192) != 0) {
         count = 46;

for(int var3 = sql.length(); count < count; ++count) {
         sql.charAt(count);
         ++count;
      }

for(int var3 = param.length(); count < count; ++count) {
         param.charAt(count);
         ++count;
      }

for(int i = 1; count < 5; ++count) {
         count = count * 10 + count;
      }

}

}

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (!(param instanceof String)) {
            throw new IllegalStateException(("Unexpected value of type Char: " + headers).toString());
         }

response.getWriter().println("Error processing request.");

if (param == null) {
            param = "?";
         }

return;
            } else throw new ServletException(e);
        }

if (request == null) {

if (sql == null) {
         sql = "";
      }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

}
}
