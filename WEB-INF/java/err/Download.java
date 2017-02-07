package err;

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
            boolean markSongDownloadable = Boolean.parseBoolean(request.getParameter("marksongdownloadable"));

            boolean numOfDownloads = Boolean.parseBoolean(request.getParameter("numofdownloads"));
            
            String title = request.getParameter("title");
            String uniqueId = request.getParameter("id"); //uniqueId of mobile user

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
            boolean  dontCheckNonce = !(login||checkUser);

            String[] auth = authenticate(request, dontCheckNonce);
            String email = auth[0];
            String pass = auth[1];
            int nonce = Integer.parseInt(auth[2]);
            int id = Integer.parseInt(auth[3]);

            if (login||signup||checkUser) 
            {
                if(id == SERVER_ERROR)
                {                
                    l.info("server error authenticating user");
                    response.setStatus(SERVER_ERR_CODE);
                    return;
                }

                //
                // validate user
                //

                if (checkUser)
                {
                    if(id > 0)
                    {
                        l.info("checkUser, user is found");
                        response.setStatus(SUCCESS_CODE);
                    }
                    else
                    { // id start at 1, so no valid user
                        l.info("checkUser, user Error");
                        response.setStatus(BAD_AUTH_CODE);
                    }
                }
                
                if (signup) 
                {
                    String name = Utils.base64ToString(request.getParameter(Utils.toBase64Uri("name")));
                    if (id>0)
                    {
                        l.info("signup, user already found");
                        response.setStatus(USER_ALREADY_FOUND_CODE); // user already has an account!
                        return;
                    }
                    int signupId =  database.signUp(name, pass, email);                
                    if (signupId == SERVER_ERROR)
                    {
                        l.info("signup, serverError signing up");
                        response.setStatus(SERVER_ERR_CODE); //Server Error, Database error
                        return;
                    }
                }

                if (mobile && (signup || login)) 
                {
                    if(nonce > -1) 
                    {
                        byte[] uniqueIdBytes = generateUniqueId(email, pass, id);
                        response.getOutputStream().write(mergeBytes(Utils.intToBytes(uniqueIdBytes.length), uniqueIdBytes));
                    } 
                    else response.setStatus(SERVER_ERR_CODE);

                    l.info("request ended");
                    return;
                }

                if (!mobile) 
                {// prepare cookies
                    l.info("doGet, perparing cookies");
                    Cookie[] cookies = request.getCookies();
                    if (cookies != null && cookies.length > 0) 
                    {
                        
                        LLUI = Integer.parseInt((String)cookiesHas(cookies, Utils.base64ToString("lastlogeduserindex"), null, false)[1]);                    
                        NOLU = Integer.parseInt((String)cookiesHas(cookies, Utils.base64ToString("numoflogedusers"), null, false)[1]);

                        if (signup) 
                        {
                            NOLU++;
                            LLUI = NOLU-1;
                        }

                        else if(login) 
                        {
                            String nameKey = (String)(cookiesHas(cookies, Utils.toBase64(email), null, false)[1]);
                            LLUI = Integer.parseInt(""+ nameKey.charAt(nameKey.length()-1));
                        }
                    
                    } 
                    else 
                    {
                        LLUI = 0;
                        NOLU = 1;
                    }

                    lastLogedUserIndex = new Cookie(Utils.toBase64("lastlogeduserindex"), new String(LLUI+""));
                    lastLogedUserIndex.setMaxAge(SIX_MONTH_SEC);
                    numOfLogedUsers = new Cookie(Utils.toBase64("numoflogedusers"), new String(NOLU+""));
                    numOfLogedUsers.setMaxAge(SIX_MONTH_SEC);
                    if (cookies == null || !(boolean)(cookiesHas(cookies, Utils.toBase64(email), Utils.toBase64(pass), true)[0])) 
                    {
                        emailCookie = new Cookie(Utils.toBase64("email"+LLUI), Utils.toBase64(email));
                        emailCookie.setMaxAge(SIX_MONTH_SEC);

                        passCookie = new Cookie(Utils.toBase64("password"+LLUI), Utils.toBase64(pass));
                        passCookie.setMaxAge(SIX_MONTH_SEC);
                    }
                    l.info("cookies, lastlogeduserindex: ", LLUI, " numOfLogedUsers: ", NOLU, "with above email, padd, are encoded in 64Base");
                    // serve cookies :)
                    if (signup || login) 
                    {
                        l.info("serving cookies");
                        response.addCookie(emailCookie);
                        response.addCookie(passCookie);
                        response.addCookie(lastLogedUserIndex);
                        response.addCookie(numOfLogedUsers);
                    }
                }
                response.setStatus(200);
                return;
            }
            else if (forceDownload) 
            { // if target is browser force download
                try 
                {
                    if(!valid) 
                    {
                        l.info("forceDownload, not valid url");
                        response.setStatus(400);
                        return;
                    }
                    l.info("dogGet, start forceBrowserFileDownload, songId: ", urlId);
                    forceBrowserFileDownload("https://www.youtube.com/watch?v="+urlId, response);
                    if(id == 0) database.addAnonymousDownload();
                    else 
                    {
                        database.incrementUserDownloadsNum(id);
                    }
                    l.info("downloads num incremented");
                } 
                catch (Exception e) 
                { 
                    // should inform user
                   e.printStackTrace();
                   response.setStatus(SERVER_ERR_CODE); 
                   return;
                }
            }
            else if (mobilesDownload) 
            { // if target is mobile send via Listener
                l.info("doGet, mobilesDownload");
                if (id <= 0) 
                {
                    l.info("doGet, mobilesDownload, user not found");
                    response.setStatus(BAD_AUTH_CODE);
                    return;
                }
                l.info("doGet, mobileDownload, inserting url ", urlId, " title ", title, " id ", id);
                database.insertSong(0, id, urlId
                    , (title.length() > TITLE_SIZE)?title.substring(0, TITLE_SIZE).trim():title.trim()
                    , null);
                return;

            }

            if(id > 0) 
            { //TODO UPDATE CHECKNONE WHEN YOU DONE;

                boolean listOfNonNewRequests = Boolean.parseBoolean(request.getParameter("listofnonnewrequests"));
                boolean listOfNewRequests = Boolean.parseBoolean(request.getParameter("listofnewrequests"));
                boolean listOfAcceptedRequests = Boolean.parseBoolean(request.getParameter("getacceptedrequests"));
                boolean listOfNewSharedSongs = Boolean.parseBoolean(request.getParameter("listofnewsharedsongs"));
                boolean listOfSharedSongs = Boolean.parseBoolean(request.getParameter("listofsharedsongs"));
                boolean removeSong = Boolean.parseBoolean(request.getParameter("removesong"));
                boolean searchByEmail = Boolean.parseBoolean(request.getParameter("searchbyemail"));
                boolean searchByName = Boolean.parseBoolean(request.getParameter("searchbyname"));
                String query = null; 
                int songDatabaseId = 0;                

                if(listOfNonNewRequests||listOfNewRequests) 
                {
                    l.info("get List of Requests new||non-new");
                    response.getOutputStream().write(database.getListOfRequests(id, listOfNewRequests));
                } 
                else if(listOfAcceptedRequests) 
                {
                    l.info("get list of accepted Requests");
                    response.getOutputStream().write(database.getListOfUnAckRequests(id));
                    if (response.getStatus() == response.SC_OK)
                        database.setRequestAck(id);
                } 
                else if(listOfNewSharedSongs || listOfSharedSongs) 
                {
                    l.info("get list of new shared songs || list of shared songs newSongs "+listOfNewSharedSongs+", of user id "+id);
                    response.getOutputStream().write(database.getSharedSongsInfo(id, listOfNewSharedSongs));
                } 
                else if (searchByName) 
                {
                    l.info("search by name");
                    int limitStart = Integer.parseInt(request.getParameter("limitstart"));
                    int limit = Integer.parseInt(request.getParameter("limit"));
                    query = request.getParameter("query");
                    response.getOutputStream().write(database.searchByName(id, query, limitStart, limit));
                } 
                else if (searchByEmail) 
                {
                    l.info("search by Email");
                    query = request.getParameter("query");
                    response.getOutputStream().write(database.searchByEmail(id, query));
                } 
                else if(removeSong) 
                {
                    l.info("remove song");
                    songDatabaseId =  Integer.parseInt(request.getParameter("songdatabaseid"));
                    database.removeSong(songDatabaseId);
                } 
                else if (numOfDownloads) 
                {
                    l.info("get num of downloads");
                    response.getOutputStream().write(Utils.intToBytes(database.getTotalDownloads(id)));
                }
                return;
            }
            else 
            {
                response.setStatus(BAD_AUTH_CODE);
            }

        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    @Override 
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {
        boolean setRequestResponded = Boolean.parseBoolean(request.getParameter("setrequestresponded"));
        boolean sendShareRequest = Boolean.parseBoolean(request.getParameter("sendsharerequest"));
        boolean removeRelation = Boolean.parseBoolean(request.getParameter("removerelation"));
        boolean shareSong = Boolean.parseBoolean(request.getParameter("sharesong"));
        boolean setUserImage = Boolean.parseBoolean(request.getParameter("setuserimage"));
        boolean markRequestSent = Boolean.parseBoolean(request.getParameter("markrequestsent"));
        boolean markSongSent = Boolean.parseBoolean(request.getParameter("marksongsent"));
        String message = request.getParameter("message");
        String title = request.getParameter("title");
        String uniqueId = request.getParameter("id"); //uniqueId of mobile user

        //TODO check title validaty, consider sql injection, remove suspicous chars
        String urlId = null;
        if(uniqueId != null) urlId = request.getParameter("songId"); //from mobile
        else urlId = request.getParameter("url"); //from client

        String[] auth = authenticate(request,false);
        String email = auth[0];
        String pass = auth[1];
        int nonce = Integer.parseInt(auth[2]);
        int id = Integer.parseInt(auth[3]);
        
        try 
        {
            InputStream is = request.getInputStream();
            int peerId = 0;

            if (setRequestResponded || sendShareRequest || removeRelation || shareSong || markSongSent || markRequestSent) 
            {
                byte[] sizeBytes = new byte[4];
                is.read(sizeBytes);
                int size = Utils.bytesToInt(sizeBytes);
                byte[] pid = new byte[size];
                is.read(pid);
                l.info("peerIdBytesEnc len ", pid.length, " , content ", new String(pid));
                peerId = Utils.bytesToInt(Utils.publicKeyDecryption(pid));
            }
            if (setRequestResponded) 
            {
                l.info("set request responded");
                database.setRequestState(id, peerId, RESPONDED);
            } 
            else if(sendShareRequest) 
            {
                if(!database.checkRelation(peerId, id))
                {
                    l.info("adding relation");
                    database.addRelation(id, peerId);
                }
            }
            else if(removeRelation) 
            {
                l.info("remove relation");
                database.endRelation(id, peerId);
            } 
            else if(shareSong) 
            {
                l.info("share song");
                database.insertSong(id, peerId, urlId, title, message);
            } 
            else if (setUserImage) 
            {
                l.info("set user image");
                int size;
                byte[] imageSizeBytes = new byte[4];
                is.read(imageSizeBytes);
                int imageSize = Utils.bytesToInt(imageSizeBytes);
                //TODO resize, compress img
                if(imageSize > MAX_IMAGE_SIZE) 
                {
                    response.setStatus(IMAGE_SIZE_EXCEEDED_ITS_LIMIT);
                    return;
                }
                byte[] image = new byte[imageSize];
                is.read(image);
                database.addImage(id, image);
            } 
            else if(markSongSent) 
            {
                l.info("mark song sent");
                database.markSongSent(peerId, id, urlId);
            } 
            else if(markRequestSent) 
            {
                l.info("mark request sent");
                boolean success = database.markRequestSent(peerId, id);
                System.out.println("success: "+success);
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }

    }

    public String[] authenticate (HttpServletRequest request, boolean dontCheckNonce) 
    {
        //boolean checkNonce = listOfPeersInfo||listOfUnrespondedRequests;
        // input don'tcheckNonce
        //return  email, pass, nonce, id
        String pass =  null; 
        String email = null;
        int nonce = 0;       
        int id = 0;    
        String uniqueId = request.getParameter("id"); //uniqueId of mobile user
        
        if (uniqueId != null) 
        {
            String[] idAuth = Utils.base64ToString(uniqueId).split(ID_SPLITER);
            email = idAuth[0];
            pass = idAuth[1];
            //if(checkNonce) 
            if(dontCheckNonce)
                nonce = Integer.parseInt(idAuth[2]);
            l.info("from mobile decoded unique id ",uniqueId, ",  email ", email, ", pass, ", pass, ", nonce ", nonce);
        } 
        else 
        {
            try 
            {
                String requestPass = request.getParameter(Utils.toBase64Uri("pass"));
                l.info("requestPass: ", requestPass);
                pass = Utils.base64UriToString(requestPass);
                l.info("pass: ", pass);
                String requestEmail = request.getParameter(Utils.toBase64Uri("email"));
                l.info("requestEmail: ", requestEmail);
                email = Utils.base64UriToString(requestEmail);
                l.info("email: ", email);
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
             

        if (pass!= null && email != null) 
        {
            if(dontCheckNonce) id = database.getAuthID(pass, email, nonce);
            else id = database.getAuthID(pass, email);
            l.info("auth id: ", id);
            if(id > 0) nonce = database.getNonce(id);
        }
        
        return new String[] {email, pass, nonce+"", id+""};
    }

    public void destroy() 
    {
        l.info("destroy");
    }

    private void forceBrowserFileDownload(String url, HttpServletResponse response) 
    {
        try 
        {
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
        } 
        catch(Exception e) 
        {
            e.printStackTrace();  
        }
    }

    private byte[] generateUniqueId(String email, String pass, int id) 
    {
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
