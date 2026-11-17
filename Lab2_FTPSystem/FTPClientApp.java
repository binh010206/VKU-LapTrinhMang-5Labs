import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FTPClientApp extends JFrame {
    private final FTPClientEngine clientEngine;

    // Quick Connect Fields
    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JButton btnConnect;
    private JButton btnDisconnect;

    // Local Explorer
    private JTextField txtLocalPath;
    private JTable tableLocal;
    private DefaultTableModel modelLocal;
    private File currentLocalDir;

    // Remote Explorer
    private JTextField txtRemotePath;
    private JTable tableRemote;
    private DefaultTableModel modelRemote;

    // Log & Progress
    private JTextArea txtLogs;
    private JProgressBar progressBar;
    private JLabel lblStatus;

    public FTPClientApp() {
        super("VKU FTP Client - Giao diện Quản lý Tệp Đa năng (Phong cách FileZilla)");
        this.clientEngine = new FTPClientEngine((msg, isCommand) -> logMessage(msg, isCommand));
        this.currentLocalDir = new File(System.getProperty("user.home"));

        initComponents();
        loadLocalFiles(currentLocalDir);
    }

    private void initComponents() {
        setSize(1100, 720);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // 1. Quick Connect Bar (Top)
        JPanel quickBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        quickBar.setBackground(new Color(245, 245, 245));
        quickBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        quickBar.add(new JLabel("Host:"));
        txtHost = new JTextField("127.0.0.1", 12);
        quickBar.add(txtHost);

        quickBar.add(new JLabel("Port:"));
        txtPort = new JTextField("2121", 5);
        quickBar.add(txtPort);

        quickBar.add(new JLabel("Username:"));
        txtUser = new JTextField("admin", 10);
        quickBar.add(txtUser);

        quickBar.add(new JLabel("Password:"));
        txtPass = new JPasswordField("admin123", 10);
        quickBar.add(txtPass);

        btnConnect = new JButton("Kết nối nhanh");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(0, 120, 215));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> performConnect());
        quickBar.add(btnConnect);

        btnDisconnect = new JButton("Ngắt kết nối");
        btnDisconnect.setEnabled(false);
        btnDisconnect.addActionListener(e -> performDisconnect());
        quickBar.add(btnDisconnect);

        add(quickBar, BorderLayout.NORTH);

        // 2. Center Dual-Pane File Explorers
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // --- Left: Local Site ---
        JPanel localPanel = new JPanel(new BorderLayout(4, 4));
        localPanel.setBorder(BorderFactory.createTitledBorder("💻 Cục bộ (Local Site)"));

        JPanel localTop = new JPanel(new BorderLayout(4, 4));
        JButton btnLocalUp = new JButton("⬆ Lên thư mục cha");
        btnLocalUp.addActionListener(e -> {
            File parent = currentLocalDir.getParentFile();
            if (parent != null) {
                loadLocalFiles(parent);
            }
        });
        txtLocalPath = new JTextField(currentLocalDir.getAbsolutePath());
        txtLocalPath.setEditable(false);
        localTop.add(btnLocalUp, BorderLayout.WEST);
        localTop.add(txtLocalPath, BorderLayout.CENTER);
        localPanel.add(localTop, BorderLayout.NORTH);

        modelLocal = new DefaultTableModel(new String[]{"Tên tệp/thư mục", "Kích thước (Bytes)", "Loại", "Ngày sửa"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tableLocal = new JTable(modelLocal);
        tableLocal.setRowHeight(22);
        tableLocal.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tableLocal.getSelectedRow();
                    if (row != -1) {
                        String name = (String) modelLocal.getValueAt(row, 0);
                        File target = new File(currentLocalDir, name);
                        if (target.isDirectory()) {
                            loadLocalFiles(target);
                        }
                    }
                }
            }
        });
        localPanel.add(new JScrollPane(tableLocal), BorderLayout.CENTER);

        JPanel localBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 4));
        JButton btnUpload = new JButton("📤 Upload lên Server ->");
        btnUpload.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnUpload.setBackground(new Color(40, 167, 69));
        btnUpload.setForeground(Color.WHITE);
        btnUpload.addActionListener(e -> performUpload());
        JButton btnLocalRefresh = new JButton("Làm mới");
        btnLocalRefresh.addActionListener(e -> loadLocalFiles(currentLocalDir));
        localBtns.add(btnUpload);
        localBtns.add(btnLocalRefresh);
        localPanel.add(localBtns, BorderLayout.SOUTH);

        // --- Right: Remote Site ---
        JPanel remotePanel = new JPanel(new BorderLayout(4, 4));
        remotePanel.setBorder(BorderFactory.createTitledBorder("🌐 Máy chủ FTP (Remote Site)"));

        JPanel remoteTop = new JPanel(new BorderLayout(4, 4));
        JButton btnRemoteUp = new JButton("⬆ Lên thư mục cha");
        btnRemoteUp.addActionListener(e -> {
            if (clientEngine.isConnected()) {
                new Thread(() -> {
                    try {
                        clientEngine.cdup();
                        loadRemoteFiles();
                    } catch (Exception ex) {
                        logMessage("Lỗi CDUP: " + ex.getMessage(), false);
                    }
                }).start();
            }
        });
        txtRemotePath = new JTextField("/");
        txtRemotePath.setEditable(false);
        remoteTop.add(btnRemoteUp, BorderLayout.WEST);
        remoteTop.add(txtRemotePath, BorderLayout.CENTER);
        remotePanel.add(remoteTop, BorderLayout.NORTH);

        modelRemote = new DefaultTableModel(new String[]{"Tên tệp/thư mục", "Kích thước (Bytes)", "Quyền hạn", "Thời gian"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tableRemote = new JTable(modelRemote);
        tableRemote.setRowHeight(22);
        tableRemote.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tableRemote.getSelectedRow();
                    if (row != -1) {
                        String name = (String) modelRemote.getValueAt(row, 0);
                        new Thread(() -> {
                            try {
                                if (clientEngine.cwd(name)) {
                                    loadRemoteFiles();
                                }
                            } catch (Exception ex) {
                                logMessage("Lỗi mở thư mục: " + ex.getMessage(), false);
                            }
                        }).start();
                    }
                }
            }
        });
        remotePanel.add(new JScrollPane(tableRemote), BorderLayout.CENTER);

        JPanel remoteBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 4));
        JButton btnDownload = new JButton("📥 <- Download về máy");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDownload.setBackground(new Color(0, 123, 255));
        btnDownload.setForeground(Color.WHITE);
        btnDownload.addActionListener(e -> performDownload());

        JButton btnNewFolder = new JButton("Tạo thư mục");
        btnNewFolder.addActionListener(e -> performNewFolder());

        JButton btnDeleteRemote = new JButton("Xóa tệp");
        btnDeleteRemote.addActionListener(e -> performDeleteRemote());

        JButton btnRemoteRefresh = new JButton("Làm mới");
        btnRemoteRefresh.addActionListener(e -> loadRemoteFiles());

        remoteBtns.add(btnDownload);
        remoteBtns.add(btnNewFolder);
        remoteBtns.add(btnDeleteRemote);
        remoteBtns.add(btnRemoteRefresh);
        remotePanel.add(remoteBtns, BorderLayout.SOUTH);

        splitPane.setLeftComponent(localPanel);
        splitPane.setRightComponent(remotePanel);

        // 3. Bottom Pane (Logs & Progress)
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setPreferredSize(new Dimension(800, 180));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("📜 Nhật ký phiên truyền tin (Socket Console)"));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(25, 25, 25));
        txtLogs.setForeground(new Color(220, 220, 220));
        bottomPanel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        lblStatus = new JLabel("Chưa kết nối");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(250, 18));
        statusPanel.add(lblStatus, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.EAST);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);

        // Center Wrapper
        JPanel centerWrapper = new JPanel(new BorderLayout(5, 5));
        centerWrapper.add(splitPane, BorderLayout.CENTER);
        centerWrapper.add(bottomPanel, BorderLayout.SOUTH);
        add(centerWrapper, BorderLayout.CENTER);
    }

    private void performConnect() {
        String host = txtHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!");
            return;
        }
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword()).trim();

        btnConnect.setEnabled(false);
        lblStatus.setText("Đang kết nối tới " + host + ":" + port + "...");

        new Thread(() -> {
            try {
                clientEngine.connect(host, port);
                if (clientEngine.login(user, pass)) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Đã đăng nhập thành công với tài khoản: " + user);
                        btnConnect.setEnabled(false);
                        btnDisconnect.setEnabled(true);
                        txtHost.setEnabled(false);
                        txtPort.setEnabled(false);
                        txtUser.setEnabled(false);
                        txtPass.setEnabled(false);
                    });
                    loadRemoteFiles();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Đăng nhập thất bại: Sai Username hoặc Password!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        btnConnect.setEnabled(true);
                        lblStatus.setText("Đăng nhập thất bại.");
                    });
                    clientEngine.disconnect();
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Không thể kết nối đến FTP Server: " + ex.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    btnConnect.setEnabled(true);
                    lblStatus.setText("Lỗi kết nối.");
                });
            }
        }).start();
    }

    private void performDisconnect() {
        new Thread(() -> {
            clientEngine.disconnect();
            SwingUtilities.invokeLater(() -> {
                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
                txtHost.setEnabled(true);
                txtPort.setEnabled(true);
                txtUser.setEnabled(true);
                txtPass.setEnabled(true);
                lblStatus.setText("Đã ngắt kết nối.");
                modelRemote.setRowCount(0);
                txtRemotePath.setText("/");
            });
        }).start();
    }

    private void loadLocalFiles(File dir) {
        currentLocalDir = dir;
        txtLocalPath.setText(dir.getAbsolutePath());
        modelLocal.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            for (File f : files) {
                String type = f.isDirectory() ? "Thư mục" : "Tệp tin";
                String size = f.isDirectory() ? "-" : String.valueOf(f.length());
                String date = sdf.format(new Date(f.lastModified()));
                modelLocal.addRow(new Object[]{f.getName(), size, type, date});
            }
        }
    }

    private void loadRemoteFiles() {
        if (!clientEngine.isConnected()) return;
        new Thread(() -> {
            try {
                String curDir = clientEngine.getCurrentRemoteDir();
                List<FTPClientEngine.RemoteFileItem> items = clientEngine.listFiles();
                SwingUtilities.invokeLater(() -> {
                    txtRemotePath.setText(curDir);
                    modelRemote.setRowCount(0);
                    for (FTPClientEngine.RemoteFileItem item : items) {
                        modelRemote.addRow(new Object[]{
                                item.getName(),
                                item.isDirectory() ? "-" : String.valueOf(item.getSize()),
                                item.getPermissions(),
                                item.getDate()
                        });
                    }
                });
            } catch (Exception e) {
                logMessage("Lỗi tải danh sách file remote: " + e.getMessage(), false);
            }
        }).start();
    }

    private void performUpload() {
        if (!clientEngine.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối FTP Server trước!");
            return;
        }
        int row = tableLocal.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp bên danh sách Cục bộ để Upload!");
            return;
        }
        String fileName = (String) modelLocal.getValueAt(row, 0);
        File localFile = new File(currentLocalDir, fileName);
        if (localFile.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Hiện tại chức năng này hỗ trợ upload tệp tin!");
            return;
        }

        lblStatus.setText("Đang upload: " + fileName + "...");
        progressBar.setValue(0);

        new Thread(() -> {
            try {
                clientEngine.uploadFile(localFile, fileName, (transferred, total) -> {
                    int percent = total > 0 ? (int) ((transferred * 100) / total) : 50;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
                });
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Upload thành công: " + fileName);
                    progressBar.setValue(100);
                    JOptionPane.showMessageDialog(this, "Đã upload thành công tệp: " + fileName);
                });
                loadRemoteFiles();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Upload thất bại!");
                    JOptionPane.showMessageDialog(this, "Lỗi upload: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void performDownload() {
        if (!clientEngine.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối FTP Server trước!");
            return;
        }
        int row = tableRemote.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp bên danh sách Server để Download!");
            return;
        }
        String fileName = (String) modelRemote.getValueAt(row, 0);
        File destination = new File(currentLocalDir, fileName);

        lblStatus.setText("Đang download: " + fileName + "...");
        progressBar.setValue(0);

        new Thread(() -> {
            try {
                clientEngine.downloadFile(fileName, destination, (transferred, total) -> {
                    int percent = total > 0 ? (int) ((transferred * 100) / total) : 50;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
                });
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Download thành công: " + fileName);
                    progressBar.setValue(100);
                    JOptionPane.showMessageDialog(this, "Đã download thành công về: " + destination.getAbsolutePath());
                    loadLocalFiles(currentLocalDir);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Download thất bại!");
                    JOptionPane.showMessageDialog(this, "Lỗi download: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void performNewFolder() {
        if (!clientEngine.isConnected()) return;
        String dirName = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới cần tạo trên Server:");
        if (dirName != null && !dirName.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    if (clientEngine.makeDirectory(dirName.trim())) {
                        loadRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể tạo thư mục (trùng tên hoặc không có quyền)!");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
                }
            }).start();
        }
    }

    private void performDeleteRemote() {
        if (!clientEngine.isConnected()) return;
        int row = tableRemote.getSelectedRow();
        if (row == -1) return;
        String name = (String) modelRemote.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa tệp '" + name + "' trên Server không?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    if (clientEngine.deleteFile(name)) {
                        loadRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể xóa tệp (có thể không đủ quyền hạn)!");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage());
                }
            }).start();
        }
    }

    private synchronized void logMessage(String msg, boolean isCommand) {
        SwingUtilities.invokeLater(() -> {
            txtLogs.append((isCommand ? ">> " : "<< ") + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            new FTPClientApp().setVisible(true);
        });
    }
}
