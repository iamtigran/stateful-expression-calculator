import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.locks.ReentrantLock;

@WebServlet(urlPatterns = {"/calc/*"})
public class CalcServlet extends HttpServlet {

    private static final int MIN_VALUE = -10000;
    private static final int MAX_VALUE = 10000;
    private final ReentrantLock lock = new ReentrantLock();  // Added lock for synchronization

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String pathInfo = request.getPathInfo();
        String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual).trim();

        if (body.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Request body cannot be empty.");
            return;
        }

        lock.lock();  // Synchronize session access
        try {
            if ("/expression".equals(pathInfo)) {
                handleExpressionPut(session, response, body, request.getRequestURI());
            } else if (pathInfo != null && pathInfo.matches("/[a-z]")) {
                handleVariablePut(session, response, pathInfo.substring(1), body, request.getRequestURI());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid URI.");
            }
        } finally {
            lock.unlock();
        }
    }

    private void handleExpressionPut(HttpSession session, HttpServletResponse response, String expression, String uri) throws IOException {
        boolean isNew = session.getAttribute("expression") == null;
        session.setAttribute("expression", expression);
        if (isNew) {
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.setHeader("Location", uri);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    private void handleVariablePut(HttpSession session, HttpServletResponse response, String varName, String body, String uri) throws IOException {
        try {
            int value = Integer.parseInt(body);
            if (value < MIN_VALUE || value > MAX_VALUE) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Variable value must be between -10000 and 10000.");
                return;
            }
            boolean isNew = session.getAttribute(varName) == null;
            session.setAttribute(varName, value);
            if (isNew) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.setHeader("Location", uri);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid variable value. It must be an integer.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        if (!"/result".equals(pathInfo)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Resource not found.");
            return;
        }

        HttpSession session = request.getSession();
        String expression = (String) session.getAttribute("expression");

        if (expression == null) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Expression is not set.");
            return;
        }

        lock.lock();  // Synchronize session access
        try {
            try {
                int result = evaluateExpression(expression, session);
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(String.valueOf(result));
            } catch (UndefinedVariableException e) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("Not all variables are set.");
            } catch (ScriptException | ArithmeticException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid expression.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String pathInfo = request.getPathInfo();

        lock.lock();  // Synchronize session access
        try {
            if ("/expression".equals(pathInfo)) {
                if (session.getAttribute("expression") != null) {
                    session.removeAttribute("expression");
                }
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else if (pathInfo != null && pathInfo.matches("/[a-z]")) {
                String varName = pathInfo.substring(1);
                if (session.getAttribute(varName) != null) {
                    session.removeAttribute(varName);
                }
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid URI.");
            }
        } finally {
            lock.unlock();
        }
    }

    private int evaluateExpression(String expression, HttpSession session) throws ScriptException, UndefinedVariableException {
        Enumeration<String> attributeNames = session.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String varName = attributeNames.nextElement();
            if (!"expression".equals(varName)) {
                Object value = session.getAttribute(varName);
                expression = expression.replaceAll("\\b" + varName + "\\b", value.toString());
            }
        }

        // Check if there are any unresolved variables
        if (expression.matches(".*[a-zA-Z].*")) {
            throw new UndefinedVariableException("Not all variables are set.");
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
        try {
            Object result = engine.eval(expression);

            if (result instanceof Number) {
                return ((Number) result).intValue();
            } else {
                throw new ScriptException("Expression did not evaluate to a number.");
            }
        } catch (ArithmeticException e) {
            throw new ScriptException("Arithmetic error: " + e.getMessage());
        }
    }

    private static class UndefinedVariableException extends Exception {
        public UndefinedVariableException(String message) {
            super(message);
        }
    }
}
