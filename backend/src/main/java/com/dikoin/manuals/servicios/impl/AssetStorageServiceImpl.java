package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.config.StorageProperties;
import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.enums.AssetType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.AssetMapper;
import com.dikoin.manuals.repositorios.AssetRepository;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.servicios.AssetStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetStorageServiceImpl implements AssetStorageService {

    private static final int MAX_IMAGE_DIMENSION = 2400;
    private static final int THUMBNAIL_DIMENSION = 360;
    private static final float JPEG_QUALITY = 0.88f;

    private final StorageProperties storageProperties;
    private final AssetRepository assetRepository;
    private final ManualRepository manualRepository;
    private final AssetMapper assetMapper;

    @Override
    @Transactional
    public AssetResponse storeAsset(MultipartFile file, AssetType assetType, Long manualId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Archivo vacio");
        }

        Manual manual = manualId != null
                ? manualRepository.findById(manualId)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + manualId))
                : null;

        String folder = switch (assetType) {
            case LOGO -> "assets/logos";
            case PRODUCT_IMAGE -> manual != null ? "assets/manuals/" + safeName(manual.getCode()) + "/product" : "assets/manuals/general/product";
            case TEMPLATE_RESOURCE -> "assets/templates";
            case EXTRACTED_IMAGE -> "assets/extracted";
            default -> manual != null ? "assets/manuals/" + safeName(manual.getCode()) : "assets/manuals/general";
        };

        StoredFile stored = shouldOptimizeImage(file)
                ? storeOptimizedImage(file, folder)
                : new StoredFile(storeRawFile(file, folder), normalizeMimeType(file), file.getSize());
        String thumbnailPath = createThumbnail(stored.path());
        Asset asset = Asset.builder()
                .originalFilename(cleanFilename(file.getOriginalFilename()))
                .storedFilename(stored.path().getFileName().toString())
                .mimeType(stored.mimeType())
                .fileSize(stored.fileSize())
                .storagePath(toRelative(stored.path()))
                .thumbnailPath(thumbnailPath)
                .assetType(assetType)
                .manual(manual)
                .build();
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    @Override
    public Path storeRawFile(MultipartFile file, String folder) {
        try {
            Path targetFolder = basePath().resolve(folder).normalize();
            Files.createDirectories(targetFolder);
            Path destination = targetFolder.resolve(uniqueFilename(file.getOriginalFilename()));
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException ex) {
            throw new ApiException("No se pudo guardar el archivo", ex);
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path resolved = basePath().resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath())) {
            throw new ApiException("Ruta de archivo no permitida");
        }
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public Path resolveAsset(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + assetId));
        return resolve(asset.getStoragePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Path resolveThumbnail(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + assetId));
        if (asset.getThumbnailPath() == null || asset.getThumbnailPath().isBlank()) {
            throw new ResourceNotFoundException("Thumbnail no encontrado: " + assetId);
        }
        return resolve(asset.getThumbnailPath());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> findAll(Long manualId, AssetType assetType) {
        List<Asset> assets;
        if (manualId != null && assetType != null) {
            assets = assetRepository.findByManualIdAndAssetType(manualId, assetType);
        } else if (manualId != null) {
            assets = assetRepository.findByManualId(manualId);
        } else if (assetType != null) {
            assets = assetRepository.findByAssetType(assetType);
        } else {
            assets = assetRepository.findAll();
        }
        return assets.stream().map(assetMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + assetId));
        deleteIfExists(asset.getStoragePath());
        deleteIfExists(asset.getThumbnailPath());
        assetRepository.delete(asset);
    }

    private Path basePath() {
        return Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize();
    }

    private String uniqueFilename(String originalFilename) {
        String clean = cleanFilename(originalFilename);
        String ext = extension(clean);
        String base = clean.substring(0, clean.length() - ext.length()).replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return timestamp + "_" + base + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "archivo";
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : "";
    }

    private String normalizeMimeType(MultipartFile file) {
        String filename = cleanFilename(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return file.getContentType();
    }

    private boolean shouldOptimizeImage(MultipartFile file) {
        String filename = cleanFilename(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".svg")) {
            return false;
        }
        String mime = file.getContentType();
        return mime != null && mime.toLowerCase().startsWith("image/");
    }

    private StoredFile storeOptimizedImage(MultipartFile file, String folder) {
        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                return new StoredFile(storeRawFile(file, folder), normalizeMimeType(file), file.getSize());
            }
            BufferedImage scaled = scaleImage(original, MAX_IMAGE_DIMENSION);
            boolean alpha = scaled.getColorModel().hasAlpha();
            String extension = alpha ? ".png" : ".jpg";
            String mimeType = alpha ? "image/png" : "image/jpeg";
            Path targetFolder = basePath().resolve(folder).normalize();
            Files.createDirectories(targetFolder);
            Path destination = targetFolder.resolve(uniqueFilenameWithExtension(file.getOriginalFilename(), extension));
            if (alpha) {
                ImageIO.write(scaled, "png", destination.toFile());
            } else {
                writeJpeg(scaled, destination, JPEG_QUALITY);
            }
            return new StoredFile(destination, mimeType, Files.size(destination));
        } catch (IOException ex) {
            throw new ApiException("No se pudo optimizar la imagen", ex);
        }
    }

    private String createThumbnail(Path source) {
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) {
                return null;
            }
            BufferedImage scaled = scaleImage(original, THUMBNAIL_DIMENSION);
            Path thumbnailFolder = basePath().resolve("thumbnails").normalize();
            Files.createDirectories(thumbnailFolder);
            String name = source.getFileName().toString().replaceAll("\\.[^.]+$", "") + "_thumb.jpg";
            Path thumbnail = thumbnailFolder.resolve(name);
            writeJpeg(toRgb(scaled), thumbnail, 0.82f);
            return toRelative(thumbnail);
        } catch (IOException ex) {
            return null;
        }
    }

    private BufferedImage scaleImage(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) {
            return original;
        }
        double ratio = (double) maxDimension / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        int type = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (!image.getColorModel().hasAlpha() && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private void writeJpeg(BufferedImage image, Path destination, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(toRgb(image), "jpg", destination.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(destination.toFile())) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(toRgb(image), null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private String uniqueFilenameWithExtension(String originalFilename, String extension) {
        String clean = cleanFilename(originalFilename);
        String currentExt = extension(clean);
        String base = clean.substring(0, clean.length() - currentExt.length()).replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return timestamp + "_" + base + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }

    private void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
        }
    }

    private String safeName(String value) {
        return value == null ? "general" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String toRelative(Path stored) {
        return basePath().relativize(stored.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }

    private record StoredFile(Path path, String mimeType, Long fileSize) {
    }
}
