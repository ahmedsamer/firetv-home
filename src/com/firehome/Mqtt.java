package com.firehome;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Just enough MQTT 3.1.1 for the Home Assistant bar: connect with a username and password, subscribe to one filter,
 * publish at QoS 0, keep alive. Reconnects on its own after any failure, every few seconds. Not thread-safe for
 * callers other than publish(), which is safe from any thread.
 */
class Mqtt {
    interface Listener {
        void connected();

        void message(String topic, String payload);

        void disconnected();
    }

    private static final int KEEPALIVE_S = 30;

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String filter;
    private final Listener listener;
    private volatile boolean running;
    private volatile OutputStream out;
    private Socket socket;

    Mqtt(String host, int port, String user, String password, String filter, Listener listener) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.filter = filter;
        this.listener = listener;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        Thread reader = new Thread(this::loop, "mqtt");
        reader.setDaemon(true);
        reader.start();
    }

    void stop() {
        running = false;
        closeQuietly();
    }

    /** QoS 0 publish; silently dropped while not connected. */
    synchronized void publish(String topic, String payload) {
        OutputStream o = out;
        if (o == null) {
            return;
        }
        try {
            byte[] t = topic.getBytes(StandardCharsets.UTF_8);
            byte[] p = payload.getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[2 + t.length + p.length];
            body[0] = (byte) (t.length >> 8);
            body[1] = (byte) t.length;
            System.arraycopy(t, 0, body, 2, t.length);
            System.arraycopy(p, 0, body, 2 + t.length, p.length);
            write(o, 0x30, body);
        } catch (IOException e) {
            closeQuietly();
        }
    }

    private void loop() {
        while (running) {
            try {
                session();
            } catch (Throwable e) {
                Log.w("FireHomeMqtt", "session ended: " + e);
            }
            closeQuietly();
            listener.disconnected();
            for (int i = 0; i < 50 && running; i++) {
                sleep(100);
            }
        }
    }

    private void session() throws IOException {
        Socket socket = new Socket();
        synchronized (this) {
            this.socket = socket;
        }
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(KEEPALIVE_S * 1500);
        InputStream in = socket.getInputStream();
        OutputStream o = socket.getOutputStream();

        // CONNECT: clean session, username + password
        Bytes connect = new Bytes();
        connect.string("MQTT").raw(4).raw(0xC2).raw(0).raw(KEEPALIVE_S);
        connect.string("firehome-" + Long.toHexString(System.nanoTime() & 0xFFFFFF));
        connect.string(user).string(password);
        write(o, 0x10, connect.bytes());
        DataInputStream data = new DataInputStream(in);
        int type = data.readUnsignedByte();
        byte[] ack = read(data, remaining(data));
        if ((type & 0xF0) != 0x20 || ack.length < 2 || ack[1] != 0) {
            throw new IOException("connection refused: " + (ack.length > 1 ? ack[1] : -1));
        }
        out = o;

        // SUBSCRIBE, packet id 1, QoS 0
        Bytes subscribe = new Bytes();
        subscribe.raw(0).raw(1).string(filter).raw(0);
        write(o, 0x82, subscribe.bytes());
        listener.connected();

        Thread pinger = new Thread(() -> {
            while (running && out == o) {
                sleep(KEEPALIVE_S * 1000L / 2);
                try {
                    synchronized (this) {
                        if (out == o) {
                            write(o, 0xC0, new byte[0]);
                        }
                    }
                } catch (IOException e) {
                    closeQuietly();
                }
            }
        }, "mqtt-ping");
        pinger.setDaemon(true);
        pinger.start();

        while (running) {
            int header = data.readUnsignedByte();
            byte[] body = read(data, remaining(data));
            if ((header & 0xF0) == 0x30) {
                int len = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                String topic = new String(body, 2, len, StandardCharsets.UTF_8);
                int start = 2 + len + (((header >> 1) & 3) > 0 ? 2 : 0);
                listener.message(topic, new String(body, start, body.length - start, StandardCharsets.UTF_8));
            }
        }
    }

    private static int remaining(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 28; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("bad length");
    }

    private static byte[] read(DataInputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    private static void write(OutputStream o, int header, byte[] body) throws IOException {
        Bytes packet = new Bytes();
        packet.raw(header);
        int n = body.length;
        do {
            int b = n & 0x7F;
            n >>= 7;
            packet.raw(n > 0 ? b | 0x80 : b);
        } while (n > 0);
        packet.raw(body);
        o.write(packet.bytes());
        o.flush();
    }

    private synchronized void closeQuietly() {
        out = null;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // already closed
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Bytes {
        private byte[] buf = new byte[64];
        private int len;

        Bytes raw(int b) {
            ensure(1);
            buf[len++] = (byte) b;
            return this;
        }

        Bytes raw(byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, buf, len, b.length);
            len += b.length;
            return this;
        }

        Bytes string(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            raw(b.length >> 8).raw(b.length & 0xFF);
            return raw(b);
        }

        byte[] bytes() {
            byte[] b = new byte[len];
            System.arraycopy(buf, 0, b, 0, len);
            return b;
        }

        private void ensure(int n) {
            if (len + n > buf.length) {
                byte[] bigger = new byte[Math.max(buf.length * 2, len + n)];
                System.arraycopy(buf, 0, bigger, 0, len);
                buf = bigger;
            }
        }
    }
}
