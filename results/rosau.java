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
public class BenchmarkTest0001835 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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

if (((Number)response).intValue() != 2) {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

throw new AssertionError("Fail");
            }

}

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

// URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().
        param = java.net.URLDecoder.decode(param, "UTF-8");

if (param == null) {
         param = "Default";
      }

String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);
            org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if ((count & 512) != 0) {
         count = 10;
      }

for(int var3 = param.length(); count < count; ++count) {
         param.charAt(count);
         ++count;
      }

if ((count & 67108864) != 0) {

while(count < 10) {
         try {
            ++count;

if ((count & 536870912) != 0) {
         count = 62;
      }

} finally {
            ++count;
         }

if ((count & 134217728) != 0) {
         count = 60;
      }

}

count = 27;

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if ((count & 1) != 0) {
         sql = null;
      }

if ((count & 8) != 0) {
         sql = "4";
      }

for(int var1 = 1; count < 5; ++count) {
         int i = count;
         count = count * 10 + count;
      }

if ((count & 65536) != 0) {
         count = 49;
      }

}

while(true) {
               count = count * 10 + count;

if ((count & 8192) != 0) {
         count = 46;
      }

if (count == count) {
                  break;
               }

if (count <= count) {
         while(true) {
            param = param + "LOL ";
            if (count == count) {
               break;
            }

            ++count;
         }
      }

count += count;
            }

} catch (java.sql.SQLException e) {

if (request.getHeader("Referer") != null) {
            sql = request.getHeader("Referer");
        }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
