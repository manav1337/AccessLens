package com.accessibleweb.colorblind_web.controller;

import com.accessibleweb.colorblind_web.service.ColorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest; // Fix for HttpServletRequest
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import com.accessibleweb.colorblind_web.render.RenderException;

@RestController
@RequestMapping("/api/accessibility")
@CrossOrigin(origins = "http://localhost:3000")
public class AccessibilityController {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    );

    private static final String[] ALLOWED_DOMAINS = {
        "https://instagram.com/","https://nymag.com/","https://www.oatly.com/","https://books.toscrape.com/","https://www.trawell.in/maharashtra/lonavala/rajmachi-fort","https://www.codehelp.in/"
    };

    @Autowired
    private ColorService colorService;

    @GetMapping
    public ResponseEntity<?> analyze(
            @RequestParam String url,
            @RequestParam(required = false, defaultValue = "default") String mode
    ) {
        if (!isValidUrl(url)) {
            return ResponseEntity.badRequest().body("Invalid URL format");
        }

        if (!isDomainAllowed(url)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Domain not allowed for security reasons");
        }

        try {
            Map<String, Object> result = colorService.analyzeAccessibility(url, mode);
            return ResponseEntity.ok(result);
        } catch (RenderException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Could not render target page: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/proxy")
    public ResponseEntity<String> proxyWebsite(@RequestParam String url) {
        if (!isValidUrl(url)) {
            return ResponseEntity.badRequest()
                    .body("Invalid URL format. Must start with http:// or https://");
        }

        if (!isDomainAllowed(url)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Domain not allowed for security reasons");
        }

        try {
            // Configure connection with browser-like headers
            Connection connection = Jsoup.connect(url)
                    .timeout(20000)
                    .followRedirects(true)
                    .maxBodySize(10 * 1024 * 1024)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache");

            Response response = connection.execute();


            // Handle HTTP errors
            if (response.statusCode() != 200) {
                String errorMsg = "Origin server returned error: " +
                        response.statusCode() + " " + response.statusMessage();
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorMsg);
            }

            Document doc = response.parse();

            // Clean potentially unsafe elements
            doc.select("script, noscript, iframe, frame").remove();

            if (doc.head() != null) {
                doc.head().prependElement("base").attr("href", url);
            }
            // Rewrite all URLs to go through our proxy
            // rewriteUrls(doc, url);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(doc.html());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Network error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error: " + e.getMessage());
        }
    }

    @GetMapping("/proxy/**")
    public ResponseEntity<byte[]> proxyResource(
            @RequestParam String baseUrl,
            HttpServletRequest request) {

        try {
            // Extract resource path from request
            String requestURI = request.getRequestURI();
            String resourcePath = requestURI.substring(requestURI.indexOf("/proxy/") + 7);
            String resourceUrl = baseUrl + resourcePath;

            if (!isValidUrl(resourceUrl)) {
                return ResponseEntity.badRequest().body("Invalid resource URL".getBytes());
            }

            // Fetch resource
            Response response = Jsoup.connect(resourceUrl)
                    .ignoreContentType(true)
                    .method(Connection.Method.GET)
                    .execute();

            // Return with proper content type
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(response.contentType()))
                    .body(response.bodyAsBytes());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isValidUrl(String url) {
        return URL_PATTERN.matcher(url).matches();
    }

    private boolean isDomainAllowed(String url) {
        for (String domain : ALLOWED_DOMAINS) {
            if (url.contains(domain)) {
                return true;
            }
        }
        return false;
    }
}