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
    boolean browser = true;
    int PORT = 1234;
    HashMap<String, String> IPDesHash = null;
    Database database = null;
    String ENCODING = "UTF-8";
    final Logger LGR = Logger.getLogger(this.toString());
    ConsoleHandler ch = new ConsoleHandler();
    public Download(){
	ch.setLevel(Level.INFO);
        LGR.setUseParentHandlers(false);
        LGR.addHandler(ch);
        LGR.setLevel(Level.INFO);
    }   
    public Object[] cookiesHas(Cookie[] cookies, String name, String pass, boolean pair){
        boolean cName = false;
        boolean cPass = false;
        int authIndex = 0;
        String currentName = name;
        Cookie tmpCookie = null;
        Object[] obj = new Object[2];
        for(int i = 0; i < cookies.length; i++){
            if((tmpCookie = cookies[i]).getValue().equals(currentName) || tmpCookie.getName().equals(currentName)){
                if(cName){
                    obj[0] = new Boolean(true);
                    return obj;
                }
                if(pair){
                    cName = true;
                    currentName = pass;
                    continue;
                }
                obj[0] = new Boolean(true);
                obj[1] = (tmpCookie.getValue().equals(currentName))? tmpCookie.getValue() : tmpCookie.getName();
                return obj;
            }
	    }
        obj[0] = new Boolean(false);
        return obj;
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws IOException, ServletException{   	
        String name = decrypt(request.getParameter("name"));
        String pass = decrypt(request.getParameter("pass"));
        boolean signup = Boolean.parseBoolean(decrypt(request.getParameter("signup")));
        boolean login = Boolean.parseBoolean(decrypt(request.getParameter("login")));   
        boolean download = Boolean.parseBoolean(decrypt(request.getParameter("download")));
        boolean browser = Boolean.parseBoolean(decrypt(request.getParameter("browser")));
        URL url = new URL("https://www.youtube.com/watch?v="+decrypt(request.getParameter("url")));
        Cookie nameCookie = null;
        Cookie passCookie = null;
        Cookie lastLogedUserIndex = null;
        Cookie numOfLogedUsers = null;
        int llui = 0;
        int nolu = 0;
        if(login||signup){
            database = new Database();
            int id = database.getAuthID(name, pass);
            if(id == -1){
                LGR.info("can't find input username, and password while logging in");
		//  response.setStatus(500);
		// return;
            }
            Cookie[] cookies = request.getCookies();
            if(cookies != null && cookies.length>=0){
	            if((boolean)(cookiesHas(cookies, encrypt(name), encrypt(pass), true)[0])){
                    lastLogedUserIndex = new Cookie(encrypt("lastlogeduserindex"), encrypt((String)(cookiesHas(cookies,
                    encrypt("lastlogeduserindex"), null, false)[1]))); //save doesn't require check.
                    lastLogedUserIndex.setMaxAge(7776000); //three month
                }
                else{
                    try{
                        llui = Integer.parseInt((String)(cookiesHas(cookies,encrypt("lastlogeduserindex"), null, false)[1]));
                        nolu = Integer.parseInt((String)(cookiesHas(cookies,encrypt("numOfLogedUsers"), null, false)[1]));
                    } catch(Exception e){
                        llui = 0;
                        nolu = 1;
                    }
                    nameCookie = new Cookie(encrypt("name"+llui), encrypt(name));
                    nameCookie.setMaxAge(7776000);
                    passCookie = new Cookie(encrypt("pass"+llui), encrypt(pass));
                    passCookie.setMaxAge(7776000);
                    lastLogedUserIndex = new Cookie(encrypt("lastlogeduserindex"), encrypt(new String(llui+"")));
                    lastLogedUserIndex.setMaxAge(7776000);
                    numOfLogedUsers = new Cookie(encrypt("numoflogedusers"), encrypt(new String(nolu+"")));
                    numOfLogedUsers.setMaxAge(7776000);
                }   
            }
            if(signup || (login && id > 0)){
                if(signup){ // signup from browser.
		            if(id>0){
		                response.setStatus(401); // user already has an account!
        	            return;
                    }
                    int success =  database.signUp(name, pass);                
                    LGR.info("signup account done with with "+((success==-1)?"failure":"success"));
                    if(success == -1){
			//            response.setStatus(500); //Server Error, Database error
			//   LGR.info("singup failed and 500 status code send to client");
			//  return;
		            }
                }
                //TODO use basic authentication to enbale user the option to use cookeis or not.
                response.addCookie(nameCookie);
                response.addCookie(passCookie);
            }   
            else{
                response.setStatus(401); //client Error, bad Authentication
                response.setHeader("WWWW-Authentication", "basic realm=UserNameIsRealm");
            }
            return;
        }
        if(!download){ // check if the url is available.
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            int code = con.getResponseCode();
            if(code == 200) response.sendError(200);
            else response.setStatus(404);
           return;
        }
        if (browser) { // if target is browser force download
            try{
                forceBrowserFileDownload(url, response);
            }catch(Exception e){ 
                // should inform user
               e.printStackTrace();
            }
        } else{ // if target is mobile send via TCP
            database = new Database();
            int id = database.getAuthID(name, pass); // make that secure. 
	        if(id == -1){
                response.setStatus(401);
                return;
	        }
            else if(id == 0){
                response.getOutputStream().write("authentication failed".getBytes("UTF-8"));
                return;
            }
            try{
                IPDesHash = database.getUserActiveIPs(id);
            }catch(Exception e){
                // user isn't registered with his mobil ip. 
                // window or new page should open and inform user to install the app with app link and how it work.
                // response should be done here, and close connection
                return;
            }
            ArrayList<Integer> idleIPsIds = new ArrayList<Integer>();
            int ipSize = IPDesHash.size();
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
        }
    }
    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException{
        database = new Database();
    	String name = decrypt(request.getParameter("uname"));
        String pass = decrypt(request.getParameter("upass"));
        String ip = decrypt(request.getParameter("ip"));
        String des = decrypt(request.getParameter("des"));
        boolean signup = Boolean.parseBoolean(decrypt(request.getParameter("signup")));
        int id = database.getAuthID(name, pass);
        if(id == -1){
            response.setStatus(401);
	    }
        if(signup){//sign up from mobile 
            if(id>0){
    		response.getOutputStream().write("you already has an account".getBytes(ENCODING));
       		return;
       	}
       	int success = database.signUP(name, pass, ip, des);
            if(success == -1){
               response.setStatus(401);
            }
            return;
        }
        if(id == 0){
            response.getOutputStream().write("authentication failed, wrong username or password".getBytes(ENCODING));
            return;
        }
        int ipsnum = database.getIPsNum(id);
        if(ipsnum >= 5){
        	response.setStatus(200);
	    } 
        else{
            database.insertIP(id, ip, des);
            response.setStatus(200);
        }
    }
    public String encrypt(String message){
        //TODO implement encrypting algorithm
        return message;
    }
    public void forceBrowserFileDownload(URL url, HttpServletResponse response){
      	try{
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
        }catch(Exception e) { e.printStackTrace();  }
    }
    public String decrypt(String s){
    	return s;
    }
    public void TCPToMobile(String[] ip,URL url){
     // there is bug in my idea i need to download file to mobile even if it closed.
     // is specific ip didn't recieve 
    	for(int i = 0; i < ip.length; i++){
    	    try{
    	    	OutputStream sos = new Socket(ip[i], PORT).getOutputStream();
    	    	File f = (File) downloadSong(url)[1];
    	    	int length = (int) f.length();
    	    	byte[] bytes = new byte[length];
    	    	FileInputStream fis = new FileInputStream(f);
    	    	fis.read(bytes);
    	    	sos.write(bytes);
    	    }catch(Exception e) { e.printStackTrace();  }
        }
    }
    public Object[] downloadSong(URL url){
        VGet v = null;
        String name = "";
        Object[] objs = null;
        String SONGS_PATH = Download.class.getProtectionDomain().getCodeSource().getLocation().getPath()+"/../../../songs/";
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
        }catch(Exception e){ e.printStackTrace(); }
        return objs; 
    }
}
