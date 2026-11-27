import unittest

import launcher_next_release as r


class NextVersion(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(r.next_version([], True), "0.0.1-beta.1")
        self.assertEqual(r.next_version([], False), "0.0.1")

    def test_beta_after_beta(self):
        self.assertEqual(r.next_version(["0.0.1-beta.1"], True), "0.0.1-beta.2")
        self.assertEqual(r.next_version(["0.0.1-beta.2", "0.0.1-beta.10", "0.0.1-beta.9"], True), "0.0.1-beta.11")

    def test_stable_after_beta_drops_the_suffix(self):
        self.assertEqual(r.next_version(["0.0.1-beta.1", "0.0.1-beta.2"], False), "0.0.1")

    def test_beta_after_stable_starts_the_next_version(self):
        self.assertEqual(r.next_version(["0.0.1-beta.2", "0.0.1"], True), "0.0.2-beta.1")

    def test_stable_after_stable(self):
        self.assertEqual(r.next_version(["0.0.1"], False), "0.0.2")

    def test_ignores_other_tags(self):
        self.assertEqual(r.next_version(["v1", "nightly", "0.0.3-beta.1"], True), "0.0.3-beta.2")


class NextUpstreamTag(unittest.TestCase):
    def pick(self, upstream, built=(), baseline="1.2.0-beta.4"):
        picked = r.next_upstream_tag(upstream, built, baseline)
        return picked[0] if picked else None

    def test_nothing_new(self):
        self.assertIsNone(self.pick(["1.1.1", "1.2.0-beta.3", "1.2.0-beta.4"]))

    def test_new_beta(self):
        self.assertEqual(self.pick(["1.2.0-beta.4", "1.2.0-beta.5"]), "1.2.0-beta.5")

    def test_betas_sort_numerically(self):
        self.assertEqual(self.pick(["1.2.0-beta.10", "1.2.0-beta.9"]), "1.2.0-beta.9")

    def test_release_comes_after_its_betas(self):
        self.assertEqual(self.pick(["1.2.0", "1.2.0-beta.5"]), "1.2.0-beta.5")
        self.assertEqual(self.pick(["1.2.0"], built=["1.2.0-beta.5"]), "1.2.0")

    def test_oldest_first_so_a_stable_is_not_skipped(self):
        self.assertEqual(self.pick(["1.2.0", "1.3.0-beta.1"]), "1.2.0")

    def test_built_tags_raise_the_floor(self):
        self.assertIsNone(self.pick(["1.2.0-beta.5"], built=["1.2.0-beta.5"]))

    def test_old_stable_is_never_built(self):
        self.assertIsNone(self.pick(["1.1.1", "1.2.0-beta.4"]))

    def test_unparseable_tags_are_ignored(self):
        self.assertEqual(self.pick(["latest", "v2", "1.2.0-beta.5"]), "1.2.0-beta.5")

    def test_v_prefix(self):
        self.assertEqual(self.pick(["v1.2.0-beta.5"]), "v1.2.0-beta.5")


if __name__ == "__main__":
    unittest.main()
