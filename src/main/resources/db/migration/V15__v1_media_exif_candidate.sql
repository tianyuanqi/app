ALTER TABLE media_asset
    ADD COLUMN exif_capture_time DATETIME(6) NULL AFTER frame_count,
    ADD COLUMN exif_camera_body VARCHAR(100) NULL AFTER exif_capture_time,
    ADD COLUMN exif_lens VARCHAR(100) NULL AFTER exif_camera_body,
    ADD COLUMN exif_focal_length VARCHAR(50) NULL AFTER exif_lens,
    ADD COLUMN exif_aperture VARCHAR(50) NULL AFTER exif_focal_length,
    ADD COLUMN exif_shutter_speed VARCHAR(50) NULL AFTER exif_aperture,
    ADD COLUMN exif_iso_value VARCHAR(50) NULL AFTER exif_shutter_speed,
    ADD COLUMN exif_warning_codes VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER exif_iso_value;
