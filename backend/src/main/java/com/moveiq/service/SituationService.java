package com.moveiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.domain.OutboxEventEntity;
import com.moveiq.domain.SituationContributionEntity;
import com.moveiq.domain.SituationEntity;
import com.moveiq.repository.OutboxEventRepository;
import com.moveiq.repository.SituationContributionRepository;
import com.moveiq.repository.SituationRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SituationService {
    private final SituationRepository situations;
    private final SituationContributionRepository contributions;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public SituationService(SituationRepository situations, SituationContributionRepository contributions, OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.situations = situations; this.contributions = contributions; this.outbox = outbox; this.objectMapper = objectMapper;
    }

    @Transactional
    public SituationEntity applySignal(DetectedSignal signal) {
        String correlationKey = String.join("|", safe(signal.businessUnit()), safe(signal.office()), safe(signal.shift()), safe(signal.direction()), signal.signalType());
        SituationEntity situation = situations.findByCorrelationKey(correlationKey)
                .orElseGet(() -> situations.save(new SituationEntity(correlationKey, signal.businessUnit(), signal.signalType(), signal.office(), signal.shift(), signal.direction())));

        if (contributions.existsBySituationIdAndSourceEventId(situation.getId(), signal.sourceEventId())) return situation;

        contributions.save(new SituationContributionEntity(situation.getId(), signal.sourceEventId(), signal.affectedEmployees(), signal.delayMinutes()));
        situation.addImpact(signal.affectedEmployees(), signal.delayMinutes());
        situations.save(situation);
        outbox.save(new OutboxEventEntity("Situation", situation.getId().toString(), "SITUATION_UPDATED", payload(situation, signal)));
        return situation;
    }

    private String payload(SituationEntity situation, DetectedSignal signal) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "situationId", situation.getId(),
                    "businessUnit", situation.getBusinessUnit(),
                    "type", situation.getSituationType(),
                    "sourceEventId", signal.sourceEventId(),
                    "affectedEmployees", situation.getAffectedEmployees(),
                    "delayMinutes", situation.getDelayMinutes()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox event", e);
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
