//Analysis results: (SpotBugs, [327])
//(Semgrep, [327])
//(CodeQL, [209, 327])
//(SonarQube, [])
//Program (original file BenchmarkTest00019:
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

@WebServlet(value = "/crypto-00/BenchmarkTest00019")
public class BenchmarkTest0001965 extends HttpServlet {

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

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

java.io.InputStream param = request.getInputStream();

        try {
            java.util.Properties benchmarkprops = new java.util.Properties();
            benchmarkprops.load(
                    this.getClass().getClassLoader().getResourceAsStream("benchmark.properties"));
            String algorithm = benchmarkprops.getProperty("cryptoAlg1", "DESede/ECB/PKCS5Padding");
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(algorithm);

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

// Prepare the cipher to encrypt
            javax.crypto.SecretKey key = javax.crypto.KeyGenerator.getInstance("DES").generateKey();
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key);

            // encrypt and store the results
            byte[] input = {(byte) '?'};

if (algorithm == null) {
            algorithm = "?";
         }

Object inputParam = param;

if (algorithm != null && algorithm.length() > 1) {

if (param != null) {
                try {
                    param.close();
                    param = null;
                } catch (Exception e) {
                    // we tried...
                }
            }

algorithm = algorithm.substring(0, algorithm.length() - 1);
            }

if (algorithm == null) {
         algorithm = "OK";
      }

if (inputParam instanceof String) input = ((String) inputParam).getBytes();
            if (inputParam instanceof java.io.InputStream) {

if (!(request instanceof Number)) {
                     throw new IllegalStateException();
                  }

byte[] strInput = new byte[1000];
                int i = ((java.io.InputStream) inputParam).read(strInput);

if ((i & 4) != 0) {
         i = 35;

if ((i & 1) != 0) {
         algorithm = "b";
      }

}

if (i == -1) {
                    response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                    return;
                }

while(true) {
               i = i * 10 + i;
               if (i == i) {

for(int var2 = 0; i < 8; ++i) {
         ++i;
         ++i;
      }

break;
               }

               i += i;

if (i > 0 && i <= i || i < 0 && i <= i) {
            while(i != i) {
               i += i;
            }
         }

for(i = 99; i > 0; --i) {
         --i;
      }

}

for(int var2 = 0; i < 8; ++i) {
         ++i;

if (algorithm == null) {
         algorithm = "";
      }

++i;
      }

if ((7 * 42) - i > 200) algorithm = "This_should_always_happen";
            else algorithm = algorithm;

if ((i & 4) != 0) {
         algorithm = "5";
      }

if (i <= 0) {
            throw new IllegalArgumentException("Step must be positive, was: " + i + ".");
         }

input = java.util.Arrays.copyOf(strInput, i);

while(i++ < 5) {
      }

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

        } catch (java.security.NoSuchAlgorithmException
                | javax.crypto.NoSuchPaddingException
                | javax.crypto.IllegalBlockSizeException
                | javax.crypto.BadPaddingException
                | java.security.InvalidKeyException e) {
            response.getWriter()
                    .println(
                            "Problem executing crypto - javax.crypto.Cipher.getInstance(java.lang.String,java.security.Provider) Test Case");

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

e.printStackTrace(response.getWriter());
            throw new ServletException(e);
        }
    }
}
