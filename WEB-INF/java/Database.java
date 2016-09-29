import java.sql.*;
import java.util.*;

public class Database{
    private String URL = "jdbc:mysql://localhost/";
    private final String DRIVER = "com.mysql.jdbc.Driver";
    private final String NAME = "root";
    private final String PASS = "Princ5195";
    Connection con = null;
    Statement stm = null;
    public Database(){
        try{
            Class.forName(DRIVER);
            con = DriverManager.getConnection(URL, NAME, PASS);
            stm = con.createStatement();
            stm.execute("create database if not exists sfp;");
            URL += "sfp";
            con = DriverManager.getConnection(URL, NAME, PASS);
            stm = con.createStatement();
            DatabaseMetaData meta = con.getMetaData();
            ResultSet tables = meta.getColumns(null, null, "userinfo", null);
            String userInfoTable = "create table userinfo ( "
                + "ID INT PRIMARY KEY AUTO_INCREMENT, "
                + "NAME VARCHAR(10), "
                + "PASS VARCHAR(10), "
                + "IPNUMS TINYINT);";
            String userIPsTable = "craete table userips ( "
                + "ID INT, "
                + "IP VARCHAR(15), "
                + "ACTIVE BOOLEAN, " //BOOLEAN IS ALIAS FOR BIT(1)
                + "DES VARCHAR(255));";
            if(!tables.next()) {
                stm.execute(userInfoTable);
                stm.execute(userIPsTable);
            }
        }catch(Exception e) { e.printStackTrace();  }
    }
    public int insertIP(int id, String ip, String des){
        PreparedStatement ps = null;
        try{
            ps = con.prepareStatement("insert into userips (ID, IP, ACTIVE, DES) values(?, ?, ?, ?)");
            ps.setInt(1, id);
            ps.setString(2, ip);
            ps.setBoolean(3, true);
            ps.setString(4, des);
            ps.execute();
        }catch(Exception e) {
            e.printStackTrace();  
            return -1;
        }
        return 0;
    }
	// sign up from website.
    public int signUp(String name, String pass){ 
        try{//TODO limit num of signup per day.
            stm.execute("insert into userinfo (NAME, PASS) values ('"+name+"', '"+pass+"');");
        }catch(Exception e) {
            e.printStackTrace();  
            return -1;
        }
        return 0;
    }
    // sign up from android client.
    public int signUP(String name, String pass, String ip, String des){
        int id = getAuthID(name, pass);//TODO can i return id from insert statement?
        if(id >= 0){//user is registered from browser client
            insertIP(id, ip, des);
            return 2; //optimize to return inserted id.
        }
        try{//TODO limit num of signup per day.
            stm.execute("insert into userinfo (NAME, PASS, IPNUMS) values ('"+name+"', '"+pass+"', "+1+");");
        }catch(Exception e) {
            e.printStackTrace();  
            return -1;
        }
        insertIP(id, ip, des);
        return 0;
    }
    public int getAuthID(String name, String pass){
        try{
            ResultSet rs = stm.executeQuery("select ID from userinfo where NAME='"+name+"' AND PASS = '"+pass+"';");
            if(rs.next()){                
                return rs.getInt(1); //return 0 if null
	    }          
        }catch(Exception e) {
            e.printStackTrace(); 
            return -1;
        }
        return 0;
    }
    public int getIPsNum(int id){
        try{
            ResultSet rs = stm.executeQuery("select IPNUMS from userinfo where ID = (select ID from userips where userips.IP = "+id+");");
            rs.next();
            return rs.getInt(1); //return 0 if null
        }catch(Exception e) {
            e.printStackTrace();  
            return -1;
        }
    }
    public HashMap getUserActiveIPs(int id){
        try{
            HashMap<String, String> hm = new HashMap<String, String>();
            ResultSet rs = stm.executeQuery("select IP, DES from userips where ID = "+id+";");
            while(rs.next()){
                hm.put(rs.getString(1), rs.getString(2));
            }
            return hm;
        }catch(Exception e) { e.printStackTrace();  }
        return null;
    }
}