package com.odontocare.appointments.controller;

import com.odontocare.appointments.service.AppointmentContactService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentContactController {
  private final AppointmentContactService contacts;

  public AppointmentContactController(AppointmentContactService contacts) {
    this.contacts = contacts;
  }

  @GetMapping("/{id}/contacts")
  @PreAuthorize("hasAuthority('APPOINTMENTS_READ') and hasAuthority('PATIENTS_READ')")
  public AppointmentContactService.Contacts get(@PathVariable UUID id) {
    return contacts.get(id);
  }
}
