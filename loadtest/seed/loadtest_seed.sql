BEGIN;

SELECT pg_advisory_xact_lock(hashtextextended('scheduler:schema', 0));
SET LOCAL statement_timeout = 0;

INSERT INTO dealership (id, name, timezone, opens_at, closes_at)
SELECT d, 'Load Dealer ' || d, 'UTC', '08:00', '18:00'
FROM generate_series(1, 51) AS d
ON CONFLICT DO NOTHING;

INSERT INTO service_type (id, code, name, duration_minutes, required_skill, required_bay_type) VALUES
    (1, 'OIL_CHANGE',      'Oil and filter change', 60,  'GENERAL_SERVICE', 'GENERAL'),
    (2, 'BRAKE_SERVICE',   'Brake pads and discs',  120, 'BRAKES',          'GENERAL'),
    (3, 'WHEEL_ALIGNMENT', 'Wheel alignment',       90,  'ALIGNMENT',       'ALIGNMENT'),
    (4, 'ANNUAL_SERVICE',  'Annual service',        180, 'GENERAL_SERVICE', 'GENERAL')
ON CONFLICT DO NOTHING;

INSERT INTO service_bay (id, dealership_id, name, bay_type)
SELECT (d - 1) * 10 + n, d, 'Bay ' || n, CASE WHEN n <= 8 THEN 'GENERAL' ELSE 'ALIGNMENT' END
FROM generate_series(1, 50) AS d, generate_series(1, 10) AS n
UNION ALL
SELECT 501, 51, 'Hot Alignment Bay', 'ALIGNMENT'
ON CONFLICT DO NOTHING;

INSERT INTO technician (id, dealership_id, name)
SELECT (d - 1) * 10 + n, d, 'Tech ' || d || '-' || n
FROM generate_series(1, 51) AS d, generate_series(1, 10) AS n
WHERE d <= 50 OR n <= 2
ON CONFLICT DO NOTHING;

INSERT INTO technician_skill (technician_id, skill)
SELECT t.id, s.skill
FROM technician t
CROSS JOIN LATERAL (SELECT (t.id - 1) % 10 + 1 AS n) AS pos
JOIN (VALUES ('GENERAL_SERVICE', 1, 8), ('BRAKES', 1, 6), ('BRAKES', 9, 10), ('ALIGNMENT', 7, 10))
    AS s (skill, from_n, to_n) ON pos.n BETWEEN s.from_n AND s.to_n
WHERE t.dealership_id <= 50
UNION ALL
SELECT id, 'ALIGNMENT' FROM technician WHERE dealership_id = 51
ON CONFLICT DO NOTHING;

INSERT INTO technician_shift (technician_id, starts_at, ends_at)
SELECT t.id, (day + TIME '08:00') AT TIME ZONE 'UTC', (day + TIME '18:00') AT TIME ZONE 'UTC'
FROM technician t
CROSS JOIN generate_series(current_date::timestamp - INTERVAL '1 day',
                           current_date::timestamp + INTERVAL '90 days',
                           INTERVAL '1 day') AS day
WHERE extract(isodow FROM day) < 7
ON CONFLICT DO NOTHING;

INSERT INTO customer (id, name, email)
SELECT c, 'Customer ' || c, NULL
FROM generate_series(1, 2000) AS c
ON CONFLICT DO NOTHING;

INSERT INTO vehicle (id, customer_id, vin, make, model)
SELECT c, c, 'LOADTEST' || lpad(c::text, 9, '0'), 'Make', 'Model'
FROM generate_series(1, 2000) AS c
ON CONFLICT DO NOTHING;

COMMIT;
