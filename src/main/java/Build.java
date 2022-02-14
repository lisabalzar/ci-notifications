import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class Build extends AbstractHandler {

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) 
                throws IOException, ServletException 
    {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        HTMLResponse(request, response);
    }

    public void HTMLResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        String id = request.getParameter("id");
        PrintWriter out = response.getWriter();

        FileReader fileReader = new FileReader(new File("./src/main/webapp/index.html"));
        BufferedReader reader = new BufferedReader(fileReader);

        String line = "";
        while ((line = reader.readLine()) != null) {
            
            if (line.contains("<title>")) {
                reader.readLine();
                out.println("<title>Build Output</title>");
                out.println("<b>Build " + id + " </b>");
            } else {
                out.println(line);
            }
            
            if (line.contains("<body>")) {
                getContentString(id, out);
            }
        }
        reader.close();
    }

    public void getContentString(String id, PrintWriter out) throws IOException {
        FileReader fileReader = new FileReader(new File("./buildFiles/" + id + ".txt"));
        BufferedReader reader = new BufferedReader(fileReader);
        
        String line = "";
        while ((line = reader.readLine()) != null) {
            out.println(line);
        }
        reader.close();

    }

}