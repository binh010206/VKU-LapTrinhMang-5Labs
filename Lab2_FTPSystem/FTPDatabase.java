import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FTPDatabase {
    private static final String DB_FILE = "ftp_database.csv";
    private static final String LOG_FILE = "ftp_transfer_logs.csv";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Cấu hình CSDL PostgreSQL (ưu tiên) và MySQL XAMPP
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/lab8_server1";
    private static final String PG_USER = "lab8_server1";
    private static final String PG_PASS = "Lab8_e03980b458a7046436b168e426cee1aa5f2db97cf615811f";

    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/vku_network_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "";

    public static class UserAccount {
        private String username;
        private String password;
        private String permission; // "READ_WRITE" or "READ_ONLY"
        private String homeDirectory;
        private String createdTime;

        public UserAccount(String username, String password, String permission, String homeDirectory, String createdTime) {
            this.username = username;
            this.password = password;
            this.permission = permission;
            this.homeDirectory = homeDirectory;
            this.createdTime = createdTime;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getPermission() { return permission; }
        public String getHomeDirectory() { return homeDirectory; }
        public String getCreatedTime() { return createdTime; }

        public void setPassword(String password) { this.password = password; }
        public void setPermission(String permission) { this.permission = permission; }
    }

    public static class TransferLog {
        private int id;
        private String timestamp;
        private String username;
        private String clientIp;
        private String action; // UPLOAD, DOWNLOAD, DELETE, MKDIR, LOGIN
        private String target;
        private long fileSize; // bytes
        private String status; // SUCCESS, FAILED

        public TransferLog(int id, String timestamp, String username, String clientIp, String action, String target, long fileSize, String status) {
            this.id = id;
            this.timestamp = timestamp;
            this.username = username;
            this.clientIp = clientIp;
            this.action = action;
            this.target = target;
            this.fileSize = fileSize;
            this.status = status;
        }

        public int getId() { return id; }
        public String getTimestamp() { return timestamp; }
        public String getUsername() { return username; }
        public String getClientIp() { return clientIp; }
        public String getAction() { return action; }
        public String getTarget() { return target; }
        public long getFileSize() { return fileSize; }
        public String getStatus() { return status; }
    }

    private final Map<String, UserAccount> users = new LinkedHashMap<>();
    private final List<TransferLog> logs = new ArrayList<>();
    private int nextLogId = 1;

    public FTPDatabase() {
        loadUsers();
        loadLogs();
        if (users.isEmpty()) {
            initDefaultUsers();
        }
    }

    private void initDefaultUsers() {
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        addUser(new UserAccount("admin", "admin123", "READ_WRITE", "/", now));
        addUser(new UserAccount("student", "vku123", "READ_WRITE", "/", now));
        addUser(new UserAccount("anonymous", "guest", "READ_ONLY", "/", now));
    }

    public synchronized boolean authenticate(String username, String password) {
        UserAccount user = users.get(username.toLowerCase());
        if (user == null) return false;
        if ("anonymous".equalsIgnoreCase(username)) return true;
        return user.getPassword().equals(password);
    }

    public synchronized boolean hasWritePermission(String username) {
        UserAccount user = users.get(username.toLowerCase());
        if (user == null) return false;
        return "READ_WRITE".equalsIgnoreCase(user.getPermission());
    }

    public synchronized List<UserAccount> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public synchronized boolean addUser(UserAccount user) {
        if (users.containsKey(user.getUsername().toLowerCase())) {
            return false;
        }
        users.put(user.getUsername().toLowerCase(), user);
        saveUsers();
        return true;
    }

    public synchronized boolean removeUser(String username) {
        if ("admin".equalsIgnoreCase(username)) return false;
        if (users.remove(username.toLowerCase()) != null) {
            saveUsers();
            return true;
        }
        return false;
    }

    public synchronized void logAction(String username, String clientIp, String action, String target, long fileSize, String status) {
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        TransferLog log = new TransferLog(nextLogId++, now, username, clientIp, action, target, fileSize, status);
        logs.add(0, log); // Thêm lên đầu danh sách
        saveLog(log);
    }

    public synchronized List<TransferLog> getAllLogs() {
        return new ArrayList<>(logs);
    }

    private synchronized void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(DB_FILE), StandardCharsets.UTF_8))) {
            writer.println("username,password,permission,homeDirectory,createdTime");
            for (UserAccount u : users.values()) {
                writer.printf("%s,%s,%s,%s,%s%n",
                        escapeCsv(u.getUsername()),
                        escapeCsv(u.getPassword()),
                        escapeCsv(u.getPermission()),
                        escapeCsv(u.getHomeDirectory()),
                        escapeCsv(u.getCreatedTime()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Lưu đồng thời vào PostgreSQL và MySQL
        new Thread(() -> {
            for (UserAccount u : users.values()) {
                try {
                    Class.forName("org.postgresql.Driver");
                    try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                         PreparedStatement ps = conn.prepareStatement(
                                 "INSERT INTO ftp_users (username, password, permission, home_dir, created_at) VALUES (?, ?, ?, ?, ?) "
                               + "ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, permission = EXCLUDED.permission")) {
                        ps.setString(1, u.getUsername());
                        ps.setString(2, u.getPassword());
                        ps.setString(3, u.getPermission());
                        ps.setString(4, u.getHomeDirectory());
                        ps.setString(5, u.getCreatedTime());
                        ps.executeUpdate();
                    }
                } catch (Throwable ignored) {}

                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                         PreparedStatement ps = conn.prepareStatement(
                                 "INSERT INTO ftp_users (username, password, permission, home_dir, created_at) VALUES (?, ?, ?, ?, ?) "
                               + "ON DUPLICATE KEY UPDATE password = VALUES(password), permission = VALUES(permission)")) {
                        ps.setString(1, u.getUsername());
                        ps.setString(2, u.getPassword());
                        ps.setString(3, u.getPermission());
                        ps.setString(4, u.getHomeDirectory());
                        ps.setString(5, u.getCreatedTime());
                        ps.executeUpdate();
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private synchronized void loadUsers() {
        // 1. Thử nạp từ PostgreSQL
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, password, permission, home_dir, created_at FROM ftp_users")) {
                while (rs.next()) {
                    UserAccount u = new UserAccount(
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("permission"),
                            rs.getString("home_dir"),
                            rs.getString("created_at")
                    );
                    users.put(u.getUsername().toLowerCase(), u);
                }
            }
        } catch (Throwable ignored) {}

        if (!users.isEmpty()) return;

        // 2. Thử nạp từ MySQL
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, password, permission, home_dir, created_at FROM ftp_users")) {
                while (rs.next()) {
                    UserAccount u = new UserAccount(
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("permission"),
                            rs.getString("home_dir"),
                            rs.getString("created_at")
                    );
                    users.put(u.getUsername().toLowerCase(), u);
                }
            }
        } catch (Throwable ignored) {}

        if (!users.isEmpty()) return;

        // 3. Fallback nạp từ file CSV
        File file = new File(DB_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 5) {
                    UserAccount u = new UserAccount(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim());
                    users.put(u.getUsername().toLowerCase(), u);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveLog(TransferLog log) {
        boolean exists = new File(LOG_FILE).exists();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8))) {
            if (!exists) {
                writer.println("id,timestamp,username,clientIp,action,target,fileSize,status");
            }
            writer.printf("%d,%s,%s,%s,%s,%s,%d,%s%n",
                    log.getId(),
                    escapeCsv(log.getTimestamp()),
                    escapeCsv(log.getUsername()),
                    escapeCsv(log.getClientIp()),
                    escapeCsv(log.getAction()),
                    escapeCsv(log.getTarget()),
                    log.getFileSize(),
                    escapeCsv(log.getStatus()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Lưu vào PostgreSQL và MySQL
        new Thread(() -> {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO ftp_logs (timestamp, username, client_ip, action, target, file_size, status) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, log.getTimestamp());
                    ps.setString(2, log.getUsername());
                    ps.setString(3, log.getClientIp());
                    ps.setString(4, log.getAction());
                    ps.setString(5, log.getTarget());
                    ps.setLong(6, log.getFileSize());
                    ps.setString(7, log.getStatus());
                    ps.executeUpdate();
                }
            } catch (Throwable ignored) {}

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO ftp_logs (timestamp, username, client_ip, action, target, file_size, status) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, log.getTimestamp());
                    ps.setString(2, log.getUsername());
                    ps.setString(3, log.getClientIp());
                    ps.setString(4, log.getAction());
                    ps.setString(5, log.getTarget());
                    ps.setLong(6, log.getFileSize());
                    ps.setString(7, log.getStatus());
                    ps.executeUpdate();
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    private synchronized void loadLogs() {
        // 1. Thử nạp từ PostgreSQL
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, timestamp, username, client_ip, action, target, file_size, status FROM ftp_logs ORDER BY id DESC")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id >= nextLogId) nextLogId = id + 1;
                    TransferLog log = new TransferLog(
                            id,
                            rs.getString("timestamp"),
                            rs.getString("username"),
                            rs.getString("client_ip"),
                            rs.getString("action"),
                            rs.getString("target"),
                            rs.getLong("file_size"),
                            rs.getString("status")
                    );
                    logs.add(log);
                }
            }
        } catch (Throwable ignored) {}

        if (!logs.isEmpty()) return;

        // 2. Thử nạp từ MySQL
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, timestamp, username, client_ip, action, target, file_size, status FROM ftp_logs ORDER BY id DESC")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id >= nextLogId) nextLogId = id + 1;
                    TransferLog log = new TransferLog(
                            id,
                            rs.getString("timestamp"),
                            rs.getString("username"),
                            rs.getString("client_ip"),
                            rs.getString("action"),
                            rs.getString("target"),
                            rs.getLong("file_size"),
                            rs.getString("status")
                    );
                    logs.add(log);
                }
            }
        } catch (Throwable ignored) {}

        if (!logs.isEmpty()) return;

        // 3. Fallback nạp từ file CSV
        File file = new File(LOG_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 8) {
                    int id = Integer.parseInt(parts[0].trim());
                    if (id >= nextLogId) nextLogId = id + 1;
                    TransferLog log = new TransferLog(id, parts[1].trim(), parts[2].trim(), parts[3].trim(),
                            parts[4].trim(), parts[5].trim(), Long.parseLong(parts[6].trim()), parts[7].trim());
                    logs.add(0, log);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace(",", " ");
    }
}
