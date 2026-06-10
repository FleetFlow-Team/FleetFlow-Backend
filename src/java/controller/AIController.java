package controller;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import service.GeminiService;

@WebServlet("/api/v1/ai/*")
public class AIController extends HttpServlet {

    private final GeminiService geminiService =
            new GeminiService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo();

        try {

            if ("/test".equals(pathInfo)) {

                String result =
                        geminiService.askGemini(
                                "Xin chào Gemini");

                out.print(result);

            } else {

                response.setStatus(404);

                out.print(
                        "{\"error\":\"Endpoint không tồn tại\"}");
            }

        } catch (Exception e) {

            response.setStatus(500);

            out.print(
                    "{\"error\":\""
                    + e.getMessage()
                    + "\"}");
        }
    }
}