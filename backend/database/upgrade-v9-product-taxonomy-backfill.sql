USE dikoin_manuales;

START TRANSACTION;

-- Completa textos ES para productos legacy o parciales.
UPDATE products
SET
  name_es = COALESCE(NULLIF(TRIM(name_es), ''), name),
  description_es = COALESCE(NULLIF(TRIM(description_es), ''), description)
WHERE
  name_es IS NULL OR TRIM(name_es) = ''
  OR description_es IS NULL OR TRIM(description_es) = '';

-- Si falta family_code, intenta derivarlo desde la etiqueta legacy "XX - Nombre familia".
UPDATE products
SET family_code = TRIM(SUBSTRING_INDEX(family, ' - ', 1))
WHERE
  (family_code IS NULL OR TRIM(family_code) = '')
  AND family IS NOT NULL
  AND TRIM(family) <> ''
  AND INSTR(family, ' - ') > 0;

-- Enlaza las familias nuevas por codigo.
UPDATE products p
JOIN product_families f
  ON UPPER(TRIM(f.code)) COLLATE utf8mb4_unicode_ci
   = UPPER(TRIM(p.family_code)) COLLATE utf8mb4_unicode_ci
SET p.family_id = f.id
WHERE
  p.family_code IS NOT NULL
  AND TRIM(p.family_code) <> ''
  AND (p.family_id IS NULL OR p.family_id <> f.id);

-- Reconstruye la tabla de asignaciones a partir de products.category_codes.
-- Solo limpia productos que realmente tienen category_codes informada.
DELETE pca
FROM product_category_assignments pca
JOIN products p ON p.id = pca.product_id
WHERE p.category_codes IS NOT NULL AND TRIM(p.category_codes) <> '';

INSERT IGNORE INTO product_category_assignments (product_id, category_id)
WITH RECURSIVE split_codes AS (
  SELECT
    p.id AS product_id,
    TRIM(SUBSTRING_INDEX(p.category_codes, ',', 1)) AS category_code,
    CASE
      WHEN INSTR(p.category_codes, ',') > 0
        THEN TRIM(SUBSTRING(p.category_codes, INSTR(p.category_codes, ',') + 1))
      ELSE ''
    END AS rest
  FROM products p
  WHERE p.category_codes IS NOT NULL AND TRIM(p.category_codes) <> ''

  UNION ALL

  SELECT
    product_id,
    TRIM(SUBSTRING_INDEX(rest, ',', 1)) AS category_code,
    CASE
      WHEN INSTR(rest, ',') > 0
        THEN TRIM(SUBSTRING(rest, INSTR(rest, ',') + 1))
      ELSE ''
    END AS rest
  FROM split_codes
  WHERE rest <> ''
)
SELECT
  s.product_id,
  c.id
FROM split_codes s
JOIN product_categories c
  ON UPPER(TRIM(c.code)) COLLATE utf8mb4_unicode_ci
   = UPPER(TRIM(s.category_code)) COLLATE utf8mb4_unicode_ci
WHERE s.category_code <> '';

COMMIT;

-- Verificaciones rapidas recomendadas:
-- 1) Familias sin enlazar pese a tener family_code:
--    SELECT code, family_code FROM products
--    WHERE family_code IS NOT NULL AND TRIM(family_code) <> '' AND family_id IS NULL;
--
-- 2) Productos con categorias declaradas pero sin relaciones:
--    SELECT p.code, p.category_codes
--    FROM products p
--    LEFT JOIN product_category_assignments pca ON pca.product_id = p.id
--    WHERE p.category_codes IS NOT NULL AND TRIM(p.category_codes) <> ''
--    GROUP BY p.id, p.code, p.category_codes
--    HAVING COUNT(pca.category_id) = 0;
--
-- 3) Conteos esperados:
--    SELECT COUNT(*) AS families FROM product_families;
--    SELECT COUNT(*) AS categories FROM product_categories;
--    SELECT COUNT(*) AS assignments FROM product_category_assignments;
