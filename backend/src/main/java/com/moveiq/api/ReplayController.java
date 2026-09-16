package com.moveiq.api;

import com.moveiq.api.dto.ReplayDtos.ReplayState;
import com.moveiq.api.dto.ReplayDtos.SetReplaySpeedRequest;
import com.moveiq.api.dto.ReplayDtos.StartReplayRequest;
import com.moveiq.service.ReplayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/replay")
public class ReplayController {

    private final ReplayService replay;

    public ReplayController(
            ReplayService replay) {

        this.replay = replay;
    }

    @GetMapping("/status")
    public ReplayState status() {

        return replay.state();
    }

    @PostMapping("/start")
    public ReplayState start(
            @RequestBody(required = false)
            StartReplayRequest request) {

        try {

            Integer speed =
                    request == null
                            ? null
                            : request.speed();

            java.time.Instant startAt =
                    request == null
                            ? null
                            : request.startAt();

            return replay.start(
                    speed,
                    startAt);

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e);

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage(),
                    e);
        }
    }

    @PostMapping("/pause")
    public ReplayState pause() {

        return transition(
                replay::pause);
    }

    @PostMapping("/resume")
    public ReplayState resume() {

        return transition(
                replay::resume);
    }

    @PostMapping("/stop")
    public ReplayState stop() {

        return replay.stop();
    }

    @PutMapping("/speed")
    public ReplayState speed(
            @Valid
            @RequestBody
            SetReplaySpeedRequest request) {

        try {

            return replay.setSpeed(
                    request.speed());

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e);
        }
    }

    private ReplayState transition(
            java.util.function.Supplier<ReplayState>
                    operation) {

        try {

            return operation.get();

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage(),
                    e);
        }
    }
}