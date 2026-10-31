import importlib.util
import sys
import unittest
from pathlib import Path


class AvailabilityTest(unittest.TestCase):
    def test_sampled_outage_includes_an_unrecovered_final_failure(self):
        sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
        spec = importlib.util.spec_from_file_location('availability', Path(__file__).resolve().parents[1] / 'availability.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.longest_outage([(0, True), (1, False), (4, False), (7, True)], 9), 6)
        self.assertEqual(module.longest_outage([(0, True), (2, False)], 12), 10)
        self.assertEqual(module.longest_outage([(0, True), (2, True)], 12), 0)


if __name__ == '__main__':
    unittest.main()
