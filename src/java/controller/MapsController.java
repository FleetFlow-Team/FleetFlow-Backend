package controller;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import service.MapsService;

@WebServlet("/api/v1/maps/*")
public class MapsController extends HttpServlet {

    private final MapsService mapsService = new MapsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo();

        try {
            if ("/distance".equals(pathInfo)) {
                handleDistance(request, response, out);

            } else if ("/geocode".equals(pathInfo)) {
                handleGeocode(request, response, out);

            } else if ("/route".equals(pathInfo)) {
                handleRoute(request, response, out);

            } else {
                response.setStatus(404);
                out.print("{\"error\": \"Endpoint không tồn tại\"}");
            }
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + e.getMessage() + "\"}");
        }

        out.flush();
    }

    /**
     * GET /api/v1/maps/distance
     * Params: pickupLat, pickupLng, dropoffLat, dropoffLng
     */
    private void handleDistance(HttpServletRequest request,
                                HttpServletResponse response,
                                PrintWriter out) throws Exception {
        double pickupLat  = Double.parseDouble(request.getParameter("pickupLat"));
        double pickupLng  = Double.parseDouble(request.getParameter("pickupLng"));
        double dropoffLat = Double.parseDouble(request.getParameter("dropoffLat"));
        double dropoffLng = Double.parseDouble(request.getParameter("dropoffLng"));

        try {
            double distanceKm = mapsService.validateAndGetDistance(
                pickupLat, pickupLng, dropoffLat, dropoffLng
            );
            out.print("{\"valid\": true, \"distanceKm\": " + distanceKm + "}");

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"valid\": false, \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * GET /api/v1/maps/geocode
     * Params: address
     */
    private void handleGeocode(HttpServletRequest request,
                                HttpServletResponse response,
                                PrintWriter out) throws Exception {
        String address = request.getParameter("address");

        if (address == null || address.trim().isEmpty()) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu tham số address\"}");
            return;
        }

        double[] coords = mapsService.geocode(address);
        out.print("{\"lat\": " + coords[0] + ", \"lng\": " + coords[1] + "}");
    }

    /**
     * GET /api/v1/maps/route
     * Params: fromLat, fromLng, toLat, toLng
     * Response: encoded polyline + distance + duration + instructions
     * Frontend dùng VietMap SDK decode polyline để vẽ đường
     */
    private void handleRoute(HttpServletRequest request,
                             HttpServletResponse response,
                             PrintWriter out) throws Exception {
        double fromLat = Double.parseDouble(request.getParameter("fromLat"));
        double fromLng = Double.parseDouble(request.getParameter("fromLng"));
        double toLat   = Double.parseDouble(request.getParameter("toLat"));
        double toLng   = Double.parseDouble(request.getParameter("toLng"));

        JsonObject route = mapsService.getRoute(fromLat, fromLng, toLat, toLng);
        out.print(route.toString());
    }
}