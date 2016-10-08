import java.sql.*;
import java.util.*;

/* application protocol
 * SERVER_ERROR  :if method return -1 it's database error
 */

// TODO check client errors, 
// TODO check all TODO lines embeded between code lines.
// TODO depending on how i keep track of user mobile ip you need to update device ip.

public class Database{
    private String URL = "jdbc:mysql://localhost/";
    private final String DRIVER = "com.mysql.jdbc.Driver";
    private final String NAME = "root";
    private final String PASS = "Princ5195";
    static final int MAX_DEVICES_NUM_REACHED = -3;
    static final int SERVER_ERROR = -1;
    Connection con = null;
    Statement stm = null;
    public Database() {
        try {
            Class.forName(DRIVER);
            con = DriverManager.getConnection(URL, NAME, PASS);
            stm = con.createStatement();
            stm.execute("create database if not exists sfp;");
            URL += "sfp";
            con = DriverManager.getConnection(URL, NAME, PASS);
            stm = con.createStatement();
            DatabaseMetaData meta = con.getMetaData();
            ResultSet tables = meta.getColumns(null, null, "USER_INFO", null);
            String userInfoTable = "CREATE TABLE USER_INFO ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "NAME VARCHAR(10) NOT NULL, "
                + "PASS VARCHAR(10) NOT NULL, "
                + "EMAIL VARCHAR(300) NOT NULL, " //TODO what is max len of e-mail
                + "DOWNLOADS INT NOT NULL, " // number of (local downloads + downloads of deleted devices)
                + "IPNUMS TINYINT NOT NULL);"; // MAX IS FOUR DEVICES PER USER
            String userIPsTable = "CREATE TABLE USER_DEVICES ( "
                + "UNIQUE_ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "ID INT, "//TODO what is that key name
                + "IP VARCHAR(15) NOT NULL, "
                + "DOWNLOADS INT NOT NULL, "  // number of downloads of each device
                + "ACTIVE BOOLEAN NOT NULL, "  //BOOLEAN IS ALIAS FOR BIT(1)
                + "DES VARCHAR(255) NOT NULL);"; //DES MUST BE UNIQUE FOR EACH DEVICE
            String anonymousDownloads = "CREATE TABLE ANONYMOUS_DOWNLOADS ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "DOWNLOAD_DATE DATE NOT NULL);";
            if (!tables.next()) {
                stm.execute(userInfoTable);
                stm.execute(userIPsTable);
                stm.execute(anonymousDownloads);
            }
        } catch(Exception e) { 
            e.printStackTrace();  
        }
    }
    // sign up from website.
    public int signUp(String name, String pass, String email) { 
        try {
            insertUser(name, pass, email, false);        
        } catch(Exception e) {
            e.printStackTrace();  
            return SERVER_ERROR;
        }
        return 0;
    }
    // sign up or post IP from mobile client.
    public int signUP(String name, String pass, String email, String ip, String des) {
        int res = 0;
        int id = getAuthID(name, pass, email);
        int numOfIPs = 0;
        if (id == SERVER_ERROR) return SERVER_ERROR;
        else if (id > 0){
            numOfIPs = getNumOfIps(name, pass, email);
            if (numOfIPs == 4) return MAX_DEVICES_NUM_REACHED;
            else numOfIPs++;
        }
        res = insertIP(id, ip, des);
        if (res == SERVER_ERROR) return SERVER_ERROR;
        try {
            if (id == 0) {
                res = insertUser(name, pass, email, true);
                if (res == SERVER_ERROR) return SERVER_ERROR;
            } else  stm.execute("UPDATE USER_INFO SET IPNUMS = "+numOfIPs+" WHERE ID = "+id+";");
        } catch (SQLException e) {
            e.printStackTrace();  
            return SERVER_ERROR;
        }
        return 0;
    }
    public int insertIP(int id, String ip, String des) {
        //TODO increment user numOfIPs
        PreparedStatement ps = null;
        try {
            ps = con.prepareStatement("INSERT INTO USER_DEVICES (ID, IP, ACTIVE, DES) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            ps.setString(2, ip);
            ps.setBoolean(3, true);
            ps.setString(4, des);
            ps.execute();
        } catch(Exception e) {
            e.printStackTrace();  
            return SERVER_ERROR;
        }
        return 0;
    }
    public int insertUser(String name, String pass, String email, boolean ip) {
        try {
            stm.execute("insert into USER_INFO (NAME, PASS, IPNUMS, EMAIL) values ('"
                +name+"', '"+pass+"', '"+email+"', "+(ip?1:0)+");");
        } catch(SQLException e) {
            e.printStackTrace();
            return SERVER_ERROR;
        }
        return 0;
    }
    
    public int getNumOfIps(String name, String pass, String email){
        try {
            ResultSet rs = stm.executeQuery("select IPNUMS FROM USER_INFO where EMAIL = '"
                +email+"' AND NAME = '"+name+"' AND PASS = '"+pass+"';");
            if (rs.next()) return rs.getInt(1);
            else return SERVER_ERROR; // that can't happen unless server error (storing user ipnums) occured
        } catch(SQLException e) {
            e.printStackTrace();
            return SERVER_ERROR;
        }
    }
    public int getAuthID(String name, String pass, String email){
        try{
            ResultSet rs = stm.executeQuery("select ID from USER_INFO where NAME='"+name+"' AND PASS = '"+pass+"' AND EMAIL = '"+email+"';");
            if(rs.next()) return rs.getInt(1); //return 0 if null
        }catch(SQLException e) {
            e.printStackTrace(); 
            return SERVER_ERROR;
        }
        return 0;
    }
    public ArrayList<String> getUserActiveIPs(int id){
        try {
            ArrayList<String> ips = new ArrayList<String>();
            ResultSet rs = stm.executeQuery("select IP from USER_DEVICES where ID = "+id+" and ACTIVE = TRUE;");
            while(rs.next()) {
                ips.add(rs.getString(1));
            }
            return ips;
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public String getDevicesInfo(int id) {
        int ips = 0;
        try {
            StringBuilder info = new StringBuilder();
            ResultSet rs = stm.executeQuery("select UNIQUEID, DES from USER_DEVICES where ID = "+id+" and ACTIVE = TRUE;");
            while(rs.next()) {
                info.append(","+rs.getString(1)+","+rs.getString(2));
                ips++;
            }
            info.insert(0, ips+"");
            return info.toString();
        } catch(Exception e) {
            e.printStackTrace();  
        }
        return null;
    }
    public void incrementDeviceDownloadsNum(int uniqueId) throws SQLException{
        int downloads = 0;
        ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_DEVICES WHERE UNIQUEID = "+uniqueId+";");
        if (rs.next()) downloads = rs.getInt(1);
        else throw new SQLException();
        stm.execute("UPDATE USER_DEVICES SET DOWNLOADS = "+(++downloads)+" WHERE UNIQUEID = "+uniqueId+";");
    }
    public void incrementUserLocalOrDeletedDownloadsNum(int id) throws SQLException {
        int downloads = 0;
        ResultSet rs = stm.executeQuery("SELECT DOWNLOADS FROM USER_INFO WHERE ID = "+id+";");
        if (rs.next()) downloads = rs.getInt(1);
        else throw new SQLException();
        stm.execute("UPDATE USER_INO SET DOWNLOADS = "+(++downloads)+" WHERE ID = "+id+";");
    }
    public void addAnonymousDownload() throws SQLException {
        stm.execute("INSERT INTO ANONYMOUS_DOWNLOADS "+new java.sql.Date(new java.util.Date().getTime())+";");
    }
    public void updateIpsActivity(int id, String[] uniqueids) throws SQLException{
        for (int i = 0; i < uniqueids.length; i++) {
            stm.execute("update upserips set ACTIVE = "
                +((Integer.parseInt(uniqueids[i])==0)?"FALSE":"TRUE")+ //-1 true 0 false
                " where UNIQUEID = "+uniqueids[++i]+";");
        }
    }
}