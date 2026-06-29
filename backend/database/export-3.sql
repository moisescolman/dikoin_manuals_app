-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Versión del servidor:         12.3.2-MariaDB - MariaDB Server
-- SO del servidor:              Win64
-- HeidiSQL Versión:             12.17.0.7270
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Volcando estructura de base de datos para dikoin_manuales
CREATE DATABASE IF NOT EXISTS `dikoin_manuales` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci */;
USE `dikoin_manuales`;

-- Volcando estructura para tabla dikoin_manuales.app_users
CREATE TABLE IF NOT EXISTS `app_users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(160) NOT NULL,
  `full_name` varchar(120) NOT NULL,
  `password_hash` varchar(120) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4vj92ux8a2eehds1mdvmks473` (`email`),
  KEY `FK7sipqsfa0da9aj10gga1iqnk1` (`role_id`),
  CONSTRAINT `FK7sipqsfa0da9aj10gga1iqnk1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.assets
CREATE TABLE IF NOT EXISTS `assets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `asset_type` enum('DOCUMENT_SOURCE','EXTRACTED_IMAGE','IMAGE','LOGO','OTHER','PDF_EXPORT','PRODUCT_IMAGE','TEMPLATE_RESOURCE') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_size` bigint(20) DEFAULT NULL,
  `mime_type` varchar(120) DEFAULT NULL,
  `original_filename` varchar(255) NOT NULL,
  `storage_path` varchar(700) NOT NULL,
  `thumbnail_path` varchar(700) DEFAULT NULL,
  `stored_filename` varchar(255) NOT NULL,
  `manual_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1x90prq509l6ua86ext5ao1tj` (`manual_id`),
  KEY `idx_assets_type_filename` (`asset_type`,`original_filename`),
  CONSTRAINT `FK1x90prq509l6ua86ext5ao1tj` FOREIGN KEY (`manual_id`) REFERENCES `manuals` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.document_types
CREATE TABLE IF NOT EXISTS `document_types` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(10) NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.export_jobs
CREATE TABLE IF NOT EXISTS `export_jobs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `log_message` longtext DEFAULT NULL,
  `pdf_path` varchar(700) DEFAULT NULL,
  `status` enum('COMPLETED','FAILED','PENDING','PROCESSING') NOT NULL,
  `manual_version_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6qj216w168pa5ofcnwbdk6msd` (`manual_version_id`),
  CONSTRAINT `FK6qj216w168pa5ofcnwbdk6msd` FOREIGN KEY (`manual_version_id`) REFERENCES `manual_versions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.import_jobs
CREATE TABLE IF NOT EXISTS `import_jobs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `detected_images` int(11) DEFAULT NULL,
  `detected_sections` int(11) DEFAULT NULL,
  `detected_tables` int(11) DEFAULT NULL,
  `file_extension` varchar(80) DEFAULT NULL,
  `language_code` enum('EN','ES') NOT NULL,
  `log_message` longtext DEFAULT NULL,
  `source_filename` varchar(255) NOT NULL,
  `status` enum('COMPLETED','FAILED','PENDING','PROCESSING') NOT NULL,
  `stored_path` varchar(700) NOT NULL,
  `manual_version_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKelqyvv0tjl52mm6j85y5h4w12` (`manual_version_id`),
  CONSTRAINT `FKelqyvv0tjl52mm6j85y5h4w12` FOREIGN KEY (`manual_version_id`) REFERENCES `manual_versions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.manual_blocks
CREATE TABLE IF NOT EXISTS `manual_blocks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `block_type` enum('CHART','FORMULA','HEADING','IMAGE','INFO_BOX','NOTE','ORDERED_LIST','PAGE_BREAK','PARAGRAPH','TABLE','UNORDERED_LIST','WARNING') NOT NULL,
  `content_json` longtext NOT NULL,
  `plain_text` longtext DEFAULT NULL,
  `asset_id` bigint(20) DEFAULT NULL,
  `reusable_block_id` bigint(20) DEFAULT NULL,
  `reusable_fragment_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `language_code` enum('EN','ES') NOT NULL,
  `sort_order` int(11) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `section_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_manual_blocks_content` (`content_json`(255)),
  KEY `idx_manual_blocks_section_sort` (`section_id`,`sort_order`),
  KEY `idx_manual_blocks_type` (`block_type`),
  KEY `idx_manual_blocks_asset` (`asset_id`),
  KEY `idx_manual_blocks_reusable` (`reusable_block_id`),
  KEY `idx_manual_blocks_reusable_fragment` (`reusable_fragment_id`),
  CONSTRAINT `FKde5k4dbihwcvo4rlu89tnjk6h` FOREIGN KEY (`section_id`) REFERENCES `manual_sections` (`id`),
  CONSTRAINT `fk_manual_blocks_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`),
  CONSTRAINT `fk_manual_blocks_reusable` FOREIGN KEY (`reusable_block_id`) REFERENCES `reusable_blocks` (`id`),
  CONSTRAINT `fk_manual_blocks_reusable_fragment` FOREIGN KEY (`reusable_fragment_id`) REFERENCES `reusable_fragments` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3774 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.manual_sections
CREATE TABLE IF NOT EXISTS `manual_sections` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `completion_status` varchar(40) DEFAULT NULL,
  `visible` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) DEFAULT NULL,
  `section_number` varchar(40) DEFAULT NULL,
  `parent_section_id` bigint(20) DEFAULT NULL,
  `level` int(11) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL,
  `title_en` varchar(220) DEFAULT NULL,
  `title_es` varchar(220) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `manual_version_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_manual_sections_version_sort` (`manual_version_id`,`sort_order`),
  KEY `idx_manual_sections_parent_sort` (`parent_section_id`,`sort_order`),
  KEY `idx_manual_sections_visible` (`manual_version_id`,`visible`,`sort_order`),
  CONSTRAINT `FK5kp4u2cl845cn856hpajxm0x2` FOREIGN KEY (`manual_version_id`) REFERENCES `manual_versions` (`id`),
  CONSTRAINT `fk_manual_sections_parent` FOREIGN KEY (`parent_section_id`) REFERENCES `manual_sections` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=524 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.manual_versions
CREATE TABLE IF NOT EXISTS `manual_versions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `change_notes` varchar(600) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `en_ready` bit(1) NOT NULL,
  `es_ready` bit(1) NOT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `status` enum('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REVIEW') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version_number` varchar(40) NOT NULL,
  `manual_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrfo7h70evwghlcsenyx649wbf` (`manual_id`),
  CONSTRAINT `FKrfo7h70evwghlcsenyx649wbf` FOREIGN KEY (`manual_id`) REFERENCES `manuals` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.manuals
CREATE TABLE IF NOT EXISTS `manuals` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category` varchar(120) DEFAULT NULL,
  `document_type_id` bigint(20) DEFAULT NULL,
  `document_year` varchar(2) DEFAULT NULL,
  `document_version` varchar(2) DEFAULT NULL,
  `language_code` varchar(10) DEFAULT NULL,
  `code` varchar(100) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `title` varchar(220) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_id` bigint(20) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `title_en` varchar(220) DEFAULT NULL,
  `title_es` varchar(220) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdfd35hiakn54gcwq1hdlydrye` (`code`),
  KEY `FKp6lu30trgn3elpu1w5ionkqlg` (`product_id`),
  KEY `idx_manuals_enabled_deleted` (`enabled`,`deleted_at`),
  KEY `idx_manuals_title_es` (`title_es`),
  KEY `idx_manuals_title_en` (`title_en`),
  KEY `idx_manuals_document_type` (`document_type_id`),
  KEY `idx_manuals_document_fields` (`document_year`,`document_version`,`language_code`),
  CONSTRAINT `FKp6lu30trgn3elpu1w5ionkqlg` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_manuals_document_type` FOREIGN KEY (`document_type_id`) REFERENCES `document_types` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.notice_applications
CREATE TABLE IF NOT EXISTS `notice_applications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `apply_scope` enum('MANUAL','PRODUCT','SECTION') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `sort_order` int(11) DEFAULT NULL,
  `manual_id` bigint(20) DEFAULT NULL,
  `notice_template_id` bigint(20) NOT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  `section_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkgr4sit6lox5wsus8jgymidkj` (`notice_template_id`),
  KEY `idx_notice_applications_manual` (`manual_id`),
  KEY `idx_notice_applications_product` (`product_id`),
  KEY `idx_notice_applications_section` (`section_id`),
  CONSTRAINT `FKap4w7tilbrpl7jiflfge5ckcp` FOREIGN KEY (`section_id`) REFERENCES `manual_sections` (`id`),
  CONSTRAINT `FKigotk4ljilek0b0dft7lg699x` FOREIGN KEY (`manual_id`) REFERENCES `manuals` (`id`),
  CONSTRAINT `FKkgr4sit6lox5wsus8jgymidkj` FOREIGN KEY (`notice_template_id`) REFERENCES `notice_templates` (`id`),
  CONSTRAINT `FKmmnhmlr6kpa7ocn16wb35fu7y` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.notice_templates
