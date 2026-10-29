import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_configs import audit_line


class AuditConfigsTest(unittest.TestCase):
    def test_valid_vless_uri_is_plausible(self):
        self.assertEqual([], audit_line(
            "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls#test"
        ))

    def test_invalid_port_is_reported_without_echoing_secret(self):
        issues = audit_line("trojan://supersecret@example.com:99999#test")
        self.assertIn("port outside valid range", issues)

    def test_unsupported_plain_text_is_reported(self):
        self.assertIn("no supported proxy URI found", audit_line("not a proxy config"))

    def test_control_characters_are_flagged(self):
        issues = audit_line("vless://id@example.com:443\x01")
        self.assertIn("contains control characters", issues)

    def test_vmess_base64_uri_is_not_mistaken_for_missing_host(self):
        self.assertEqual([], audit_line("vmess://eyJhZGQiOiJleGFtcGxlLmNvbSJ9"))


if __name__ == "__main__":
    unittest.main()
