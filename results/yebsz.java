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
public class BenchmarkTest0003491 extends HttpServlet {

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

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

response.setContentType("text/html;charset=UTF-8");

        java.util.Map<String, String[]> map = request.getParameterMap();
        String param = "";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (!map.isEmpty()) {

if (param != null && param.length() > 1) {

if (map == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

param = param.substring(0, param.length() - 1);
        }

String[] values = map.get("BenchmarkTest00034");

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

if (values != null) param = values[0];

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

}

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

if (param != null && param.length() > 1) {
                param = param.substring(0, param.length() - 1);
            }

String sql = "SELECT * from USERS where USERNAME='foo' and PASSWORD='" + param + "'";

        try {
            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

if (sql != null && param.length() > 1) {
                param = sql.substring(0, sql.length() - 1);
            }

statement.execute(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

if (param == null) {

if (param != null) {

if (statement == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

sql = param + sql;
         }

sql = "";
      }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {

if (sql != null && param.length() > 1) {
                sql = param.substring(0, sql.length() - 1);
            }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

if (sql == null) {
         sql = "";
      }

if (sql == null) {

if (((Number)map).intValue() != 2) {
               throw new AssertionError("Fail");
            }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

response.getWriter().println("Error processing request.");

if (param == null) sql = "";

return;
            } else throw new ServletException(e);
        }

if (sql != null) sql = param.split(" ")[0];

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

}
}
