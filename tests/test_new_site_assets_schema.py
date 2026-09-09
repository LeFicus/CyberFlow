"""Regression checks for the new-site brand asset migration."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class NewSiteAssetSchemaTests(unittest.TestCase):
    def test_migration_has_storage_metadata_cascade_and_permission(self):
        migration = (ROOT / "script" / "migrations" / "20260901_add_new_site_assets.sql").read_text("utf-8")
        self.assertIn("CREATE TABLE IF NOT EXISTS new_site_asset", migration)
        self.assertIn("FOREIGN KEY (site_id) REFERENCES new_site(id) ON DELETE CASCADE", migration)
        self.assertIn("storage_key", migration)
        self.assertNotIn("LONGBLOB", migration.upper())
        self.assertIn("newsite:asset", migration)

    def test_container_mounts_a_persistent_asset_volume(self):
        compose = (ROOT / "docker-compose.yml").read_text("utf-8")
        self.assertIn("SITE_ASSET_DIRECTORY: /app/site-assets", compose)
        self.assertIn("site_assets:/app/site-assets", compose)


if __name__ == "__main__":
    unittest.main()
