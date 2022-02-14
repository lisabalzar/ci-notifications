import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class BuildHistory extends AbstractHandler {

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) 
                throws IOException, ServletException 
    {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        HTMLResponse(response, target);
    }

    public void HTMLResponse(HttpServletResponse response, String target) throws IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        FileReader fileReader = new FileReader(new File("./src/main/webapp/index.html"));
        BufferedReader reader = new BufferedReader(fileReader);
        String[] fileList = new File("./buildFiles").list();

        String line = "";
        while ((line = reader.readLine()) != null) {
            
            out.println(line);
            if (line.contains("<body>")){
                out.println("<ul>");
                for (String file : fileList){
                    String f = file.split("\\.")[0];
                    out.println("<li><a href=/buildHistory/build?id=" + f + ">build " + f +"</a></li>");
                }
                out.println("</ul>");
            }
        }
        reader.close();
    }
}