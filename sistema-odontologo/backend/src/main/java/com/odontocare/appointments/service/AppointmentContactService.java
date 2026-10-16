package com.odontocare.appointments.service;

import com.odontocare.appointments.repository.AppointmentRepository;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentContactService {
  private final AppointmentRepository appointments;

  public AppointmentContactService(AppointmentRepository appointments) {
    this.appointments = appointments;
  }

  public record Contact(
      String phone, String name, String relationship, boolean guardian, boolean payer) {}

  public record Contacts(List<Contact> contacts) {}

  @Transactional(readOnly = true)
  public Contacts get(UUID id) {
    var appointment = appointments.findById(id).orElseThrow(ApiException::notFound);
    return new Contacts(
        appointment.getPatient().getContacts().stream()
            .map(
                c ->
                    new Contact(
                        c.getPhone(),
                        c.getName(),
                        c.getRelationship(),
                        c.getGuardian(),
                        c.getPayer()))
            .toList());
  }
}
