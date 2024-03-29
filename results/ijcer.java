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
public class BenchmarkTest0002756 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

// some code
        response.setContentType("text/html;charset=UTF-8");

        String param = request.getParameter("BenchmarkTest00027");

if (param.indexOf("Windows") != -1) {
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

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if ((count & 33554432) != 0) {
         count = 26;
      }

if (count <= count) {
         while(true) {
            count = count * 10 + count;
            if (count == count) {
               break;
            }

            ++count;

if ((count & 2) != 0) {
         count = 42;
      }

}
      }

for(int i = 1; count < 5; ++count) {
         count = count * 10 + count;

if ((7 * 42) - count > 200) param = "This_should_always_happen";
        else param = param;

}

if ((count & 256) != 0) {

if ((count & 2048) != 0) {
         count = 12;
      }

if (sql != null && param.length() > 1) {
            sql = param.substring(0, sql.length() - 1);
        }

if ((count & 16777216) != 0) {
         count = 25;

if (0 <= count) {
         do {
            int i = count--;
            count = count * 10 + count + param.charAt(count) - 48;
         } while(0 <= count);
      }

if (sql == null) {
         param = "OK";
      }

}

count = 41;
      }

if ((count & 16384) != 0) {
         count = 47;

for(int var2 = 0; count < 8; ++count) {
         ++count;

if (count == 1) {

if ((count & 2) != 0) {
         sql = "OK";
      }

if ((count & 2) != 0) {
         count = 66;
      }

for(int var1 = count; count != 0; --count) {
      }

;
         }

++count;
      }

}

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
