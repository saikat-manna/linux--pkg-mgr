package com.linuxpkgmgr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LinuxPkgMgrApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context wires up correctly
    }
}
