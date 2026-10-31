import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'check_test_reports.py'


class TestReportGateTest(unittest.TestCase):
    def check(self, xml):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'TEST-example.xml').write_text(xml)
            return subprocess.run([sys.executable, str(SCRIPT), str(root), '--required', 'CriticalTest'], capture_output=True, text=True, check=False)

    def test_passing_critical_test_is_accepted(self):
        result = self.check('<testsuite><testcase classname="CriticalTest" name="works"/></testsuite>')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_skipped_or_failed_critical_test_is_rejected(self):
        for xml in ['<testsuite/>', '<testsuite><testcase classname="OtherTest" name="works"/></testsuite>',
                    '<testsuite><testcase classname="CriticalTest" name="works"><skipped/></testcase></testsuite>',
                    '<testsuite><testcase classname="CriticalTest" name="works"><failure/></testcase></testsuite>']:
            with self.subTest(xml=xml):
                self.assertEqual(self.check(xml).returncode, 1)


if __name__ == '__main__':
    unittest.main()
