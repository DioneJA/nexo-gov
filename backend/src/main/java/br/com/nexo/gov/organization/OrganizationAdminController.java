package br.com.nexo.gov.organization;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

import static br.com.nexo.gov.organization.DepartmentAdminDtos.*;

@RestController
@RequestMapping("/api/v1/staff")
public class OrganizationAdminController {
    private final OrganizationAdminService service;

    public OrganizationAdminController(OrganizationAdminService service) {
        this.service = service;
    }

    @GetMapping("/organization/department-management")
    public DepartmentManagementCapabilities departmentManagementCapabilities(Authentication authentication) {
        return service.departmentManagementCapabilities(authentication.getName());
    }

    @GetMapping("/organization/organogram")
    public List<OrganogramNode> organogram() {
        return service.organogram();
    }

    @PutMapping("/organization/staff/{username}")
    public StaffProfileView updateStaffProfile(Authentication authentication, @PathVariable String username,
                                               @Valid @RequestBody StaffProfileUpdateRequest request) {
        return service.updateStaffProfile(authentication.getName(), username, request);
    }

    @GetMapping("/organization/members")
    public List<OrganizationMemberView> organizationMembers(Authentication authentication) {
        return service.organizationMembers(authentication.getName());
    }

    @PutMapping("/organization/members/{username}/access")
    public OrganizationMemberAccessView setOrganizationMemberAccess(Authentication authentication,
                                                                     @PathVariable String username,
                                                                     @Valid @RequestBody OrganizationMemberAccessRequest request) {
        return service.setOrganizationMemberAccess(authentication.getName(), username, request.active());
    }

    @PostMapping("/session/heartbeat")
    public PresenceHeartbeatView heartbeat() {
        return service.heartbeat();
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentView> createDepartment(Authentication authentication,
                                                            @Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDepartment(authentication.getName(), request));
    }

    @ExceptionHandler(OrganizationAdminService.DepartmentNameConflict.class)
    public ResponseEntity<Map<String, String>> duplicateDepartmentName(OrganizationAdminService.DepartmentNameConflict conflict) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", conflict.getMessage()));
    }

    @ExceptionHandler(OrganizationAdminService.LastActiveOrganizationAdministrator.class)
    public ResponseEntity<Map<String, String>> lastActiveAdministrator(
            OrganizationAdminService.LastActiveOrganizationAdministrator conflict) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", conflict.getMessage()));
    }
}
