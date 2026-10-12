import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.concurrent.*;

/**
 * Download server.
 *
 *   java Server trad   -> Traditional I/O : read file into a byte[]  ->  OutputStream.write
 *   java Server nio    -> NIO native path : FileChannel.transferTo(...) straight to the socket
 *
 * Protocol (every request / header is ONE text line that ends with \n):
 *   LIST                            ->  FILE <name> <size>   (one line per file), then END
 *   INFO <filename>                 ->  SIZE <bytes>
 *   GET <filename> <offset> <length>->  OK <length>  then exactly <length> raw bytes
 *   anything wrong                  ->  ERROR <code> <message>
 */
public class Server {

    static final int PORT = 9090;
    static final String SHARED_DIR = "shared";   // files in this folder can be downloaded
    static boolean nio;                          // true = NIO transferTo, false = Traditional I/O

    public static void main(String[] args) throws Exception {
        nio = args[0].equals("nio");

        // Thread pool: every connection is handled by one thread from this pool,
        // so many clients (and many workers of one client) are served at the same time.
        ExecutorService pool = Executors.newFixedThreadPool(32);

        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(PORT));
        System.out.println("Server started on port " + PORT + ", mode = " + args[0]);

        while (true) {
            SocketChannel client = server.accept();                // wait for a client to connect
            pool.submit(() -> { handle(client); return null; });   // serve it in another thread
        }
    }

    // Serve one connection: read one command line at a time until the client closes.
    static void handle(SocketChannel channel) throws Exception {
        Socket socket = channel.socket();
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        OutputStream out = socket.getOutputStream();

        String line;
        while ((line = in.readLine()) != null) {
            String[] p = line.split(" ");                          // e.g. ["GET", "test.bin", "0", "1000"]
            if (p[0].equals("LIST")) {
                list(out);
            } else if (p[0].equals("INFO")) {
                info(out, p[1]);
            } else if (p[0].equals("GET")) {
                get(channel, out, p[1], Long.parseLong(p[2]), Long.parseLong(p[3]));
            } else {
                send(out, "ERROR 400 unknown command");
            }
        }
        channel.close();
    }

    static void send(OutputStream out, String text) throws Exception {
        out.write((text + "\n").getBytes());
    }

    // LIST
    static void list(OutputStream out) throws Exception {
        for (File f : new File(SHARED_DIR).listFiles()) {
            send(out, "FILE " + f.getName() + " " + f.length());
        }
        send(out, "END");
    }

    // INFO <filename>
    static void info(OutputStream out, String name) throws Exception {
        File file = new File(SHARED_DIR, name);
        if (!file.exists()) {
            send(out, "ERROR 404 file not found");
            return;
        }
        send(out, "SIZE " + file.length());
    }

    // GET <filename> <offset> <length>  : send ONLY that part of the file
    static void get(SocketChannel channel, OutputStream out, String name, long offset, long length) throws Exception {
        File file = new File(SHARED_DIR, name);
        if (!file.exists()) {
            send(out, "ERROR 404 file not found");
            return;
        }
        if (offset < 0 || length < 0 || offset + length > file.length()) {
            send(out, "ERROR 416 invalid range");
            return;
        }

        send(out, "OK " + length);                                 // header, then the payload

        if (nio) {
            // NIO native transfer: the OS copies file -> socket (may send less than asked, so loop)
            try (FileChannel fc = FileChannel.open(file.toPath())) {
                long sent = 0;
                while (sent < length) {
                    sent += fc.transferTo(offset + sent, length - sent, channel);
                }
            }
        } else {
            // Traditional I/O: file -> byte[] -> socket
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                byte[] buf = new byte[64 * 1024];
                long left = length;
                while (left > 0) {
                    int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                    out.write(buf, 0, n);
                    left -= n;
                }
            }
        }
    }
}
