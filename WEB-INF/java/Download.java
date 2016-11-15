import java.sql.SQLException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.*;
import java.net.*;
import com.github.axet.vget.VGet;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Download extends HttpServlet{
    private final String SONGS_PATH = Download.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        +"/../../../songs/";   
    private final String ENCODING = "UTF-8";
    private final int SIX_MONTH_SEC = 15552000;
    private final Logger LGR = Logger.getLogger(this.toString());
    private ConsoleHandler ch = new ConsoleHandler();
    private boolean browser = true;
    private int PORT = 1234;
    private Database database = null;

    public Download() {
        ch.setLevel(Level.INFO);
        LGR.setUseParentHandlers(false);
        LGR.addHandler(ch);
        LGR.setLevel(Level.INFO);
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
        String name = decrypt(request.getParameter("name"));
        String pass = decrypt(request.getParameter("pass"));
        String email = decrypt(request.getParameter("email"));
        boolean signup = Boolean.parseBoolean(decrypt(request.getParameter("signup")));
        boolean login = Boolean.parseBoolean(decrypt(request.getParameter("login")));   
        boolean checkUser = Boolean.parseBoolean(decrypt(request.getParameter("checkuser")));
        boolean forceDownload = Boolean.parseBoolean(decrypt(request.getParameter("forcedownload")));
        boolean mobilesDownload = Boolean.parseBoolean(decrypt(request.getParameter("mobilesdownload")));
        boolean devicesInfo = Boolean.parseBoolean(decrypt(request.getParameter("devicesInfo")));
        URL url = new URL("https://www.youtube.com/watch?v="+decrypt(request.getParameter("url")));
        Cookie nameCookie = null;
        Cookie passCookie = null;
        Cookie lastLogedUserIndex = null;
        Cookie numOfLogedUsers = null;
        int LLUI = 0;
        int NOLU = 0;
        database = new Database();
        int id = database.getAuthID(name, pass, email);
        if(id == -1){                
                response.setStatus(500);
                return;
        }
        if(login||signup||checkUser){
            // validate user.
            if(checkUser == true){
                if(id > 0){
                    response.setStatus(200);
                } else{ // id start at 1, so no valid user
                    response.setStatus(401);
                }
                return;
            }
            {// prepare cookies
                Cookie[] cookies = request.getCookies();
                if (cookies != null && cookies.length > 0) {
                    LLUI = Integer.parseInt((String)(cookiesHas(cookies,encrypt("lastlogeduserindex"), null, false)[1]));
                    NOLU = Integer.parseInt((String)(cookiesHas(cookies,encrypt("numoflogedusers"), null, false)[1]));
                    if (signup) {
                        NOLU++;
                        LLUI = NOLU-1;
                    } 
                    else if(login) {
                        String nameKey = (String)(cookiesHas(cookies, encrypt(name), null, false)[1]);
                        LLUI = Integer.parseInt(""+nameKey.charAt(nameKey.length()-1));
                    }
                } else {
                    LLUI = 0;
                    NOLU = 1;
                }
                lastLogedUserIndex = new Cookie(encrypt("lastlogeduserindex"), encrypt(new String(LLUI+"")));
                lastLogedUserIndex.setMaxAge(SIX_MONTH_SEC);
                numOfLogedUsers = new Cookie(encrypt("numoflogedusers"), encrypt(new String(NOLU+"")));
                numOfLogedUsers.setMaxAge(SIX_MONTH_SEC);
                if (cookies == null || !(boolean)(cookiesHas(cookies, encrypt(name), encrypt(pass), true)[0])) {
                    nameCookie = new Cookie(encrypt("name"+LLUI), encrypt(name));
                    nameCookie.setMaxAge(SIX_MONTH_SEC);
                    passCookie = new Cookie(encrypt("pass"+LLUI), encrypt(pass));
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
                    int success =  database.signUp(name, pass, email);                
                    if(success == -1){
                        response.setStatus(500); //Server Error, Database error
                        return;
                    }
                    response.addCookie(nameCookie);
                    response.addCookie(passCookie);
                }
                response.addCookie(lastLogedUserIndex);
                response.addCookie(numOfLogedUsers);
            } else{
                response.setStatus(401); //client Error, bad Authentication
            }
            return;
        }
        else if (forceDownload) { // if target is browser force download
            try {
                forceBrowserFileDownload(url, response);
                if(id == 0) database.addAnonymousDownload();
                else database.incrementUserLocalOrDeletedDownloadsNum(id);
            } catch (Exception e) { 
                // should inform user
               e.printStackTrace();
               response.setStatus(500); //TODO implement this
               return;
            }
            clean();
        }
        else if (devicesInfo) {
            String info = database.getDevicesInfo(id);
            response.getOutputStream().write(info.getBytes());
            return;
        }
        /*else if (mobilesDownload) { // if target is mobile send via TCP
            if (id == 0) {
                response.getOutputStream().write("authentication failed".getBytes("UTF-8"));
                return;
            }
            try {
                IPDesArray = database.getUserActiveIPs(id);
            } catch (Exception e) {
                // user isn't registered with his mobil ip. 
                // window or new page should open and inform user to install the app with app link and how it work.
                // response should be done here, and close connection
                return;
            }
            //increament num of downloads
            ArrayList<Integer> idleIPsIds = new ArrayList<Integer>();
            ArrayList<String> ipsArray = new ArrayList<String>();
            int ipSize = ipsArray.size();
            StringBuilder inactiveIPsDes = new StringBuilder("the following devices aren't accessible ");
            ArrayList<String> activeIPs = new ArrayList<String>();
            for(Map.Entry<String, String> e : IPDesHash.entrySet()){
                String mapIP = e.getKey();
                String mapDes = e.getValue(); 
                try {
                    HttpURLConnection con =(HttpURLConnection) new URL(mapIP).openConnection();
                    con.setRequestMethod("HEAD");
                    con.connect();
                    int responseCode = con.getResponseCode();
                    con.disconnect();
                    if(responseCode != 200){
                        // message to user
                        inactiveIPsDes.append(mapDes+"\n "); 
                        // ips To tcpconnection
                    }else activeIPs.add(mapIP); 
                }catch(Exception exc) { exc.printStackTrace();  }
            }
            response.setContentType("text/html;charset=UTF-8");
            String[] ip = activeIPs.toArray(new String[0]);
            if(ip.length == 0){
                inactiveIPsDes.append("FAILED to downlaod, no device is accessible please make sure you downloaded our AndroidApp, ON and accessible");
                response.getOutputStream().write(inactiveIPsDes.toString().getBytes(ENCODING));
                return;
            }else {
                response.getOutputStream().write("song will be on your active devices soon".toString().getBytes(ENCODING));
            }
            TCPToMobile(ip, url);
            clean();
        }*/
    }
    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException{
        database = new Database();
        String name = decrypt(request.getParameter("uname"));
        String pass = decrypt(request.getParameter("upass"));
        String email = decrypt(request.getParameter("email"));
        String ip = decrypt(request.getParameter("ip"));
        String des = decrypt(request.getParameter("des"));
        boolean signup = Boolean.parseBoolean(decrypt(request.getParameter("signup")));
        boolean activity = Boolean.parseBoolean(decrypt(request.getParameter("activity")));
        int id = database.getAuthID(name, pass, email);
        if(id == -1) {
            response.setStatus(500);
            return;
        } else if(!signup && id == 0) {
            response.setStatus(401);
            return;
        }
        if(signup) {//sign up from mobile 
            int success = database.signUP(name, pass, email, ip, des);
            if(success == Database.SERVER_ERROR) {
               response.setStatus(500);
               return;
            }
            
        }
        else if(activity) {
            int car = 0;
            int messageLen = 0;
            String activityMessage;
            BufferedReader br = null;
            byte[] messageBytes = new byte[0];
            ArrayList<Byte> messageBytesList = null;
            try {
                br = request.getReader();
                messageBytesList = new ArrayList<Byte>();
                while ((car = br.read()) != -1) messageBytesList.add(new Byte((byte)car));
                messageLen = messageBytesList.size();
                messageBytes = new byte[messageLen];
                for (int i = 0; i < messageLen; i++) 
                    messageBytes[i] = messageBytesList.get(i).byteValue();
                activityMessage = new String(messageBytes);
                database.updateIpsActivity(id, activityMessage.split(" "));
            } catch(SQLException e) {
                e.printStackTrace();                
                response.setStatus(500);
                return;
            }
        }
    }
    public String encrypt(String message){
        //TODO implement encrypting algorithm
        return message;
    }
    public Object[] downloadSong(URL url){
        VGet v = null;
        String name = "";
        Object[] objs = null;
        byte[] bytes;
        try{
            File f = new File(SONGS_PATH);
            if(!f.exists()){
                f.mkdirs();
            }
            v = new VGet(url, f);
            v.download();
            bytes = v.getFileName().getBytes("UTF-8");
            name = new String(bytes, "ISO-8859-1");
            objs = new Object[2];
            String path = SONGS_PATH+new String(bytes);
            byte[] pathBytes = path.getBytes();
            objs[0] = new File(new String(pathBytes, "UTF-8"));
            objs[1] = name;
        } catch(Exception e) { 
            e.printStackTrace(); 
        }
        return objs; 
    }
    public void forceBrowserFileDownload(URL url, HttpServletResponse response) {
        try {
            Object[] fileInfo = downloadSong(url);
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
    public void TCPToMobile(String[] ip,URL url) {
     // there is bug in my idea i need to download file to mobile even if it closed.
     // is specific ip didn't recieve 
        for (int i = 0; i < ip.length; i++) {
            try {
                OutputStream sos = new Socket(ip[i], PORT).getOutputStream();
                File f = (File) downloadSong(url)[1];
                int length = (int) f.length();
                byte[] bytes = new byte[length];
                FileInputStream fis = new FileInputStream(f);
                fis.read(bytes);
                sos.write(bytes);
            } catch(Exception e) { 
                e.printStackTrace();  
            }
        }
    }
    public String decrypt(String s){
        return s;
    }
    public void clean(){
        String[] files = new File(SONGS_PATH).list();
        for (int i = 0; i < files.length; i++) new File(files[i]).delete();
    }

}
