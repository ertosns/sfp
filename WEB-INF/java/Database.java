import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

/* application protocol
 * SERVER_ERROR  :if method return -1 it's database error
 */

// TODO check client errors, 
// TODO check all TODO lines embeded between code lines.
// TODO depending on how i keep track of user mobile ip you need to update device ip.
// TODO (fix) log doesn't work!!

public class Database implements Consts {
    Connection con = null;
    Statement stm = null;
    Logger l = Logger.getLogger(this.toString());
    Handler handler = null;

    public Database() {
        handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        l.setUseParentHandlers(false);
        l.addHandler(handler);
        l.setLevel(Level.INFO);
        
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
            String userInfoTable = "CREATE TABLE USER_INFO ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "NAME VARCHAR(10) NOT NULL, "
                + "PASS VARCHAR(10) NOT NULL, "
                + "EMAIL VARCHAR(300) NOT NULL, " //TODO what is max len of e-mail
                + "DOWNLOADS INT NOT NULL);";
            String anonymousDownloads = "CREATE TABLE ANONYMOUS_DOWNLOADS ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "DOWNLOAD_DATE DATE NOT NULL);";
            String songs = "CREATE TABLE SONGS (ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "USER_ID INT NOT NULL, " //TODO does quering hashed id instead of id(int) response faster considering decoding overhead.
                + "URL VARCHAR(50) NOT NULL, "
                + "DATE DATE NOT NULL);";
        
            if (!userInfoTableRS.next()) {
                stm.execute(anonymousDownloads);
                stm.execute(songs);
                stm.execute(userInfoTable);
                l.info("Database, all tables are created");
            }

            assert meta.getColumns(null, null, "USER_INFO", null).next();
        
        } catch(Exception e) { 
            e.printStackTrace();  
        }
    }
    
    public int signUp(String name, String pass, String email) { 
        try {
            stm.execute("insert into USER_INFO (NAME, PASS, EMAIL, DOWNLOADS) values ('"
                +name+"', '"+pass+"', '"+email+"', 0);");
            l.info("signUp, signed up");
            return getAuthID(pass, email);
        } catch(SQLException e) {
            e.printStackTrace();
            return SERVER_ERROR;
        }      
    }

    public void insertSong(int id,  String urlId) {
        try {
            stm.execute("INSERT INTO SONGS (ID, URL, MILLIS) VALUES ("
                + id + ", "
                + urlId + ", "
                + new java.sql.Date(new java.util.Date().getTime())+");");
            l.info("insertSong, song url inserted with id = "+id);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
    }
    
    public void removeSong(int id) {
        try {
            stm.execute("DELETE FROM SONGS WHERE ID = "+id+";");
            l.info("removeSong, song removed with id = "+id);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
    }

    public String getSongUrlId(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT URL FROM SONGS WHERE ID = "+id+";");
            if(rs.next()) return rs.getString(1);
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        return null;
    }

    public java.util.Date getSongDate(int id) {
        try {
            ResultSet rs = stm.executeQuery("SELECT DATE FROM SONGS WHERE ID = "+id+";");
            if(rs.next()) return new java.util.Date(rs.getDate(1).getTime());
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        return null;
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

    //anonymous downloads

    
    public void incrementUserLocalDownloadsNum(int id) throws SQLException {
        int downloads = 0;
        ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_INFO WHERE ID = "+id+";");
        if (rs.next()) downloads = rs.getInt(1);
        else throw new SQLException();
        stm.execute("UPDATE USER_INFO SET DOWNLOADS = "+(++downloads)+" WHERE ID = "+id+";");
    }
    
    public void addAnonymousDownload() throws SQLException {
        stm.execute("INSERT INTO ANONYMOUS_DOWNLOADS "+new java.sql.Date(new java.util.Date().getTime())+";");
    }
}
