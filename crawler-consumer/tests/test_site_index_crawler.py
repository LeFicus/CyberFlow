import unittest

from crawlers.site_index_crawler import normalize_ymd


class SiteIndexCrawlerTest(unittest.TestCase):
    def test_ymd_compact_date_is_not_parsed_as_unix_timestamp(self):
        self.assertEqual("2026-09-14", normalize_ymd("20260914"))

    def test_ymd_iso_date_is_kept_as_snapshot_time(self):
        self.assertEqual("2026-09-14", normalize_ymd("2026-09-14"))

    def test_invalid_ymd_is_missing(self):
        self.assertIsNone(normalize_ymd("20261340"))


if __name__ == "__main__":
    unittest.main()
