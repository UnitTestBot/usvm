//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program:
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
public class BenchmarkTest00027174 extends HttpServlet {

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

if (response == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";

if (param == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

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

if ((count & 1) != 0) {
         sql = "b";
      }

for(int i = 1; count < 3; ++count) {
      }

if ((count & 4) != 0) {

if (count != 1) {
               throw new AssertionError("Should be executed once");
            }

count = 1;

if (count % 2 != 0) {
            count += count;
         }

}

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if (count <= count) {
            while(true) {
               count = count * 10 + count;
               if (count == count) {
                  break;
               }

               count += count;
            }
         }

if ((count & 2048) != 0) {

for(int j = 10; 0 < count; --count) {
         }

for(count = 0; count != 0; count /= 10) {
         count += count % 10;
      }

count = 44;
      }

if (count % 2 != 0) {
            sql = param + count + ":" + param + ";";
         }

if (count < 2 * count) {

if (7 == count) {
      }

if ((count & 4) != 0) {
         count = 1;
      }

if ((count & 524288) != 0) {
         count = 20;
      }

++count;
         } else {

if (count == 6) {
         if (count == 6) {
            param = "a";
         } else {
            sql = "b";
         }
      } else {
         param = "c";
      }

count *= 2;
         }

if ((count & 1) != 0) {

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
            break;
         case 8:
            count = 17;
            break;
         case 9:
            count = count + 9;
      }

param = "OK";
            }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
