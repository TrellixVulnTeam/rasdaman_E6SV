/*
 * This file is part of PetaScope.
 *
 * PetaScope is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PetaScope is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with PetaScope. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information please see <http://www.PetaScope.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 *
 * Copyright 2009 Jacobs University Bremen, Peter Baumann.
 */

package wcps.server.test;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.io.FileUtils;

/** Runs the available XML tests against a deployed version of Petascope,
 * available at some URL.
 *
 * Assumes that a tests succeeds if the server does not throw or display
 * an error.
 *
 * @author Andrei Aiordachioaie
 */
public class XmlTest {
    // Put new test cases in this folder
//    public final String PetascopeURL = "http://localhost:8080/PetaScope/WCPService";
    public final String PetascopeURL = "http://kahlua.eecs.jacobs-university.de:8080/petascope/wcps/";

    String folder = "testing/testcases-wcps/";
    // How many tests we have to run
    int numTests = 0;
    // tests
    File[] tests;
    // queries
    String[] queries;
    // success code
    boolean[] ok;
    // message for tests
    String[] errors;
    // How many successes?
    int passCount = 0;

    public void printResults()
    {
        System.out.println("\n \nRESULTS \n");

        for (int i = 0; i < numTests; i++) {
            String tname = tests[i].getName();
            tname = tname.substring(0, tname.length() - 9);
            if (ok[i] == true)
            {
                System.out.println("*** Test '" + tname + "' ok");
//                System.out.println("\t" + queries[i]);
            } else {
                System.out.println("*** Test '" + tname + "' FAILED");
//                System.out.println("\t" + queries[i]);
                System.out.println("\t ERROR: " + errors[i]);
            }
        }
        System.out.println("\n\nRESULTS\n");
        System.out.println("Tests succeeded: " + String.valueOf(passCount));
        System.out.println("Tests failed: " + String.valueOf(numTests - passCount));
    }

    public XmlTest() {
        // Find out how many tests we have to run
        File dir = new File(folder);
        System.out.println("Looking for tests in " + dir.getAbsolutePath() + "\n");
        XmlFileFilter filter = new XmlFileFilter();
        tests = dir.listFiles(filter);
        numTests = tests.length;
//        numTests = 1;
        ok = new boolean[numTests];
        errors = new String[numTests];
        queries = new String[numTests];
    }

    /* Accept all files with extension xml. */
    private class XmlFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            if (name.endsWith("xml")) {
                return true;
            }
            return false;
        }
    }

    public void runAllTests() {
        String query = "";
        String tname = "";
        for (int i = 0; i < numTests; i++)
        {
            ok[i] = false;
            tname = tests[i].getName();
            tname = tname.substring(0, tname.length() - 9);
            System.out.println("Running test '" + tname + "'...");
            try {
                query = FileUtils.readFileToString(tests[i]);
                queries[i] = query;
            } catch (IOException e) {
                errors[i] = "Could not read file " + tests[i].getName();
                continue;
            }
            try {
                String err = runOneTest(query);
                if (err == null)
                    ok[i] = true;
                else
                    errors[i] = err;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (ok[i] == true)
                passCount ++;
        }
    }

    /** Send an XML request to the WCPS server. Hopefully it will succeed.
     * Returns a message on error or null otherwise.
     **/
    public String runOneTest(String xml) throws MalformedURLException, IOException {
        // connect to the servlet
        URL servlet = new URL(PetascopeURL);
        HttpURLConnection conn = (HttpURLConnection) servlet.openConnection();

        // inform the connection that we will send output and accept input
        conn.setDoInput(true);
        conn.setDoOutput(true);

        // Don't use a cached version of URL connection.
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);

        // Send POST request
        conn.setRequestMethod("POST");

        // Specify the content type that we will send binary data
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

//        String data = "xml=" + URLEncoder.encode("xml", "UTF-16");
        String data = "xml=" + xml;
        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(data);
        out.flush();
        out.close();


        BufferedReader cgiOutput = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        String line1 = cgiOutput.readLine();
        String line2 = cgiOutput.readLine();
        String line3 = cgiOutput.readLine();

        if (line1 != null && line2 != null && line3 != null &&
                line2.equals("<h1>An error has occured</h1>"))
        {
            System.out.println("Error executing query: ");
            String error = line3.substring(10, line3.length() - 4);
            System.out.println("\t" + error);
            return error;
        }
        else
            return null;

    }

    public static void main(String args[])
    {
      XmlTest tester = new XmlTest();
      tester.runAllTests();
      tester.printResults();
    }

}
