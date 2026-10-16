package com.odontocare.appointments.repository;

import com.odontocare.appointments.model.Appointment;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AppointmentRepository
    extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {
  Optional<Appointment> findByRequestKey(UUID key);

  @Query(
      "select a from Appointment a where a.dentist.id=:dentist and a.status <>"
          + " com.odontocare.appointments.model.AppointmentStatus.CANCELLED and a.startsAt < :end"
          + " and a.blockedUntil > :start order by a.startsAt")
  List<Appointment> occupied(UUID dentist, Instant start, Instant end);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Appointment a where a.id=:id")
  Optional<Appointment> lockById(UUID id);
}
