package br.com.nexo.gov.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class OrganizationContactDtos {
    private OrganizationContactDtos() {}

    public enum ContactType {
        PERSON,
        MUNICIPAL_DEPARTMENT,
        LEGAL_ENTITY
    }

    public record ContactInput(
            @NotNull ContactType type,
            @NotBlank @Size(max = 180) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone
    ) {}

    public record ContactView(UUID id, ContactType type, String name, String email,
                              String phone, boolean active) {}

    public record ContactPage(List<ContactView> items, long total, int page, int size) {}

    public record ContactManagementCapabilities(boolean canManageContacts) {}

    public record ContactActiveRequest(@NotNull Boolean active) {}

    public record ContactImportResult(int created, int updated, int unchanged, int processed) {}

    public record ContactActiveResult(UUID id, boolean active, boolean changed) {}
}
