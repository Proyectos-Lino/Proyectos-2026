package com.admision.lector_dbf.participant.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.Group;
import com.admision.lector_dbf.participant.model.EnrollmentPlan;
import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.model.ParticipantRequests.SaveEnrollment;
import com.admision.lector_dbf.participant.repository.ParticipantRepository;
import com.admision.lector_dbf.shared.ApiException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentPlanner {
    private static final Set<String> DOCUMENT_TYPES = Set.of("DOCUMENTO", "DNI", "CE", "PASAPORTE", "OTRO");
    private final ParticipantRepository people;
    private final Validator validator;

    public EnrollmentPlanner(ParticipantRepository people, Validator validator) {
        this.people = people;
        this.validator = validator;
    }

    public SaveEnrollment normalize(SaveEnrollment input, List<Group> groups) {
        String type = clean(input.documentType()).isEmpty() ? "DOCUMENTO" : clean(input.documentType()).toUpperCase(Locale.ROOT);
        var result = new SaveEnrollment(clean(input.code()), input.personId(), clean(input.names()), clean(input.surnames()),
                type, clean(input.document()), clean(input.email()), input.groupId());
        if (!validator.validate(result).isEmpty() || result.code().chars().anyMatch(Character::isISOControl))
            throw ApiException.badRequest("Revisa código, nombres, documento, correo y longitudes");
        if (!DOCUMENT_TYPES.contains(type)) throw ApiException.badRequest("Tipo de documento inválido");
        if (!groups.isEmpty() && result.groupId() == null) throw ApiException.badRequest("Selecciona un grupo de esta evaluación");
        if (result.groupId() != null && groups.stream().noneMatch(group -> group.id() == result.groupId()))
            throw ApiException.badRequest("El grupo no pertenece a esta evaluación");
        return result;
    }

    public EnrollmentPlan plan(long organization, long evaluation, SaveEnrollment input, Enrollment existing, boolean preserve) {
        if (existing != null && !existing.automatic()) {
            if (!preserve) throw ApiException.conflict("Ya existe una inscripción con este código. Utiliza Editar participante");
            if (input.personId() != null && input.personId() != existing.personId())
                throw ApiException.conflict("El código pertenece a otra persona");
            if (!input.document().isBlank() && (!documentKey(input.document()).equals(documentKey(existing.document()))
                    || !input.documentType().equals(existing.documentType())))
                throw ApiException.conflict("El documento no corresponde a la persona inscrita con este código");
            return new EnrollmentPlan(input, existing, people.person(organization, existing.personId()), false, false, false);
        }
        boolean currentPlaceholder = existing != null && input.personId() != null && input.personId() == existing.personId();
        Person person = input.personId() == null || currentPlaceholder
                ? matchingPerson(organization, input) : people.person(organization, input.personId());
        if (person != null) {
            if (person.automatic()) throw ApiException.conflict("Selecciona una persona con sus datos registrados");
            var registration = people.registrationOf(evaluation, person.id());
            if (registration.isPresent() && (existing == null || registration.get() != existing.id()))
                throw ApiException.conflict("Esta persona ya está inscrita en la evaluación con otro código");
            return new EnrollmentPlan(input, existing, person, false, false, false);
        }
        if (existing == null) return new EnrollmentPlan(input, null, null, true, false, false);
        Person placeholder = people.person(organization, existing.personId());
        boolean remainsAutomatic = input.names().equals(existing.names()) && input.surnames().isEmpty()
                && input.document().isEmpty() && input.email().isEmpty();
        // Un antiguo registro provisional compartido nunca se completa en otra evaluación por accidente.
        return new EnrollmentPlan(input, existing, placeholder, placeholder.registrations() > 1,
                placeholder.registrations() <= 1, remainsAutomatic);
    }

    public Person matchingPerson(long organization, SaveEnrollment input) {
        if (input.document().isBlank()) return null;
        var matches = people.byDocument(organization, input.documentType(), documentKey(input.document()));
        if (matches.size() > 1) throw ApiException.conflict("Más de una persona tiene ese documento. Revisa el directorio o selecciona la persona explícitamente");
        return matches.isEmpty() ? null : matches.get(0);
    }

    public long apply(long organization, long evaluation, EnrollmentPlan plan) {
        var input = plan.input();
        long person;
        if (plan.createPerson()) person = people.createPerson(organization, input.names(), input.surnames(),
                input.documentType(), input.document(), input.email(), plan.automatic());
        else {
            person = plan.person().id();
            if (plan.completePerson()) people.updatePerson(organization, person, input.names(), input.surnames(),
                    input.documentType(), input.document(), input.email(), plan.automatic());
        }
        if (plan.existing() == null) return people.register(organization, evaluation, person, input.code(), input.groupId());
        people.updateEnrollment(organization, evaluation, plan.existing().id(), person, input.code(), input.groupId());
        return plan.existing().id();
    }

    public static String documentKey(String value) { return clean(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    public static String clean(String value) { return value == null ? "" : value.trim(); }
}
