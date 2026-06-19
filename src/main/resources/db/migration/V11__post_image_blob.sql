-- Store post images in MySQL so they survive Render.com ephemeral disk redeploys.
ALTER TABLE posts ADD COLUMN image_data LONGBLOB NULL;
ALTER TABLE posts ADD COLUMN image_type VARCHAR(50) NULL;
