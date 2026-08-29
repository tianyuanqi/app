package com.yuanqi.app.photo.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.yuanqi.app.common.text.UnicodeText;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从单个原始媒体提取可展示的、范围受限的 EXIF 候选值。 */
@Component
public class ExifExtractor {
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EXIF_TIME = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public Result extract(Path source, Clock clock) {
        List<Warning> warnings = new ArrayList<>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(source.toFile());
            ExifIFD0Directory main = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            LocalDateTime captureTime = captureTime(sub, clock, warnings);
            Candidate candidate = new Candidate(captureTime,
                    field(main == null ? null : main.getString(ExifIFD0Directory.TAG_MODEL), "cameraBody", 100, warnings),
                    field(sub == null ? null : sub.getString(ExifSubIFDDirectory.TAG_LENS_MODEL), "lens", 100, warnings),
                    field(sub == null ? null : sub.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH), "focalLength", 50, warnings),
                    field(sub == null ? null : sub.getDescription(ExifSubIFDDirectory.TAG_FNUMBER), "aperture", 50, warnings),
                    field(sub == null ? null : sub.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME), "shutterSpeed", 50, warnings),
                    field(sub == null ? null : sub.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT), "iso", 50, warnings));
            if (metadata.hasErrors()) warnings.add(new Warning("EXIF_PARSE_FAILED", null,
                    "部分 EXIF 元数据无法解析，已忽略异常字段"));
            return new Result(candidate.hasValues() ? candidate : null, distinct(warnings));
        } catch (Exception ignored) {
            return new Result(null, List.of(new Warning("EXIF_PARSE_FAILED", null,
                    "EXIF 元数据无法解析，媒体处理不受影响")));
        }
    }

    private LocalDateTime captureTime(ExifSubIFDDirectory directory, Clock clock, List<Warning> warnings) {
        if (directory == null) return null;
        String raw = directory.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (raw == null || raw.isBlank()) return null;
        try {
            LocalDateTime value = LocalDateTime.parse(raw.trim(), EXIF_TIME);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
            if (value.isAfter(now)) {
                warnings.add(new Warning("EXIF_CAPTURE_TIME_IN_FUTURE", "captureTime",
                        "EXIF 拍摄时间晚于当前时间，未写入有效参数"));
                return null;
            }
            return value;
        } catch (DateTimeParseException ignored) {
            warnings.add(new Warning("EXIF_FIELD_IGNORED", "captureTime",
                    "EXIF 拍摄时间格式无效，已忽略"));
            return null;
        }
    }

    private String field(String raw, String name, int max, List<Warning> warnings) {
        if (raw == null) return null;
        String value = UnicodeText.nfc(UnicodeText.trimUnicode(raw));
        if (value == null || value.isEmpty()) return null;
        if (UnicodeText.graphemeCount(value) > max || UnicodeText.containsForbiddenControl(value, false)
                || value.contains("\n") || value.contains("\r")) {
            warnings.add(new Warning("EXIF_FIELD_IGNORED", name, "EXIF 字段不符合展示约束，已忽略"));
            return null;
        }
        return value;
    }

    private List<Warning> distinct(List<Warning> warnings) {
        Set<Warning> unique = new LinkedHashSet<>(warnings);
        return List.copyOf(unique);
    }

    public static String encodeWarnings(List<Warning> warnings) {
        if (warnings == null || warnings.isEmpty()) return null;
        return warnings.stream().map(w -> w.code() + ":" + (w.field() == null ? "" : w.field()))
                .distinct().reduce((a, b) -> a + "," + b).orElse(null);
    }

    public static List<Warning> decodeWarnings(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<Warning> values = new ArrayList<>();
        for (String token : encoded.split(",")) {
            String[] parts = token.split(":", 2);
            String field = parts.length == 2 && !parts[1].isBlank() ? parts[1] : null;
            values.add(new Warning(parts[0], field, message(parts[0])));
        }
        return List.copyOf(values);
    }

    private static String message(String code) {
        return switch (code) {
            case "EXIF_CAPTURE_TIME_IN_FUTURE" -> "EXIF 拍摄时间晚于当前时间，未写入有效参数";
            case "EXIF_FIELD_IGNORED" -> "EXIF 字段不符合展示约束，已忽略";
            default -> "EXIF 元数据无法完整解析，媒体处理不受影响";
        };
    }

    public record Result(Candidate candidate, List<Warning> warnings) {
    }

    public record Candidate(LocalDateTime captureTime, String cameraBody, String lens,
                            String focalLength, String aperture, String shutterSpeed, String iso) {
        public boolean hasValues() {
            return captureTime != null || cameraBody != null || lens != null || focalLength != null
                    || aperture != null || shutterSpeed != null || iso != null;
        }
    }

    public record Warning(String code, String field, String message) {
    }
}
