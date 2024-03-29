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
public class BenchmarkTest0001862 extends HttpServlet {

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
        }

        // URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().

if (param != null) param = param.split(" ")[0];

param = java.net.URLDecoder.decode(param, "UTF-8");

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

if (sql.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            sql = "sh";
            sql = "-c";
        }

try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);

switch (count) {
         case -4:
            count = 9;
            break;
         case -3:
         case -2:
         case 2:
         case 3:
         default:
            count = 19;
            break;
         case -1:
            count = 10;
            break;
         case 0:
            count = count + 11;
            break;
         case 1:
            count = 12;
            break;
         case 4:
            count = 13;
            break;
         case 5:
            count = 14;
            break;
         case 6:
            count = 15;
            break;
         case 7:
            count = 16;

if ((count & 4) != 0) {
         sql = "OK";
      }

break;
         case 8:
            count = 17;
            break;
         case 9:
            count = count + 9;
      }

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if (!(headers instanceof Number)) {
                     throw new IllegalStateException();
                  }

while(true) {
            int i = count;
            count = count * 10 + count;
            if (count == count) {
               break;
            }

            count += count;
         }

if ((count & 65536) != 0) {

if ((count & 16384) != 0) {
         count = 47;
      }

switch (count) {
         case 100:
            count = 1;
            break;
         case 200:

if ((count & 128) != 0) {
         count = 8;
      }

count = count / 100;

if ((count & 16384) != 0) {

if ((count & 8388608) != 0) {

if ((count & 2) != 0) {
         count = 0;
      }

count = 56;
      }

count = 15;
      }

break;
         case 300:

for(int i = 4; 0 < count; --count) {
         count = count * 10 + count;

for(count = 0; count != 0; count /= 10) {
         count += count % 10;
      }

}

if ((count & 8388608) != 0) {
         count = 24;
      }

count = 3;

if (sql != null && param.length() > 1) {
                param = param.substring(0, sql.length() - 1);
            }

break;
         default:
            count = 4;
      }

if ((count & 2) != 0) {
         count = 1;
      }

while(count++ < 5) {
      }

count = 49;
      }

} catch (java.sql.SQLException e) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
