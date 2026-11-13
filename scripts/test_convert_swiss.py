import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from convert_swiss import convert, NUTRIENTS


class ConversionTest(unittest.TestCase):
    def run_conversion(self, changes):
        fields = ['ID', 'Name', 'Synonyms', 'Category', 'Matrix unit', *NUTRIENTS.values()]
        headers = {str(i): name for i, name in enumerate(fields)}
        data = dict(zip(fields, ['123', 'Test food', '', 'Test', 'per 100g edible portion', *(['0'] * 7)]))
        data.update(changes)
        with tempfile.TemporaryDirectory() as d:
            source = Path(d) / 'source.xlsx'
            source.write_bytes(b'test fixture')
            output = Path(d) / 'foods.csv'
            with patch('convert_swiss.rows', return_value=iter([
                {'A': 'Generic foods V 7.1'}, {'A': 'ID', **headers},
                {key: data[value] for key, value in headers.items()}])):
                # The synthetic header needs its ID in column A, like the official workbook.
                return convert(source, output)

    def test_real_zero_is_retained(self):
        self.assertEqual(self.run_conversion({})['imported_rows'], 1)

    def test_missing_and_invalid_nutrients_are_excluded(self):
        for value in ['n.d.', '', 'NaN', 'inf', '-1', '<0.1']:
            with self.subTest(value=value):
                report = self.run_conversion({'Sugars (g)': value})
                self.assertEqual(report['excluded_rows'], 1)
                self.assertEqual(report['imported_rows'], 0)

    def test_non_100g_basis_is_excluded(self):
        self.assertEqual(self.run_conversion({'Matrix unit': 'per 100ml'})['imported_rows'], 0)


if __name__ == '__main__':
    unittest.main()
