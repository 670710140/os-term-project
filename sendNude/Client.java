import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Download client.
 *
 *   java Client list
 *   java Client info <filename>
 *   java Client download <filename> <workers> <trad|nio> <runs>
 *
 * download = split the file into <workers> parts (10 for the assignment), one worker (thread) per part.
 * Every worker opens its OWN connection and sends  GET <filename> <offset> <length>.
 *
 * Two ways to write the result (pick one in download(), look for "CHANGE HERE"):
 *   writePartFilesThenMerge : each worker writes its own part file, then the parts are merged
 *   writeToFinalFile        : each worker writes straight into the final file at its own offset
 */
public class Client {

    static final String HOST = "localhost";
    static final int PORT = 9090;
    static final String DOWNLOAD_DIR = "downloads";

    public static void main(String[] args) throws Exception {
        if (args[0].equals("list")) {
            list();
        } else if (args[0].equals("info")) {
            System.out.println(getSize(args[1]) + " bytes");
        } else if (args[0].equals("download")) {
            String name = args[1];
            int workers = Integer.parseInt(args[2]);
            boolean nio = args[3].equals("nio");
            int runs = Integer.parseInt(args[4]);
            download(name, workers, nio, runs);
        }
    }

    // ============================================================ small helpers

    static SocketChannel connect() throws Exception {
        return SocketChannel.open(new InetSocketAddress(HOST, PORT));
    }

    static void send(OutputStream out, String text) throws Exception {
        out.write((text + "\n").getBytes());
    }

