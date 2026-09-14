package com.moveiq.api;

import com.moveiq.domain.SituationEntity;
import com.moveiq.repository.SituationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/situations")
public class SituationController {
    private final SituationRepository repository;
    public SituationController(SituationRepository repository) { this.repository = repository; }
    @GetMapping public List<SituationEntity> list() { return repository.findAll(); }
    @GetMapping("/{id}") public SituationEntity get(@PathVariable UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Situation not found"));
    }
}
