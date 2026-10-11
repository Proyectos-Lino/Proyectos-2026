package com.labcore.platform.results;

import com.labcore.platform.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/results")
public class ResultWorkspaceController {
  private final ResultWorkspaceService workspaceService;

  public ResultWorkspaceController(ResultWorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @GetMapping("/entry-queue")
  @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
  public PageResponse<ResultWorkspaceDtos.TechnicalResultTask> entryQueue(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      Authentication authentication) {
    return PageResponse.from(
        workspaceService.getEntryQueue(
            authentication.getName(),
            PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
  }

  @GetMapping("/validation-queue")
  @PreAuthorize("hasAnyRole('ADMIN','VALIDATOR')")
  public PageResponse<ResultWorkspaceDtos.TechnicalResultTask> validationQueue(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      Authentication authentication) {
    return PageResponse.from(
        workspaceService.getValidationQueue(
            authentication.getName(),
            PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
  }
}
