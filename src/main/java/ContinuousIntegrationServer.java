import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;

/**
 Skeleton of a ContinuousIntegrationServer which acts as webhook
 See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler
{
    /**
     * Handler for the path "/". This is the main handler for the CI-Server.
     */
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        if (request.getMethod() == "POST") {
            String event = request.getHeader("X-GitHub-Event");
            if (event.compareTo("push") == 0) {
                JSONObject json = getJSON(request);
                String branch = json.get("ref").toString();
                JSONObject repo = (JSONObject) json.get("repository");
                String url = repo.get("clone_url").toString();
                try {
                    cloneRepo(branch, url);
                    processCall("git pull", "./repo");
                    setStatus(json);

                    String compileOutput = processCall("gradle compileJava", "./repo");
                    String testOutput = processCall("gradle test", "./repo");

                    createBuildFile(json, compileOutput, testOutput);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            response.getWriter().println("Welcome to the CI-Server! Go to /buildHistory to se the history of previous builds");
        }

    }


    /**
     * Sets commit-status on github
     *
     * @param json JSON object from request payload
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    public void setStatus(JSONObject json) throws IOException, InterruptedException  {
        boolean compile = compileRepo();
        boolean test = testRepo();

        JSONObject repo = (JSONObject) json.get("repository");
        JSONObject owner = (JSONObject) repo.get("owner");
        String repoName = repo.get("name").toString();
        String ownerName = owner.get("name").toString();
        JSONObject headCommit = (JSONObject) json.get("head_commit");
        String sha = headCommit.get("id").toString();

        FileReader auth = new FileReader(new File("auth"));
        FileReader user = new FileReader(new File("username"));
        BufferedReader authReader = new BufferedReader(auth);
        BufferedReader userReader = new BufferedReader(user);

        String token = authReader.readLine();
        String username = userReader.readLine();

        String status = "";
        if (compile && test) {
            status = "success";
        } else if (compile && !test) {
            status = "error";
        } else {
            status = "failure";
        }
        String command = "curl -u " + username + ":" + token + " -X POST -H \"Accept: application/vnd.github.v3+json\" https://api.github.com/repos/" + ownerName + "/" + repoName + "/statuses/" + sha + " -d {\"state\":\"" + status + "\"}";
        processCall(command, "");
    }

    /**
     * Clones the branch specified in the git repo
     * @param branch name of the branch to be cloned
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    public void cloneRepo(String branch, String repo) throws IOException, InterruptedException  {
        if (new File("repo").exists()) {
            processCall("rm -rf repo", "");
        }
        String[] branchPath = branch.split("/");
        String command = "git clone -b " + branchPath[branchPath.length - 1] +  " " + repo + " ./repo";
        processCall(command, "");
    }

    /**
     * Check that cloned repo compiles successfully.
     * @return True if the cloned repo compiles successfully, false otherwise.
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    public boolean compileRepo() throws IOException, InterruptedException  {
        String compileOutput = processCall("gradle compileJava", "./repo");

        if (compileOutput.contains("BUILD SUCCESSFUL")) {
            return true;
        }
        return false;
    }

    /**
     * Check that all the tests in the repo pass.
     * @return True if all the tests pass, false otherwise.
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    public boolean testRepo() throws IOException, InterruptedException  {
        String testOutput = processCall("gradle test", "./repo");

        if (testOutput.contains("FAILED")) {
            return false;
        }
        return true;
    }

    /**
     * @param command String representing a commandline command
     * Runs the command given by the argument
     * @return Returns a String containing what the command outputted.
     * @throws IOException Throws IOException if reader fails.
     * @throws InterruptedException throws InterruptedException
     */
    public String processCall(String command, String directory) throws IOException, InterruptedException {
        String line = "";
        Process process;
        if (directory.equals("")) {
            process = Runtime.getRuntime().exec(command);
        } else {
            process = Runtime.getRuntime().exec(command, null, new File(directory));
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        process.waitFor();
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

        ContextHandler chMain = new ContextHandler("/");
        chMain.setHandler(new ContinuousIntegrationServer());

        ContextHandler chBuild = new ContextHandler("/buildHistory");
        chBuild.setHandler(new BuildHistory());

        ContextHandler chbuildId = new ContextHandler("/buildHistory/build");
        chbuildId.setHandler(new Build());

        ContextHandlerCollection chc = new ContextHandlerCollection();
        chc.addHandler(chMain);
        chc.addHandler(chBuild);
        chc.addHandler(chbuildId);

        server.setHandler(chc);

        server.start();
        server.join();
    }

    /**
     * @param request A HTTP request with a body whose content is in JSON format.
     * Creates a JSON Object out of the payload of the request.
     * @return A JSON Object that is the payload of the request.
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

    /**
     * Creates build file. The commit id is the name of the file. The build file contains branch name, time stamp,
     * commit id and url, and output from the compilation and tests.
     * @param json JSON object from request payload
     * @param compileOutput output from the compilation
     * @param testOutput output from tests
     * @throws IOException throws IOException
     */
    public void createBuildFile(JSONObject json, String compileOutput, String testOutput) throws IOException{
        String b = json.get("ref").toString();
        String[] branchPath = b.split("/");
        String branch = branchPath[branchPath.length - 1];

        JSONObject hc = (JSONObject) json.get("head_commit");
        String id = hc.get("id").toString();
        String timestamp = hc.get("timestamp").toString();
        String url = hc.get("url").toString();

        File fileObj = new File("./buildFiles/" + id + ".txt");
        if (fileObj.createNewFile()) {
            System.out.println("Build file created: " + fileObj.getName());
        } else {
            System.out.println("Build file already exists.");
        }
        System.out.println("middle CBF");

        PrintWriter out = new PrintWriter("./buildFiles/" + id + ".txt");
        out.write("<b> Build executed in branch " + branch + "</b>");
        out.println();
        out.write("<br> Timestamp: " + timestamp);
        out.println();
        out.write("<br> Commit id: " + id);
        out.println();
        out.write("<br> Commit url: <a href=" + url + ">" + url +"</a>\n");
        out.println();
        out.write("<br><br> <b>Compile output:<br>" + compileOutput);
        out.println();
        out.write("<br><br> <b>Test output:<br>" + testOutput);
        out.close();




    }
}