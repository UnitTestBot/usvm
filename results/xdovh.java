//Analysis results: (SpotBugs, [0])
//(Semgrep, [89])
//(CodeQL, [89])
//(SonarQube, [89])
//Program (original file BenchmarkTest00037:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00037")
public class BenchmarkTest0003736 extends HttpServlet {

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

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

response.setContentType("text/html;charset=UTF-8");

        String param = "";
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (param == null) {
            param = "?";
         }

if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

}

if (!flag) {
         throw new AssertionError("Fail");
      }

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

if (value.equals("BenchmarkTest00037")) {

if (param == null) name = "";

param = name;

if (request.getHeader("Referer") != null) {
            value = request.getHeader("Referer");
        }

flag = false;
                    }

if (((Object[])values)[0] == null) {
                                       throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
                                    }

if (value.indexOf("Windows") != -1) {
            value = "cmd.exe";
            param = "/c";
        } else {
            name = "sh";
            name = "-c";
        }

if (value == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

}

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

if (param != null && name.length() > 1) {
                param = param.substring(0, name.length() - 1);

if (param == null) {
         throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
      } else {
         String var10000 = (String)name;
      }

}

}
if (values != null) name = values[0];

}

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();
            java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

statement.setString(1, "foo");
            statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (flag && flag) {
         param = param;
         param = "def";
         flag = false;
         sql = sql;
         sql = sql;
      } else {
         sql = param;
         param = "def";
         flag = false;
         param = sql;
         sql = param;
      }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");

if (param == null) {
         param = "Default";
      }

return;
            } else throw new ServletException(e);
        }

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

}
}
