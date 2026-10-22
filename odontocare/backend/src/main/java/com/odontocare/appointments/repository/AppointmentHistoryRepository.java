package com.odontocare.appointments.repository;

import com.odontocare.appointments.model.AppointmentHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface AppointmentHistoryRepository
    extends JpaRepository<AppointmentHistory, UUID>, JpaSpecificationExecutor<AppointmentHistory> {}
