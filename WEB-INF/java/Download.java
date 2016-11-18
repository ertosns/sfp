import java.sql.SQLException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

//TODO register users and info them of registered and active devices.
//TODO log
public final class Download extends HttpServlet implements Consts{
    
    private final Logger LGR = Logger.getLogger(this.toString());
    private ConsoleHandler ch = new ConsoleHandler();
    private boolean browser = true;
    private Database database = null;
    Listener listener;

    private Download() {
        ch.setLevel(Level.INFO);
        LGR.setUseParentHandlers(false);
        LGR.addHandler(ch);
        LGR.setLevel(Level.INFO);
        listener = new Listener();
        listener.listen();
    }
    // inspect cookies (names and values) for one or two cookies or both for (i.e name, pass)
    // if found name return it's value and ture else return false
    public Object[] cookiesHas(Cookie[] cookies, String name, String pass, boolean pair) {
        boolean cName = false;
        boolean cPass = false;
        int authIndex = 0;
        String currentName = name;
        Cookie tmpCookie = null;
        Object[] obj = new Object[2];
        for (int i = 0; i < cookies.length; i++) {
            if ((tmpCookie = cookies[i]).getValue().equals(currentName) || tmpCookie.getName().equals(currentName)) {
                if (cName) {
                    obj[0] = new Boolean(true);
                    return obj;
                }
                if (pair) {
                    cName = true;
                    currentName = pass;
                    continue;
                }
                obj[0] = new Boolean(true);
                obj[1] = (tmpCookie.getValue().equals(currentName))? tmpCookie.getName() : tmpCookie.getValue();
                return obj;
            }
        }
        obj[0] = new Boolean(false);
        return obj;
    }
    // login, signup, check or valid user, download locally, download to phones
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws IOException, ServletException{       
        String name = Utils.base64ToString(request.getParameter("name"));
        String pass = Utils.base64ToString(request.getParameter("pass"));
        String email = Utils.base64ToString(request.getParameter("email"));
        boolean signup = Boolean.parseBoolean(Utils.base64ToString(request.getParameter("signup")));
        boolean login = Boolean.parseBoolean(Utils.base64ToString(request.getParameter("login")));   
        boolean checkUser = Boolean.parseBoolean(Utils.base64ToString(request.getParameter("checkuser")));
        boolean forceDownload = Boolean.parseBoolean(Utils.base64ToString(request.getParameter("forcedownload")));
        boolean mobilesDownload = Boolean.parseBoolean(Utils.base64ToString(request.getParameter("mobilesdownload")));
        String urlId = Utils.base64ToString(request.getParameter("url"));
        boolean valid = validateUrlId(urlId);
        Cookie nameCookie = null;
        Cookie passCookie = null;
        Cookie lastLogedUserIndex = null;
        Cookie numOfLogedUsers = null;
        int LLUI = 0;
        int NOLU = 0;
        database = new Database();
        int id = database.getAuthID(pass, email);
        System.out.println("download class access console");
        if(id == -1){                

                response.setStatus(500);
                return;
        }
        if(login||signup||checkUser){
            // validate user.
            if(checkUser == true){
                if(id > 0){
                    response.setStatus(200);
                    response.getOutputStream().write(generateUniqueId(pass, id));
                } else{ // id start at 1, so no valid user
                    response.setStatus(401);
                }
                return;
            }
            {// prepare cookies
                Cookie[] cookies = request.getCookies();
                if (cookies != null && cookies.length > 0) {
                    LLUI = Integer.parseInt((String)(cookiesHas(cookies,Utils.toBase64Url("lastlogeduserindex"), null, false)[1]));
                    NOLU = Integer.parseInt((String)(cookiesHas(cookies,Utils.toBase64Url("numoflogedusers"), null, false)[1]));
                    if (signup) {
                        NOLU++;
                        LLUI = NOLU-1;
                    } 
                    else if(login) {
                        String nameKey = (String)(cookiesHas(cookies, Utils.toBase64Url(name), null, false)[1]);
                        LLUI = Integer.parseInt(""+nameKey.charAt(nameKey.length()-1));
                    }
                } else {
                    LLUI = 0;
                    NOLU = 1;
                }
                lastLogedUserIndex = new Cookie(Utils.toBase64Url("lastlogeduserindex"), Utils.toBase64Url(new String(LLUI+"")));
                lastLogedUserIndex.setMaxAge(SIX_MONTH_SEC);
                numOfLogedUsers = new Cookie(Utils.toBase64Url("numoflogedusers"), Utils.toBase64Url(new String(NOLU+"")));
                numOfLogedUsers.setMaxAge(SIX_MONTH_SEC);
                if (cookies == null || !(boolean)(cookiesHas(cookies, Utils.toBase64Url(name), Utils.toBase64Url(pass), true)[0])) {
                    nameCookie = new Cookie(Utils.toBase64Url("name"+LLUI), Utils.toBase64Url(name));
                    nameCookie.setMaxAge(SIX_MONTH_SEC);
                    passCookie = new Cookie(Utils.toBase64Url("pass"+LLUI), Utils.toBase64Url(pass));
                    passCookie.setMaxAge(SIX_MONTH_SEC);
                }
            }
            // serve cookies :)
            if (signup || (login && id > 0)) {
                if(signup){ // signup from browser.
                    if(id>0){
                        response.setStatus(401); // user already has an account!
                        return;
                    }
                    int signupId =  database.signUp(name, pass, email);                
                    if(signupId == SERVER_ERROR){
                        response.setStatus(500); //Server Error, Database error
                        return;
                    }
                    response.addCookie(nameCookie);
                    response.addCookie(passCookie);
                }
                response.addCookie(lastLogedUserIndex);
                response.addCookie(numOfLogedUsers);
                response.getOutputStream().write(generateUniqueId(pass, id));
            } else{
                response.setStatus(401); //client Error, bad Authentication
            }
            return;
        }
        else if (forceDownload) { // if target is browser force download
            try {
                if(!valid) {
                    response.setStatus(400);
                    return;
                }
                forceBrowserFileDownload("https://www.youtube.com/watch?v="+urlId, response);
                if(id == 0) database.addAnonymousDownload();
                else database.incrementUserLocalOrDeletedDownloadsNum(id);
            } catch (Exception e) { 
                // should inform user
               e.printStackTrace();
               response.setStatus(500); //TODO implement this
               return;
            }
        }
        else if (mobilesDownload) { // if target is mobile send via TCP
            if (id == 0) {
                response.setStatus(400);
                return;
            }
            database.insertSong(id, urlId);
        }
    }

    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException{
    }

    public void destroy() {
        listener.stop();
    }

    private void forceBrowserFileDownload(String url, HttpServletResponse response) {
        try {
            Object[] fileInfo = Utils.downloadSong(url);
            File f = (File) fileInfo[0];
            String fileName = (String) fileInfo[1];
            response.setContentType("application/force-download");
            response.setContentLength((int)f.length());
            response.setHeader("Content-Disposition","attachment; filename=\"" +fileName);
            InputStream is = new FileInputStream(f);
            byte[] bytes = new byte[(int)f.length()];
            is.read(bytes);
            response.getOutputStream().write(bytes);
        } catch(Exception e) {
            e.printStackTrace();  
        }
    }

    private byte[] generateUniqueId(String pass, int id) {
        byte[] newPass = Utils.changeStringSize(pass, 10);
        //TODO pass should be bitwised, and padding shouldn't be the same characters & not even fixed pattern 
        return Utils.toBase64Bytes(new StringBuilder(new String(newPass)).append(id).toString());
    }

    private boolean validateUrlId(String urlId) { 
        //TODO validate
        return true;
    }

}
