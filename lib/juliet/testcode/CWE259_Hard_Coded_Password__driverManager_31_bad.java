/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE259_Hard_Coded_Password__driverManager_31.java
Label Definition File: CWE259_Hard_Coded_Password.label.xml
Template File: sources-sink-31.tmpl.java
*/
/*
 * @description
 * CWE: 259 Hard Coded Password
 * BadSource: hardcodedPassword Set data to a hardcoded string
 * GoodSource: Read data from the console using readLine()
 * Sinks: driverManager
 *    BadSink : data used as password in database connection
 * Flow Variant: 31 Data flow: make a copy of data within the same method
 *
 * */

package juliet.testcases;

import juliet.support.*;

import java.util.logging.Level;
import java.io.*;

import java.sql.*;

public class CWE259_Hard_Coded_Password__driverManager_31_bad extends AbstractTestCase
{
    /* uses badsource and badsink */
    public void bad() throws Throwable
    {
        String dataCopy;
        {
            String data;

            /* FLAW: Set data to a hardcoded string */
            data = "7e5tc4s3";

            dataCopy = data;
        }
        {
            String data = dataCopy;

            Connection connection = null;
            PreparedStatement preparedStatement = null;
            ResultSet resultSet = null;

            if (data != null)
            {
                try
                {
                    /* POTENTIAL FLAW: data used as password in database connection */
                    connection = DriverManager.getConnection("data-url", "root", data);
                    preparedStatement = connection.prepareStatement("select * from test_table");
                    resultSet = preparedStatement.executeQuery();
                }
                catch (SQLException exceptSql)
                {
                    IO.logger.log(Level.WARNING, "Error with database connection", exceptSql);
                }
                finally
                {
                    try
                    {
                        if (resultSet != null)
                        {
                            resultSet.close();
                        }
                    }
                    catch (SQLException exceptSql)
                    {
                        IO.logger.log(Level.WARNING, "Error closing ResultSet", exceptSql);
                    }

                    try
                    {
                        if (preparedStatement != null)
                        {
                            preparedStatement.close();
                        }
                    }
                    catch (SQLException exceptSql)
                    {
                        IO.logger.log(Level.WARNING, "Error closing PreparedStatement", exceptSql);
                    }

                    try
                    {
                        if (connection != null)
                        {
                            connection.close();
                        }
                    }
                    catch (SQLException exceptSql)
                    {
                        IO.logger.log(Level.WARNING, "Error closing Connection", exceptSql);
                    }
                }
            }

        }
    }

    public void good() throws Throwable
    { return;}

    /* Below is the main(). It is only used when building this testcase on
     * its own for testing or for building a binary to use in testing binary
     * analysis tools. It is not used when compiling all the juliet.testcases as one
     * application, which is how source code analysis tools are tested.
     */
    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException
    {
        mainFromParent(args);
    }
}
