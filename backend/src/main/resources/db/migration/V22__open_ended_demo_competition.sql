-- Make the seeded demo competition open-ended so demo tasks do not silently expire 7 days after deployment
UPDATE competitions
SET end_date = NULL
WHERE id = '40000000-0000-0000-0000-000000000001'
  AND status = 'ACTIVE'
  AND end_date IS NOT NULL
  AND end_date <= created_at + INTERVAL '8 days';
