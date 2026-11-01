import re
import unittest
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]


def can_crawl(policy, url):
    """Evaluate this site's single wildcard-agent group using Google's longest match."""
    parsed = urlsplit(url)
    target = parsed.path + (('?' + parsed.query) if parsed.query else '')
    matches = []
    for line in policy.splitlines():
        name, _, value = line.partition('#')[0].partition(':')
        name, value = name.strip().lower(), value.strip()
        if name not in ('allow', 'disallow') or not value:
            continue
        exact = value.endswith('$')
        path = value[:-1] if exact else value
        matched = target == path if exact else target.startswith(path)
        if matched:
            matches.append((len(path.encode()), name == 'allow'))
    return max(matches, default=(0, True))[1]


class PublicCrawlPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = (ROOT / 'frontend/public/robots.txt').read_text()

    def test_google_can_load_public_data_needed_to_render_published_tours(self):
        for path in (
            '/', '/explore', '/search?category=lan-ngam-san-ho',
            '/tour/cau-ca-bien-son-tra-danafi', '/sitemap.xml',
            '/assets/index-example.js',
            '/api/services', '/api/services?page=0&size=30',
            '/api/services/a6f4ab9c-4794-542b-a443-e3013e6bbe47',
            '/api/services/by-slug/cau-ca-bien-son-tra-danafi',
            '/api/services/a6f4ab9c-4794-542b-a443-e3013e6bbe47/reviews?page=0&size=10',
            '/api/categories', '/api/categories?lang=en',
            '/api/categories/cheo-sup', '/api/flash-sales/active',
            '/api/flash-sales/active?lang=en',
        ):
            with self.subTest(path=path):
                self.assertTrue(can_crawl(self.policy, path), path)

    def test_private_and_unrelated_api_namespaces_stay_blocked(self):
        for path in (
            '/api/admin/users', '/api/auth/me', '/api/bookings', '/api/orders',
            '/api/payments', '/api/vendors', '/api/v1/catalog',
            '/api/services-admin', '/api/services-internal/secret',
            '/api/categories-admin', '/api/flash-sales/active-internal',
            '/admin', '/admin/users', '/vendor', '/vendor/services',
            '/profile', '/profile/orders', '/auth', '/auth/forgot-password',
            '/cart', '/checkout', '/payment/vnpay-return',
        ):
            with self.subTest(path=path):
                self.assertFalse(can_crawl(self.policy, path), path)

    def test_production_serves_the_same_policy_without_rebuilding_the_image(self):
        config = (ROOT / 'frontend/nginx.prod.conf').read_text()
        location = re.search(r'location\s+=\s+/robots\.txt\s*\{([^}]+)\}', config)
        self.assertIsNotNone(location, 'Read-only production needs a reloadable robots response')
        self.assertRegex(location.group(1), r'return\s+200\s+\$public_robots_txt\s*;')
        response = re.search(r"geo\s+\$public_robots_txt\s*\{\s*default\s+'([^']*)'\s*;\s*\}", config)
        self.assertIsNotNone(response)
        self.assertEqual(response.group(1), self.policy)

    def test_policy_keeps_one_global_agent_and_one_public_sitemap(self):
        directives = [line.strip() for line in self.policy.splitlines() if line.strip() and not line.startswith('#')]
        self.assertEqual([line for line in directives if line.lower().startswith('user-agent:')], ['User-agent: *'])
        self.assertEqual([line for line in directives if line.lower().startswith('sitemap:')], [
            'Sitemap: https://danasea.tailfc3563.ts.net/sitemap.xml',
        ])


if __name__ == '__main__':
    unittest.main()