CREATE TABLE IF NOT EXISTS `notice_templates` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(80) NOT NULL,
  `content_en` longtext DEFAULT NULL,
  `content_es` longtext NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `visible_title_es` varchar(220) DEFAULT NULL,
  `visible_title_en` varchar(220) DEFAULT NULL,
  `product_category` varchar(120) DEFAULT NULL,
  `product_codes` varchar(600) DEFAULT NULL,
  `type` enum('ALERT','NOTE','SUGGESTION','WARNING') NOT NULL,
  `title` varchar(220) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnwhu05fuydarain0fhkwy7me3` (`code`),
  KEY `idx_notice_templates_type_active` (`type`,`active`),
  KEY `idx_notice_templates_category` (`product_category`),
  KEY `idx_notice_templates_title` (`title`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.product_categories
CREATE TABLE IF NOT EXISTS `product_categories` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL,
  `name_es` varchar(180) NOT NULL,
  `name_en` varchar(180) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_categories_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.product_category_assignments
CREATE TABLE IF NOT EXISTS `product_category_assignments` (
  `product_id` bigint(20) NOT NULL,
  `category_id` bigint(20) NOT NULL,
  PRIMARY KEY (`product_id`,`category_id`),
  KEY `fk_product_category_assignments_category` (`category_id`),
  CONSTRAINT `fk_product_category_assignments_category` FOREIGN KEY (`category_id`) REFERENCES `product_categories` (`id`),
  CONSTRAINT `fk_product_category_assignments_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.product_families
