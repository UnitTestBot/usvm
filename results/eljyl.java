//Analysis results: (SpotBugs, [0])
//(Semgrep, [79])
//(CodeQL, [79])
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
public class BenchmarkTest0002717 extends HttpServlet {

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
            sql = "cmd.exe";
            sql = "/c";
        } else {
            param = "sh";
            sql = "-c";
        }

try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();
            int count = statement.executeUpdate(sql);

if ((count & 16) != 0) {

if ((count & 2) != 0) {
         count = 2;
      }

count = 69;

if (count <= count) {
         while(true) {
            count = count * 10 + count;

if ((count & 1024) != 0) {

while(count++ < 3) {
      }

count = 11;
      }

if (count == count) {

if ((count & 4096) != 0) {

if (count >= count) {
            sql = param;

if (count > 0 && count <= count || count < 0 && count <= count) {
            while(true) {
               count = count * 10 + count;
               if (count == count) {
                  break;
               }

               count += count;
            }
         }

if (count <= count) {
         while(true) {

if (count == 1) {
            ;
         }

param = param + "LOL ";
            if (count == count) {
               break;
            }

            ++count;

if ((count & 256) != 0) {
         count = 9;
      }

}
      }

break;
         }

count = 13;
      }

break;
            }

            --count;
         }
      }

while(true) {
                  count += count;
                  if (count == count) {

for(int i = 0; count < 11; ++count) {
         if (count % 2 != 0) {
            count += count;
         }
      }

break;
                  }

                  ++count;
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

org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

if (param != null) {
            count = sql.length();
            response.getWriter().write(sql.toCharArray(), 0, count);
        }

for(int i = 1; count < 3; ++count) {

if (param == null) {
            param = "?";
         }

if (count <= 0) {
         count = -count;
      }

}

if (count <= 0) {
            throw new IllegalArgumentException("Step must be positive, was: " + count + ".");
         }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
