package br.com.nexo.gov.organization;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.organization.OrganizationPrivacyNoticeDtos.PrivacyNoticeInput;
import br.com.nexo.gov.organization.OrganizationPrivacyNoticeDtos.PrivacyNoticeView;
import br.com.nexo.gov.organization.OrganizationPrivacyNoticeDtos.PublicPrivacyNoticeView;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationPrivacyNoticeService {
    private final JdbcTemplate jdbc;
    private final OrganizationAdminService organizationAdmin;

    public OrganizationPrivacyNoticeService(JdbcTemplate jdbc, OrganizationAdminService organizationAdmin) {
        this.jdbc = jdbc;
        this.organizationAdmin = organizationAdmin;
    }

    public PrivacyNoticeView staff(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        NoticeState state = load(organizationId);
        return new PrivacyNoticeView(state.explanatoryText(), state.privacyPolicyUrl(),
                organizationAdmin.isOrganizationAdmin(actor, organizationId));
    }

    public PublicPrivacyNoticeView publicNotice(String organizationSlug) {
        List<PublicPrivacyNoticeView> notices = jdbc.query("""
                        SELECT n.explanatory_text, n.privacy_policy_url
                          FROM organizations o
                          LEFT JOIN organization_privacy_notice n ON n.organization_id=o.id
                         WHERE o.slug=? AND o.active=true
                        """, (rs, row) -> new PublicPrivacyNoticeView(
                        rs.getString("explanatory_text"), rs.getString("privacy_policy_url")), organizationSlug);
        if (notices.isEmpty()) throw new NotFound();
        return notices.getFirst();
    }

    @Transactional
    public PrivacyNoticeView publish(String actor, PrivacyNoticeInput input) {
        UUID organizationId = TenantContext.requireTenantId();
        List<UUID> activeOrganizations = jdbc.query(
                "SELECT id FROM organizations WHERE id=? AND active=true FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), organizationId);
        if (activeOrganizations.isEmpty()) throw new NotFound();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);

        String explanatoryText = normalize(input.explanatoryText());
        String privacyPolicyUrl = normalize(input.privacyPolicyUrl());
        validate(explanatoryText, privacyPolicyUrl);

        int changed;
        if (explanatoryText == null) {
            changed = jdbc.update("DELETE FROM organization_privacy_notice WHERE organization_id=?", organizationId);
        } else {
            changed = jdbc.update("""
                    INSERT INTO organization_privacy_notice
                        (organization_id, explanatory_text, privacy_policy_url, created_by, updated_by)
                    VALUES(?,?,?,?,?)
                    ON CONFLICT (organization_id) DO UPDATE SET
                        explanatory_text=EXCLUDED.explanatory_text,
                        privacy_policy_url=EXCLUDED.privacy_policy_url,
                        updated_by=EXCLUDED.updated_by,
                        updated_at=now()
                    WHERE organization_privacy_notice.explanatory_text IS DISTINCT FROM EXCLUDED.explanatory_text
                       OR organization_privacy_notice.privacy_policy_url IS DISTINCT FROM EXCLUDED.privacy_policy_url
                    """, organizationId, explanatoryText, privacyPolicyUrl, actor, actor);
        }

        if (changed == 1) {
            jdbc.update("""
                    INSERT INTO organization_audit_events
                        (organization_id,actor_username,action,entity_type,entity_id,summary)
                    VALUES(?,?,'ORGANIZATION_PRIVACY_NOTICE_PUBLISHED','ORGANIZATION_PRIVACY_NOTICE',?,?)
                    """, organizationId, actor, organizationId, "Aviso público de privacidade atualizado.");
        }
        return staff(actor);
    }

    private NoticeState load(UUID organizationId) {
        List<NoticeState> notices = jdbc.query("""
                        SELECT n.explanatory_text, n.privacy_policy_url
                          FROM organizations o
                          LEFT JOIN organization_privacy_notice n ON n.organization_id=o.id
                         WHERE o.id=? AND o.active=true
                        """, (rs, row) -> new NoticeState(
                        rs.getString("explanatory_text"), rs.getString("privacy_policy_url")), organizationId);
        if (notices.isEmpty()) throw new NotFound();
        return notices.getFirst();
    }

    private static void validate(String explanatoryText, String privacyPolicyUrl) {
        if ((explanatoryText == null) != (privacyPolicyUrl == null)) throw new InvalidPrivacyNotice();
        if (privacyPolicyUrl == null) return;
        try {
            URI uri = URI.create(privacyPolicyUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() >= 0 && uri.getPort() > 65535)) {
                throw new InvalidPrivacyNotice();
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidPrivacyNotice();
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private record NoticeState(String explanatoryText, String privacyPolicyUrl) {}

    public static final class InvalidPrivacyNotice extends RuntimeException {
        public InvalidPrivacyNotice() { super("Informe juntos um texto e um link HTTPS válido, ou deixe os dois em branco."); }
    }
}
