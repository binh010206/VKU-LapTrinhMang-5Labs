import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FTPClientHandler implements Runnable {
    private final Socket controlSocket;
    private final File rootDir;
    private final FTPDatabase database;
    private final FTPServerApp serverApp;

    private BufferedReader reader;
    private BufferedWriter writer;
    private String clientIp;

    private String user = null;
    private boolean isAuthenticated = false;
    private File currentDir;

    // Passive Mode
    private ServerSocket passiveServerSocket;

    // Active Mode
    private String activeHost;
    private int activePort;

    public FTPClientHandler(Socket controlSocket, File rootDir, FTPDatabase database, FTPServerApp serverApp) {
        this.controlSocket = controlSocket;
        this.rootDir = rootDir;
        this.currentDir = rootDir;
        this.database = database;
        this.serverApp = serverApp;
        this.clientIp = controlSocket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            sendResponse(FTPConstants.SERVICE_READY, "VKU FTP Server Ready (RFC 959)");
            serverApp.addConnectedClient(clientIp);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                serverApp.logMessage("[" + clientIp + " -> Server] " + line);

                int spaceIdx = line.indexOf(' ');
                String command = (spaceIdx != -1 ? line.substring(0, spaceIdx) : line).toUpperCase();
                String argument = (spaceIdx != -1 ? line.substring(spaceIdx + 1).trim() : "");

                handleCommand(command, argument);

                if ("QUIT".equals(command)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            closeDataConnection();
            try {
                controlSocket.close();
            } catch (IOException ignored) {}
            serverApp.removeConnectedClient(clientIp, user);
            if (user != null) {
                database.logAction(user, clientIp, "LOGOUT", "Session ended", 0, "SUCCESS");
            }
            serverApp.logMessage("[" + clientIp + "] Disconnected.");
        }
    }

    private void handleCommand(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "USER":
                user = arg;
                sendResponse(FTPConstants.NEED_PASSWORD, "User name okay, need password.");
                break;

            case "PASS":
                if (user == null) {
                    sendResponse(FTPConstants.NOT_LOGGED_IN, "Please specify USER first.");
                    return;
                }
                if (database.authenticate(user, arg)) {
                    isAuthenticated = true;
                    sendResponse(FTPConstants.LOGGED_IN, "User logged in, proceed.");
                    serverApp.updateClientUser(clientIp, user);
                    database.logAction(user, clientIp, "LOGIN", "Login success", 0, "SUCCESS");
                } else {
                    sendResponse(FTPConstants.NOT_LOGGED_IN, "Authentication failed.");
                    database.logAction(user, clientIp, "LOGIN", "Login failed (wrong pass)", 0, "FAILED");
                }
                break;

            case "SYST":
                sendResponse(FTPConstants.SYSTEM_TYPE, "UNIX Type: L8");
                break;

            case "FEAT":
                writer.write("211-Features:\r\n PASV\r\n EPSV\r\n UTF8\r\n SIZE\r\n211 End\r\n");
                writer.flush();
                break;

            case "PWD":
            case "XPWD":
                String relativePath = getRelativePath(currentDir);
                sendResponse(FTPConstants.PATHNAME_CREATED, "\"" + relativePath + "\" is current directory.");
                break;

            case "TYPE":
                sendResponse(FTPConstants.CMD_OK, "Type set to " + arg);
                break;

            case "PASV":
                setupPassiveMode();
                break;

            case "EPSV":
                setupExtendedPassiveMode();
                break;

            case "PORT":
                setupActiveMode(arg);
                break;

            case "CWD":
            case "XCWD":
                changeDirectory(arg);
                break;

            case "CDUP":
            case "XCUP":
                changeDirectory("..");
                break;

            case "LIST":
            case "NLST":
                listDirectory(arg);
                break;

            case "RETR":
                retrieveFile(arg);
                break;

            case "STOR":
                storeFile(arg);
                break;

            case "DELE":
                deleteFile(arg);
                break;

            case "MKD":
            case "XMKD":
                makeDirectory(arg);
                break;

            case "RMD":
            case "XRMD":
                removeDirectory(arg);
                break;

            case "SIZE":
                getFileSize(arg);
                break;

            case "NOOP":
                sendResponse(FTPConstants.CMD_OK, "OK");
                break;

            case "QUIT":
                sendResponse(FTPConstants.CLOSING_CONTROL, "Goodbye.");
                break;

            default:
                sendResponse(FTPConstants.SYNTAX_ERROR, "Command not implemented: " + cmd);
                break;
        }
    }

    private void setupPassiveMode() throws IOException {
        closeDataConnection();
        passiveServerSocket = new ServerSocket(0, 1, controlSocket.getLocalAddress());
        int port = passiveServerSocket.getLocalPort();

        byte[] ip = controlSocket.getLocalAddress().getAddress();
        String pasvMsg = String.format("%d,%d,%d,%d,%d,%d",
                ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF,
                port / 256, port % 256);

        sendResponse(FTPConstants.ENTERING_PASV, "Entering Passive Mode (" + pasvMsg + ")");
    }

    private void setupExtendedPassiveMode() throws IOException {
        closeDataConnection();
        passiveServerSocket = new ServerSocket(0, 1, controlSocket.getLocalAddress());
        int port = passiveServerSocket.getLocalPort();
        sendResponse(229, "Entering Extended Passive Mode (|||" + port + "|)");
    }

    private void setupActiveMode(String arg) throws IOException {
        String[] parts = arg.split(",");
        if (parts.length == 6) {
            activeHost = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            activePort = (Integer.parseInt(parts[4]) << 8) + Integer.parseInt(parts[5]);
            sendResponse(FTPConstants.CMD_OK, "PORT command successful.");
        } else {
            sendResponse(FTPConstants.SYNTAX_ERROR, "Invalid PORT command syntax.");
        }
    }

    private Socket openDataSocket() throws IOException {
        if (passiveServerSocket != null) {
            passiveServerSocket.setSoTimeout(10000);
            Socket s = passiveServerSocket.accept();
            passiveServerSocket.close();
            passiveServerSocket = null;
            return s;
        } else if (activeHost != null && activePort > 0) {
            Socket s = new Socket(activeHost, activePort);
            activeHost = null;
            activePort = 0;
            return s;
        }
        return null;
    }

    private void listDirectory(String arg) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        try {
            sendResponse(FTPConstants.REQ_ACTION_INIT, "Opening data connection for directory list.");
            try (Socket dataSocket = openDataSocket()) {
                if (dataSocket == null) {
                    sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Cannot open data connection.");
                    return;
                }
                BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8));
                File target = currentDir;
                if (!arg.isEmpty() && !arg.startsWith("-")) {
                    target = resolvePath(arg);
                }

                File[] files = target.listFiles();
                if (files != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
                    for (File f : files) {
                        String perms = (f.isDirectory() ? "d" : "-") + "rwxrwxrwx 1 ftp ftp ";
                        String size = String.format("%12d", f.length());
                        String date = sdf.format(new Date(f.lastModified()));
                        String name = f.getName();
                        dataWriter.write(perms + size + " " + date + " " + name + "\r\n");
                    }
                }
                dataWriter.flush();
            }
            sendResponse(FTPConstants.CLOSING_DATA, "Directory send OK.");
        } catch (IOException e) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Failed to list directory: " + e.getMessage());
        }
    }

    private void retrieveFile(String filename) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        File targetFile = resolvePath(filename);
        if (!targetFile.exists() || targetFile.isDirectory()) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "File not found or is a directory.");
            return;
        }

        try {
            sendResponse(FTPConstants.REQ_ACTION_INIT, "Opening BINARY mode data connection for " + filename + " (" + targetFile.length() + " bytes).");
            try (Socket dataSocket = openDataSocket()) {
                if (dataSocket == null) {
                    sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Cannot open data connection.");
                    return;
                }
                try (InputStream fis = new FileInputStream(targetFile);
                     OutputStream dataOut = dataSocket.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dataOut.write(buffer, 0, bytesRead);
                    }
                    dataOut.flush();
                }
            }
            sendResponse(FTPConstants.CLOSING_DATA, "Transfer complete.");
            database.logAction(user, clientIp, "DOWNLOAD", targetFile.getName(), targetFile.length(), "SUCCESS");
            serverApp.refreshDatabaseTable();
        } catch (IOException e) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Error retrieving file: " + e.getMessage());
            database.logAction(user, clientIp, "DOWNLOAD", targetFile.getName(), targetFile.length(), "FAILED");
        }
    }

    private void storeFile(String filename) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        if (!database.hasWritePermission(user)) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Permission denied (Read-only account).");
            database.logAction(user, clientIp, "UPLOAD", filename, 0, "DENIED");
            return;
        }

        File targetFile = resolvePath(filename);
        try {
            sendResponse(FTPConstants.REQ_ACTION_INIT, "Ready to receive data connection.");
            long totalWritten = 0;
            try (Socket dataSocket = openDataSocket()) {
                if (dataSocket == null) {
                    sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Cannot open data connection.");
                    return;
                }
                try (InputStream dataIn = dataSocket.getInputStream();
                     OutputStream fos = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = dataIn.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalWritten += bytesRead;
                    }
                    fos.flush();
                }
            }
            sendResponse(FTPConstants.CLOSING_DATA, "Transfer complete. Saved " + totalWritten + " bytes.");
            database.logAction(user, clientIp, "UPLOAD", targetFile.getName(), totalWritten, "SUCCESS");
            serverApp.refreshDatabaseTable();
        } catch (IOException e) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Error storing file: " + e.getMessage());
            database.logAction(user, clientIp, "UPLOAD", filename, 0, "FAILED");
        }
    }

    private void deleteFile(String filename) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        if (!database.hasWritePermission(user)) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Permission denied.");
            return;
        }
        File targetFile = resolvePath(filename);
        if (targetFile.exists() && targetFile.isFile() && targetFile.delete()) {
            sendResponse(FTPConstants.FILE_ACTION_OK, "File deleted successfully.");
            database.logAction(user, clientIp, "DELETE", targetFile.getName(), 0, "SUCCESS");
            serverApp.refreshDatabaseTable();
        } else {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Could not delete file.");
        }
    }

    private void makeDirectory(String dirname) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        if (!database.hasWritePermission(user)) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Permission denied.");
            return;
        }
        File targetDir = resolvePath(dirname);
        if (targetDir.mkdir()) {
            sendResponse(FTPConstants.PATHNAME_CREATED, "\"" + dirname + "\" directory created.");
            database.logAction(user, clientIp, "MKDIR", dirname, 0, "SUCCESS");
            serverApp.refreshDatabaseTable();
        } else {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Could not create directory.");
        }
    }

    private void removeDirectory(String dirname) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        if (!database.hasWritePermission(user)) {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Permission denied.");
            return;
        }
        File targetDir = resolvePath(dirname);
        if (targetDir.exists() && targetDir.isDirectory() && targetDir.delete()) {
            sendResponse(FTPConstants.FILE_ACTION_OK, "Directory removed.");
            database.logAction(user, clientIp, "RMD", dirname, 0, "SUCCESS");
            serverApp.refreshDatabaseTable();
        } else {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Could not remove directory (must be empty).");
        }
    }

    private void changeDirectory(String dir) {
        if (!isAuthenticated) {
            sendResponse(FTPConstants.NOT_LOGGED_IN, "Not logged in.");
            return;
        }
        File target = resolvePath(dir);
        if (target.exists() && target.isDirectory() && isSafePath(target)) {
            currentDir = target;
            sendResponse(FTPConstants.FILE_ACTION_OK, "Directory changed to \"" + getRelativePath(currentDir) + "\"");
        } else {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "Failed to change directory.");
        }
    }

    private void getFileSize(String filename) {
        File f = resolvePath(filename);
        if (f.exists() && f.isFile()) {
            sendResponse(FTPConstants.CMD_OK, String.valueOf(f.length()));
        } else {
            sendResponse(FTPConstants.ACTION_NOT_TAKEN, "File not found.");
        }
    }

    private File resolvePath(String path) {
        if (path.startsWith("/")) {
            return new File(rootDir, path.substring(1));
        } else {
            return new File(currentDir, path);
        }
    }

    private boolean isSafePath(File file) {
        try {
            String rootCanonical = rootDir.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            return fileCanonical.startsWith(rootCanonical);
        } catch (IOException e) {
            return false;
        }
    }

    private String getRelativePath(File file) {
        try {
            String rootCanonical = rootDir.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            if (fileCanonical.equals(rootCanonical)) return "/";
            String rel = fileCanonical.substring(rootCanonical.length()).replace('\\', '/');
            return rel.startsWith("/") ? rel : "/" + rel;
        } catch (IOException e) {
            return "/";
        }
    }

    private void sendResponse(int code, String text) {
        try {
            String msg = code + " " + text + "\r\n";
            writer.write(msg);
            writer.flush();
            serverApp.logMessage("[" + clientIp + " <- Server] " + code + " " + text);
        } catch (IOException e) {
            // Ignore
        }
    }

    private void closeDataConnection() {
        if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
            try {
                passiveServerSocket.close();
            } catch (IOException ignored) {}
            passiveServerSocket = null;
        }
    }
}