CREATE TABLE IF NOT EXISTS `product_families` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL,
  `name_es` varchar(160) NOT NULL,
  `name_en` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_families_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.products
CREATE TABLE IF NOT EXISTS `products` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `category` varchar(120) DEFAULT NULL,
  `category_codes` varchar(120) DEFAULT NULL,
  `code` varchar(80) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(600) DEFAULT NULL,
  `description_es` varchar(600) DEFAULT NULL,
  `description_en` varchar(600) DEFAULT NULL,
  `family` varchar(120) DEFAULT NULL,
  `family_code` varchar(20) DEFAULT NULL,
  `family_id` bigint(20) DEFAULT NULL,
  `name` varchar(160) NOT NULL,
  `name_es` varchar(160) DEFAULT NULL,
  `name_en` varchar(160) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK57ivhy5aj3qfmdvl6vxdfjs4p` (`code`),
  KEY `idx_products_code_name` (`code`,`name`),
  KEY `FKd0y82kpa1ikaasb268xa1i9g9` (`family_id`),
  CONSTRAINT `FKd0y82kpa1ikaasb268xa1i9g9` FOREIGN KEY (`family_id`) REFERENCES `product_families` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.reusable_blocks
CREATE TABLE IF NOT EXISTS `reusable_blocks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(80) NOT NULL,
  `title` varchar(220) NOT NULL,
  `title_es` varchar(220) DEFAULT NULL,
  `title_en` varchar(220) DEFAULT NULL,
  `description` varchar(600) DEFAULT NULL,
  `description_es` varchar(600) DEFAULT NULL,
  `description_en` varchar(600) DEFAULT NULL,
  `reusable_type` varchar(30) NOT NULL DEFAULT 'SINGLE_BLOCK',
  `product_category` varchar(120) DEFAULT NULL,
  `product_codes` varchar(600) DEFAULT NULL,
  `content_json` longtext NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_reusable_blocks_active_updated` (`active`,`updated_at`),
  KEY `idx_reusable_blocks_code` (`code`),
  KEY `idx_reusable_blocks_category` (`product_category`),
  KEY `idx_reusable_blocks_type_active` (`reusable_type`,`active`),
  KEY `idx_reusable_blocks_library` (`reusable_type`,`active`,`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.reusable_fragments
