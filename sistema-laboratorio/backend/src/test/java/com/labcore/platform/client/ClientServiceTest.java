package com.labcore.platform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock private ClientRepository clientRepository;

  @Test
  void preventsUsingAnInactiveClientInAnOrder() {
    Client client = client();
    client.deactivate();
    given(clientRepository.findById(any(UUID.class))).willReturn(Optional.of(client));

    assertThatThrownBy(() -> service().getActiveRequired(UUID.randomUUID()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("El cliente seleccionado se encuentra inactivo.");
  }

  @Test
  void reactivatesAClientFromTheDirectory() {
    Client client = client();
    client.deactivate();
    UUID clientId = UUID.randomUUID();
    given(clientRepository.findById(clientId)).willReturn(Optional.of(client));

    ClientDtos.ClientResponse response =
        service().updateStatus(clientId, new ClientDtos.StatusRequest(true));

    assertThat(response.active()).isTrue();
  }

  @Test
  void preventsUsingAProducerOnlyParticipantAsRequester() {
    Client producer = participant(ParticipantType.PRODUCER);
    given(clientRepository.findById(any(UUID.class))).willReturn(Optional.of(producer));

    assertThatThrownBy(() -> service().getActiveClientRequired(UUID.randomUUID()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("El participante seleccionado no está clasificado como cliente.");
  }

  @Test
  void acceptsABothParticipantAsProducer() {
    Client participant = participant(ParticipantType.BOTH);
    given(clientRepository.findById(any(UUID.class))).willReturn(Optional.of(participant));

    assertThat(service().getActiveProducerRequired(UUID.randomUUID())).isSameAs(participant);
  }

  private Client client() {
    return participant(ParticipantType.CLIENT);
  }

  private Client participant(ParticipantType type) {
    return new Client(
        type,
        DocumentType.RUC,
        "20123456789",
        "Productora Andina S.A.C.",
        "Productora Andina",
        "contacto@example.test",
        "Lima",
        "Ana López",
        "999 999 999");
  }

  private ClientService service() {
    return new ClientService(clientRepository);
  }
}
