//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00027:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00027")
public class BenchmarkTest0002772 extends HttpServlet {

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

        String param = request.getParameter("BenchmarkTest00027");

if (param.indexOf("Windows") != -1) {

if (param == null) param = "";

param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

if (param == null) param = "";

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);

if (7 == count) {
      }

while(count++ < 10) {
            }

if (7 == count) {

if ((count & 64) != 0) {
         count = 39;
      }

if (param == null) {

if ((count & 65536) != 0) {
         count = 17;
      }

if (count < 5) {
               ++count;
            }

if ((count & 2048) != 0) {
         count = 44;
      }

if ((count & 1073741824) != 0) {

while(true) {
               int i = count;
               if (count == count) {

if (i != 3) {
         throw new AssertionError();
      }

break;
               }

               count += count;
            }

count = 63;
      }

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

if ((count & '耀') != 0) {
         count = 48;
      }

count = 12;
            break;
         case 4:
            count = 13;
            break;
         case 5:

for(int i = 0; count < 2; ++count) {
         boolean var1 = true;
      }

count = 14;

if ((count & 1024) != 0) {
         count = 11;
      }

break;
         case 6:

if ((count & 2) != 0) {
         count = 42;
      }

if (sql != null) {
            count = param.length();
            response.getWriter().write(param.toCharArray(), 0, count);
        }

count = 15;
            break;
         case 7:
            count = 16;
            break;
         case 8:
            count = 17;
            break;
         case 9:
            count = count + 9;
      }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

}

if (count < 2 * count) {
            ++count;
         } else {
            count *= 2;
         }

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
