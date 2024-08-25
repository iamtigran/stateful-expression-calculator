import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class CalcServlet extends HttpServlet {

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String pathInfo = request.getPathInfo();
        String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);

        if (pathInfo.equals("/expression")) {
            // Set expression
            session.setAttribute("expression", body);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } else if (pathInfo.matches("/[a-z]")) {
            // Set variable
            try {
                int value = Integer.parseInt(body);
                if (value < -10000 || value > 10000) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    session.setAttribute(pathInfo.substring(1), value);
                    response.setStatus(HttpServletResponse.SC_CREATED);
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid variable value.");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid URI.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String expression = (String) session.getAttribute("expression");
        if (expression == null) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Expression is not set.");
            return;
        }

        try {
            int result = evaluateExpression(expression, session);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(String.valueOf(result));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Cannot evaluate expression.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String pathInfo = request.getPathInfo();

        if (pathInfo.equals("/expression")) {
            session.removeAttribute("expression");
        } else if (pathInfo.matches("/[a-z]")) {
            session.removeAttribute(pathInfo.substring(1));
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid URI.");
            return;
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private int evaluateExpression(String expression, HttpSession session) throws Exception {
        // Logic to evaluate the expression using session variables
        // For simplicity, use JavaScript engine or a custom parser
        return 0; // Placeholder
    }
}
