package br.com.nexo.gov.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public final class DepartmentAdminDtos {
    private DepartmentAdminDtos() {}

    public record DepartmentCreateRequest(@NotBlank @Size(max = 180) String name, UUID parentDepartmentId) {}
    public record DepartmentView(UUID id, String name) {}
    public record DepartmentManagementCapabilities(boolean canManageDepartments) {}
    public record StaffAssignmentView(String username, String fullName, String positionTitle,
                                      LocalDate registrationDate, String registrationDateSource,
                                      Instant lastOnlineAt, String presenceStatus, String roleCode) {}
    public record StaffProfileUpdateRequest(@NotBlank @Size(max = 180) String fullName,
                                            @Size(max = 180) String positionTitle,
                                            @PastOrPresent LocalDate registrationDate) {}
    public record StaffProfileView(String username, String fullName, String positionTitle,
                                   LocalDate registrationDate, String registrationDateSource) {}
    public record OrganizationMemberView(String username, String fullName, String positionTitle,
                                         String accessStatus, List<String> organizationRoles,
                                         List<String> departmentRoles) {}
    public record OrganizationMemberAccessRequest(@jakarta.validation.constraints.NotNull Boolean active) {}
    public record OrganizationMemberAccessView(String username, String accessStatus, boolean changed) {}
    public record PresenceHeartbeatView(String presenceStatus) {}
    public record OrganogramNode(UUID id, String name, UUID parentId,
                                 List<StaffAssignmentView> staff, List<OrganogramNode> children) {}
}
