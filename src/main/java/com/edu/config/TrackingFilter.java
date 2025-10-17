package com.edu.filter;

import com.edu.model.EmailTrackingEntity;
import com.edu.service.EmailTrackingService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TrackingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TrackingFilter.class);

    private final EmailTrackingService emailTrackingService;

    // Pattern to match /api/mail/track/{trackingId}.gif
    private static final Pattern TRACKING_PATTERN = Pattern.compile("^/api/mail/track/([^/]+)\\.gif$");

    // A tiny 1x1 transparent GIF image in byte array format
    private static final byte[] TRACKING_PIXEL_GIF = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            0x00, 0x00, 0x00, 0x21, (byte) 0xf9, 0x04, 0x01, 0x0a, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00,
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b
    };

    // Constructor injection
    public TrackingFilter(EmailTrackingService emailTrackingService) {
        this.emailTrackingService = emailTrackingService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        Matcher matcher = TRACKING_PATTERN.matcher(uri);

        // Check if the request matches the tracking pixel URL pattern
        if (matcher.matches()) {
            String trackingId = matcher.group(1);

            // 1. Handle tracking logic
            Optional<EmailTrackingEntity> trackingOpt = emailTrackingService.trackOpen(trackingId);

            if (trackingOpt.isPresent()) {
                EmailTrackingEntity tracking = trackingOpt.get();
                log.info("Filter handled email open. Tracking ID: {}, Total Opens: {}, Last Open: {}",
                        trackingId, tracking.getOpenCount(), tracking.getLastOpenTimestamp());
            } else {
                log.warn("Filter hit received for unknown ID: {}", trackingId);
            }

            // 2. Return the transparent GIF image directly from the Filter
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_GIF_VALUE);
            httpResponse.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(TRACKING_PIXEL_GIF.length));
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            httpResponse.setHeader(HttpHeaders.PRAGMA, "no-cache");

            httpResponse.getOutputStream().write(TRACKING_PIXEL_GIF);

            // Do NOT call chain.doFilter(request, response) as we are terminating the request here.
        } else {
            // If the URI doesn't match the tracking pattern, let the request continue to the controllers/resources
            chain.doFilter(request, response);
        }
    }
}
