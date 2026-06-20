ALTER TABLE challenges ADD COLUMN flag VARCHAR(255);
UPDATE challenges SET flag = 'CTF{placeholder}' WHERE flag IS NULL;
ALTER TABLE challenges ALTER COLUMN flag SET NOT NULL;
