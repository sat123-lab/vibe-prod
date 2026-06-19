-- Hibernate @Lob on MySQL may create BLOB (65KB max). Widen to LONGBLOB (4GB).
ALTER TABLE posts MODIFY COLUMN image_data LONGBLOB NULL;
