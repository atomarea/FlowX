package net.atomarea.flowx.services;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CheckAppVersionService extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public CheckAppVersionService() {
        super();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request,response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        //send a JSON response with the app Version and file URI
        JsonObject myObj = new JsonObject();
        myObj.addProperty("success", false);
        myObj.addProperty("latestVersionCode", 2);
        myObj.addProperty("latestVersion", "1.0.0");
        myObj.addProperty("filesize", "");
        myObj.addProperty("appURI", "");
        out.println(myObj.toString());
        out.close();

    }

}
