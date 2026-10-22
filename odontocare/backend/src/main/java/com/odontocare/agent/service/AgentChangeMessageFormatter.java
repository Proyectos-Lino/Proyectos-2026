package com.odontocare.agent.service;

import com.odontocare.appointments.dto.AppointmentResponse;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentChangeMessageFormatter {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.forLanguageTag("es"));
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  public String confirmed(String action, AppointmentResponse appointment) {
    return format(
        action.equals("CANCEL")
            ? "Tu cita quedó cancelada."
            : "Tu cita quedó reprogramada y confirmada.",
        appointment);
  }

  public String repeated(String action, AppointmentResponse appointment) {
    String heading =
        action.equals("CANCEL")
            ? "La cancelación ya estaba registrada."
            : "La reprogramación ya estaba registrada.";
    return format(heading + " Estos son los datos actuales de tu cita:", appointment);
  }

  private String format(String heading, AppointmentResponse a) {
    String endDate =
        a.localStart().toLocalDate().equals(a.localEnd().toLocalDate())
            ? ""
            : " del "
                + a.localEnd().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    return heading
        + "\n\n"
        + "Paciente: "
        + a.patientName()
        + "\n"
        + "Servicio: "
        + a.serviceName()
        + "\n"
        + "Profesional: "
        + a.dentistName()
        + "\n"
        + "Fecha: "
        + a.localStart().format(DATE)
        + "\n"
        + "Horario: "
        + a.localStart().format(TIME)
        + "–"
        + a.localEnd().format(TIME)
        + endDate
        + " (hora del consultorio)\n"
        + "Duración: "
        + a.durationMinutes()
        + " minutos\n"
        + "Estado: "
        + status(a.status())
        + "\n"
        + "Referencia: "
        + a.id();
  }

  private String status(String value) {
    return switch (value) {
      case "RESERVED" -> "Reservada";
      case "CONFIRMED" -> "Confirmada";
      case "WAITING" -> "En espera";
      case "IN_PROGRESS" -> "En atención";
      case "ATTENDED" -> "Atendida";
      case "CANCELLED" -> "Cancelada";
      case "NO_SHOW" -> "No asistió";
      default -> value;
    };
  }
}
