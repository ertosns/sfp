package err;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.io.*;

/* application protocol
 * SERVER_ERROR  :if method return -1 it's database error
 */
 
public class Database implements Consts {
    Connection con = null;
    Statement stm = null;
    Log l = null;

    public Database() {
        l =  new Log(this.toString(), Log.CONSOLE);
        try {
            Class.forName(DRIVER);
            con = DriverManager.getConnection(DATABASE_URL, NAME, PASS);
            stm = con.createStatement();
            stm.execute("create database if not exists sfp;");
            con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
            stm = con.createStatement();
            DatabaseMetaData meta = con.getMetaData();
            assert meta.getColumns(null, "SFP", null, null).next();
            ResultSet userInfoTableRS = meta.getColumns(null, null, "USER_INFO", null);
            String userInfoTable = "CREATE TABLE IF NOT EXISTS USER_INFO ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "NAME VARCHAR(20) NOT NULL, "
                + "PASS VARCHAR(20) NOT NULL, "
                + "EMAIL VARCHAR(100) NOT NULL, "
                + "IMAGE BLOB, "
                + "DOWNLOADS INT DEFAULT 0, "
                + "NONCE INT DEFAULT 0);";

            String anonymousDownloads = "CREATE TABLE IF NOT EXISTS ANONYMOUS_DOWNLOADS ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "DOWNLOAD_DATE TIMESTAMP NOT NULL);";
              
            String songs = "CREATE TABLE IF NOT EXISTS SONGS ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "URL VARCHAR(50) NOT NULL, "
                + "REQUEST_DATE TIMESTAMP, "
                + "RECEIVER_ID INT NOT NULL, "
                + "SENDER_ID INT DEFAULT 0, "
                + "MESSAGE VARCHAR(140), "
                + "SENT BIT(1) DEFAULT 0, " //TODO updated at client side
                + "SONG_TITLE VARCHAR(100));";

            String shareSongsRequest = "CREATE TABLE IF NOT EXISTS SHARE_SONGS_REQUEST ( "
                + "SENDER_ID INT NOT NULL, "
                + "RECEIVER_ID INT NOT NULL, "
                + "RECEIVER_ACK BIT(1) DEFAULT 0, "
                + "SENT BIT(1) DEFAULT 0, " //TODO should be update at client side to ensure it sent
                + "RESPONDED BIT(1) DEFAULT 0);"; //responded 1 means peer
            
            stm.execute(userInfoTable);
            stm.execute(songs);
            stm.execute(anonymousDownloads);
            stm.execute(shareSongsRequest);

            assert meta.getColumns(null, null, "USER_INFO", null).next();
        
        } catch(Exception e) { 
            e.printStackTrace();  
        }
    }

    // USER_INFO table API
    
    public int signUp(String name, String pass, String email) 
    { 
        try 
        {
            int peerId = getAuthID(pass, email);
            if(peerId > SERVER_ERROR) return peerId;
            stm.execute("insert into USER_INFO (NAME, PASS, EMAIL, DOWNLOADS, NONCE) values ('"
                +name+"', '"+pass+"', '"+email+"', 0, 0);");
            l.info("signed up name ", name, " pass ", pass, " email ", email);
            return getAuthID(pass, email);
        } catch(Exception e) {
            e.printStackTrace();
            try {
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
            return SERVER_ERROR;
        }      
    }

    public int getAuthID(String pass, String email) {
        try {
            ResultSet rs = stm.executeQuery("select ID from USER_INFO where PASS = '"+pass+"' AND EMAIL = '"+email+"';");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        } catch(Exception e) {
            l.info("auth Id failed ");
            e.printStackTrace(); 
            try {
                if(stm == null || stm.isClosed()) {
                    l.info("re-constructing con, stm");
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public int getAuthID(String pass, String email, int nonce) 
    {
        try
        {
            ResultSet rs = stm.executeQuery("select ID from USER_INFO where PASS = '"+pass+"' AND EMAIL = '"+email+"' AND NONCE = "+nonce+";");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        } 
        catch(Exception e) 
        {
            l.info("auth Id failed ");
            e.printStackTrace(); 
            try 
            {
                if(stm == null || stm.isClosed()) 
                {
                    l.info("re-constructing con, stm");
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } 
            catch (Exception sql) 
            {
                sql.printStackTrace();
            }
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public int getTotalDownloads(int id) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to get downloads num"); 
            try {
                ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_INFO WHERE ID = "+id+";");
                if (rs.next()) return rs.getInt(1);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                   if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return SERVER_ERROR; //fail flag for testcase
    }

    public int getNonce(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT NONCE FROM USER_INFO WHERE ID = "+id+";");
            if(rs.next()) return rs.getInt(1);
        } catch(Exception e) {
            e.printStackTrace();
            try {
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public byte[] getImage(int id) {
        byte[] picBytes = new byte[0];
        try {
            ResultSet rs = stm.executeQuery("SELECT IMAGE FROM USER_INFO WHERE ID = "+id+";");
            if(rs.next()) {
                Blob pic = rs.getBlob(1);
                if(pic != null) 
                    picBytes = pic.getBytes(1, (int)pic.length());
                else picBytes = new byte[0];
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
        }
        return picBytes;
    }
    
    public void addImage(int id, byte[] picBytes) {
        String UPDATE_IMAGE = "UPDATE USER_INFO SET IMAGE = ? WHERE ID = ?";
        try {
            PreparedStatement ps = con.prepareStatement(UPDATE_IMAGE);
            ps.setBytes(1, picBytes);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
        }
    }

    public int incrementUserDownloadsNum(int id) {
        int downloads = 0;
        boolean failed = true;
        while(failed) {
            l.info("attempting to increment downloads num"); 
            try {
                ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_INFO WHERE ID = "+id+";");
                if (rs.next()) downloads = rs.getInt(1);
                stm.execute("UPDATE USER_INFO SET DOWNLOADS = "+(++downloads)+" WHERE ID = "+id+";");
                l.info("downloads for userId ", id, " = ", downloads);
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                   if(stm == null || stm.isClosed()) {
                    Class.forName(DRIVER);
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return downloads;
    }

    public byte[] getPeersInfoById (int id) 
    {
        String query = "SELECT NAME, EMAIL, IMAGE, ID FROM USER_INFO INNER JOIN SHARE_SONGS_REQUEST ON ((USER_INFO.ID=SHARE_SONGS_REQUEST.RECEIVER_ID AND SENDER_ID = "
        +id+") OR (USER_INFO.ID=SHARE_SONGS_REQUEST.SENDER_ID AND SHARE_SONGS_REQUEST.RECEIVER_ID = "+id+")) AND USER_INFO.ID!="+id+" AND RESPONDED = 1;";
        return getPeersInfo(id, query, new int[] {FALSE, FALSE, FALSE, FALSE}, true);
    }

    public byte[] searchByEmail (int id, String email) 
    {
        String query = "SELECT NAME, IMAGE, ID FROM USER_INFO WHERE EMAIL = '"+email+"' AND ID != "+id+";";
        return getPeersInfo(id, query, new int[] {FALSE, TRUE, FALSE, FALSE}, false);
    }

    public byte[] searchByName (int id, String query, int start, int len) 
    {
        String httpQuery = "SELECT NAME, IMAGE, ID FROM USER_INFO WHERE NAME >= '"+query+"' ORDER BY NAME ASC LIMIT "+start+", "+len+" AND ID != "+id+";";
        return getPeersInfo(id, httpQuery, new int[]{FALSE, TRUE, FALSE, FALSE}, true);
    }

    // SHARE_SONGS_REQUEST Table API
    // TODO check sent, responded preciously
    public boolean hasNewPeerRequests (int id) 
    {
        try 
        {
            ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SHARE_SONGS_REQUEST WHERE RECEIVER_ID = "+id
                +" AND SENT = "+0+";");
            if(rs.next()) return rs.getInt(1) > 0;
        } 
        catch (Exception s) 
        {
            s.printStackTrace();
            try 
            {
                if(stm == null || stm.isClosed()) 
                {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
        }
        return false;
    }

    public byte[] getListOfRequests(int id, boolean newRequests) {
        String httpQuery = "SELECT NAME, EMAIL, IMAGE, ID FROM USER_INFO INNER JOIN SHARE_SONGS_REQUEST ON USER_INFO.ID=SHARE_SONGS_REQUEST.SENDER_ID AND RECEIVER_ID = "+id+" AND SENT = "+((newRequests)?"0":"1")+";";
        return getPeersInfo(id, httpQuery, new int[]{FALSE, FALSE, FALSE, FALSE}, true);
    }

    public boolean hasUnackRequests(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SHARE_SONGS_REQUEST WHERE SENDER_ID = "+id+" AND RESPONDED = 1 AND RECEIVER_ACK = 0;");
            if(rs.next()) return rs.getInt(1) > 0;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } catch (Exception sql) {
                sql.printStackTrace();
            }
        }
        return false;
    }

    public byte[] getListOfUnAckRequests(int id) {
        String httpQuery = "SELECT NAME, EMAIL, IMAGE, ID FROM USER_INFO INNER JOIN SHARE_SONGS_REQUEST ON USER_INFO.ID=SHARE_SONGS_REQUEST.RECEIVER_ID AND SENDER_ID = "+id+" AND RECEIVER_ACK = 0 AND RESPONDED = 1;";
        return getPeersInfo(id, httpQuery, new int[] {FALSE, FALSE, FALSE, FALSE}, true);
    }

    public byte[] getPeersInfo(int id, String query, int[] miss, boolean addCount) 
    {
        Blob image = null;
        String tmp = null;
        int count = 0;
        int col = 0;
        byte[] bytes = new byte[0];
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
        {
            System.out.println("getPeersQuery: "+query);
            ResultSet rs = stm.executeQuery(query);
            while(rs.next()) 
            {
                if (miss[0] == FALSE) 
                {
                    tmp = rs.getString(++col);
                    l.info("name size "+tmp.length()+", "+tmp);
                    Utils.writeToStream(tmp.getBytes(), stream);
                }
                if (miss[1] == FALSE)
                {
                    tmp = rs.getString(++col);
                    l.info("email size "+tmp.length()+", "+tmp);
                    Utils.writeToStream(tmp.getBytes(), stream);
                }
                if (miss[2] == FALSE)
                {
                    image = rs.getBlob(++col);
                    byte[] imageBytes; 
                    if (image  == null) imageBytes = new byte[0];
                    else imageBytes = image.getBytes(1, (int)image.length()); 
                    l.info("image size "+imageBytes.length);
                    Utils.writeToStream(imageBytes, stream);
                }
                if (miss[3] == FALSE)
                {
                    byte[] idBytes = Utils.publicKeyEncryption(rs.getInt(++col));
                    l.info("id size "+idBytes.length);
                    Utils.writeToStream(idBytes, stream);
                }
                col=0;
                count++;
            }
            bytes = stream.toByteArray();
        }
        catch(Exception e)
        {
            e.printStackTrace(); 
            try 
            {
                if(stm == null || stm.isClosed())
                {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            } 
            catch (Exception sql) 
            {
                sql.printStackTrace();
            }
        }

        return  ((addCount)?Utils.mergeBytes(Utils.intToBytes(count), bytes):bytes);
    }

    public boolean setRequestAck (int sender) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to set Request Ack");
            try {
                stm.execute("UPDATE SHARE_SONGS_REQUEST SET RECEIVER_ACK = 1 WHERE SENDER_ID = "+sender+";");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    public boolean markRequestSent (int sender, int receiver) {
        boolean failed = true;
        while(failed) 
        {
            l.info("attempting to mark request as sent with sender id "+sender+", receiver id "+receiver);
            try 
            {
                stm.execute("UPDATE SHARE_SONGS_REQUEST SET SENT = 1 WHERE RECEIVER_ID = "+receiver+" AND SENDER_ID = "+sender+";");
                failed = false;
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                try 
                {
                    if(stm == null || stm.isClosed()) 
                    {
                        con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                        stm = con.createStatement();
                    }
                } 
                catch (Exception sql) 
                {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    public int incrementNonce (int id) {
        int cNonce = getNonce(id)+1;
        boolean failed = true;
        l.info("incrementing nonce for id ", id, " to cNonc ", cNonce);
        while (failed) {
            l.info("attempting to increment nonce");
            try {
                stm.execute("UPDATE USER_INFO SET NONCE = "+(cNonce)+" WHERE ID = "+id+";");
                failed = false;
                return cNonce;
            } catch(Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return SERVER_ERROR;
    }


    public boolean checkRelation(int id1, int id2) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to check relation");
            try {
                ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SHARE_SONGS_REQUEST WHERE (SENDER_ID = "+id1+" AND RECEIVER_ID = "+id2+") OR (SENDER_ID = "+id2+" AND RECEIVER_ID = "+id1+");");
                if(rs.next()) return rs.getInt(1) > 0;
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    public boolean endRelation (int id1, int id2) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to end relation");
            try {
                if(checkRelation(id1, id2))
                    stm.execute("DELETE FROM SHARE_SONGS_REQUEST WHERE (SENDER_ID = "
                        +id1+" AND RECEIVER_ID = "+id2+") OR (SENDER_ID = "+id2
                        +" AND RECEIVER_ID = "+id1+");");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }
    
    public boolean addRelation (int sender, int receiver) 
    {
        if(sender == receiver) return false;

        boolean failed = true;
        while (failed) 
        {
            l.info("attempting to add Relation with sender id "+sender +" and receiver id "+receiver);
            try 
            {
                //if(!checkRelation(sender, receiver) && sender != receiver) 
                    stm.execute("INSERT INTO SHARE_SONGS_REQUEST (SENDER_ID, RECEIVER_ID) VALUES ( "
                        + sender +", "
                        + receiver+ ");");
                failed = false;
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                try 
                {
                    if(stm == null || stm.isClosed()) 
                    {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                    }
                } 
                catch (Exception sql) 
                {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    //set both states from client side by protocol
    public boolean setRequestState (int sender, int receiver, boolean responded) 
    {
        boolean failed = true;
        while(failed) 
        {
            l.info("attempting to request state");
            try {
                stm.execute("UPDATE SHARE_SONGS_REQUEST SET "+((responded==RESPONDED)?"RESPONDED":"SENT")+" = 1 WHERE RECEIVER_ID = "+receiver+" AND SENDER_ID = "+sender+";");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    // SONGS TABLE API
    public boolean insertSong (int senderId, int receiver, String urlId, String title, String message) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to insert song");
            try {
                ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SONGS WHERE URL = '"+urlId+"' AND RECEIVER_ID = "+receiver
                    +" AND SENDER_ID = "+senderId+" AND MESSAGE = '"+message+"';");
                if(rs.next()) {
                    if(rs.getInt(1) > 0) return true;  
                } 

                //TODO check song with the same receiver id, urlId
                stm.execute("INSERT INTO SONGS (RECEIVER_ID, URL, SONG_TITLE, REQUEST_DATE, MESSAGE, SENDER_ID) VALUES ("
                    + receiver + ", '"
                    + urlId + "', '"
                    + title + "', '"
                    + new java.sql.Timestamp(new java.util.Date().getTime())+"', '"
                    + message + "', "
                    + senderId+");");

                l.info("song url inserted with receiver id ", receiver, "urlId", urlId, "title", title);
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    public byte[] getSharedSongsInfo (int id, boolean newSongRequests) {
        //fix query
        l.info("getSharedSongsInfo id "+id+" newSongs "+newSongRequests);
        String query = "SELECT SONGS.ID, SONGS.URL, SONGS.SONG_TITLE, SONGS.REQUEST_DATE, SONGS.MESSAGE, USER_INFO.NAME, SONGS.SENDER_ID FROM SONGS INNER JOIN USER_INFO ON SONGS.SENDER_ID=USER_INFO.ID AND SONGS.RECEIVER_ID="
            +id+" AND SONGS.SENDER_ID>0 AND SONGS.SENT="+(newSongRequests?"0":"1")+";";
        l.info("query "+query);
        int count = 0;
        byte[] bytes = new byte[0];
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
        {
            ResultSet rs = stm.executeQuery(query);
            while(rs.next()) {
                id = rs.getInt(1);
                stream.write(Utils.intToBytes(id), 0, 4);

                bytes = rs.getString(2).getBytes();
                l.info("url size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);

                bytes = rs.getString(3).getBytes();
                l.info("title size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);
                
                bytes =  Utils.longToBytes(rs.getTimestamp(4).getTime());
                l.info("date size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);

                bytes = rs.getString(5).getBytes();
                l.info("message size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);

                bytes = rs.getString(6).getBytes();
                l.info("name size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);
               
                bytes = Utils.publicKeyEncryption(rs.getInt(7)); 
                l.info("sender id size "+bytes.length);
                l.info("in hex "+Utils.bytesToHex(bytes));
                Utils.writeToStream(bytes, stream);
                
                count++;
            }
            bytes = stream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        bytes = Utils.mergeBytes(Utils.intToBytes(count), bytes);
        l.info("in hex "+Utils.bytesToHex(bytes));
        return bytes;
    }
    
    public String[] getLocalSongsInfo(int id) {
        //should return as string, first record url, title will be used to download song
        //TODO update listener 
        ArrayList<String> result = new ArrayList<String>();
        try {
            ResultSet rs = stm.executeQuery("SELECT URL, SONG_TITLE, ID FROM SONGS WHERE RECEIVER_ID = "+id+" AND SENDER_ID=0;");
            while(rs.next()) {
                result.add (rs.getString(1)
                    + FORWARDSLASH_SPLITER+rs.getString(2)
                    + FORWARDSLASH_SPLITER+rs.getInt(3)+"\n");
            } 
        } catch (Exception sql) {
            sql.printStackTrace();
        }
        l.info("gongsInfo in form(songId/songTitle/sender_id newLine) fetched, num of songs added "+result.size());
        return result.toArray(new String[result.size()]);
    }

    public boolean markSongDownloadable(int id) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to update senderId to native");
            try {
                stm.execute("UPDATE SONGS SET SENDER_ID=0 WHERE ID="+id+";");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    public boolean hasNewSongs(int receiver) {
        try {
            ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SONGS WHERE RECEIVER_ID = "+receiver+" AND SENDER_ID > 0 AND SENT = 0;");
            if(rs.next()) return rs.getInt(1) > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // set from client side for integrity
    public boolean markSongSent (int sender, int receiver, String url) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to mark song as sent");
            try {
                stm.execute("UPDATE SONGS SET SENT = 1 WHERE RECEIVER_ID = "+receiver+" AND SENDER_ID = "+sender+" AND URL = '"+url+"';");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

    /*public void removeSong(int id, String url) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to remove song with receiver id and songUrl");
            try {
                stm.execute("DELETE FROM SONGS WHERE RECEIVER_ID = "+id+" AND URL = '"+url+"';");
                l.info("removeSong, song removed with user id = "+id);
                failed = false;
            } catch (Exception sql) {
                sql.printStackTrace();
                if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
            }
        }
    }*/

    public boolean removeSong(int id) {
        boolean failed = true;
        while(failed) {
            l.info("attempting to remove song with databaseId");
            try {
                stm.execute("DELETE FROM SONGS WHERE ID = "+id+";");
                l.info("removeSong, song removed with database id = "+id);
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }   
        }
        return !failed;
    }

    public java.util.Date getSongDate(int id) {
        //DATE-LOCATION PROBLEM
        try {
            ResultSet rs = stm.executeQuery("SELECT REQUEST_DATE FROM SONGS WHERE RECEIVER_ID = "+id+";");
            if(rs.next()) return new java.util.Date(rs.getDate(1).getTime());
        } catch (Exception sql) {
            sql.printStackTrace();
        }
        return null;
    }
    
    public boolean addAnonymousDownload() {
        boolean failed = true;
        while(failed) {
            l.info("attempting to add anonymousDownload");
            try {
                stm.execute("INSERT INTO ANONYMOUS_DOWNLOADS (DOWNLOAD_DATE) VALUES ('"+new java.sql.Timestamp(new java.util.Date().getTime())+"');");
                failed = false;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if(stm == null || stm.isClosed()) {
                    con = DriverManager.getConnection(SFP_DATABASE_URL, NAME, PASS);
                    stm = con.createStatement();
                }
                } catch (Exception sql) {
                    sql.printStackTrace();
                }
            }
        }
        return !failed;
    }

}
