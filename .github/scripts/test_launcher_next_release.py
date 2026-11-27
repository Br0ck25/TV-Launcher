import unittest

import launcher_next_release as r


class Version(unittest.TestCase):
    def run_main(self, upstream, built=(), baseline="1.2.0-beta.4"):
        import io, json, os, tempfile
        from contextlib import redirect_stdout
        with tempfile.TemporaryDirectory() as d:
            files = {}
            for name, lines in (("up", upstream), ("built", built)):
                files[name] = os.path.join(d, name)
                with open(files[name], "w") as f:
                    f.write("\n".join(lines) + "\n")
            out = io.StringIO()
            with redirect_stdout(out):
                r.main(["--upstream-tags", files["up"], "--built-tags", files["built"], "--baseline", baseline])
            return json.loads(out.getvalue())

    def test_beta_keeps_nuvio_tvs_version(self):
        self.assertEqual(self.run_main(["1.2.0-beta.5"]), {"tag": "1.2.0-beta.5", "prerelease": True, "version": "1.2.0-beta.5"})

    def test_release_keeps_nuvio_tvs_version(self):
        out = self.run_main(["1.2.0"], built=["1.2.0-beta.5"])
        self.assertEqual((out["version"], out["prerelease"]), ("1.2.0", False))

    def test_v_prefix_is_dropped(self):
        self.assertEqual(self.run_main(["v1.3.0-beta.1"])["version"], "1.3.0-beta.1")

    def test_nothing_new(self):
        self.assertEqual(self.run_main(["1.2.0-beta.4"]), {"tag": ""})


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
