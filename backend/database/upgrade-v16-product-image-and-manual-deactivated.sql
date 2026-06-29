USE dikoin_manuales;

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS product_image_asset_id BIGINT NULL AFTER description_en;

CREATE INDEX IF NOT EXISTS idx_products_product_image_asset_id
  ON products(product_image_asset_id);

ALTER TABLE products
  ADD CONSTRAINT fk_products_product_image_asset
  FOREIGN KEY (product_image_asset_id)
  REFERENCES assets(id)
  ON DELETE SET NULL;

-- Los estados de manual se guardan como VARCHAR por JPA EnumType.STRING.
-- No hace falta alterar la columna si no existe un CHECK externo:
-- nuevo valor soportado: DEACTIVATED.
