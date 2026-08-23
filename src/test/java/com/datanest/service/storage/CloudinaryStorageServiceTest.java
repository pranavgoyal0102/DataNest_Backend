package com.datanest.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryStorageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryStorageService storageService;

    @BeforeEach
    void setUp() {

        storageService = new CloudinaryStorageService(cloudinary);
    }

    private void givenUploaderReturns(Map<String, Object> result) throws IOException {

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.uploadLarge(any(), anyMap())).thenReturn(result);
    }

    private MockMultipartFile file(String contentType) {

        return new MockMultipartFile(
                "file",
                "holiday.bin",
                contentType,
                "payload".getBytes()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedOptions() throws IOException {

        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(uploader).uploadLarge(any(), captor.capture());

        return captor.getValue();
    }

    @Test
    @DisplayName("uploads stream in chunks and never buffer the whole file")
    void alwaysStreamsViaUploadLarge() throws IOException {

        givenUploaderReturns(Map.of(
                "secure_url", "https://cdn/x.png",
                "public_id", "folder/x"
        ));

        storageService.uploadFile(file("image/png"));

        verify(uploader).uploadLarge(any(), anyMap());
        verify(uploader, never()).upload(any(), anyMap());
    }

    @Test
    @DisplayName("the stored asset carries the handles needed to delete it later")
    void returnsPublicIdAndResourceType() throws IOException {

        givenUploaderReturns(Map.of(
                "secure_url", "https://cdn/x.png",
                "public_id", "folder/x"
        ));

        StoredAsset asset =
                storageService.uploadFile(file("image/png"));

        assertThat(asset.url()).isEqualTo("https://cdn/x.png");
        assertThat(asset.publicId()).isEqualTo("folder/x");
        assertThat(asset.resourceType()).isEqualTo("image");
    }

    @ParameterizedTest
    @CsvSource({
            "image/png,       image",
            "image/jpeg,      image",
            "video/mp4,       video",
            "audio/mpeg,      video",
            "application/pdf, raw",
            "text/plain,      raw"
    })
    @DisplayName("resource type is derived from the content type")
    void derivesResourceType(String contentType, String expected) throws IOException {

        givenUploaderReturns(Map.of(
                "secure_url", "https://cdn/x",
                "public_id", "x"
        ));

        StoredAsset asset =
                storageService.uploadFile(file(contentType));

        assertThat(asset.resourceType()).isEqualTo(expected);
        assertThat(capturedOptions()).containsEntry("resource_type", expected);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("an unknown content type falls back to raw")
    void unknownContentTypeIsRaw(String contentType) throws IOException {

        givenUploaderReturns(Map.of(
                "secure_url", "https://cdn/x",
                "public_id", "x"
        ));

        assertThat(storageService.uploadFile(file(contentType)).resourceType())
                .isEqualTo("raw");
    }

    @Test
    @DisplayName("delete addresses the asset in the resource type it was stored under")
    void deletePassesResourceType() throws IOException {

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("folder/x"), anyMap())).thenReturn(Map.of());

        storageService.delete("folder/x", "video");

        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(uploader).destroy(eq("folder/x"), captor.capture());

        assertThat(captor.getValue()).containsEntry("resource_type", "video");
    }

    @Test
    @DisplayName("an upload failure surfaces as a StorageException")
    void uploadFailureIsWrapped() throws IOException {

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.uploadLarge(any(), anyMap()))
                .thenThrow(new IOException("cloudinary is down"));

        assertThatThrownBy(() -> storageService.uploadFile(file("image/png")))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("a delete failure surfaces as a StorageException so callers can decide")
    void deleteFailureIsWrapped() throws IOException {

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(any(), anyMap()))
                .thenThrow(new IOException("cloudinary is down"));

        assertThatThrownBy(() -> storageService.delete("folder/x", "image"))
                .isInstanceOf(StorageException.class);
    }
}
