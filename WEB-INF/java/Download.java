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
    
    
    private boolean browser = true;
    private Database database = null;
    Listener listener;
    private final Logger l = Logger.getLogger(this.toString());
    Handler handler = null;

    public Download() {

        handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        l.setUseParentHandlers(false);
        l.addHandler(handler);
        l.setLevel(Level.INFO);

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
      throws IOException, ServletException {       
    try {
        boolean signup = Boolean.parseBoolean(request.getParameter("signup"));
        boolean login = Boolean.parseBoolean(request.getParameter("login"));   
        boolean checkUser = Boolean.parseBoolean(request.getParameter("checkuser"));
        boolean forceDownload = Boolean.parseBoolean(request.getParameter("forcedownload"));
        boolean mobilesDownload = Boolean.parseBoolean(request.getParameter("mobilesdownload"));
        
        String urlId = request.getParameter("url");
        boolean valid = validateUrlId(urlId);

        Cookie emailCookie = null;
        Cookie passCookie = null;
        Cookie lastLogedUserIndex = null;
        Cookie numOfLogedUsers = null;
        int LLUI = 0;
        int NOLU = 0;
        int id = -1;
        database = new Database();
        
        
        if(login||signup||checkUser){

            String pass = Utils.base64ToString(request.getParameter("pass"));
            String email = Utils.base64ToString(request.getParameter("email"));
            
            id = database.getAuthID(pass, email);

            if(id == -1){                
                l.info("doGet, server error authenticating user");
                response.setStatus(500);
                return;
            }

            // validate user.
            if(checkUser == true){
                if(id > 0){
                    l.info("doGet, checkUser, user is found");
                    response.setStatus(200);
                    response.getOutputStream().write(generateUniqueId(pass, id));
                } else{ // id start at 1, so no valid user
                    l.info("goGet, checkUser, user Error");
                    response.setStatus(401);
                }
                return;
            }

            {// prepare cookies
                l.info("doGet, perparing cookies");
                Cookie[] cookies = request.getCookies();
                if (cookies != null && cookies.length > 0) {
                    
                    LLUI = Integer.parseInt(Utils.base64ToString((String)cookiesHas(cookies,Utils.toBase64("lastlogeduserindex"), null, false)[1]));                    
                    NOLU = Integer.parseInt(Utils.base64ToString((String)cookiesHas(cookies,Utils.toBase64("numoflogedusers"), null, false)[1]));

                    if (signup) {
                        NOLU++;
                        LLUI = NOLU-1;
                    }

                    else if(login) {
                        String nameKey = (String)(cookiesHas(cookies, Utils.toBase64(email), null, false)[1]);
                        LLUI = Integer.parseInt(""+ nameKey.charAt(nameKey.length()-1));
                    }
                
                } else {
                    LLUI = 0;
                    NOLU = 1;
                }

                lastLogedUserIndex = new Cookie(Utils.toBase64("lastlogeduserindex"), Utils.toBase64(new String(LLUI+"")));
                lastLogedUserIndex.setMaxAge(SIX_MONTH_SEC);
                numOfLogedUsers = new Cookie(Utils.toBase64("numoflogedusers"), Utils.toBase64(new String(NOLU+"")));
                numOfLogedUsers.setMaxAge(SIX_MONTH_SEC);
                if (cookies == null || !(boolean)(cookiesHas(cookies, Utils.toBase64(email), Utils.toBase64(pass), true)[0])) {
                    emailCookie = new Cookie(Utils.toBase64("email"+LLUI), Utils.toBase64(email));
                    emailCookie.setMaxAge(SIX_MONTH_SEC);
                    passCookie = new Cookie(Utils.toBase64("pass"+LLUI), Utils.toBase64(pass));
                    passCookie.setMaxAge(SIX_MONTH_SEC);
                }
            }
            // serve cookies :)
            if (signup || (login && id > 0)) {
                if(signup){ // signup from browser.
                    String name = Utils.base64ToString(request.getParameter("name"));
                    if(id>0){
                        l.info("doGet, signup, user already found");
                        response.setStatus(401); // user already has an account!
                        return;
                    }
                    int signupId =  database.signUp(name, pass, email);                
                    if(signupId == SERVER_ERROR){
                        l.info("doGet, signup, serverError signing up");
                        response.setStatus(500); //Server Error, Database error
                        return;
                    }
                    
                }
                response.addCookie(emailCookie);
                response.addCookie(passCookie);
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
                    l.info("doGet, forceDownload, not valid url");
                    response.setStatus(400);
                    return;
                }
                l.info("dogGet, start forceBrowserFileDownload");
                forceBrowserFileDownload("https://www.youtube.com/watch?v="+urlId, response);
                if(id == 0) database.addAnonymousDownload();
                else database.incrementUserLocalDownloadsNum(id);
            } catch (Exception e) { 
                // should inform user
               e.printStackTrace();
               response.setStatus(500); //TODO implement this
               return;
            }
        }
        else if (mobilesDownload) { // if target is mobile send via TCP
            l.info("doGet, mobilesDownload");
            if (id == 0) {
                l.info("doGet, mobilesDownload, user not found");
                response.setStatus(400);
                return;
            }
            l.info("doGet, mobileDownload, inserting givin url and id");
            database.insertSong(id, urlId);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    }

    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException{
        l.info("dopPost!");
    }

    public void destroy() {
        l.info("destroy");
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
            l.info("song "+fileName+" is sent");
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
