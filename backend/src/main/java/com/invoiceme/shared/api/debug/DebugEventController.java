package com.invoiceme.shared.api.debug;

import com.invoiceme.shared.infrastructure.persistence.DomainEventJpaRepository;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Debug controller for inspecting persisted domain events.
 * 
 * This controller is only available in the 'dev' profile for debugging and audit purposes.
 * It should NOT be accessible in production environments.
 */
@RestController
@RequestMapping("/api/debug")
@Profile("dev")
public class DebugEventController {
    private final DomainEventJpaRepository eventRepository;

    public DebugEventController(DomainEventJpaRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Get all persisted domain events, ordered by creation date (newest first).
     * 
     * @return list of all domain events
     */
    @GetMapping("/events")
    public ResponseEntity<List<DomainEventResponse>> getAllEvents() {
        List<DomainEventEntity> entities = eventRepository.findAllByOrderByCreatedAtDesc();
        List<DomainEventResponse> responses = entities.stream()
            .map(DomainEventResponse::from)
            .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Response DTO for domain events.
     */
    public record DomainEventResponse(
        UUID id,
        String type,
        String payload,
        java.time.Instant createdAt
    ) {
        public static DomainEventResponse from(DomainEventEntity entity) {
            return new DomainEventResponse(
                entity.getId(),
                entity.getType(),
                entity.getPayload(),
                entity.getCreatedAt()
            );
        }
    }
}

