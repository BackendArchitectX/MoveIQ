package com.moveiq.api;

import com.moveiq.api.dto.ControlRoomState;
import com.moveiq.service.ControlRoomStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/control-room")
public class ControlRoomController {
    private final ControlRoomStateService stateService;

    public ControlRoomController(ControlRoomStateService stateService) {
        this.stateService = stateService;
    }

    @GetMapping("/state")
    public ControlRoomState state() {
        return stateService.state();
    }
}
