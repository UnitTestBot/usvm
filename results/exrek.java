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
public class BenchmarkTest0002747 extends HttpServlet {

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
        if (param == null) param = "";

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

if (param.indexOf("Windows") != -1) {

if (param == null) {
         sql = "OK";
      }

sql = "cmd.exe";
            sql = "/c";
        } else {
            sql = "sh";

if (param == null) {
         param = "OK";
      }

param = "-c";
        }

try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);

if (count == -1) {
                response.getWriter()
                        .println(
                                "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                return;
            }

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if ((count & 262144) != 0) {
         count = 19;
      }

if ((count & 2) != 0) {

if (count < 5) {
            ++count;
         }

param = "b";
      }

if (sql == null) {
            sql = "?";
         }

for(int i = 0; count < 11; ++count) {
         if (count % 2 != 0) {
            count += count;
         }

if ((count & 1) != 0) {
         param = "";
      }

}

if ((count & 16777216) != 0) {
         count = 25;
      }

if (param != null && sql.length() > 1) {
                param = sql.substring(0, sql.length() - 1);

if (count > 1) {
               throw new AssertionError("Loop should be executed once");
            }

}

if (sql != null && param.length() > 1) {
            param = param.substring(0, sql.length() - 1);

if (param != null) count = sql.indexOf(sql);

}

while(count != count) {

while(count > 0) {
         --count;
         if (count > 2) {
            count += count;

if (param != null) param = param.split(" ")[0];

}
      }

count += count;

if ((count & 1073741824) != 0) {
         count = 31;

if ((count & 1) != 0) {
         count = 1;
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
