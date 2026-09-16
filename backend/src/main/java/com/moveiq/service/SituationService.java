package com.moveiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveiq.api.dto.DetectedSignal;
import com.moveiq.domain.OutboxEventEntity;
import com.moveiq.domain.SituationEntity;
import com.moveiq.repository.OutboxEventRepository;
import com.moveiq.repository.SituationRepository;
import com.moveiq.store.SituationContributionStore;
import com.moveiq.store.SituationStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SituationService {
    private final SituationStore situationStore;
    private final SituationContributionStore contributionStore;
    private final SituationRepository situations;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public SituationService(
            SituationStore situationStore,
            SituationContributionStore contributionStore,
            SituationRepository situations,
            OutboxEventRepository outbox,
            ObjectMapper objectMapper) {
        this.situationStore = situationStore;
        this.contributionStore = contributionStore;
        this.situations = situations;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SituationEntity applySignal(DetectedSignal signal) {
        String correlationKey = String.join("|",
                safe(signal.businessUnit()), safe(signal.office()), safe(signal.shift()),
                safe(signal.direction()), signal.signalType());

        SituationEntity situation = situationStore.getOrCreate(correlationKey, signal);
        if (!contributionStore.insertIfAbsent(situation.getId(), signal)) {
            return situation;
        }

        situation.addImpact(signal.affectedEmployees(), signal.delayMinutes());
        situations.save(situation);
        outbox.save(new OutboxEventEntity(
                "Situation", situation.getId().toString(), "SITUATION_UPDATED", payload(situation, signal)));
        return situation;
    }

    @Transactional(readOnly = true)
    public SituationEntity requireById(
            UUID situationId) {

        return situations.findById(
                        situationId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Situation does not exist: "
                                                + situationId));
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
