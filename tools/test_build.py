import contextlib
import hashlib
import http.client
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock
import urllib.error


spec = importlib.util.spec_from_file_location("ect_build", Path(__file__).with_name("build.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class DownloadTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.data = b"verified dependency"
        self.url = "https://example.invalid/dependency.jar"
        self.cache = self.root / "dependencies" / "fixture.jar"
        self.output = io.StringIO()
        self.context = contextlib.ExitStack()
        self.addCleanup(self.context.close)
        self.context.enter_context(patch.object(build, "BUILD", self.root))
        self.context.enter_context(patch.dict(build.DEPENDENCIES, {
            "fixture": (self.url, hashlib.sha256(self.data).hexdigest())
        }))
        self.open = self.context.enter_context(patch.object(build.urllib.request, "urlopen"))
        self.sleep = self.context.enter_context(patch.object(build.time, "sleep"))
        self.context.enter_context(contextlib.redirect_stdout(self.output))

    def http_error(self, code):
        return urllib.error.HTTPError(self.url, code, "fixture failure", {}, None)

    def test_valid_cache_avoids_network(self):
        self.cache.parent.mkdir()
        self.cache.write_bytes(self.data)
        self.assertEqual(self.cache, build.download("fixture"))
        self.open.assert_not_called()
        self.sleep.assert_not_called()

    def test_success_logs_url_and_writes_verified_bytes(self):
        self.open.return_value = io.BytesIO(self.data)
        self.assertEqual(self.cache, build.download("fixture"))
        self.assertEqual(self.data, self.cache.read_bytes())
        self.assertIn(self.url, self.output.getvalue())
        self.assertIn("attempt 1/3", self.output.getvalue())
        self.assertEqual(self.url, self.open.call_args.args[0].full_url)
        self.assertEqual(60, self.open.call_args.kwargs["timeout"])

    def test_transient_errors_retry_then_succeed(self):
        errors = [self.http_error(code) for code in (404, 408, 429, 500, 502, 503, 504)]
        errors.extend([urllib.error.URLError("connection failed"), TimeoutError(), ConnectionResetError()])
        for error in errors:
            with self.subTest(error=error):
                self.cache.unlink(missing_ok=True)
                self.open.reset_mock()
                self.sleep.reset_mock()
                self.open.side_effect = [error, io.BytesIO(self.data)]
                build.download("fixture")
                self.assertEqual(self.data, self.cache.read_bytes())
                self.assertEqual(2, self.open.call_count)
                self.sleep.assert_called_once_with(2)

    def test_partial_response_retries_without_caching_partial_bytes(self):
        broken = MagicMock()
        broken.__enter__.return_value.read.side_effect = http.client.IncompleteRead(b"partial", 100)
        self.open.side_effect = [broken, io.BytesIO(self.data)]
        build.download("fixture")
        self.assertEqual(self.data, self.cache.read_bytes())
        self.sleep.assert_called_once_with(2)

    def test_persistent_404_stops_after_three_attempts_with_url(self):
        self.open.side_effect = self.http_error(404)
        with self.assertRaises(RuntimeError) as error:
            build.download("fixture")
        self.assertIn(self.url, str(error.exception))
        self.assertIn("fixture", str(error.exception))
        self.assertIn("attempt 3/3", str(error.exception))
        self.assertEqual(3, self.open.call_count)
        self.assertEqual([2, 4], [call.args[0] for call in self.sleep.call_args_list])
        self.assertFalse(self.cache.exists())

    def test_non_retryable_http_errors_stop_immediately(self):
        for code in (400, 401, 403, 410):
            with self.subTest(code=code):
                self.open.reset_mock()
                self.open.side_effect = self.http_error(code)
                with self.assertRaisesRegex(RuntimeError, self.url):
                    build.download("fixture")
                self.assertEqual(1, self.open.call_count)
                self.sleep.assert_not_called()

    def test_checksum_mismatch_is_never_cached_or_retried(self):
        self.open.return_value = io.BytesIO(b"unexpected response")
        with self.assertRaisesRegex(RuntimeError, "checksum mismatch") as error:
            build.download("fixture")
        self.assertIn(self.url, str(error.exception))
        self.assertFalse(self.cache.exists())
        self.open.assert_called_once()
        self.sleep.assert_not_called()

    def test_invalid_cache_is_replaced_after_verification(self):
        self.cache.parent.mkdir()
        self.cache.write_bytes(b"invalid")
        self.open.return_value = io.BytesIO(self.data)
        build.download("fixture")
        self.assertEqual(self.data, self.cache.read_bytes())

    def test_failed_refresh_does_not_overwrite_existing_file(self):
        self.cache.parent.mkdir()
        self.cache.write_bytes(b"invalid")
        self.open.return_value = io.BytesIO(b"unexpected response")
        with self.assertRaises(RuntimeError):
            build.download("fixture")
        self.assertEqual(b"invalid", self.cache.read_bytes())


if __name__ == "__main__":
    unittest.main()