    // Read one text line one byte at a time, so we never swallow any of the file data that follows.
    static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != '\n') {
            sb.append((char) b);
        }
        return sb.toString();
    }

    // Worker i gets the bytes [offsetOf(i), offsetOf(i) + lengthOf(i)).  No overlap.
    static long offsetOf(int i, long size, int workers) {
        return i * (size / workers);
    }

    static long lengthOf(int i, long size, int workers) {
        long chunk = size / workers;
        if (i == workers - 1) {
            return size - i * chunk;        // the last worker takes whatever is left
        }
        return chunk;
    }

    // ============================================================ LIST and INFO

    static void list() throws Exception {
        try (SocketChannel ch = connect()) {
            InputStream in = Channels.newInputStream(ch);
            OutputStream out = Channels.newOutputStream(ch);

            send(out, "LIST");
            String line;
            while (!(line = readLine(in)).equals("END")) {
                System.out.println(line);                          // FILE <name> <size>
            }
        }
    }

    static long getSize(String name) throws Exception {
        try (SocketChannel ch = connect()) {
            InputStream in = Channels.newInputStream(ch);
            OutputStream out = Channels.newOutputStream(ch);

            send(out, "INFO " + name);
            String reply = readLine(in);                           // "SIZE <bytes>" or "ERROR <code> <message>"
            if (reply.startsWith("ERROR")) {
                throw new Exception(reply);
            }
            return Long.parseLong(reply.split(" ")[1]);
        }
    }

    // ============================================================ DOWNLOAD

    static void download(String name, int workers, boolean nio, int runs) throws Exception {
        long size = getSize(name);                                 // step 1: ask the server for the file size
        new File(DOWNLOAD_DIR).mkdirs();
        File finalFile = new File(DOWNLOAD_DIR, name);

        for (int run = 1; run <= runs; run++) {
            finalFile.delete();                                    // start clean, so an old file cannot hide a bug

            long start = System.nanoTime();

            // ===================== CHANGE HERE: choose the write method by its name =====================
            writePartFilesThenMerge(name, size, workers, nio);
            // writeToFinalFile(name, size, workers, nio);
            // ============================================================================================

            double seconds = (System.nanoTime() - start) / 1e9;
            double mbPerSec = size / 1024.0 / 1024.0 / seconds;

            String sizeCheck = (finalFile.length() == size) ? "OK" : "WRONG";
            System.out.printf("run %d: %d workers, %s, %.3f s, %.1f MB/s, size %s, sha256 %s%n",
                    run, workers, nio ? "NIO" : "Traditional", seconds, mbPerSec, sizeCheck, sha256(finalFile));
        }
    }

    // ---------------------------------------------------------------- write method A
    // Every worker writes its own part file (name.part0, name.part1, ...), then we merge them in order.
    static void writePartFilesThenMerge(String name, long size, int workers, boolean nio) throws Exception {
        System.out.println("[write method: writePartFilesThenMerge]");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int worker = i;
            tasks.add(() -> {
                long offset = offsetOf(worker, size, workers);
                long length = lengthOf(worker, size, workers);
                try (RandomAccessFile part = new RandomAccessFile(partFile(name, worker), "rw")) {
                    fetchRange(name, offset, length, part, 0, nio);   // part file starts at position 0
                }
                return null;
            });
        }
        runAll(tasks);                                             // all workers finished -> merge

        try (FileOutputStream out = new FileOutputStream(new File(DOWNLOAD_DIR, name))) {
            for (int i = 0; i < workers; i++) {
                File part = partFile(name, i);
                if (nio) {
                    // NIO: FileChannel.transferTo (part file -> final file)
                    try (FileChannel in = FileChannel.open(part.toPath())) {
                        long done = 0;
                        while (done < in.size()) {
                            done += in.transferTo(done, in.size() - done, out.getChannel());
                        }
                    }
                } else {
                    // Traditional: InputStream.read -> OutputStream.write
                    try (FileInputStream in = new FileInputStream(part)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
                part.delete();
            }
        }
    }

    static File partFile(String name, int i) {
        return new File(DOWNLOAD_DIR, name + ".part" + i);
    }

    // ---------------------------------------------------------------- write method B
    // The final file is made full size first. Every worker has its OWN file handle
    // and writes only its own part, at its own offset.
    static void writeToFinalFile(String name, long size, int workers, boolean nio) throws Exception {
        System.out.println("[write method: writeToFinalFile]");

        File finalFile = new File(DOWNLOAD_DIR, name);
        try (RandomAccessFile f = new RandomAccessFile(finalFile, "rw")) {
            f.setLength(size);
        }

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int worker = i;
            tasks.add(() -> {
                long offset = offsetOf(worker, size, workers);
                long length = lengthOf(worker, size, workers);
                try (RandomAccessFile dest = new RandomAccessFile(finalFile, "rw")) {
                    fetchRange(name, offset, length, dest, offset, nio);   // write at position = offset
                }
                return null;
            });
        }
        runAll(tasks);
    }

    // ---------------------------------------------------------------- shared by both methods

    // Start all workers at the same time (one thread each) and wait until every one is done.
    static void runAll(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        List<Future<Void>> results = pool.invokeAll(tasks);
        pool.shutdown();
        for (Future<Void> r : results) {
            r.get();                                               // re-throws if a worker failed
        }
    }

    // ONE worker: own connection -> GET offset/length -> save the bytes into 'dest' starting at 'destPos'.
    static void fetchRange(String name, long offset, long length,
                           RandomAccessFile dest, long destPos, boolean nio) throws Exception {
        try (SocketChannel ch = connect()) {
            InputStream in = Channels.newInputStream(ch);
            OutputStream out = Channels.newOutputStream(ch);

            send(out, "GET " + name + " " + offset + " " + length);
            String reply = readLine(in);                           // "OK <length>" or "ERROR <code> <message>"
            if (reply.startsWith("ERROR")) {
                throw new Exception(reply);
            }

            if (nio) {
                // NIO native transfer: socket -> file (may move less than asked, so loop)
                long got = 0;
                while (got < length) {
                    got += dest.getChannel().transferFrom(ch, destPos + got, length - got);
                }
            } else {
                // Traditional I/O: socket -> byte[] -> file
                dest.seek(destPos);
                byte[] buf = new byte[64 * 1024];
                long left = length;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    dest.write(buf, 0, n);
                    left -= n;
                }
            }
        }
    }

    static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return String.format("%064x", new BigInteger(1, md.digest()));
    }
}
