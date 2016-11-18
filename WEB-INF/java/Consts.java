public interface Consts {
	//database consts
    String DATABASE_URL = "jdbc:mysql://localhost/";
    String SFP_DATABASE_URL = "jdbc:mysql://localhost/sfp";
	int PORT = 1234;
    String DRIVER = "com.mysql.jdbc.Driver";
    String NAME = "root";
    String PASS = "Princ5195";
    int SERVER_ERROR = -1;
    //downloads consts
    String SONGS_PATH = Consts.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        +"/../../../songs/";   
    String UTF8 = "UTF-8";
    int SIX_MONTH_SEC = 15552000;
    int USER_NOT_FOUND = 0;
    int MAX_BACK_LOG = 100;
    int W8_INTERVAL = 60000;
    byte[] HAS_SONG_FLAG = {1};
    byte[] HAS_NO_SONG_FLAG = {0};
    
}