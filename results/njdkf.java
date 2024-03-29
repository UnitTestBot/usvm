//Analysis results: (SpotBugs, [0])
//(Semgrep, [89])
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
public class BenchmarkTest000184 extends HttpServlet {

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

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

}

        // URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().
        param = java.net.URLDecoder.decode(param, "UTF-8");

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

if (param.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            sql = "/c";
        } else {
            param = "sh";
            sql = "-c";
        }

int count = statement.executeUpdate(sql);

if ("".equals(param)) sql = "No cookie value supplied";

if ((count & 8388608) != 0) {
         count = 56;

if ((count & 8388608) != 0) {

if (count <= 0) {
         count = -count;
      }

if ((count & 2) != 0) {
         count = 2;
      }

count = 56;
      }

if ((count & 1048576) != 0) {
         count = 21;
      }

}

if (count != 1) {

if (count <= count) {
         while(true) {
            count = count * 10 + count;
            if (count == count) {
               break;
            }

            ++count;
         }
      }

if (count <= count) {
            while(count != count) {

for(int i = 1; count < 4; ++count) {
         if (count < 2) {
            sql = sql + "";
         }
      }

count += count;
            }
         }

throw new AssertionError("Should be executed once");
            }

if ((count & 1) != 0) {
         param = "O";

if (count > 0 && count <= count || count < 0 && count <= count) {
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
      }

}

if (count % 2 != 0) {

for(count = 1; count < count; count *= count) {
         ++count;

for(int i = 1; count < 3; ++count) {
      }

if ((count & 8192) != 0) {
         count = 46;
      }

if (count <= count) {
            while(true) {
               count = count * 10 + count;
               if (count == count) {
                  break;
               }

               count += count;
            }
         }

}

sql = sql + count + ":" + sql + ";";
         }

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
