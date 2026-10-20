package com.qsy.edifice.service.support;

import com.qsy.edifice.domain.entity.Files;
import com.qsy.edifice.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;

@Component
public class FileObjectStorage {
    @Value("${file.upload-common-url:.}") private String localRoot;
    @Value("${file.storage-type:local}") private String type;
    @Value("${file.minio.endpoint:http://127.0.0.1:9000}") private String endpoint;
    @Value("${file.minio.bucket:edifice-prod}") private String bucket;
    @Value("${file.minio.region:us-east-1}") private String region;
    @Value("${file.minio.access-key:}") private String accessKey;
    @Value("${file.minio.secret-key:}") private String secretKey;
    private volatile S3Client client;

    public String currentType() { return "minio".equalsIgnoreCase(type) ? "minio" : "local"; }
    public String bucket() { return bucket; }

    public S3Client s3() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (accessKey.isBlank() || secretKey.isBlank()) throw new BusinessException("对象存储尚未配置");
                    client = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region))
                            .forcePathStyle(true)
                            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                            .httpClientBuilder(UrlConnectionHttpClient.builder()
                                    .connectionTimeout(Duration.ofSeconds(10)).socketTimeout(Duration.ofMinutes(5)))
                            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMinutes(10)))
                            .build();
                }
            }
        }
        return client;
    }

    public Path root() { return Paths.get(localRoot).toAbsolutePath().normalize(); }

    public Path localPath(String relative) {
        String key = relative == null ? "" : relative.replaceFirst("^/+", "");
        Path path = root().resolve(key).normalize();
        if (key.isBlank() || !path.startsWith(root())) throw new BusinessException("文件路径不合法");
        return path;
    }

    public Path resolveLegacy(Files file) throws IOException {
        if (file.getObjectKey() != null && !file.getObjectKey().isBlank()) return checkedLocal(localPath(file.getObjectKey()));
        String path = file.getFilePath();
        if (path == null) throw new FileNotFoundException("文件路径不存在");
        String rel = path.replaceFirst("^/+", "");
        List<Path> options = rel.startsWith("upload/")
                ? List.of(localPath(rel.substring(7)), localPath(rel))
                : List.of(localPath(rel), localPath("upload/" + rel));
        for (Path option : options) if (java.nio.file.Files.isRegularFile(option)) return checkedLocal(option);
        throw new FileNotFoundException("原文件不存在，fileId=" + file.getFileId());
    }

    private Path checkedLocal(Path path) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(root().toRealPath())) throw new BusinessException("文件路径超出存储目录");
        return real;
    }

    public InputStream open(Files file) throws IOException { return open(file, 0, null); }

    public InputStream open(Files file, long offset, Long length) throws IOException {
        if ("minio".equalsIgnoreCase(file.getStorageType())) {
            GetObjectRequest.Builder request = GetObjectRequest.builder()
                    .bucket(file.getStorageBucket()).key(file.getObjectKey());
            if (file.getObjectVersion() != null) request.versionId(file.getObjectVersion());
            if (offset > 0 || length != null) request.range("bytes=" + offset + "-" + (length == null ? "" : offset + length - 1));
            ResponseInputStream<GetObjectResponse> input = s3().getObject(request.build());
            return new FilterInputStream(input) {
                @Override public void close() { input.abort(); }
            };
        }
        InputStream input = java.nio.file.Files.newInputStream(resolveLegacy(file));
        try { input.skipNBytes(offset); return input; }
        catch (IOException e) { input.close(); throw e; }
    }

    public String begin(String key) {
        return s3().createMultipartUpload(b -> b.bucket(bucket).key(key)
                .contentType("application/octet-stream")).uploadId();
    }

    public String putPart(String targetBucket, String key, String uploadId, int number, Path source) {
        return s3().uploadPart(b -> b.bucket(targetBucket).key(key).uploadId(uploadId).partNumber(number),
                RequestBody.fromFile(source)).eTag();
    }

    public String finish(String targetBucket, String key, String uploadId, List<CompletedPart> parts) {
        // A retry after S3 completed but before the SQL commit must not complete twice.
        try { return s3().headObject(b -> b.bucket(targetBucket).key(key)).versionId(); }
        catch (S3Exception e) { if (e.statusCode() != 404) throw e; }
        return s3().completeMultipartUpload(b -> b.bucket(targetBucket).key(key).uploadId(uploadId)
                .multipartUpload(m -> m.parts(parts))).versionId();
    }

    public void abort(String targetBucket, String key, String uploadId) {
        try { s3().abortMultipartUpload(b -> b.bucket(targetBucket).key(key).uploadId(uploadId)); }
        catch (S3Exception e) { if (e.statusCode() != 404) throw e; }
    }

    @PreDestroy public void close() { if (client != null) client.close(); }
}
