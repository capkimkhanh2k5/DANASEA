-- Preserve the previous 36 integer digits while retaining precise venue coordinates.
-- Existing values remain unchanged; previously rounded digits cannot be recovered.
ALTER TABLE services
    ALTER COLUMN latitude TYPE numeric(46,10),
    ALTER COLUMN longitude TYPE numeric(46,10);
