package com.personal.dashboard.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** At most twenty workspace tabs can be restored after a reload. */
public record TabLayout(@NotNull @Size(max = 20) List<@Valid TabRequest> tabs) {}
