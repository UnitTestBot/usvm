//Analysis results: (SpotBugs, [327])
//(Semgrep, [327])
//(CodeQL, [209, 79, 327])
//(SonarQube, [])
//Program (original file BenchmarkTest00035:
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

@WebServlet(value = "/crypto-00/BenchmarkTest00035")
public class BenchmarkTest0003551 extends HttpServlet {

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
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00035")) {
                        param = name;
                        flag = false;
                    }
                }
            }

if (param.indexOf("Windows") != -1) {
            name = "cmd.exe";
            name = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

if (name == null) {
         param = "OK";
      }

}

        try {
            java.util.Properties benchmarkprops = new java.util.Properties();
            benchmarkprops.load(
                    this.getClass().getClassLoader().getResourceAsStream("benchmark.properties"));
            String algorithm = benchmarkprops.getProperty("cryptoAlg1", "DESede/ECB/PKCS5Padding");
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(algorithm);

            // Prepare the cipher to encrypt
            javax.crypto.SecretKey key = javax.crypto.KeyGenerator.getInstance("DES").generateKey();
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key);

            // encrypt and store the results
            byte[] input = {(byte) '?'};
            Object inputParam = param;
            if (inputParam instanceof String) input = ((String) inputParam).getBytes();
            if (inputParam instanceof java.io.InputStream) {
                byte[] strInput = new byte[1000];
                int i = ((java.io.InputStream) inputParam).read(strInput);

if ((i & 1) != 0) {

if (i != i) {

if ((i & 1) != 0) {

if ((i & 2) != 0) {
         i = 0;
      }

param = "Companion";
      }

throw new AssertionError(algorithm + ": " + i + " != " + i);
      }

if (i <= i) {
            while(true) {
               i = i * 10 + i;
               if (i == i) {

while(i > 0) {
         --i;
         if (i > 2) {
            i += i;
         }

while(true) {
            algorithm = algorithm + "LOL ";
            if (i == i) {
               break;
            }

            ++i;
         }

}

break;
               }

if (i != 3) {
         throw new AssertionError();
      }

i += i;
            }

if ((500 / 42) + i > 200) param = param;
        else algorithm = "This should never happen";

}

if ((i & 8) != 0) {
         param = "4";
      }

if ((i & 8) != 0) {
         i = 36;

if ((i & 2) != 0) {
               param = "OK";
            }

}

if (i > 1) {
            throw new AssertionError("Loop should be executed once");
         }

param = "Companion";
      }

if (i == -1) {

if ((i & 32) != 0) {
         i = 6;
      }

if (flag) {

if ((i & 33554432) != 0) {
         i = 58;
      }

throw new IllegalArgumentException("Array contains more than one matching element.");
            }

response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                    return;
                }
                input = java.util.Arrays.copyOf(strInput, i);
            }
            byte[] result = c.doFinal(input);

            java.io.File fileTarget =
                    new java.io.File(
                            new java.io.File(org.owasp.benchmark.helpers.Utils.TESTFILES_DIR),
                            "passwordFile.txt");
            java.io.FileWriter fw =
                    new java.io.FileWriter(fileTarget, true); // the true will append the new data
            fw.write(
                    "secret_value="
                            + org.owasp.esapi.ESAPI.encoder().encodeForBase64(result, true)
                            + "\n");
            fw.close();
            response.getWriter()
                    .println(
                            "Sensitive value: '"
                                    + org.owasp
                                            .esapi
                                            .ESAPI
                                            .encoder()
                                            .encodeForHTML(new String(input))
                                    + "' encrypted and stored<br/>");

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

} catch (java.security.NoSuchAlgorithmException
                | javax.crypto.NoSuchPaddingException
                | javax.crypto.IllegalBlockSizeException
                | javax.crypto.BadPaddingException
                | java.security.InvalidKeyException e) {
            response.getWriter()
                    .println(
                            "Problem executing crypto - javax.crypto.Cipher.getInstance(java.lang.String,java.security.Provider) Test Case");
            e.printStackTrace(response.getWriter());
            throw new ServletException(e);
        }
    }
}
