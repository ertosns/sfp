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

public final class Download extends HttpServlet implements Consts{
    
    
    private boolean browser = true;
    private Database database = null;
    Log l = null;

    public Download() {
        database = new Database();
        l = new Log(this.toString(), Log.CONSOLE);
    }

    // inspect cookies (names and values) for one or two cookies or both for (i.e name, pass)
    // if found name return it's value and ture else return false
    public Object[] cookiesHas(Cookie[] cookies, String name, String pass, boolean pair) {
        l.info("inspeck cookies for name ", name, " pass, ", pass);
        boolean cName = false;
        boolean cPass = false;
        int authIndex = 0;
        Cookie tmpCookie = null;
        Object[] obj = new Object[2];
        for (int i = 0; i < cookies.length; i++) {
            if ((tmpCookie = cookies[i]).getValue().equals(name) || tmpCookie.getName().equals(name)) {
                if (cName) {
                    obj[0] = new Boolean(true);
                    return obj;
                }
                if (pair) {
                    cName = true;
                    name = pass;
                    continue;
                }
                obj[0] = new Boolean(true);
                obj[1] = (tmpCookie.getValue().equals(name))? tmpCookie.getName() : tmpCookie.getValue();
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
            boolean mobile = Boolean.parseBoolean(request.getParameter("mobile"));

            boolean listOfUnrespondedRequests = Boolean.parseBoolean(request.getParameter("listofunrespondedrequests"));
            boolean listOfNewRequests = Boolean.parseBoolean(request.getParameter("listofnewrequests"));
            boolean setRequestResponded = Boolean.parseBoolean(request.getParameter("setrequestresponded"));

            boolean listOfPeersInfoById = Boolean.parseBoolean(request.getParameter("listofpeersinfobyid"));

            boolean listOfNewSharedSongs = Boolean.parseBoolean(request.getParameter("listofnewsharedsongs"));
            boolean listOfSharedSongs = Boolean.parseBoolean(request.getParameter("listofsharedsongs"));
            
            boolean setUserImage = Boolean.parseBoolean(request.getParameter("setuserimage"));
            boolean sendShareRequest = Boolean.parseBoolean(request.getParameter("sendsharerequest"));
            boolean removeRelation = Boolean.parseBoolean(request.getParameter("removerelation"));
            boolean removeSong = Boolean.parseBoolean(request.getParameter("removesong"));
            boolean shareSong = Boolean.parseBoolean(request.getParameter("sharesong"));
            boolean getListOfPeersIds = Boolean.parseBoolean(request.getParameter("listofpeersids"));        
            boolean searchByEmail = Boolean.parseBoolean(request.getParameter("searchbyemail"));
            boolean searchByName = Boolean.parseBoolean(request.getParameter("searchbyname"));
            String message = request.getParameter("message");
            String query = request.getParameter("query");
            int songDatabaseId = request.getParameter("songdatabaseid");
            int limitStart = Integer.parseIng(request.getParameter("limitstart"));
            int limit = Integer.parseIng(request.getParameter("limit"));
            String uniqueId = request.getParameter("id"); //uniqueId of mobile user
            String title = request.getParameter("title");
            String peerId = request.getParameter("peerid");
            //TODO check title validaty, consider sql injection, remove suspicous chars
            String urlId = null;
            if(uniqueId != null) urlId = request.getParameter("songId"); //from mobile
            else urlId = request.getParameter("url"); //from client
            boolean valid = validateUrlId(urlId);
            Cookie emailCookie = null;
            Cookie passCookie = null;
            Cookie lastLogedUserIndex = null;
            Cookie numOfLogedUsers = null;
            int LLUI = 0;
            int NOLU = 0;
            int id = -1;
            String pass =  null; 
            String email = null;
            String nonce = null;
            boolean checkNonce = listOfPeersInfo||listOfUnrespondedRequests;

            if(urlId != null && !uniqueId.equals("null")) {
                String[] idAuth = Utils.base64ToString(uniqueId).split(ID_SPLITER);
                email = idAuth[0];
                pass = idAuth[1];
                if(checkNonce) nonce = idAuth[2];
                l.info("from mobile decoded unique id. email, ", email, "pass, ", pass);
            } else {
                try {
                    String requestPass = request.getParameter(Utils.toBase64Uri("pass"));
                    pass = Utils.base64ToString(requestPass);
                    String requestEmail = request.getParameter(Utils.toBase64Uri("email"));
                    email = Utils.base64ToString(requestEmail);
                    l.info("url encoded email, ", email, "url encoded pass, ", pass);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
             

            if(pass!= null & email != null) {
                id = database.getAuthID(pass, email);
                if(checkNonce) id = database.getAuthId(pass, email, nonce);
                l.info("auth id: ", id);
            }

            if(login||signup||checkUser) {
                
                if(id == SERVER_ERROR){                
                    l.info("server error authenticating user");
                    response.setStatus(SERVER_ERR_CODE);
                    return;
                }

                // validate user., 
                if(checkUser == true){
                    if(id > 0){
                        l.info("checkUser, user is found");
                        response.setStatus(SUCCESS_CODE);
                    } else{ // id start at 1, so no valid user
                        l.info("checkUser, user Error");
                        response.setStatus(BAD_AUTH_CODE);
                    }
                    return;
                }
                
                if (signup) {
                    String name = Utils.base64ToString(request.getParameter(Utils.toBase64Uri("name")));
                    if(id>0){
                        l.info("signup, user already found");
                        response.setStatus(USER_ALREADY_FOUND_CODE); // user already has an account!
                        return;
                    }
                    int signupId =  database.signUp(name, pass, email);                
                    if(signupId == SERVER_ERROR){
                        l.info("signup, serverError signing up");
                        response.setStatus(SERVER_ERR_CODE); //Server Error, Database error
                        return;
                    }
                }

                if(mobile && (signup || login)) {
                    l.info("mobile rquest, ", signup?"signup, ":"login, ", "generating uniqueId");
                    byte[] uniqueIdBytes = generateUniqueId(email, pass);
                    response.getOutputStream().write(mergeBytes(Utils.intToBytes(uniqueIdBytes.length), uniqueIdBytes));
                    return;
                }

                {// prepare cookies
                    l.info("doGet, perparing cookies");
                    Cookie[] cookies = request.getCookies();
                    if (cookies != null && cookies.length > 0) {
                        
                        LLUI = Integer.parseInt((String)cookiesHas(cookies, Utils.base64ToString("lastlogeduserindex"), null, false)[1]);                    
                        NOLU = Integer.parseInt((String)cookiesHas(cookies, Utils.base64ToString("numoflogedusers"), null, false)[1]);

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

                    lastLogedUserIndex = new Cookie(Utils.toBase64("lastlogeduserindex"), new String(LLUI+""));
                    lastLogedUserIndex.setMaxAge(SIX_MONTH_SEC);
                    numOfLogedUsers = new Cookie(Utils.toBase64("numoflogedusers"), new String(NOLU+""));
                    numOfLogedUsers.setMaxAge(SIX_MONTH_SEC);
                    if (cookies == null || !(boolean)(cookiesHas(cookies, Utils.toBase64(email), Utils.toBase64(pass), true)[0])) {
                        emailCookie = new Cookie(Utils.toBase64("email"+LLUI), Utils.toBase64(email));
                        emailCookie.setMaxAge(SIX_MONTH_SEC);

                        passCookie = new Cookie(Utils.toBase64("password"+LLUI), Utils.toBase64(pass));
                        passCookie.setMaxAge(SIX_MONTH_SEC);
                    }
                    l.info("cookies, lastlogeduserindex: ", LLUI, " numOfLogedUsers: ", NOLU, "with above email, padd, are encoded in 64Base");
                }
                // serve cookies :)
                if (signup || login) {
                    l.info("serving cookies");
                    response.addCookie(emailCookie);
                    response.addCookie(passCookie);
                    response.addCookie(lastLogedUserIndex);
                    response.addCookie(numOfLogedUsers);
                }
            }
            else if (forceDownload) { // if target is browser force download
                try {
                    if(!valid) {
                        l.info("forceDownload, not valid url");
                        response.setStatus(400);
                        return;
                    }
                    l.info("dogGet, start forceBrowserFileDownload, songId: ", urlId);
                    forceBrowserFileDownload("https://www.youtube.com/watch?v="+urlId, response);
                    if(id == 0) database.addAnonymousDownload();
                    else {
                        database.incrementUserDownloadsNum(id);
                    }
                    l.info("downloads num incremented");
                } catch (Exception e) { 
                    // should inform user
                   e.printStackTrace();
                   response.setStatus(SERVER_ERR_CODE); 
                   return;
                }
            }
            else if (mobilesDownload) { // if target is mobile send via TCP
                l.info("doGet, mobilesDownload");
                if (id <= 0) {
                    l.info("doGet, mobilesDownload, user not found");
                    response.setStatus(BAD_AUTH_CODE);
                    return;
                }
                l.info("doGet, mobileDownload, inserting url ", urlId, " title ", title, " id ", id);
                database.insertSong(id, urlId, (title.length() > TITLE_SIZE)?
                    title.substring(0, TITLE_SIZE).trim():title.trim(), message, 0);
            }

            if(id > 0 && checkNonce) { //TODO UPDATE CHECKNONE WHEN YOU DONE;

                if (listOfPeersInfoById) {
                    response.getOutputStream.write(database.getListOfpeersById(id));
                } else if(getListOfPeersIds) {
                    response.getOutputStream.write(database.getListOfPeersIds(id));
                } else if(listOfUnrespondedRequests||listOfNewRequests) {
                    response.getOutputStream.write(database.getListOfRequests(id, listOfNewRequests));
                } else if(listOfNewSharedSongs || listOfSharedSongs) {
                    response.getOutputStream.write(database.geSharedSongsInfo(id, listOfNewSharedSongs));
                } else if (searchByName) {
                    response.getOutputStream.write(database.searchByName(query, limitStart, limit));
                } else if (searchByEmail) {
                    response.getOutputStream.write(database.searchByEmail(query));
                } else if(setRequestResponded) {
                    database.setRequestState(Utils.publickKeyDecryption(peerid.getBytes()), RESPONDED);
                } else if (setUserImage) {
                    int size;
                    InputStream is = response.getInputStream();
                    byte[] imageSizeBytes = new byte[4];
                    is.read(imageSizeBytes);
                    int imageSize = Utils.bytesToInt(imageSizeBytes);
                    if(imageSiz > MAX_IMAGE_SIZE) {
                        response.setStatus(IMAGE_SIZE_EXCEEDED_ITS_LIMIT);
                        return;
                    }
                    byte[] image = new byte[imageSize];
                    is.read(image);
                    database.addImage(id, image);
                } else if(sendShareRequest) {
                    database.addRelation(id, Utils.publickKeyDecryption(peerId.getBytes()));
                } else if(removeRelation) {
                    database.endRelation(id, Utils.publickKeyDecryption(peerId.getBytes()));
                } else if(removeSong) {
                    database.removeSong(songDatabaseId);
                } else if(shareSong) {
                    database.insertSong(id, songId, title, message, Utils.bytesToInt(Utils.publickKeyDecryption(peerId.getBytes)));
                } 
                return;
            } else {
                responde.setStatus(BAD_AUTH_CODE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException{
        l.info("doPost!");
    }

    public void destroy() {
        l.info("destroy");
    }

    private void forceBrowserFileDownload(String url, HttpServletResponse response) {
        try {
            l.info("forceDownload url ", url);
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

    private byte[] generateUniqueId(String email, String pass, id) {
        String uniqueId = Utils.toBase64(new StringBuilder(email).append(ID_SPLITER).append(pass)
            .append(ID_SPLITER).append(database.incrementNonce(id)).toString());
        l.info("generating user uniqueId for id ",uniqueId);
        return uniqueId.getBytes();
    }    
    

    private boolean validateUrlId(String urlId) { 
        //TODO validate
        return true;
    }

    private byte[] mergeBytes(byte[] byte1, byte[] byte2) {
        int len1 = byte1.length;
        int len2 = byte2.length;
        byte[] newBytes = new byte[len1+len2];
        for(int i = 0; i < len1; i++)
            newBytes[i] = byte1[i];
        for(int i = 0; i < len2; i++)
            newBytes[i+len1] = byte2[i];
        return newBytes;
    }

}
