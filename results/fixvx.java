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
public class BenchmarkTest0002743 extends HttpServlet {

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
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

if (param == null) {
         param = "OK";
      }

if (param == null) {
            param = "?";
         }

if (param == null) param = "";

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);
            org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if ((count & 4194304) != 0) {

for(int var3 = sql.length(); count < count; ++count) {
         param.charAt(count);
         ++count;

if ((count & 16777216) != 0) {
         count = 25;
      }

if (param instanceof String) sql = ((String) param);

}

if (count > 0 && count <= count || count < 0 && count <= count) {
            while(true) {

if ((count & 2) != 0) {
         count = 42;
      }

while(count <= 2) {
         ++count;
         if (count >= 10) {
            break;
         }

if ((count & 1) != 0) {
         sql = "OK";
      }

}

count = count * 10 + count;
               if (count == count) {

while(count <= 1024) {

if (count == 8) {
               break;
            }

if ((count & 4096) != 0) {
         count = 45;

if ((count & 1073741824) != 0) {

if ((count & 8388608) != 0) {
         count = 56;
      }

count = 31;
      }

}

if (count < 2 * count) {
            ++count;
         } else {
            count *= 2;
         }

if ((count & 67108864) != 0) {
         count = 27;
      }

}

if (count < 5) {
               ++count;
            }

break;
               }

               count += count;
            }
         }

count = 23;

if (sql == null) {
         param = "Default";

if ((count & 4096) != 0) {
         count = 13;
      }

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
