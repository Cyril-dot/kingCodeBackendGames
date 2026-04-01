package com.kikiBettingWebBack.KikiWebSite.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class GeoLocationService {

    // Free, no-key-required API — 45 req/min on free tier
    private static final String GEO_API_URL = "http://ip-api.com/json/%s?fields=countryCode,currency";

    // Comprehensive country code → currency map
    // Covers Africa-first (your core market) then global fallbacks
    private static final Map<String, String> COUNTRY_CURRENCY_MAP = Map.ofEntries(
            // Africa
            Map.entry("GH", "GHS"),   // Ghana — Cedi
            Map.entry("NG", "NGN"),   // Nigeria — Naira
            Map.entry("KE", "KES"),   // Kenya — Shilling
            Map.entry("ZA", "ZAR"),   // South Africa — Rand
            Map.entry("UG", "UGX"),   // Uganda — Shilling
            Map.entry("TZ", "TZS"),   // Tanzania — Shilling
            Map.entry("ET", "ETB"),   // Ethiopia — Birr
            Map.entry("EG", "EGP"),   // Egypt — Pound
            Map.entry("MA", "MAD"),   // Morocco — Dirham
            Map.entry("SN", "XOF"),   // Senegal — CFA Franc
            Map.entry("CI", "XOF"),   // Côte d'Ivoire — CFA Franc
            Map.entry("CM", "XAF"),   // Cameroon — CFA Franc
            Map.entry("ZM", "ZMW"),   // Zambia — Kwacha
            Map.entry("ZW", "USD"),   // Zimbabwe — uses USD
            Map.entry("RW", "RWF"),   // Rwanda — Franc
            Map.entry("MZ", "MZN"),   // Mozambique — Metical
            Map.entry("AO", "AOA"),   // Angola — Kwanza
            Map.entry("TN", "TND"),   // Tunisia — Dinar

            // Europe
            Map.entry("GB", "GBP"),   // UK — Pound
            Map.entry("DE", "EUR"),   // Germany — Euro
            Map.entry("FR", "EUR"),   // France — Euro
            Map.entry("IT", "EUR"),   // Italy — Euro
            Map.entry("ES", "EUR"),   // Spain — Euro
            Map.entry("NL", "EUR"),   // Netherlands — Euro
            Map.entry("PT", "EUR"),   // Portugal — Euro

            // Americas
            Map.entry("US", "USD"),   // USA — Dollar
            Map.entry("CA", "CAD"),   // Canada — Dollar
            Map.entry("BR", "BRL"),   // Brazil — Real

            // Asia / Middle East
            Map.entry("IN", "INR"),   // India — Rupee
            Map.entry("AE", "AED"),   // UAE — Dirham
            Map.entry("SA", "SAR")    // Saudi Arabia — Riyal
    );

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Detects the currency for the given IP address.
     * Falls back to "USD" if the IP is local, unknown, or the API call fails.
     *
     * @param ipAddress raw IP from HttpServletRequest
     * @return currency code e.g. "GHS", "NGN", "USD"
     */
    public String detectCurrency(String ipAddress) {

        // Local / loopback IPs during development — default to GHS
        if (ipAddress == null
                || ipAddress.isBlank()
                || ipAddress.equals("127.0.0.1")
                || ipAddress.equals("0:0:0:0:0:0:0:1")) {
            log.warn("Local/loopback IP detected ({}), defaulting currency to GHS", ipAddress);
            return "GHS";
        }

        try {
            String url = String.format(GEO_API_URL, ipAddress);
            log.debug("Calling geo API: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                log.warn("Geo API returned null for IP: {}", ipAddress);
                return "USD";
            }

            // ip-api.com returns the currency directly — use it first
            String apiCurrency = (String) response.get("currency");
            if (apiCurrency != null && !apiCurrency.isBlank()) {
                log.info("Currency detected from API for IP {}: {}", ipAddress, apiCurrency);
                return apiCurrency.toUpperCase();
            }

            // Fallback — derive from countryCode using our own map
            String countryCode = (String) response.get("countryCode");
            if (countryCode != null) {
                String mapped = COUNTRY_CURRENCY_MAP.get(countryCode.toUpperCase());
                if (mapped != null) {
                    log.info("Currency mapped from countryCode {} for IP {}: {}", countryCode, ipAddress, mapped);
                    return mapped;
                }
            }

            log.warn("Could not determine currency for IP: {} — defaulting to USD", ipAddress);
            return "USD";

        } catch (Exception ex) {
            log.error("Geo API call failed for IP {}: {} — defaulting to USD", ipAddress, ex.getMessage());
            return "USD";
        }
    }

    /**
     * Extracts the real client IP from the request, handling proxies and load balancers.
     * Checks X-Forwarded-For first (set by Render, Nginx, Cloudflare etc.)
     */
    public String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list — first IP is the real client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}