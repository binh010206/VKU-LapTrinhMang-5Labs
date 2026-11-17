import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class FTPServerApp extends JFrame {
    private final FTPDatabase database;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isRunning = false;
    private File rootDir;

    private JLabel lblStatus;
    private JLabel lblLanIp;
    private JTextField txtPort;
    private JTextField txtRootDir;
    private JButton btnStartStop;
    private JTextArea txtLogs;
    private DefaultTableModel clientsModel;
    private DefaultTableModel usersModel;
    private DefaultTableModel logsModel;

    public FTPServerApp() {
        super("VKU FTP Server (RFC 959) - Hệ thống Quản trị & Máy chủ Tệp");
        this.database = new FTPDatabase();
        this.rootDir = new File("ftp_root").getAbsoluteFile();
        if (!rootDir.exists()) rootDir.mkdirs();

        initSampleFiles();
        initComponents();
        refreshDatabaseTable();
        startServer(); // Tự động khởi động khi mở ứng dụng
    }

    private void initSampleFiles() {
        try {
            File f1 = new File(rootDir, "chao_mung.txt");
            if (!f1.exists()) {
                java.nio.file.Files.writeString(f1.toPath(), "Chào mừng bạn đến với FTP Server Môn Lập Trình Mạng VKU!\nHỗ trợ đầy đủ lệnh RFC 959.");
            }
            File docsDir = new File(rootDir, "TaiLieu");
            if (!docsDir.exists()) docsDir.mkdirs();
            File f2 = new File(docsDir, "DeCuong_LTM.txt");
            if (!f2.exists()) {
                java.nio.file.Files.writeString(f2.toPath(), "Nội dung ôn thi thực hành môn Lập trình mạng: Socket TCP, UDP, FTP, Mail SMTP/POP3, Web, RMI.");
            }
        } catch (Exception ignored) {}
    }

    private void initComponents() {
        setSize(980, 680);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // Top Banner Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(240, 245, 255));

        JPanel ipPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        ipPanel.setOpaque(false);
        String lanIp = getLanIpAddress();
        lblLanIp = new JLabel("🌐 ĐỊA CHỈ IP LAN CỦA SERVER: " + lanIp);
        lblLanIp.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblLanIp.setForeground(new Color(0, 80, 180));

        JLabel lblSub = new JLabel("Máy Client kết nối đến Host: " + lanIp + " | Cổng mặc định: " + FTPConstants.DEFAULT_CONTROL_PORT);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(Color.DARK_GRAY);
        ipPanel.add(lblLanIp);
        ipPanel.add(lblSub);

        // Control buttons
        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        ctrlPanel.setOpaque(false);

        ctrlPanel.add(new JLabel("Port:"));
        txtPort = new JTextField(String.valueOf(FTPConstants.DEFAULT_CONTROL_PORT), 5);
        ctrlPanel.add(txtPort);

        lblStatus = new JLabel("● STOPPED", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatus.setForeground(Color.RED);
        ctrlPanel.add(lblStatus);

        btnStartStop = new JButton("Khởi động Server");
        btnStartStop.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnStartStop.setBackground(new Color(40, 167, 69));
        btnStartStop.setForeground(Color.WHITE);
        btnStartStop.setFocusPainted(false);
        btnStartStop.addActionListener(e -> toggleServer());
        ctrlPanel.add(btnStartStop);

        topPanel.add(ipPanel, BorderLayout.CENTER);
        topPanel.add(ctrlPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Tab 1: Nhật ký hoạt động (Logs)
        JPanel tabLogs = new JPanel(new BorderLayout(5, 5));
        tabLogs.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(25, 25, 25));
        txtLogs.setForeground(new Color(220, 220, 220));
        JScrollPane scrollLogs = new JScrollPane(txtLogs);
        tabLogs.add(scrollLogs, BorderLayout.CENTER);

        JPanel logBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClearLog = new JButton("Xóa màn hình nhật ký");
        btnClearLog.addActionListener(e -> txtLogs.setText(""));
        logBtnBar.add(btnClearLog);
        tabLogs.add(logBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("📜 Nhật ký Socket (RFC 959)", tabLogs);

        // Tab 2: Danh sách Client đang kết nối
        JPanel tabClients = new JPanel(new BorderLayout(5, 5));
        tabClients.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        clientsModel = new DefaultTableModel(new String[]{"Địa chỉ IP", "Tài khoản (USER)", "Thời gian kết nối"}, 0);
        JTable tableClients = new JTable(clientsModel);
        tableClients.setRowHeight(24);
        tabClients.add(new JScrollPane(tableClients), BorderLayout.CENTER);
        tabbedPane.addTab("👥 Client đang kết nối", tabClients);

        // Tab 3: Quản trị Database (Tài khoản & Lịch sử File)
        JPanel tabDb = new JPanel(new GridLayout(2, 1, 8, 8));
        tabDb.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Bảng Users
        JPanel panelUsers = new JPanel(new BorderLayout(5, 5));
        panelUsers.setBorder(BorderFactory.createTitledBorder("Cơ sở dữ liệu: Danh sách Tài khoản truy cập"));
        usersModel = new DefaultTableModel(new String[]{"Tài khoản", "Mật khẩu", "Quyền hạn", "Thư mục gốc", "Ngày tạo"}, 0);
        JTable tableUsers = new JTable(usersModel);
        tableUsers.setRowHeight(22);
        panelUsers.add(new JScrollPane(tableUsers), BorderLayout.CENTER);

        JPanel userBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnAddUser = new JButton("Thêm tài khoản");
        btnAddUser.addActionListener(e -> showAddUserDialog());
        JButton btnDelUser = new JButton("Xóa tài khoản đã chọn");
        btnDelUser.addActionListener(e -> {
            int row = tableUsers.getSelectedRow();
            if (row != -1) {
                String u = (String) usersModel.getValueAt(row, 0);
                if (database.removeUser(u)) {
                    refreshDatabaseTable();
                    JOptionPane.showMessageDialog(this, "Đã xóa tài khoản: " + u);
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản admin mặc định!");
                }
            }
        });
        userBtns.add(btnAddUser);
        userBtns.add(btnDelUser);
        panelUsers.add(userBtns, BorderLayout.SOUTH);
        tabDb.add(panelUsers);

        // Bảng Logs Truyền File
        JPanel panelTransfers = new JPanel(new BorderLayout(5, 5));
        panelTransfers.setBorder(BorderFactory.createTitledBorder("Cơ sở dữ liệu: Lịch sử Upload / Download / Hoạt động"));
        logsModel = new DefaultTableModel(new String[]{"ID", "Thời gian", "Người dùng", "IP Client", "Hành động", "Tệp / Đối tượng", "Dung lượng (Bytes)", "Trạng thái"}, 0);
        JTable tableLogs = new JTable(logsModel);
        tableLogs.setRowHeight(22);
        panelTransfers.add(new JScrollPane(tableLogs), BorderLayout.CENTER);

        JPanel transferBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefreshDb = new JButton("Làm mới bảng");
        btnRefreshDb.addActionListener(e -> refreshDatabaseTable());
        transferBtns.add(btnRefreshDb);
        panelTransfers.add(transferBtns, BorderLayout.SOUTH);
        tabDb.add(panelTransfers);

        tabbedPane.addTab("🗄️ Quản lý Cơ sở Dữ liệu (Database)", tabDb);

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom Root dir
        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        botPanel.setBorder(BorderFactory.createEtchedBorder());
        botPanel.add(new JLabel("Thư mục lưu trữ FTP Root:"));
        txtRootDir = new JTextField(rootDir.getAbsolutePath(), 50);
        txtRootDir.setEditable(false);
        botPanel.add(txtRootDir);
        add(botPanel, BorderLayout.SOUTH);
    }

    private void showAddUserDialog() {
        JTextField uField = new JTextField();
        JPasswordField pField = new JPasswordField();
        JComboBox<String> permBox = new JComboBox<>(new String[]{"READ_WRITE", "READ_ONLY"});

        Object[] message = {
                "Tên tài khoản:", uField,
                "Mật khẩu:", pField,
                "Quyền hạn:", permBox
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Thêm tài khoản FTP mới", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String u = uField.getText().trim();
            String p = new String(pField.getPassword()).trim();
            String perm = (String) permBox.getSelectedItem();
            if (!u.isEmpty() && !p.isEmpty()) {
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                if (database.addUser(new FTPDatabase.UserAccount(u, p, perm, "/", now))) {
                    refreshDatabaseTable();
                    logMessage("[DATABASE] Thêm thành công tài khoản: " + u + " (" + perm + ")");
                } else {
                    JOptionPane.showMessageDialog(this, "Tài khoản đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    public void refreshDatabaseTable() {
        SwingUtilities.invokeLater(() -> {
            usersModel.setRowCount(0);
            for (FTPDatabase.UserAccount u : database.getAllUsers()) {
                usersModel.addRow(new Object[]{u.getUsername(), "••••••••", u.getPermission(), u.getHomeDirectory(), u.getCreatedTime()});
            }

            logsModel.setRowCount(0);
            for (FTPDatabase.TransferLog l : database.getAllLogs()) {
                logsModel.addRow(new Object[]{l.getId(), l.getTimestamp(), l.getUsername(), l.getClientIp(), l.getAction(), l.getTarget(), l.getFileSize(), l.getStatus()});
            }
        });
    }

    public synchronized void logMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLogs.append("[" + time + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    public synchronized void addConnectedClient(String ip) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            clientsModel.addRow(new Object[]{ip, "(chưa xác thực)", time});
        });
    }

    public synchronized void updateClientUser(String ip, String user) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientsModel.getRowCount(); i++) {
                if (clientsModel.getValueAt(i, 0).equals(ip)) {
                    clientsModel.setValueAt(user, i, 1);
                    break;
                }
            }
        });
    }

    public synchronized void removeConnectedClient(String ip, String user) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientsModel.getRowCount(); i++) {
                if (clientsModel.getValueAt(i, 0).equals(ip)) {
                    clientsModel.removeRow(i);
                    break;
                }
            }
        });
    }

    private void toggleServer() {
        if (isRunning) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            isRunning = true;
            lblStatus.setText("● ONLINE (Port " + port + ")");
            lblStatus.setForeground(new Color(40, 167, 69));
            btnStartStop.setText("Dừng Server");
            btnStartStop.setBackground(new Color(220, 53, 69));
            txtPort.setEnabled(false);
            logMessage("=== VKU FTP SERVER ĐÃ KHỞI ĐỘNG TRÊN CỔNG " + port + " ===");

            serverThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        FTPClientHandler handler = new FTPClientHandler(clientSocket, rootDir, database, this);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        if (!isRunning) break;
                    }
                }
            });
            serverThread.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể mở cổng " + port + ": " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            logMessage("[LỖI] " + e.getMessage());
        }
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (serverThread != null) serverThread.interrupt();
        } catch (IOException ignored) {}
        lblStatus.setText("● STOPPED");
        lblStatus.setForeground(Color.RED);
        btnStartStop.setText("Khởi động Server");
        btnStartStop.setBackground(new Color(40, 167, 69));
        txtPort.setEnabled(true);
        clientsModel.setRowCount(0);
        logMessage("=== VKU FTP SERVER ĐÃ DỪNG ===");
    }

    private String getLanIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            new FTPServerApp().setVisible(true);
        });
    }
}
