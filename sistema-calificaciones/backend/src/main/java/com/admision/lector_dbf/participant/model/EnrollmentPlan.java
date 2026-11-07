package com.admision.lector_dbf.participant.model;

import com.admision.lector_dbf.participant.model.ParticipantModels.Enrollment;
import com.admision.lector_dbf.participant.model.ParticipantModels.Person;
import com.admision.lector_dbf.participant.model.ParticipantRequests.SaveEnrollment;

/** Resultado validado antes de escribir o mostrar la confirmación de una carga. */
public record EnrollmentPlan(SaveEnrollment input, Enrollment existing, Person person,
                             boolean createPerson, boolean completePerson, boolean automatic) {
    public boolean groupChanged() { return existing != null && !java.util.Objects.equals(existing.groupId(), input.groupId()); }
    public boolean completed() { return existing != null && existing.automatic() && !automatic; }
    public boolean reused() { return !createPerson && !completePerson && person != null && (existing == null || person.id() != existing.personId()); }
}
