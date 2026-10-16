package com.odontocare.appointments.service;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class AppointmentClock {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