CREATE TABLE IF NOT EXISTS `reusable_fragments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(80) NOT NULL,
  `title` varchar(220) NOT NULL,
  `description` varchar(600) DEFAULT NULL,
  `product_category` varchar(120) DEFAULT NULL,
  `product_codes` varchar(600) DEFAULT NULL,
  `content_json` longtext NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_reusable_fragments_active_updated` (`active`,`updated_at`),
  KEY `idx_reusable_fragments_code` (`code`),
  KEY `idx_reusable_fragments_category` (`product_category`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para vista dikoin_manuales.reusable_sections
-- Creando tabla temporal para superar errores de dependencia de VIEW
CREATE TABLE `reusable_sections` (
	`id` BIGINT(20) NOT NULL,
	`code` VARCHAR(1) NOT NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`title` VARCHAR(1) NOT NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`title_es` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`title_en` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`description` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`description_es` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`description_en` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`reusable_type` VARCHAR(1) NOT NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`product_category` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`product_codes` VARCHAR(1) NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`content_json` LONGTEXT NOT NULL COLLATE 'utf8mb4_uca1400_ai_ci',
	`active` TINYINT(1) NOT NULL,
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

-- Volcando estructura para tabla dikoin_manuales.roles
CREATE TABLE IF NOT EXISTS `roles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` varchar(120) NOT NULL,
  `name` enum('ADMIN','CLIENTE','REVISOR','TECNICO') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.template_assets
CREATE TABLE IF NOT EXISTS `template_assets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `template_id` bigint(20) NOT NULL,
  `asset_id` bigint(20) NOT NULL,
  `usage_type` varchar(40) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_template_assets_template` (`template_id`),
  KEY `idx_template_assets_asset` (`asset_id`),
  CONSTRAINT `fk_template_assets_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`),
  CONSTRAINT `fk_template_assets_template` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.template_versions
CREATE TABLE IF NOT EXISTS `template_versions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_json` longtext NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `version_number` varchar(40) NOT NULL,
  `template_id` bigint(20) NOT NULL,
  `active` bit(1) NOT NULL,
  `notes` varchar(600) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_versions_template_active` (`template_id`,`active`),
  CONSTRAINT `FK9gy13bl9n6n45605pb93htaxx` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Volcando estructura para tabla dikoin_manuales.templates
CREATE TABLE IF NOT EXISTS `templates` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `company_name` varchar(160) DEFAULT NULL,
  `contact_email` varchar(160) DEFAULT NULL,
  `contact_phone` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `footer_config_json` longtext DEFAULT NULL,
  `header_config_json` longtext DEFAULT NULL,
  `logo_path` varchar(700) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `website` varchar(180) DEFAULT NULL,
  `description` varchar(300) DEFAULT NULL,
  `logo_asset_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1nah70jfu9ck93htxiwym9c3b` (`name`),
  KEY `fk_templates_logo_asset` (`logo_asset_id`),
  CONSTRAINT `FKn56uj96g2dgct19cd0e17iewe` FOREIGN KEY (`logo_asset_id`) REFERENCES `assets` (`id`),
  CONSTRAINT `fk_templates_logo_asset` FOREIGN KEY (`logo_asset_id`) REFERENCES `assets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- La exportación de datos fue deseleccionada.

-- Eliminando tabla temporal y crear estructura final de VIEW
DROP TABLE IF EXISTS `reusable_sections`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `reusable_sections` AS SELECT *
FROM reusable_blocks
WHERE reusable_type = 'SINGLE_BLOCK' 
;

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
