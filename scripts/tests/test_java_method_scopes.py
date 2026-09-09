import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from repository_inventory.java_scanner import duplicate_method_signatures


class JavaMethodScopeTest(unittest.TestCase):
    def test_anonymous_siblings_are_distinct(self):
        source = '''class Host {
          Object a = new Owner(call(1, 2)) { public boolean ready() { return true; } };
          Object b = new Owner() { public boolean ready() { return false; } };
          public boolean ready() { return true; }
        }'''
        self.assertEqual([], duplicate_method_signatures(source))

    def test_duplicates_inside_anonymous_type_still_fail(self):
        source = '''class Host { Object a = new Owner() {
          public boolean ready() { return true; }
          public boolean ready() { return false; }
        }; }'''
        found = duplicate_method_signatures(source)
        self.assertEqual(1, len(found))
        self.assertTrue(found[0][0].startswith('anonymous@'))
        self.assertEqual(('ready', ()), found[0][1:])

    def test_outer_duplicates_across_nested_type_still_fail(self):
        source = '''class Host {
          public void run() { }
          record Nested(int value) { public void run() { } }
          public void run() { }
        }'''
        self.assertEqual([('Host', 'run', ())], duplicate_method_signatures(source))

    def test_nested_same_names_and_overloads(self):
        source = '''class Host {
          class First { class Same { public void run() { } } }
          class Second { class Same { public void run() { } } }
          public void run(int value) { }
          public void run(String value) { }
          public void run(final int other) { }
        }'''
        self.assertEqual([('Host', 'run', ('int',))], duplicate_method_signatures(source))

    def test_comments_strings_and_text_blocks_do_not_change_owner(self):
        source = '''class Host {
          String fake = "class Fake { public void run() { }";
          String block = """class Other { public void run() { }""";
          // class Comment { public void run() { }
          /* public void run() { } */
          public void run() { }
          public void run() { }
        }'''
        self.assertEqual([('Host', 'run', ())], duplicate_method_signatures(source))

    def test_real_native_adapters_have_independent_owner_contracts(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / 'src/main/java/hu/taliann/icesmp/trash/TrashRelicRuntime.java').read_text()
        self.assertEqual([], duplicate_method_signatures(source))


if __name__ == '__main__':
    unittest.main()
