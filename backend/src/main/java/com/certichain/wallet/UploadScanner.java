package com.certichain.wallet;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UploadScanner {

    private static final Logger log = LoggerFactory.getLogger(UploadScanner.class);

    private final String host;
    private final int port;

    public UploadScanner(@Value("${certichain.scan.host:}") String host, @Value("${certichain.scan.port:3310}") int port) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
    }

    public String scan(byte[] bytes) {
        if (host.isBlank()) {
            return "UNSCANNED";
        }
        try {
            String verdict = interpret(clam(bytes));
            if ("INFECTED".equals(verdict)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The malware scanner rejected this file.");
            }
            return verdict;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Malware scanner at {}:{} did not answer. The file was stored without a clean result. {}", host, port, exception.getMessage());
            return "SCAN_UNAVAILABLE";
        }
    }

    static String interpret(String response) {
        String normalized = response == null ? "" : response.toUpperCase();
        if (normalized.contains("FOUND")) {
            return "INFECTED";
        }
        if (normalized.contains("OK")) {
            return "CLEAN";
        }
        return "SCAN_UNAVAILABLE";
    }

    private String clam(byte[] bytes) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(8000);
            socket.getOutputStream().write("nINSTREAM\n".getBytes(StandardCharsets.US_ASCII));
            int offset = 0;
            while (offset < bytes.length) {
                int length = Math.min(2048, bytes.length - offset);
                socket.getOutputStream().write(ByteBuffer.allocate(4).putInt(length).array());
                socket.getOutputStream().write(bytes, offset, length);
                offset += length;
            }
            socket.getOutputStream().write(new byte[4]);
            socket.getOutputStream().flush();
            InputStream input = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read > 0) {
                response.write(buffer, 0, read);
            }
            return response.toString(StandardCharsets.US_ASCII);
        }
    }
}
