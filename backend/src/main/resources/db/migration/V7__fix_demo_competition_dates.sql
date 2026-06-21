UPDATE competitions 
SET starts_at = NOW() - INTERVAL '1 hour', 
    ends_at = NOW() + INTERVAL '7 days' 
WHERE id = '40000000-0000-0000-0000-000000000001';
