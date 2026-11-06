package com.justjava.ams.integration;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

final class IntegrationJwksTestSupport {
    static final String KEY_ID = "test-key";
    static final KeyPair SIGNING_KEY = createKeyPair();

    private IntegrationJwksTestSupport() {
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static HttpServer metadataServer(int port, String issuer, int jwksPort) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", exchange -> {
                byte[] json = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"http://127.0.0.1:"
                        + jwksPort + "/jwks\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static HttpServer jwksServer(int port, long responseDelayMillis) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/jwks", exchange -> {
                if (responseDelayMillis > 0) {
                    try {
                        Thread.sleep(responseDelayMillis);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        exchange.close();
                        return;
                    }
                }
                byte[] json = jwks().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String token(String issuer, KeyPair keyPair, String keyId) {
        return token(issuer, keyPair, keyId, "ams-api", 4102444800L);
    }

    static String token(String issuer, KeyPair keyPair, String keyId, String audience, long expiration) {
        String header = "{\"alg\":\"RS256\",\"kid\":\"" + keyId + "\",\"typ\":\"JWT\"}";
        String payload = "{\"iss\":\"" + issuer
                + "\",\"aud\":[\"" + audience + "\"],\"exp\":" + expiration + ",\"iat\":4102440000,"
                + "\"scope\":\"payroll-journal:post payroll-journal:read\",\"azp\":\"jjeip-ams\","
                + "\"sub\":\"jjeip-ams\"}";
        String encodedHeader = encode(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signedContent = encodedHeader + "." + encodedPayload;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signedContent.getBytes(StandardCharsets.US_ASCII));
            return signedContent + "." + encode(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) SIGNING_KEY.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KEY_ID + "\",\"use\":\"sig\","
                + "\"alg\":\"RS256\",\"n\":\"" + encode(unsigned(publicKey.getModulus().toByteArray()))
                + "\",\"e\":\"" + encode(unsigned(publicKey.getPublicExponent().toByteArray())) + "\"}]}";
    }

    private static byte[] unsigned(byte[] value) {
        return value.length > 1 && value[0] == 0
                ? java.util.Arrays.copyOfRange(value, 1, value.length)
                : value;
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
