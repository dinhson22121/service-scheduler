BEGIN;

SELECT pg_advisory_xact_lock(hashtextextended('scheduler:schema', 0));
SET LOCAL statement_timeout = 0;

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE technician_shift
    ADD COLUMN IF NOT EXISTS shift tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED;
ALTER TABLE appointment
    ADD COLUMN IF NOT EXISTS slot tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED;
ALTER TABLE appointment
    DROP COLUMN IF EXISTS idempotency_key,
    DROP COLUMN IF EXISTS request_hash;

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN SELECT * FROM (VALUES
        (1,  'dealership',       'dealership_hours_ck',               'CHECK (opens_at < closes_at)'),
        (2,  'service_type',     'service_type_code_uq',              'UNIQUE (code)'),
        (3,  'service_type',     'service_type_duration_ck',          'CHECK (duration_minutes > 0)'),
        (4,  'service_bay',      'service_bay_dealership_fk',         'FOREIGN KEY (dealership_id) REFERENCES dealership (id)'),
        (5,  'service_bay',      'service_bay_dealership_uq',         'UNIQUE (id, dealership_id)'),
        (6,  'technician',       'technician_dealership_fk',          'FOREIGN KEY (dealership_id) REFERENCES dealership (id)'),
        (7,  'technician',       'technician_dealership_uq',          'UNIQUE (id, dealership_id)'),
        (8,  'technician_skill', 'technician_skill_technician_fk',    'FOREIGN KEY (technician_id) REFERENCES technician (id)'),
        (9,  'technician_shift', 'technician_shift_technician_fk',    'FOREIGN KEY (technician_id) REFERENCES technician (id)'),
        (10, 'technician_shift', 'technician_shift_ck',               'CHECK (starts_at < ends_at)'),
        (11, 'technician_shift', 'technician_shift_no_overlap',       'EXCLUDE USING gist (technician_id WITH =, shift WITH &&)'),
        (12, 'vehicle',          'vehicle_customer_fk',               'FOREIGN KEY (customer_id) REFERENCES customer (id)'),
        (13, 'vehicle',          'vehicle_vin_uq',                    'UNIQUE (vin)'),
        (14, 'vehicle',          'vehicle_customer_uq',               'UNIQUE (id, customer_id)'),
        (17, 'appointment',      'appointment_slot_ck',               'CHECK (starts_at < ends_at)'),
        (18, 'appointment',      'appointment_dealership_fk',         'FOREIGN KEY (dealership_id) REFERENCES dealership (id)'),
        (19, 'appointment',      'appointment_service_type_fk',       'FOREIGN KEY (service_type_id) REFERENCES service_type (id)'),
        (20, 'appointment',      'appointment_vehicle_fk',            'FOREIGN KEY (vehicle_id, customer_id) REFERENCES vehicle (id, customer_id)'),
        (21, 'appointment',      'appointment_bay_fk',                'FOREIGN KEY (service_bay_id, dealership_id) REFERENCES service_bay (id, dealership_id)'),
        (22, 'appointment',      'appointment_technician_fk',         'FOREIGN KEY (technician_id, dealership_id) REFERENCES technician (id, dealership_id)'),
        (23, 'appointment',      'appointment_no_bay_overlap',        'EXCLUDE USING gist (service_bay_id WITH =, slot WITH &&) WHERE (status = ''CONFIRMED'')'),
        (24, 'appointment',      'appointment_no_technician_overlap', 'EXCLUDE USING gist (technician_id WITH =, slot WITH &&) WHERE (status = ''CONFIRMED'')'),
        (25, 'appointment',      'appointment_no_vehicle_overlap',    'EXCLUDE USING gist (vehicle_id WITH =, slot WITH &&) WHERE (status = ''CONFIRMED'')')
    ) AS t (position, table_name, constraint_name, definition) ORDER BY position
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = c.constraint_name) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s', c.table_name, c.constraint_name, c.definition);
        END IF;
    END LOOP;
END
$$;

CREATE INDEX IF NOT EXISTS service_bay_dealership_type_idx ON service_bay (dealership_id, bay_type);
CREATE INDEX IF NOT EXISTS technician_skill_skill_idx ON technician_skill (skill, technician_id);
CREATE INDEX IF NOT EXISTS appointment_dealership_slot_idx ON appointment USING gist (dealership_id, slot);

COMMIT;
