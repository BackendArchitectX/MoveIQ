package com.moveiq.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TripKeyTest {
    @Test
    void tripIdentityIsTenantScoped() {
        assertNotEquals(new TripKey("vanta-Aus", 42L), new TripKey("catalyst-Sac", 42L));
        assertEquals(new TripKey("vanta-Aus", 42L), new TripKey("vanta-Aus", 42L));
    }
}
