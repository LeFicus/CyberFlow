import json
import unittest

from consumers.order_identity import build_dedupe_keys, normalize_address, shipping_address_json


def row(order_id, email, street, postal="10001", valid=0):
    return {
        "id": order_id,
        "user_group": "B",
        "create_time": "2026-09-09 12:00:00",
        "product_host": "www.example.com",
        "shipping_email": email,
        "shipping_address": json.dumps({
            "country": "US", "state": "NY", "city": "New York",
            "address": street, "zipCode": postal,
        }),
        "is_valid": valid,
    }


class OrderIdentityTest(unittest.TestCase):
    def test_email_or_address_matches_are_transitively_merged(self):
        rows = [
            row(1, "first@example.com", "1 Main St"),
            row(2, "first@example.com", "2 Main St"),
            row(3, "second@example.com", "2 Main St"),
            row(4, "third@example.com", "3 Main St"),
        ]

        keys = build_dedupe_keys(rows)

        self.assertEqual(keys[("B", 1)], keys[("B", 2)])
        self.assertEqual(keys[("B", 2)], keys[("B", 3)])
        self.assertNotEqual(keys[("B", 3)], keys[("B", 4)])
        self.assertEqual(2, len(set(keys.values())))

    def test_identity_is_scoped_by_day_group_and_site(self):
        rows = [row(1, "same@example.com", "1 Main St")]
        rows.append({**row(2, "same@example.com", "1 Main St"), "product_host": "other.example"})
        rows.append({**row(3, "same@example.com", "1 Main St"), "create_time": "2026-09-10 01:00:00"})

        self.assertEqual(3, len(set(build_dedupe_keys(rows).values())))

    def test_address_normalization_ignores_case_spacing_and_punctuation(self):
        left = {"country": "US", "state": "NY", "city": "New York", "address": " 81 Barton St.", "zipCode": "04769"}
        right = {"country": "us", "state": "ny", "city": "new-york", "address": "81  BARTON ST", "zipCode": "04769"}

        self.assertEqual(normalize_address(left), normalize_address(json.dumps(right)))
        self.assertEqual(left, json.loads(shipping_address_json(left)))


if __name__ == "__main__":
    unittest.main()
