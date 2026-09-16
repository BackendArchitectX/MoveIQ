package com.moveiq.api;

import com.moveiq.api.dto.DecisionDossier;
import com.moveiq.service.DecisionDossierService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/control-room/cases")
public class DecisionCaseController {

    private final DecisionDossierService dossiers;

    public DecisionCaseController(
            DecisionDossierService dossiers) {

        this.dossiers = dossiers;
    }

    @GetMapping("/current")
    public DecisionDossier current() {

        return dossiers.current()
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No decision case is available"));
    }

    @GetMapping("/{decisionId}")
    public DecisionDossier byId(
            @PathVariable UUID decisionId) {

        return dossiers.byId(decisionId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Decision case not found: "
                                                + decisionId));
    }
}