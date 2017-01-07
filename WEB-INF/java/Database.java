import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

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
                + "IMAGE BLOB DEFAULT 0, "
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
    
    public int signUp(String name, String pass, String email) { 
        try {
            stm.execute("insert into USER_INFO (NAME, PASS, EMAIL, DOWNLOADS, NONCE) values ('"
                +name+"', '"+pass+"', '"+email+"', 0, 0);");
            l.info("signed up name ", name, " pass ", pass, " email ", email);
            return getAuthID(pass, email);
        } catch(SQLException e) {
            e.printStackTrace();
            return SERVER_ERROR;
        }      
    }

    public int getAuthID(String pass, String email){
        try{
            ResultSet rs = stm.executeQuery("select ID from USER_INFO where PASS = '"+pass+"' AND EMAIL = '"+email+"';");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        }catch(SQLException e) {
            e.printStackTrace(); 
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public int getAuthID(String pass, String email, int nonce){
        try{
            ResultSet rs = stm.executeQuery("select ID from USER_INFO where PASS = '"+pass+"' AND EMAIL = '"+email+"' AND NONCE = "+nonce+";");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        }catch(SQLException e) {
            e.printStackTrace(); 
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public int getTotalDownloads(int id){
        try {
            ResultSet rs = stm.executeQuery("select DOWNLOADS from USER_INFO where ID = "+id+";");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        } catch(SQLException e) {
            e.printStackTrace(); 
            return SERVER_ERROR;
        }
        return USER_NOT_FOUND;
    }

    public int getNonce(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT NONCE FROM USER_INFO WHERE ID = "+id+";");
            if(rs.next()) return rs.getInt(1);
        } catch(SQLException e) {
            e.printStackTrace();
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
                picBytes = pic.getArray(1, (int)pic.length());
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void incrementUserDownloadsNum(int id) throws SQLException {
        int downloads = 0;
        ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_INFO WHERE ID = "+id+";");
        if (rs.next()) downloads = rs.getInt(1);
        else throw new SQLException();
        stm.execute("UPDATE USER_INFO SET DOWNLOADS = "+(++downloads)+" WHERE ID = "+id+";");
        l.info("downloads for userId ", id, " = ", downloads);
    }

    public byte[] getPeersInfoById (int id) {
        ByteBuffer bf = new ByteBuffer();
        Blob image = null;
        String tmp = null;
        int count;
        for(byte[] idArray : idsArray) {
            if(getAuthId(id)) {
                try {
                    ResultSet rs = stm.execute("SELECT NAME, EMAIL, BLOB, ID FROM USER_INFO WHERE ID = "+id+";");
                    while(rs.next()) {
                       tmp = rs.getString(1);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       tmp = rs.getString(2);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       image = rs.getBlob(3);
                       int len = image.length();
                       bf.put(Utils.intToBytes(len));
                       bf.put(image.getBytes(1, len));
                    
                       byte[] idBytes = publicKeyEncryption(rs.getInt(4));
                       bf.put(Utils.intToBytes(idBytes.length));
                       bf.put(idBytes);

                       count++;
                    }
                }
            } // else it won't work properly on clientSide, no way wrong id will be sent.
        }
        return  Utils.mergeBytes(Utils.intToBytes(count), bf.bytes);
    }

    public byte[] getPeerInfoById (int id) { // used below
        ByteBuffer bf = new ByteBuffer();
        Blob image = null;
        String tmp = null;
        for(byte[] idArray : idsArray) {
            if(getAuthId(id)) {
                try {
                    ResultSet rs = stm.execute("SELECT NAME, EMAIL, BLOB, ID FROM USER_INFO WHERE ID = "+id+";");
                    while(rs.next()) {
                       tmp = rs.getString(1);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       tmp = rs.getString(2);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       image = rs.getBlob(3);
                       int len = image.length();
                       bf.put(Utils.intToBytes(len));
                       bf.put(image.getBytes(1, len));
                    
                       byte[] idBytes = publicKeyEncryption(rs.getInt(4));
                       bf.put(Utils.intToBytes(idBytes.length));
                       bf.put(idBytes);
                    }
                }
            }
        }
        return  bf.bytes;
    }

    public byte[] searchByEmail (String email) {
        ByteBuffer bf = new ByteBuffer();
        Blob image = null;
        String tmp = null;
        for(byte[] idArray : idsArray) {
            int id = Utils.publicKeyDecryption(idArray);
            if(getAuthId(id)) {
                try {
                    ResultSet rs = stm.execute("SELECT NAME, BLOB, SENDER_ID FROM USER_INFO WHERE EMAIL = "+email+";");
                    while(rs.next()) {
                       tmp = rs.getString(1);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       image = rs.getBlob(2);
                       int len = image.length();
                       bf.put(Utils.intToBytes(len));
                       bf.put(image.getBytes(1, len));

                       byte[] idBytes = publicKeyEncryption(rs.getInt(3));
                       bf.put(Utils.intToBytes(idBytes.length));
                       bf.put(idBytes);

                       count++;
                    }
                }
            } // else it won't work properly on clientSide, no way wrong id will be sent.
        }
        return  bf.bytes;
    }

    public byte[] searchByName (String query, int start, boolean len) {
        ByteBuffer bf = new ByteBuffer();
        Blob image = null;
        String tmp = null;
        int count;
        for(byte[] idArray : idsArray) {
            int id = Utils.publicKeyDecryption(idArray);
            if(getAuthId(id)) {
                try {
                    ResultSet rs = stm.execute("SELECT NAME, BLOB, SENDER_ID FROM USER_INFO WHERE NAME >= "+query+" ORDER BY NAME ASC LIMIT "+start+", "+len+";");
                    while(rs.next()) {
                       tmp = rs.getString(1);
                       bf.put(Utils.intToBytes(tmp.length()));
                       bf.put(tmp.getBytes());

                       image = rs.getBlob(2);
                       int len = image.length();
                       bf.put(Utils.intToBytes(len));
                       bf.put(image.getBytes(1, len));

                       byte[] idBytes = publicKeyEncryption(rs.getInt(3));
                       bf.put(Utils.intToBytes(idBytes.length));
                       bf.put(idBytes);

                       count++;
                    }
                }
            } // else it won't work properly on clientSide, no way wrong id will be sent.
        }
        return  Utils.mergeBytes(Utils.intToBytes(count), bf.bytes);
    }

    // SHARE_SONGS_REQUEST Table API

    public boolean hasNewPeerRequests (int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM SHARE_SONGS_REQUEST WHERE ID = "+id
                +" AND SENT = "+0+";");
            if(rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException s) {
            s.printStackTrace();
        }
        return false;
    }

    public byte[] getListOfRequests(int id, boolean newRequests) {
        ByteBuffer bf = new ByteBuffer();
        try {
            ResultSet rs = stm.executeQuery("SELECT SENDER_ID FROM SHARE_SONGS_REQUEST WHERE (RECEIVER_ID = "+id+" AND "+((newRequests)?"RESPONDED":"SENT")+"= 0);");
            int count = 0;
            while(rs.next()) {
                int id = rs.getInt(1);
                bf.put(getPeerInfoById(id));
                count++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Utils.mergeBytes(Utils.intToBytes(count), bf.array());
    }

    public int incrementNonce (int id) {
        int cNonce = ++getNonce(id);
        l.info("incrementing nonce for id", id, "with current cNonce", cNonce);
        try {
            stm.execute("UPDATE NONCE SET NONCE = "+(cNonce)+" WHERE ID = "+id+";");
            return cNonce;
        } catch(SQLException e) {
            e.printStackTrace();
            return SERVER_ERROR
        }
        return USER_NOT_FOUND;
    }


    public boolean checkRelation(int id1, int id2) {
        try {
            ResultSet rs = stm.executeQuery("SELECT FROM SHARE_SONGS_REQUEST WHERE (SENDER_ID = "+id1+" AND RECEIVER_ID = "+id2+") OR (SENDER_ID = "+id2+" AND RECEIVER_ID = "+id1+");");
            if(rs.next()) return true
        } catch (SQLException e) {
            e.printStackTrace();
        }
        else return false;
    }

    public void endRelation (int id1, int id2) {
        try {
            if(checkRelation(id1, id2))
                stm.execute("DELETE FROM SHARE_SONGS_REQUEST WHERE (SENDER_ID = "
                    +id1+" AND RECEIVER_ID = "+id2+") OR (SENDER_ID = "+id2
                    +" AND RECEIVER_ID = "+id1+");");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void addRelation (int sender, int receiver) {
        try {
            if(!checkRelation(id1, id2)) 
                stm.execute("INSERT INTO SHARE_SONGS_REQUEST (SENDER_ID, RECEIVER_ID) VALUES ( "
                    + sender +", "
                    + receiver+ ");");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //set both states from client side by protocol
    public void setRequestState (byte[] idsArray, boolean responded) {
            try {
                int id = Utils.bytesToBigEndianInt(idsArray); 
                stm.execute("UPDATE SHARE_SONGS_REQUES SET "+((responded==RESPONDED)?"RESPONDED":"SENT")+" = 1 WHERE ID="+id+";");
            } catch (SQLException e) {
                e.printStackTrace();
            }
    }

    // SONGS TABLE API
    public void insertSong (int id, String urlId, String title, String message, int sender_id) {
        try {
            int count = 0;
            for(String info: getSongsInfo(id)) {
                count++;
                if (count%2 == 0) continue;
                if(info.equals(urlId)) return;
            }
            //TODO check song with the same id, urlId
            stm.execute("INSERT INTO SONGS (RECEIVER_ID, URL, SONG_TITLE, REQUEST_DATE, MESSAGE, SENDER_ID) VALUES ("
                + id + ", '"
                + urlId + "', '"
                + title + "', '"
                + new java.sql.Timestamp(new java.util.Date().getTime())+"', '"
                + message + "', "
                + sender_id+");");
            l.info("song url inserted with id ", id, "urlId", urlId, "title", title);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
    }

    public byte[] getSharedSongsInfo (int id, boolean newSongRequests) {
        ByteBuffer bf = new ByteBuffer();
        String newSongs = "SELECT ID, URL, SONG_TITLE, REQUEST_DATE, MESSAGE, (SELECT NAME FROM USER_INFO WHERE ID = "+id+"), SENDER_ID  FROM SONGS WHERE RECEIVER_ID="+id+" AND SENDER_ID>0 AND SENT=0;";
        String all = "SELECT ID, URL, SONG_TITLE, REQUEST_DATE, MESSAGE, (SELECT NAME FROM USER_INFO WHERE ID = "+id+"), SENDER_ID  FROM SONGS WHERE RECEIVER_ID="+id+" AND SENDER_ID>0;";
        try {
            ResultSet rs = stm.execute(newSongRequests?newSongs:all);
            int count = 0;
            while(rs.next()) {
                int id = rs.getInt(1);
                bf.put(Utils.intToBytes(id));

                bytes = rs.getString(2);
                bf.put(Utils.intToBytes(bytes.length));
                bf.put(bytes);

                bytes = rs.getString(3);
                bf.put(Utils.intToBytes(bytes.length));
                bf.put(bytes);
                
                bytes =  Utils.longToBytes(rs.getTimestamp(4).getTime());
                bf.put(Utils.intToBytes(bytes.length));
                bf.put(bytes);

                bytes = rs.getString(5).getBytes();
                bf.put(Utils.intToBytes(bytes.length));
                bf.put(bytes);

                bytes = rs.getString(6).getBytes();
                bf.put(Utils.intToBytes(bytes.length));
                bf.put(bytes);

                bytes = Utils.publicKeyEncryption(rs.getInt(7));
                bf.put(bytes.length);
                bf.put(bytes);
                
                count++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Utils.mergeBytes(Utils.intToBytes(count), bf.bytes);
    }
    
    public String[] RECEIVER_ID(int id) {
        //should return as string, first record url, title will be used to download song
        //TODO update listener 
        ArrayList<String> result = new ArrayList<String>();
        try {
            ResultSet rs = stm.executeQuery("SELECT URL, SONG_TITLE, ID FROM SONGS WHERE RECEIVER_ID = "+id+" AND SENDER_ID=0;");
            while(rs.next()) {
                result.add (rs.getString(1)
                    + FORWARDSLASH_SPLITER+rs.getString(2)
                    + FORWARDSLASH_SPLITER+Utils.intToBytes(rs.getInt(3))+"\n");
            } 
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        l.info("gongsInfo in form(songId/songTitle/sender_id newLine) fetched, num of songs added "+result.size());
        return result.toArray(new String[result.size()]);
    }

    public void updateSenderIdtoNative(int id) {
        try {
            ResultSet rs = stm.execute("UPDATE SONGS SET SENDER_ID=0 WHERE ID="+id+";");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean hasNewSongs(int receiver) {
        try {
            ResultSet rs = stm.execute("SELECT COUNT(*) FROM SONGS WHERE RECEIVER_ID = "+receiver+";");
            if(rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // set from client side for integrity
    public void setSongSent (int id) {
            try {
                int id = Utils.bytesToBigEndianInt(idsArray); 
                stm.execute("UPDATE SONGS SET SENT = 1 WHERE ID="+id+";");
            } catch (SQLException e) {
                e.printStackTrace();
            }
    }

    public void removeSong(int id, String url) {
        try {
            stm.execute("DELETE FROM SONGS WHERE RECEIVER_ID = "+id+" AND URL = '"+url+"';");
            l.info("removeSong, song removed with user id = "+id);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
    }

    public void removeSong(int id) {
        try {
            stm.execute("DELETE FROM SONGS WHERE ID = "+id+";");
            l.info("removeSong, song removed with database id = "+id);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }   
    }

    public java.util.Date getSongDate(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT REQUEST_DATE FROM SONGS WHERE RECEIVER_ID = "+id+";");
            if(rs.next()) return new java.util.Date(rs.getDate(1).getTime());
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }
    
    public void addAnonymousDownload() throws SQLException {
        stm.execute("INSERT INTO ANONYMOUS_DOWNLOADS "+new java.sql.Timestamp(new java.util.Date().getTime())+";");
    }
}
