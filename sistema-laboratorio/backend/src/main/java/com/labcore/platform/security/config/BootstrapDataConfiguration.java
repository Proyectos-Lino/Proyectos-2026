package com.labcore.platform.security;

import com.labcore.platform.catalog.LaboratoryArea;
import com.labcore.platform.catalog.LaboratoryAreaRepository;
import com.labcore.platform.catalog.LaboratoryService;
import com.labcore.platform.catalog.LaboratoryServiceRepository;
import com.labcore.platform.catalog.MeasurementUnit;
import com.labcore.platform.catalog.MeasurementUnitRepository;
import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.SampleType;
import com.labcore.platform.catalog.SampleTypeRepository;
import com.labcore.platform.catalog.ServiceTestId;
import com.labcore.platform.catalog.ServiceTestLink;
import com.labcore.platform.catalog.ServiceTestLinkRepository;
import com.labcore.platform.catalog.TestDefinition;
import com.labcore.platform.catalog.TestDefinitionRepository;
import com.labcore.platform.catalog.TestMethod;
import com.labcore.platform.catalog.TestMethodRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapDataConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
  ApplicationRunner bootstrapData(
      ApplicationRoleRepository roleRepository,
      ApplicationUserRepository userRepository,
      LaboratoryAreaRepository areaRepository,
      MeasurementUnitRepository unitRepository,
      SampleTypeRepository sampleTypeRepository,
      TestDefinitionRepository testRepository,
      TestMethodRepository methodRepository,
      LaboratoryServiceRepository serviceRepository,
      ServiceTestLinkRepository serviceTestRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.bootstrap.admin-password}") String adminPassword) {
    return args -> {
      Map<RoleCode, String> roleNames =
          Map.of(
              RoleCode.ADMIN,
              "Administrador",
              RoleCode.COMMERCIAL,
              "Comercial",
              RoleCode.TECHNICAL_MANAGER,
              "Responsable técnico",
              RoleCode.ANALYST,
              "Analista",
              RoleCode.VALIDATOR,
              "Validador");
      roleNames.forEach(
          (code, name) ->
              roleRepository
                  .findByCode(code)
                  .orElseGet(() -> roleRepository.save(new ApplicationRole(code, name))));
      if (userRepository.count() == 0) {
        if (adminPassword == null || adminPassword.length() < 12) {
          throw new IllegalStateException(
              "BOOTSTRAP_ADMIN_PASSWORD debe tener por lo menos 12 caracteres para crear el usuario administrador inicial.");
        }
        ApplicationRole adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        userRepository.save(
            new ApplicationUser(
                "Administrador inicial",
                "admin@labcore.local",
                passwordEncoder.encode(adminPassword),
                Set.of(adminRole)));
      }
      if (areaRepository.count() > 0) return;
      LaboratoryArea analytics =
          areaRepository.save(new LaboratoryArea("ANALITICA", "Análisis fisicoquímico"));
      MeasurementUnit milligramPerLiter =
          unitRepository.save(new MeasurementUnit("MG_L", "Miligramos por litro", "mg/L"));
      unitRepository.save(new MeasurementUnit("G", "Gramos", "g"));
      sampleTypeRepository.save(new SampleType("AGUA", "Agua", "AG", ResultMode.SCALAR));
      sampleTypeRepository.save(new SampleType("SUELO", "Suelo", "SU", ResultMode.SCALAR));
      sampleTypeRepository.save(new SampleType("ALIMENTO", "Alimento", "AL", ResultMode.MATRIX));
      TestDefinition ph =
          testRepository.save(
              new TestDefinition(
                  "PH", "Potencial de hidrógeno (pH)", ResultDataType.NUMERIC, null));
      TestDefinition solids =
          testRepository.save(
              new TestDefinition(
                  "SOLIDOS",
                  "Sólidos disueltos",
                  ResultDataType.NUMERIC,
                  milligramPerLiter.getId()));
      TestMethod phMethod =
          methodRepository.save(
              new TestMethod(ph.getId(), "PH-01", "Medición potenciométrica", "1.0"));
      methodRepository.save(new TestMethod(solids.getId(), "SOL-01", "Gravimetría", "1.0"));
      LaboratoryService service =
          serviceRepository.save(
              new LaboratoryService(
                  "FQ-BASICO", "Análisis fisicoquímico básico", analytics.getId()));
      serviceTestRepository.save(
          new ServiceTestLink(new ServiceTestId(service.getId(), ph.getId()), 1));
      serviceTestRepository.save(
          new ServiceTestLink(new ServiceTestId(service.getId(), solids.getId()), 2));
    };
  }
}
