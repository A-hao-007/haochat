package com.ahao.haochat.common.file.service;

import com.ahao.haochat.common.file.dao.FileAssetDao;
import com.ahao.haochat.common.file.dao.FileProcessTaskDao;
import com.ahao.haochat.common.file.domain.FileAsset;
import com.ahao.haochat.common.file.domain.FileProcessTask;
import com.ahao.haochat.oss.MinIOTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private static final int MAX_TASKS_PER_TICK = 10;
    private static final int MAX_RETRY = 3;

    private final FileProcessTaskDao taskDao;
    private final FileAssetDao assetDao;
    private final MinIOTemplate minio;

    @Value("${haochat.file.virus-scan.enabled:false}")
    private boolean virusScanEnabled;
    @Value("${haochat.file.virus-scan.host:clamav}")
    private String virusScanHost;
    @Value("${haochat.file.virus-scan.port:3310}")
    private int virusScanPort;
    @Value("${haochat.file.virus-scan.timeout-ms:5000}")
    private int virusScanTimeoutMs;

    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
        List<FileProcessTask> tasks = taskDao.listPending(MAX_TASKS_PER_TICK);
        for (FileProcessTask task : tasks) {
            if (!taskDao.claim(task.getId())) {
                continue;
            }
            try {
                handle(task);
                finish(task, FileProcessTaskDao.SUCCESS, null);
            } catch (SkipTaskException e) {
                finish(task, FileProcessTaskDao.SKIPPED, e.getMessage());
            } catch (Exception e) {
                retryOrFail(task, e);
            }
        }
    }

    private void handle(FileProcessTask task) throws Exception {
        FileAsset asset = assetDao.getById(task.getAssetId());
        if (asset == null || asset.getStatus() == null || asset.getStatus() != FileAssetService.AVAILABLE) {
            throw new SkipTaskException("asset unavailable");
        }
        if (FileProcessTaskDao.THUMBNAIL.equals(task.getTaskType())) {
            createThumbnail(asset);
            return;
        }
        if (FileProcessTaskDao.VIRUS_SCAN.equals(task.getTaskType())) {
            scanVirus(asset);
            return;
        }
        throw new SkipTaskException("unknown task");
    }

    private void createThumbnail(FileAsset asset) throws Exception {
        if (asset.getContentType() == null || !asset.getContentType().startsWith("image/")) {
            throw new SkipTaskException("not image");
        }
        try (InputStream in = minio.getObject(asset.getObjectKey())) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                throw new SkipTaskException("image unreadable");
            }
            int max = 320;
            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0D, Math.min(max * 1.0D / width, max * 1.0D / height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", output);
            String thumbKey = asset.getObjectKey() + ".thumb.jpg";
            minio.putObject(thumbKey, new ByteArrayInputStream(output.toByteArray()), output.size(), "image/jpeg");

            FileAsset update = new FileAsset();
            update.setId(asset.getId());
            update.setThumbnailKey(thumbKey);
            assetDao.updateById(update);
        }
    }

    private void scanVirus(FileAsset asset) throws Exception {
        if (!virusScanEnabled) {
            FileAsset update = new FileAsset();
            update.setId(asset.getId());
            update.setVirusStatus(3);
            assetDao.updateById(update);
            throw new SkipTaskException("virus scan disabled");
        }
        boolean infected;
        try (InputStream input = minio.getObject(asset.getObjectKey())) {
            infected = scanWithClamAv(input);
        }
        FileAsset update = new FileAsset();
        update.setId(asset.getId());
        update.setVirusStatus(infected ? 2 : 1);
        if (infected) {
            update.setStatus(FileAssetService.FAILED);
        }
        assetDao.updateById(update);
        if (infected) {
            try {
                minio.removeObject(asset.getObjectKey());
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("virus found");
        }
    }

    private boolean scanWithClamAv(InputStream input) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(virusScanHost, virusScanPort), virusScanTimeoutMs);
            socket.setSoTimeout(virusScanTimeoutMs);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
            output.writeInt(0);
            output.flush();
            byte[] responseBytes = new byte[512];
            int responseLen = socket.getInputStream().read(responseBytes);
            String response = responseLen <= 0 ? "" : new String(responseBytes, 0, responseLen, StandardCharsets.UTF_8);
            return response.contains("FOUND");
        }
    }

    private void retryOrFail(FileProcessTask task, Exception e) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetry = retryCount + 1;
        int nextStatus = nextRetry >= MAX_RETRY ? FileProcessTaskDao.FAILED : FileProcessTaskDao.PENDING;
        task.setStatus(nextStatus);
        task.setRetryCount(nextRetry);
        task.setErrorMsg(trimError(e));
        task.setUpdateTime(new Date());
        taskDao.updateById(task);
        log.warn("file process task failed, taskId={}, type={}, retry={}", task.getId(), task.getTaskType(), nextRetry, e);
    }

    private void finish(FileProcessTask task, int status, String message) {
        task.setStatus(status);
        task.setErrorMsg(message);
        task.setUpdateTime(new Date());
        taskDao.updateById(task);
    }

    private String trimError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    private static class SkipTaskException extends Exception {
        SkipTaskException(String message) {
            super(message);
        }
    }
}
