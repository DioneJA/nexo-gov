package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static br.com.nexo.gov.protocol.ProtocolDtos.InteractionAttachmentView;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProtocolAttachmentService {
    private final JdbcTemplate jdbc;
    private final ProtocolAttachmentStore store;
    private final ProtocolAttachmentPolicyService policies;
    private final ProtocolPublicAccessService publicAccess;
    private final long maximumFileSizeBytes;
    private final int maximumFiles;

    public ProtocolAttachmentService(JdbcTemplate jdbc, ProtocolAttachmentStore store,
            ProtocolAttachmentPolicyService policies,
            ProtocolPublicAccessService publicAccess,
            @Value("${nexogov.attachments.maximum-file-size-bytes:10485760}") long maximumFileSizeBytes,
            @Value("${nexogov.attachments.maximum-files:10}") int maximumFiles) {
        this.jdbc = jdbc;
        this.store = store;
        this.policies = policies;
        this.publicAccess = publicAccess;
        this.maximumFileSizeBytes = maximumFileSizeBytes;
        this.maximumFiles = maximumFiles;
    }

    public List<Upload> prepare(List<MultipartFile> files, List<UUID> requirementIds) {
        List<MultipartFile> providedFiles = files == null ? List.of() : files;
        List<UUID> providedTypes = requirementIds == null ? List.of() : requirementIds;
        if (providedFiles.size() != providedTypes.size()) throw new AttachmentRejected("Cada arquivo precisa estar identificado por um tipo de anexo.");
        enforceMaximumFiles(providedFiles);
        List<Upload> uploads = new ArrayList<>();
        for (int index = 0; index < providedFiles.size(); index++) {
            MultipartFile file = providedFiles.get(index);
            UUID requirementId = providedTypes.get(index);
            uploads.add(read(file, requirementId));
        }
        return List.copyOf(uploads);
    }

    public List<Upload> prepareInteraction(List<MultipartFile> files) {
        List<MultipartFile> providedFiles = files == null ? List.of() : files;
        enforceMaximumFiles(providedFiles);
        List<Upload> uploads = new ArrayList<>();
        for (MultipartFile file : providedFiles) uploads.add(read(file, null));
        return List.copyOf(uploads);
    }

    private void enforceMaximumFiles(List<MultipartFile> files) {
        if (files.size() > maximumFiles)
            throw new AttachmentRejected("Envie no máximo " + maximumFiles + " arquivos por solicitação.");
    }

    private Upload read(MultipartFile file, UUID requirementId) {
        if (file == null || file.isEmpty()) throw new AttachmentRejected("Não envie arquivos vazios.", requirementId);
        if (file.getSize() > maximumFileSizeBytes) throw new AttachmentRejected("O arquivo excede o limite de tamanho configurado.", requirementId);
        String filename = safeFilename(file.getOriginalFilename(), requirementId);
        String extension = extension(filename, requirementId);
        byte[] content;
        try { content = file.getBytes(); }
        catch (Exception failure) { throw new AttachmentRejected("Não foi possível ler um dos arquivos enviados.", requirementId); }
        String mediaType = detectMediaType(extension, content, requirementId);
        return new Upload(requirementId, filename, extension, mediaType, content, sha256(content));
    }

    public List<Upload> validatePolicy(UUID organizationId, UUID subjectId, List<Upload> uploads) {
        policies.validateRequest(organizationId, subjectId, uploads);
        return uploads;
    }

    /** Writes objects first, then records metadata within the caller's DB transaction. */
    public List<AttachmentView> storeAll(UUID organizationId, UUID protocolId, UUID subjectId, String actor,
                                         List<Upload> uploads, List<String> stagedObjectKeys) {
        return storeAll(organizationId, protocolId, subjectId, actor, null, "PUBLIC", uploads, stagedObjectKeys);
    }

    public List<AttachmentView> storeAll(UUID organizationId, UUID protocolId, UUID subjectId, String actor,
                                         UUID interactionId, String visibility, List<Upload> uploads,
                                         List<String> stagedObjectKeys) {
        List<AttachmentView> stored = new ArrayList<>();
        for (Upload upload : uploads) {
            UUID id = UUID.randomUUID();
            String key = store.objectKey(organizationId, protocolId, id);
            stagedObjectKeys.add(key);
            store.put(key, upload.content());
            jdbc.update("""
                            INSERT INTO protocol_attachments
                                (id,organization_id,protocol_id,subject_id,requirement_id,interaction_id,visibility,original_filename,media_type,size_bytes,sha256,object_key,uploaded_by)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """, id, organizationId, protocolId, subjectId, upload.requirementId(),
                    interactionId, visibility, upload.filename(), upload.mediaType(), upload.content().length,
                    upload.sha256(), key, actor);
            stored.add(new AttachmentView(id, upload.filename(), upload.mediaType(), upload.content().length,
                    0L, OffsetDateTime.now(), downloadPath(protocolId, id, false)));
        }
        return List.copyOf(stored);
    }

    public void deleteStagedObject(String key) { store.delete(key); }

    public List<AttachmentView> publicList(UUID organizationId, UUID protocolId) {
        return list(organizationId, protocolId, false);
    }

    public List<InteractionAttachmentView> interactionList(UUID organizationId, UUID protocolId, UUID interactionId,
                                                            boolean staff, String actor) {
        String visibilityPredicate = staff ? "(a.visibility='PUBLIC' OR (a.visibility='INTERNAL' AND (" +
                "EXISTS (SELECT 1 FROM organization_memberships om WHERE om.organization_id=a.organization_id " +
                "AND om.username=? AND om.role_code='ADMIN' AND om.active=true AND NOT EXISTS " +
                "(SELECT 1 FROM organization_member_access_status s WHERE s.organization_id=om.organization_id " +
                "AND s.username=om.username AND s.access_active=false)) OR EXISTS " +
                "(SELECT 1 FROM protocol_interactions i JOIN staff_memberships sm " +
                "ON sm.organization_id=i.organization_id AND sm.department_id=i.department_id " +
                "WHERE i.organization_id=a.organization_id AND i.protocol_id=a.protocol_id " +
                "AND i.id=a.interaction_id AND sm.username=? AND sm.active=true))))" : "a.visibility='PUBLIC'";
        String sql = "SELECT a.id,a.original_filename,a.media_type,a.size_bytes FROM protocol_attachments a " +
                "WHERE a.organization_id=? AND a.protocol_id=? AND a.interaction_id=? AND " + visibilityPredicate +
                " ORDER BY a.uploaded_at,a.id";
        return jdbc.query(sql, (rs, row) -> new InteractionAttachmentView(rs.getObject("id", UUID.class),
                        rs.getString("original_filename"), rs.getString("media_type"), rs.getLong("size_bytes"),
                        downloadPath(protocolId, rs.getObject("id", UUID.class), staff)),
                staff ? new Object[]{organizationId, protocolId, interactionId, actor, actor}
                        : new Object[]{organizationId, protocolId, interactionId});
    }

    @Transactional
    public Downloaded publicDownload(UUID protocolId, UUID attachmentId, String rawToken) {
        ProtocolPublicAccessService.Principal principal = publicAccess.lockForProtocol(protocolId, rawToken);
        return download(principal.organizationId(), protocolId, attachmentId,
                principal.applicant() ? "APPLICANT" : "PARTICIPANT", "PUBLIC", true);
    }

    public List<AttachmentView> staffList(UUID organizationId, UUID protocolId) {
        return jdbc.query("""
                        SELECT a.id, a.original_filename, a.media_type, a.size_bytes, a.download_count, a.uploaded_at
                          FROM protocol_attachments a
                         WHERE a.organization_id=? AND a.protocol_id=? AND a.interaction_id IS NULL
                         ORDER BY a.uploaded_at, a.id
                        """, (rs, row) -> new AttachmentView(rs.getObject("id", UUID.class), rs.getString("original_filename"),
                        rs.getString("media_type"), rs.getLong("size_bytes"), rs.getLong("download_count"),
                        rs.getObject("uploaded_at", OffsetDateTime.class), downloadPath(protocolId,
                                rs.getObject("id", UUID.class), true)), organizationId, protocolId);
    }

    @Transactional
    public Downloaded staffDownload(UUID organizationId, UUID protocolId, UUID attachmentId, String actor) {
        return download(organizationId, protocolId, attachmentId, actor, "STAFF", false);
    }

    private List<AttachmentView> list(UUID organizationId, UUID protocolId, boolean staff) {
        return jdbc.query("""
                        SELECT id, original_filename, media_type, size_bytes, download_count, uploaded_at
                          FROM protocol_attachments
                         WHERE organization_id=? AND protocol_id=? AND interaction_id IS NULL AND visibility='PUBLIC'
                         ORDER BY uploaded_at, id
                        """, (rs, row) -> new AttachmentView(rs.getObject("id", UUID.class), rs.getString("original_filename"),
                        rs.getString("media_type"), rs.getLong("size_bytes"), rs.getLong("download_count"),
                        rs.getObject("uploaded_at", OffsetDateTime.class), downloadPath(protocolId,
                                rs.getObject("id", UUID.class), staff)), organizationId, protocolId);
    }

    private Downloaded download(UUID organizationId, UUID protocolId, UUID attachmentId,
                                String actor, String actorAudience, boolean publicOnly) {
        StoredMetadata metadata = jdbc.query("""
                        SELECT a.original_filename, a.media_type, a.size_bytes, a.sha256, a.object_key
                          FROM protocol_attachments a
                         WHERE a.organization_id=? AND a.protocol_id=? AND a.id=?
                           AND (?=false OR a.visibility='PUBLIC')
                           AND (a.visibility='PUBLIC' OR EXISTS (
                               SELECT 1 FROM protocol_interactions i
                                WHERE i.organization_id=a.organization_id AND i.protocol_id=a.protocol_id AND i.id=a.interaction_id
                                  AND i.visibility='INTERNAL'
                                  AND (EXISTS (SELECT 1 FROM organization_memberships om
                                        WHERE om.organization_id=a.organization_id AND om.username=? AND om.role_code='ADMIN' AND om.active=true
                                          AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s
                                            WHERE s.organization_id=om.organization_id AND s.username=om.username AND s.access_active=false))
                                    OR EXISTS (SELECT 1 FROM staff_memberships sm
                                        JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id AND d.active=true
                                        WHERE sm.organization_id=i.organization_id AND sm.department_id=i.department_id AND sm.username=? AND sm.active=true))
                           ))
                        """, (rs, row) -> new StoredMetadata(rs.getString("original_filename"), rs.getString("media_type"),
                        rs.getLong("size_bytes"), rs.getString("sha256"), rs.getString("object_key")),
                organizationId, protocolId, attachmentId, publicOnly, actor, actor)
                .stream().findFirst().orElseThrow(NotFound::new);
        ProtocolAttachmentStore.StoredObject object = store.get(metadata.objectKey(), maximumFileSizeBytes);
        if (object.sizeBytes() != metadata.sizeBytes()) throw new IllegalStateException("O tamanho do objeto armazenado não corresponde aos metadados.");
        if (!sha256(object.content()).equals(metadata.sha256())) throw new IllegalStateException("A integridade do objeto armazenado foi invalidada.");
        jdbc.update("INSERT INTO protocol_attachment_downloads(organization_id,protocol_id,attachment_id,actor,audience) VALUES(?,?,?,?,?)",
                organizationId, protocolId, attachmentId, actor, actorAudience);
        jdbc.update("UPDATE protocol_attachments SET download_count=download_count+1 WHERE organization_id=? AND protocol_id=? AND id=?",
                organizationId, protocolId, attachmentId);
        return new Downloaded(metadata.filename(), metadata.mediaType(), object.content());
    }

    public static String downloadPath(UUID protocolId, UUID attachmentId, boolean staff) {
        String audience = staff ? "staff" : "public";
        return "/api/v1/" + audience + "/protocols/" + protocolId + "/attachments/" + attachmentId + "/download";
    }

    private static String safeFilename(String rawName, UUID requirementId) {
        if (rawName == null || rawName.isBlank()) throw new AttachmentRejected("O arquivo precisa ter um nome.", requirementId);
        String candidate = rawName.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (candidate.isBlank() || candidate.length() > 255 || candidate.equals(".") || candidate.equals(".."))
            throw new AttachmentRejected("O nome de arquivo não é válido.", requirementId);
        return candidate;
    }

    private static String extension(String filename, UUID requirementId) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) throw new AttachmentRejected("O arquivo precisa usar uma extensão permitida.", requirementId);
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String detectMediaType(String extension, byte[] bytes, UUID requirementId) {
        if (extension.equals(".pdf") && startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (extension.equals(".png") && startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if ((extension.equals(".jpg") || extension.equals(".jpeg")) && bytes.length > 3
                && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (extension.equals(".mp4") && bytes.length >= 12 && new String(bytes, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) return "video/mp4";
        if (extension.equals(".txt") && isUtf8Text(bytes)) return "text/plain";
        throw new AttachmentRejected("O conteúdo do arquivo não corresponde ao formato permitido pela extensão.", requirementId);
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
        return true;
    }

    private static boolean isUtf8Text(byte[] content) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            return value.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                    && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
        } catch (CharacterCodingException invalidUtf8) { return false; }
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Upload(UUID requirementId, String filename, String extension, String mediaType, byte[] content, String sha256) {}
    public record AttachmentView(UUID id, String filename, String mediaType, long sizeBytes, long downloadCount,
                                 OffsetDateTime uploadedAt, String downloadPath) {}
    public record Downloaded(String filename, String mediaType, byte[] content) {}
    private record StoredMetadata(String filename, String mediaType, long sizeBytes, String sha256, String objectKey) {}

    public static class AttachmentRejected extends RuntimeException {
        private final UUID attachmentRequirementId;
        public AttachmentRejected(String message) { this(message, null); }
        public AttachmentRejected(String message, UUID attachmentRequirementId) {
            super(message);
            this.attachmentRequirementId = attachmentRequirementId;
        }
        public UUID attachmentRequirementId() { return attachmentRequirementId; }
    }
}
