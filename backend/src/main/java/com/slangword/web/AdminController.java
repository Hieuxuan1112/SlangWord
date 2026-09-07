package com.slangword.web;

import com.slangword.service.SeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Maintenance operations")
public class AdminController {

    private final SeedService seedService;

    public AdminController(SeedService seedService) {
        this.seedService = seedService;
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Wipe the dictionary and reload it from the original slang.txt")
    public Map<String, Integer> reset() {
        return Map.of("seeded", seedService.reset());
    }
}
