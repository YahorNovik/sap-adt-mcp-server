package com.sap.adt.mcp.sap;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Core HTTP client for SAP ADT (ABAP Development Tools) REST APIs.
 */
public class AdtRestClient {

    private static final String CSRF_TOKEN_HEADER = "x-csrf-token";
    public static final String SESSION_TYPE_HEADER = "X-sap-adt-sessiontype";
    private static final String DISCOVERY_PATH = "/sap/bc/adt/core/discovery";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String sapClient;
    private final String language;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;

    private String csrfToken;
    private boolean loggedIn;

    public AdtRestClient(String baseUrl, String username, String password,
                         String sapClient, String language, boolean allowInsecureSsl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.sapClient = sapClient;
        this.language = language;
        this.loggedIn = false;

        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(this.cookieManager)
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1);

        if (allowInsecureSsl) {
            try {
                SSLContext sslContext = createTrustAllSslContext();
                builder.sslContext(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create trust-all SSL context", e);
            }
        }

        this.httpClient = builder.build();
    }

    public void login() throws Exception {
        if (loggedIn) {
            return;
        }

        String url = buildUrl(DISCOVERY_PATH);
        System.out.println("AdtRestClient: logging in to " + baseUrl + " ...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header(CSRF_TOKEN_HEADER, "Fetch")
                .header("Accept", "application/atomsvc+xml")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new java.net.ConnectException(
                    "Cannot connect to SAP system at " + baseUrl
                    + ". Verify the URL is correct and the system is reachable. "
                    + "Original error: " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Login failed with HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        csrfToken = response.headers()
                .firstValue(CSRF_TOKEN_HEADER)
                .orElse(null);

        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new IOException("Login succeeded but no CSRF token was returned");
        }

        loggedIn = true;
    }

    public HttpResponse<String> get(String path, String accept) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Accept", accept)
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .GET();

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    public HttpResponse<String> post(String path, String body,
                                     String contentType, String accept) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept", accept)
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    public HttpResponse<String> postWithHeaders(String path, String body,
                                                String contentType, String accept,
                                                Map<String, String> extraHeaders) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept", accept)
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        return executeWithCsrfRetry(builder);
    }

    public HttpResponse<String> put(String path, String body,
                                    String contentType) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept", "text/plain, application/*")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    public HttpResponse<String> putWithHeaders(String path, String body,
                                               String contentType,
                                               Map<String, String> extraHeaders) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", contentType)
                .header("Accept", "text/plain, application/*")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body));

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        return executeWithCsrfRetry(builder);
    }

    public HttpResponse<String> delete(String path) throws Exception {
        String url = buildUrl(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .DELETE();

        if (csrfToken != null) {
            builder.header(CSRF_TOKEN_HEADER, csrfToken);
        }

        return executeWithCsrfRetry(builder);
    }

    public void logout() {
        csrfToken = null;
        loggedIn = false;
        cookieManager.getCookieStore().removeAll();
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getUsername() {
        return username;
    }

    private String buildUrl(String path) {
        String fullPath = path.startsWith("/") ? path : "/" + path;
        String separator = fullPath.contains("?") ? "&" : "?";
        return baseUrl + fullPath + separator
                + "sap-client=" + sapClient
                + "&sap-language=" + language;
    }

    private String basicAuthHeader() {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private HttpResponse<String> executeWithCsrfRetry(HttpRequest.Builder requestBuilder)
            throws Exception {
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403) {
            refreshCsrfToken();
            requestBuilder.header(CSRF_TOKEN_HEADER, csrfToken);
            HttpRequest retryRequest = requestBuilder.build();
            response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode()
                    + " " + request.method() + " " + request.uri()
                    + " -- " + response.body());
        }

        return response;
    }

    private void refreshCsrfToken() throws Exception {
        String url = buildUrl(DISCOVERY_PATH);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader())
                .header(CSRF_TOKEN_HEADER, "Fetch")
                .header("Accept", "application/atomsvc+xml")
                .header("Accept-Language", language)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String newToken = response.headers()
                .firstValue(CSRF_TOKEN_HEADER)
                .orElse(null);

        if (newToken != null && !newToken.isEmpty()) {
            csrfToken = newToken;
        }
    }

    private static SSLContext createTrustAllSslContext()
            throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }
}
