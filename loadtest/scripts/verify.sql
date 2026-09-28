SELECT 'bay_overlaps (must be 0)' AS check_name, count(*) AS value
  FROM appointment a JOIN appointment b
    ON a.service_bay_id = b.service_bay_id AND a.id < b.id AND a.slot && b.slot
 WHERE a.status = 'CONFIRMED' AND b.status = 'CONFIRMED'
UNION ALL
SELECT 'technician_overlaps (must be 0)', count(*)
  FROM appointment a JOIN appointment b
    ON a.technician_id = b.technician_id AND a.id < b.id AND a.slot && b.slot
 WHERE a.status = 'CONFIRMED' AND b.status = 'CONFIRMED'
UNION ALL
SELECT 'hot_slots_with_several_winners (must be 0)', count(*)
  FROM (SELECT slot FROM appointment WHERE dealership_id = 51 AND status = 'CONFIRMED'
         GROUP BY slot HAVING count(*) > 1) s
UNION ALL
SELECT 'hot_slots_won (info)', count(*) FROM appointment WHERE dealership_id = 51 AND status = 'CONFIRMED'
UNION ALL
SELECT 'confirmed_total (info, compare with k6 201 count)', count(*) FROM appointment WHERE status = 'CONFIRMED';
