package com.neobank.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Operational health summary for Sprint 18.
 * Provides quick status check for deployment gates and incident triage.
 */
@RestController
@RequestMapping("/actuator/health-summary")
public class OperationalHealthController {

    private final HealthEndpoint healthEndpoint;

    public OperationalHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /**
     * Quick health summary for ops teams.
     * Returns critical dependency status + app readiness in one call.
     */
    @GetMapping
    public Map<String, Object> getHealthSummary() {
        HealthComponent health = healthEndpoint.health();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("status", health.getStatus().toString());
        summary.put("components", extractCriticalComponents(health));
        summary.put("timestamp", System.currentTimeMillis());
        
        return summary;
    }

    /**
     * Extract only critical components for operational awareness.
     * Include: app status, db, cache, kafka (if enabled)
     */
    private Map<String, Object> extractCriticalComponents(HealthComponent health) {
        Map<String, Object> critical = new HashMap<>();
        
        if (health instanceof Health healthDetails) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) healthDetails.getDetails().get("components");
            
            if (components != null) {
                // Include essential components for deployment safety
                String[] essentialComponents = {
                    "db", "redis", "diskSpace", "ping"
                };
                
                for (String component : essentialComponents) {
                    if (components.containsKey(component)) {
                        HealthComponent comp = (HealthComponent) components.get(component);
                        critical.put(component, comp.getStatus().toString());
                    }
                }
            }
        }
        
        return critical;
    }
}

