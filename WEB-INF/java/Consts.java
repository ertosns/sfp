public interface Consts {
	//database consts
    String DATABASE_URL = "jdbc:mysql://localhost/";
    String SFP_DATABASE_URL = "jdbc:mysql://localhost/sfp";
	int PORT = 65123;
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
    int W8_INTERVAL = 30000;
    byte[] HAS_SONG_FLAG = {1};
    byte[] HAS_NO_SONG_FLAG = {0};

    int USER_ALREADY_FOUND_CODE = 201;
    int BAD_AUTH_CODE = 202;
    int CONNECTIVITY_ERR_CODE = 203;
    int SERVER_ERR_CODE = 500;
    int SUCCESS_CODE = 200;
    String ID_SPLITER = ",";
    int TITLE_SIZE = 30;
    String CHARS_CHARSET = "ISO-8859-1";
    String FORWARDSLASH_SPLITER = "/";
}