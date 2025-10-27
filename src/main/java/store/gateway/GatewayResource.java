package store.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayResource {

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(
            Map.of(
                "service", "Store Gateway API",
                "version", "1.0.0",
                "status", "running"
            )
        );
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(
            Map.of(
                "status", "healthy",
                "service", "gateway"
            )
        );
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> systemInfo() {
        Map<String, String> info = new HashMap<>();
        
        // Hostname
        try {
            InetAddress address = InetAddress.getLocalHost();
            info.put("hostname", address.getHostName());
        } catch (UnknownHostException e) {
            info.put("hostname", "unknown");
        }
        
        // System properties
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        
        return ResponseEntity.ok(info);
    }

}