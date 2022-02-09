import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.File;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import org.eclipse.jetty.util.ajax.JSON;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 Skeleton of a ContinuousIntegrationServer which acts as webhook
 See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler
{
    /**
     * Handler for the path "/". This is the main handler for the CI-Server.
     */
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException, ServletException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Accept", "application/vnd.github.v3+json");
        JSONObject test = new JSONObject();
        test.put("state", "success");
        response.getWriter().println(test.toString());
        baseRequest.setHandled(true);

        JSONObject json = getJSON(request);
        if (request.getMethod() == "POST") {
            String event = request.getHeader("X-GitHub-Event");
            if (event.compareTo("push") == 0) {
                System.out.println("branch below:");
                String branch = json.get("ref").toString();

                cloneRepo(branch);
                // how to call gradle and print output
                String compileOutput = processCall("./CI-Server/gradlew compileJava");
                System.out.println(compileOutput);
                String testOutput = processCall("./CI-Server/gradlew test");
                System.out.println(testOutput);
            }
        }
        
    }

    /**
     * Clones the branch specified in the git repo 
     * @param branch
     * @throws IOException throws IOException
     */
    public void cloneRepo(String branch) throws IOException {
        if (new File("CI-Server").exists()) {
            processCall("rm -rf CI-Server");
        }
        String[] branchPath = branch.split("/");
        String command = "git clone -b " + branchPath[branchPath.length - 1] + " https://github.com/joakimAbdinurIusuf/CI-Server.git";
        processCall(command);
    }

    /**
     * @param command String representing a commandline command
     * Runs the command given by the argument
     * @return Returns a String containing what the command outputted.
     * @throws IOException Throws IOException if reader fails.
     */
    public String processCall(String command) throws IOException {
        String line = "";
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Main function. Initializes the server on port 8080.
     * @param args Takes no arguments
     * @throws Exception throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer());
        server.start();
        server.join();
    }

    /**
     * @param request A HTTP request with a body whose content is in JSON format.
     * Creates a JSON Object out of the payload of the request.
     * @return A JSONObject that is the payload of the request.
     * @throws IOException throws IOException if reader fails.
     */
    public JSONObject getJSON(HttpServletRequest request) throws IOException {
        InputStream inputStream = request.getInputStream();
        BufferedReader bufReader = new BufferedReader(new InputStreamReader(inputStream));
        String s = bufReader.readLine();
        JSONParser parser = new JSONParser();

        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj = (JSONObject) parser.parse(s.toString());
        } catch (ParseException e) {
            System.out.println("Couldn't parse JSON object");
            e.printStackTrace();
        }

        return jsonObj;



    }
}