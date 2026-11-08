package br.com.nexo.gov.protocol;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolAttachmentServiceUnitTest {

    @Test void rejectsExcessFilesBeforeCopyingAnyBytes() {
        AtomicInteger bytesCopied = new AtomicInteger();
        ProtocolAttachmentService service = new ProtocolAttachmentService(null, null, null, null, 1024, 1);

        assertThatThrownBy(() -> service.prepareInteraction(List.of(
                multipart("first.pdf", bytesCopied), multipart("second.pdf", bytesCopied))))
                .isInstanceOf(ProtocolAttachmentService.AttachmentRejected.class)
                .hasMessageContaining("no máximo 1");

        assertThat(bytesCopied).hasValue(0);
    }

    private MultipartFile multipart(String filename, AtomicInteger bytesCopied) {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return filename; }
            @Override public String getContentType() { return "application/pdf"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return 1; }
            @Override public byte[] getBytes() { bytesCopied.incrementAndGet(); return new byte[]{1}; }
            @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
            @Override public void transferTo(File dest) throws IOException {}
        };
    }
}
