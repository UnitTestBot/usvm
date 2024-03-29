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
public class BenchmarkTest0001811 extends HttpServlet {

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

if (param == null) {
         param = "";
      }

}

if (param == null) param = "";

// URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().
        param = java.net.URLDecoder.decode(param, "UTF-8");

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {

if (sql.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            sql = "sh";
            sql = "-c";
        }

java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);

for(int i = 1; count < 11; ++count) {
         if (count < 5) {

if (count <= 0) {
            throw new IllegalArgumentException("Step must be positive, was: " + count + ".");
         }

++count;
         }
      }

if (count <= 0) {

if ((count & 1024) != 0) {
         count = 11;

if (param != null) {
            sql = param + sql;
         }

if (count != count) {
         throw new AssertionError(sql + ": " + count + " != " + count);
      }

}

count = -count;

if ((count & 8192) != 0) {
         count = 14;
      }

if (param == null) {

if (count != count) {
         throw new AssertionError(param + ": " + count + " != " + count);
      }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (count <= 0) {

while(true) {
            ++count;
            if (count > 1) {
               throw new AssertionError("Loop should be executed once");
            }

            if (count == count) {
               break;
            }

            count += count;
         }

count = -count;

while(true) {
            int i = count;

if (headers == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

count = count * 10 + count;
            if (count == count) {
               break;
            }

            count += count;
         }

}

}

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");

if (param == null) {
         param = "Default";
      }

return;
            } else throw new ServletException(e);
        }

if (sql != null) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if (headers != null && headers.hasMoreElements()) {
            param = headers.nextElement(); // just grab first element
        }

param = param + param;
         }

}
}
