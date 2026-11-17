public class FTPConstants {
    public static final int DEFAULT_CONTROL_PORT = 2121;
    public static final String SERVER_NAME = "VKU-FTPServer-RFC959/1.0";
    
    // Status Codes RFC 959
    public static final int REQ_ACTION_INIT = 150;      // File status okay; about to open data connection.
    public static final int CMD_OK = 200;               // Command okay.
    public static final int SYSTEM_STATUS = 211;        // System status
    public static final int SYSTEM_TYPE = 215;          // NAME system type.
    public static final int SERVICE_READY = 220;        // Service ready for new user.
    public static final int CLOSING_CONTROL = 221;      // Service closing control connection.
    public static final int CLOSING_DATA = 226;         // Closing data connection. Requested file action successful.
    public static final int ENTERING_PASV = 227;        // Entering Passive Mode (h1,h2,h3,h4,p1,p2).
    public static final int LOGGED_IN = 230;            // User logged in, proceed.
    public static final int FILE_ACTION_OK = 250;       // Requested file action okay, completed.
    public static final int PATHNAME_CREATED = 257;     // "PATHNAME" created.
    public static final int NEED_PASSWORD = 331;        // User name okay, need password.
    public static final int NOT_LOGGED_IN = 530;        // Not logged in.
    public static final int ACTION_NOT_TAKEN = 550;     // Requested action not taken. File unavailable.
    public static final int SYNTAX_ERROR = 500;         // Syntax error, command unrecognized.
}
