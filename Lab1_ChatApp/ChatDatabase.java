import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatDatabase {
    private static final String HISTORY_FILE = "chat_history.csv";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Cấu hình CSDL PostgreSQL (ưu tiên) và MySQL XAMPP
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/lab8_server1";
    private static final String PG_USER = "lab8_server1";
    private static final String PG_PASS = "Lab8_e03980b458a7046436b168e426cee1aa5f2db97cf615811f";

    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/vku_network_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "";

    public static class HistoryEntry {
        public int id;
        public String time;
        public String sender;
        public String receiver;
        public String type;
        public String content;
        public String fileName;
        public long fileSize;

        public HistoryEntry(int id, String time, String sender, String receiver, String type, String content, String fileName, long fileSize) {
            this.id = id;
            this.time = time;
            this.sender = sender;
            this.receiver = receiver;
            this.type = type;
            this.content = content;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }

    private final List<HistoryEntry> history = new ArrayList<>();
    private int nextId = 1;

    public ChatDatabase() {
        loadHistory();
    }

    public synchronized void saveMessage(ChatMessage msg) {
        String now = LocalDateTime.now().format(TIME_FMT);
        String fName = msg.getFileName() != null ? msg.getFileName() : "";
        HistoryEntry entry = new HistoryEntry(nextId++, now, msg.getSender(), msg.getReceiver(),
                msg.getType().name(), msg.getContent(), fName, msg.getFileSize());
        history.add(0, entry);

        boolean exists = new File(HISTORY_FILE).exists();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(HISTORY_FILE, true), StandardCharsets.UTF_8))) {
            if (!exists) {
                writer.println("id,time,sender,receiver,type,content,fileName,fileSize");
            }
            writer.printf("%d,%s,%s,%s,%s,%s,%s,%d%n",
                    entry.id,
                    escape(entry.time),
                    escape(entry.sender),
                    escape(entry.receiver),
                    escape(entry.type),
                    escape(entry.content),
                    escape(entry.fileName),
                    entry.fileSize);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Lưu vào CSDL PostgreSQL và MySQL
        new Thread(() -> {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO chat_messages (time, sender, receiver, type, content, file_name, file_size) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, entry.time);
                    ps.setString(2, entry.sender);
                    ps.setString(3, entry.receiver);
                    ps.setString(4, entry.type);
                    ps.setString(5, entry.content);
                    ps.setString(6, entry.fileName);
                    ps.setLong(7, entry.fileSize);
                    ps.executeUpdate();
                }
            } catch (Throwable ignored) {}

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO chat_messages (time, sender, receiver, type, content, file_name, file_size) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, entry.time);
                    ps.setString(2, entry.sender);
                    ps.setString(3, entry.receiver);
                    ps.setString(4, entry.type);
                    ps.setString(5, entry.content);
                    ps.setString(6, entry.fileName);
                    ps.setLong(7, entry.fileSize);
                    ps.executeUpdate();
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    public synchronized List<HistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }

    private synchronized void loadHistory() {
        // 1. Thử nạp từ PostgreSQL
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, time, sender, receiver, type, content, file_name, file_size FROM chat_messages ORDER BY id DESC")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id >= nextId) nextId = id + 1;
                    history.add(new HistoryEntry(
                            id,
                            rs.getString("time"),
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("type"),
                            rs.getString("content"),
                            rs.getString("file_name"),
                            rs.getLong("file_size")
                    ));
                }
            }
        } catch (Throwable ignored) {}

        if (!history.isEmpty()) return;

        // 2. Thử nạp từ MySQL
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, time, sender, receiver, type, content, file_name, file_size FROM chat_messages ORDER BY id DESC")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id >= nextId) nextId = id + 1;
                    history.add(new HistoryEntry(
                            id,
                            rs.getString("time"),
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("type"),
                            rs.getString("content"),
                            rs.getString("file_name"),
                            rs.getLong("file_size")
                    ));
                }
            }
        } catch (Throwable ignored) {}

        if (!history.isEmpty()) return;

        // 3. Fallback nạp từ file CSV
        File f = new File(HISTORY_FILE);
        if (!f.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 8) {
                    int id = Integer.parseInt(parts[0].trim());
                    if (id >= nextId) nextId = id + 1;
                    history.add(0, new HistoryEntry(id, parts[1].trim(), parts[2].trim(), parts[3].trim(),
                            parts[4].trim(), parts[5].trim(), parts[6].trim(), Long.parseLong(parts[7].trim())));
                }
            }
        } catch (Exception ignored) {}
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(",", " ").replace("\n", " ");
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("[VKU CHAT] Day la lop ChatDatabase (CSDL Tin nhan).");
        System.out.println("-> De chay ung dung Chat, vui long mo mot trong hai file sau:");
        System.out.println("   1. ChatServerApp.java (May chu Chat Server)");
        System.out.println("   2. ChatClientApp.java (Giao dien nguoi dung Client)");
        System.out.println("=================================================");
    }
}
