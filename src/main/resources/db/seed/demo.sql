BEGIN;

SELECT pg_advisory_xact_lock(hashtextextended('scheduler:schema', 0));

INSERT INTO dealership (id, name, timezone, opens_at, closes_at) VALUES
    (1, 'Keyloop Motors London', 'Europe/London', '08:00', '18:00'),
    (2, 'Keyloop Motors Saigon', 'Asia/Ho_Chi_Minh', '07:30', '17:30')
ON CONFLICT DO NOTHING;

INSERT INTO service_type (id, code, name, duration_minutes, required_skill, required_bay_type) VALUES
    (1, 'OIL_CHANGE',      'Oil and filter change', 60,  'GENERAL_SERVICE', 'GENERAL'),
    (2, 'BRAKE_SERVICE',   'Brake pads and discs',  120, 'BRAKES',          'GENERAL'),
    (3, 'WHEEL_ALIGNMENT', 'Wheel alignment',       90,  'ALIGNMENT',       'ALIGNMENT'),
    (4, 'ANNUAL_SERVICE',  'Annual service',        180, 'GENERAL_SERVICE', 'GENERAL')
ON CONFLICT DO NOTHING;

INSERT INTO service_bay (id, dealership_id, name, bay_type) VALUES
    (1, 1, 'Bay 1',         'GENERAL'),
    (2, 1, 'Bay 2',         'GENERAL'),
    (3, 1, 'Alignment Bay', 'ALIGNMENT'),
    (4, 2, 'Bay A',         'GENERAL'),
    (5, 2, 'Bay B',         'GENERAL')
ON CONFLICT DO NOTHING;

INSERT INTO technician (id, dealership_id, name) VALUES
    (1, 1, 'Alice Morgan'),
    (2, 1, 'Ben Carter'),
    (3, 1, 'Chloe Evans'),
    (4, 2, 'Nguyen Van An'),
    (5, 2, 'Tran Thi Binh')
ON CONFLICT DO NOTHING;

INSERT INTO technician_skill (technician_id, skill) VALUES
    (1, 'GENERAL_SERVICE'), (1, 'BRAKES'),
    (2, 'GENERAL_SERVICE'), (2, 'ALIGNMENT'),
    (3, 'BRAKES'),          (3, 'ALIGNMENT'),
    (4, 'GENERAL_SERVICE'), (4, 'BRAKES'),
    (5, 'GENERAL_SERVICE')
ON CONFLICT DO NOTHING;

INSERT INTO technician_shift (technician_id, starts_at, ends_at)
SELECT t.id,
       (day + s.starts_at) AT TIME ZONE d.timezone,
       (day + s.ends_at) AT TIME ZONE d.timezone
FROM technician t
JOIN dealership d ON d.id = t.dealership_id
JOIN (VALUES (1, TIME '08:00', TIME '16:00'),
             (2, TIME '10:00', TIME '18:00'),
             (3, TIME '08:00', TIME '16:00'),
             (4, TIME '07:30', TIME '17:30'),
             (5, TIME '07:30', TIME '17:30')) AS s (technician_id, starts_at, ends_at)
    ON s.technician_id = t.id
CROSS JOIN generate_series(current_date::timestamp - INTERVAL '1 day',
                           current_date::timestamp + INTERVAL '90 days',
                           INTERVAL '1 day') AS day
WHERE extract(isodow FROM day) < 7
ON CONFLICT DO NOTHING;

INSERT INTO customer (id, name, email) VALUES
    (1, 'Olivia Smith', 'olivia@example.com'),
    (2, 'James Brown',  'james@example.com'),
    (3, 'Le Minh Chau', 'chau@example.com'),
    (4, 'Demo Fleet Leasing', 'fleet@example.com')
ON CONFLICT DO NOTHING;

INSERT INTO vehicle (id, customer_id, vin, make, model) VALUES
    (1, 1, 'WVWZZZ1KZAW000001', 'Volkswagen', 'Golf'),
    (2, 1, 'WBA3A5C50CF000002', 'BMW',        '320d'),
    (3, 2, 'SALGA2EF8EA000003', 'Land Rover', 'Range Rover'),
    (4, 3, 'MHFXX8GF5K0000004', 'Toyota',     'Vios')
ON CONFLICT DO NOTHING;

INSERT INTO vehicle (id, customer_id, vin, make, model)
SELECT 1000 + n, 4, 'DEMOFLEET' || lpad(n::text, 8, '0'), 'Toyota', 'Corolla'
FROM generate_series(1, 1000) AS n
ON CONFLICT DO NOTHING;

COMMIT;
