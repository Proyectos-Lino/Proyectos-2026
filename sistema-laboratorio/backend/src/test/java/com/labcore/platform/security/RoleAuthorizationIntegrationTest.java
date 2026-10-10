package com.labcore.platform.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.labcore.platform.audit.AuditLogController;
import com.labcore.platform.audit.AuditLogService;
import com.labcore.platform.client.ClientController;
import com.labcore.platform.client.ClientService;
import com.labcore.platform.dashboard.DashboardController;
import com.labcore.platform.dashboard.DashboardService;
import com.labcore.platform.orders.ServiceOrderController;
import com.labcore.platform.orders.ServiceOrderCorrectionController;
import com.labcore.platform.orders.ServiceOrderCorrectionWorkflowService;
import com.labcore.platform.orders.ServiceOrderDocumentService;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.organization.OrganizationProfileController;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.reports.LaboratoryReportController;
import com.labcore.platform.reports.LaboratoryReportService;
import com.labcore.platform.results.ResultController;
import com.labcore.platform.results.ResultService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  LaboratoryReportController.class,
  AuditLogController.class,
  ClientController.class,
  ServiceOrderController.class,
  ResultController.class,
  OrganizationProfileController.class,
  DashboardController.class,
  ServiceOrderCorrectionController.class
})
@Import(RoleAuthorizationIntegrationTest.MethodSecurityConfiguration.class)
class RoleAuthorizationIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private LaboratoryReportService reportService;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private ClientService clientService;
  @MockitoBean private ServiceOrderService serviceOrderService;
  @MockitoBean private ServiceOrderDocumentService serviceOrderDocumentService;
  @MockitoBean private ServiceOrderCorrectionWorkflowService correctionWorkflowService;
  @MockitoBean private ResultService resultService;
  @MockitoBean private OrganizationProfileService organizationProfileService;
  @MockitoBean private DashboardService dashboardService;

  @Test
  void analystCanOpenTheirRoleScopedDashboard() throws Exception {
    mockMvc
        .perform(get("/api/v1/dashboard/summary").with(user("analyst@test").roles("ANALYST")))
        .andExpect(status().isOk());
  }

  @Test
  void commercialUserCanConsultReportsButCannotIssueThem() throws Exception {
    when(reportService.list(any(), any(), any(), any(), any(), any(), any(), anyString()))
        .thenReturn(Page.empty());

    mockMvc
        .perform(get("/api/v1/reports").with(user("commercial@test").roles("COMMERCIAL")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{id}/issue", UUID.randomUUID())
                .with(user("commercial@test").roles("COMMERCIAL"))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void validatorCanPrepareButCannotIssueReports() throws Exception {
    UUID orderId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/reports/service-orders/{id}", orderId)
                .with(user("validator@test").roles("VALIDATOR"))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{id}/issue", UUID.randomUUID())
                .with(user("validator@test").roles("VALIDATOR"))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void analystCannotAccessCommercialReportsOrAuditLog() throws Exception {
    mockMvc
        .perform(get("/api/v1/reports").with(user("analyst@test").roles("ANALYST")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit-log").with(user("analyst@test").roles("ANALYST")))
        .andExpect(status().isForbidden());
  }

  @Test
  void technicalManagerCanIssueReports() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/reports/{id}/issue", UUID.randomUUID())
                .with(user("manager@test").roles("TECHNICAL_MANAGER"))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void auditLogIsRestrictedToAdministrators() throws Exception {
    when(auditLogService.list(any(), any(), any(), any(), any())).thenReturn(Page.empty());

    mockMvc
        .perform(get("/api/v1/audit-log").with(user("manager@test").roles("TECHNICAL_MANAGER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit-log").with(user("admin@test").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void technicalManagerCannotUseCommercialOrClientRoutes() throws Exception {
    mockMvc
        .perform(get("/api/v1/clients").with(user("manager@test").roles("TECHNICAL_MANAGER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/service-orders").with(user("manager@test").roles("TECHNICAL_MANAGER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void commercialCorrectionSeparatesTechnicalAndCommercialResponsibilities() throws Exception {
    UUID orderId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/service-orders/{id}/return-to-commercial", orderId)
                .with(user("manager@test").roles("TECHNICAL_MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Corregir identificación registrada\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/service-orders/{id}/return-to-commercial", orderId)
                .with(user("commercial@test").roles("COMMERCIAL"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Corregir identificación registrada\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/service-orders/{id}/submit-correction", orderId)
                .with(user("commercial@test").roles("COMMERCIAL"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"changeNote\":\"Identificación corregida\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/service-orders/{id}/approve-correction", orderId)
                .with(user("analyst@test").roles("ANALYST"))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void technicalManagerCannotBulkExportReportIdentities() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reports/export").with(user("manager@test").roles("TECHNICAL_MANAGER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyValidatorsAndAdministratorsCanRejectOrCorrectAndValidate() throws Exception {
    UUID testId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/work-order-tests/{id}/result/reject", testId)
                .with(user("analyst@test").roles("ANALYST"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Repetir lectura\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/work-order-tests/{id}/result/reject", testId)
                .with(user("validator@test").roles("VALIDATOR"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Repetir lectura\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/work-order-tests/{id}/result/correct-and-validate", testId)
                .with(user("analyst@test").roles("ANALYST"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"reason\":\"Error de transcripción\",\"values\":[],\"analystObservation\":\"Corregido\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyAnalystsAndAdministratorsCanManageMatrixExecution() throws Exception {
    UUID testId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/work-order-tests/{id}/result/matrix-execution/start", testId)
                .with(user("validator@test").roles("VALIDATOR"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"declaredRowCount\":2,\"declaredColumnCount\":2}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/work-order-tests/{id}/result/matrix-execution/draft", testId)
                .with(user("manager@test").roles("TECHNICAL_MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"matrix\":{\"rowCount\":1,\"columnCount\":1,\"columns\":[{\"columnIndex\":0}],\"cells\":[{\"rowIndex\":0,\"columnIndex\":0}]}}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void organizationBrandingCanBeReadButOnlyAdministratorsCanAccessFullConfiguration()
      throws Exception {
    String content =
        """
        {
          "commercialName":"Laboratorio",
          "legalName":"Laboratorio S.A.C.",
          "primaryColor":"#0F766E",
          "secondaryColor":"#0F172A",
          "timeZone":"America/Lima",
          "serviceOrderPrefix":"OS",
          "workOrderPrefix":"OT",
          "reportPrefix":"IR",
          "contacts":[],
          "authorizedSignerIds":[]
        }
        """;

    mockMvc
        .perform(
            get("/api/v1/organization-profile/public")
                .with(user("commercial@test").roles("COMMERCIAL")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/organization-profile").with(user("commercial@test").roles("COMMERCIAL")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/organization-profile")
                .with(user("commercial@test").roles("COMMERCIAL"))
                .with(csrf())
                .contentType("application/json")
                .content(content))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/organization-profile")
                .with(user("admin@test").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(content))
        .andExpect(status().isOk());
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}
