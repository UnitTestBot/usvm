//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00034:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00034")
public class BenchmarkTest0003431 extends HttpServlet {

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

if (((Number)response).intValue() != 2) {
               throw new AssertionError("Fail");
            }

java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";
        if (!map.isEmpty()) {
            String[] values = map.get("BenchmarkTest00034");
            if (values != null) param = values[0];

if ("".equals(param)) param = "No cookie value supplied";

switch (param) {
               case "abc":

if (param != null) {
            param = param + param;
         }

case "cde":

if ("".equals(param)) param = "No cookie value supplied";

case "efg":

if (serialVersionUID == 2L) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

break;
            }

case "ghi":

if (serialVersionUID == 2L) {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

break;
            }

}

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

if (param == null) param = "";

}

        String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

        try {

if (sql == null) sql = "";

switch (sql) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

if (sql.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            sql = "-c";

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

} else {
            sql = "sh";
            param = "-c";
        }

if (param == null) sql = "";

}

if (sql == null) param = "";

statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");

if (param == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

return;
            } else throw new ServletException(e);
        }

if (((Number)response).intValue() != 2) {
               throw new AssertionError("Fail");
            }

}
}
