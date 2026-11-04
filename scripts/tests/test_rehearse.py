import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))


class RehearsalTest(unittest.TestCase):
    def test_cli_loads_dependencies_outside_the_repo(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, "-E", str(SCRIPTS / "ops" / "rehearse.py"), "--help"],
                cwd=directory, capture_output=True, text=True, check=False,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--model-dir", result.stdout)

    def test_rendering_does_not_inherit_production_configuration(self):
        spec = importlib.util.spec_from_file_location(
            "rehearse", SCRIPTS / "ops" / "rehearse.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docker-compose.prod.yml").write_text(
                "password: ${POSTGRES_PASSWORD}\n"
            )
            with patch.dict(
                os.environ,
                {
                    "COMPOSE_PROJECT_NAME": "danasea",
                    "POSTGRES_PASSWORD": "production-value",
                },
            ):
                with module.rehearsal_environment(root):
                    self.assertNotIn("COMPOSE_PROJECT_NAME", os.environ)
                    self.assertNotIn("POSTGRES_PASSWORD", os.environ)
                self.assertEqual(os.environ["COMPOSE_PROJECT_NAME"], "danasea")

    def test_isolation_removes_production_names_subnets_and_host_bindings(self):
        spec = importlib.util.spec_from_file_location(
            "rehearse", SCRIPTS / "ops" / "rehearse.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        config = {
            "services": {
                "postgres": {
                    "container_name": "danasea-postgres",
                    "ports": ["5433:5432"],
                    "networks": ["data"],
                },
                "backend": {
                    "container_name": "danasea-backend",
                    "environment": {},
                    "depends_on": {"cli-proxy-api": {}},
                    "networks": {"app": {"ipv4_address": "172.30.10.3"}},
                },
                "frontend": {
                    "container_name": "danasea-frontend",
                    "ports": ["8080:8080"],
                    "networks": ["app"],
                },
                "quyet": {"environment": {}, "networks": ["ai"]},
                "cli-proxy-api": {"container_name": "cli-proxy-api"},
            },
            "networks": {
                "app": {
                    "name": "danasea_edge",
                    "ipam": {"config": [{"subnet": "172.30.10.0/28"}]},
                },
                "data": {},
                "ai": {},
            },
            "volumes": {
                "postgres_data": {"name": "danasea_postgres_data", "external": True}
            },
        }
        isolated = module.isolate(config)
        for service in isolated["services"].values():
            self.assertNotIn("container_name", service)
            self.assertNotIn("ipv4_address", str(service["networks"]))
        self.assertEqual(isolated["networks"], {"app": {}, "data": {}, "ai": {}})
        self.assertEqual(isolated["volumes"], {"postgres_data": {}})
        self.assertNotIn("ports", isolated["services"]["postgres"])
        self.assertEqual(isolated["services"]["frontend"]["ports"], ["127.0.0.1::8080"])
        self.assertNotIn("ports", isolated["services"]["cli-proxy-api"])
        self.assertEqual(
            isolated["services"]["backend"]["environment"]["APP_SCHEDULER_ENABLED"],
            "false",
        )
        self.assertEqual(
            isolated["services"]["quyet"]["environment"]["QUYET_CPU_THREADS"], "2"
        )

    def test_business_probe_rejects_missing_or_deviated_coordinates(self):
        spec = importlib.util.spec_from_file_location(
            "rehearse", SCRIPTS / "ops" / "rehearse.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        with (
            patch.object(module, "execute") as mock_exec,
            patch.object(module, "http_json") as mock_http,
        ):
            mock_exec.return_value = b""
            # Case 1: missing coordinates
            mock_http.side_effect = [
                {},  # register
                {"accessToken": "token123"},  # login
                {"id": "svc1", "name": "REHEARSAL"},  # service detail missing lat/lon
            ]
            with self.assertRaises(ValueError) as ctx:
                module.business_probe("http://127.0.0.1:8080", "test-postgres")
            self.assertIn("missing coordinate fields", str(ctx.exception))

            # Case 2: coordinates deviated
            mock_http.side_effect = [
                {},  # register
                {"accessToken": "token123"},  # login
                {
                    "id": "svc1",
                    "name": "REHEARSAL",
                    "latitude": 10.0,
                    "longitude": 100.0,
                },  # deviated
            ]
            with self.assertRaises(ValueError) as ctx:
                module.business_probe("http://127.0.0.1:8080", "test-postgres")
            self.assertIn("coordinate readback mismatch", str(ctx.exception))




class RehearsalOwnershipRegressionTest(unittest.TestCase):
    def module(self):
        spec = importlib.util.spec_from_file_location(
            "rehearse", SCRIPTS / "ops" / "rehearse.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_mutable_images_are_rejected_before_model_or_docker_io(self):
        module = self.module()
        with patch.object(module.production, "validate_quyet_model") as model:
            with self.assertRaisesRegex(ValueError, "immutable"):
                try:
                    module.rehearse(
                        Path("/unused"),
                        Path("/unused"),
                        {
                            name: "image:latest"
                            for name in ("backend", "frontend", "quyet")
                        },
                        None,
                        Path("/unused"),
                    )
                except OSError:
                    self.fail("Rehearsal touched files before rejecting mutable images")
            model.assert_not_called()

    def test_cleanup_refuses_resources_without_run_ownership(self):
        module = self.module()
        project = "danasea_rehearse_0123456789ab"
        with patch.object(
            module,
            "execute",
            side_effect=[
                b"container-id",
                b'[{"Config":{"Labels":{"com.docker.compose.project":"'
                + project.encode()
                + b'"}}}]',
            ],
        ):
            with self.assertRaisesRegex(ValueError, "ownership"):
                module.assert_owned(project)

    def test_readback_rejects_loss_of_last_coordinate_decimals(self):
        module = self.module()
        with (
            patch.object(module, "execute", return_value=b""),
            patch.object(
                module,
                "http_json",
                side_effect=[
                    {},
                    {"accessToken": "token"},
                    {"latitude": "16.05432198", "longitude": "108.24123456"},
                ],
            ),
        ):
            with self.assertRaisesRegex(ValueError, "coordinate readback mismatch"):
                module.business_probe("http://127.0.0.1:1234", "owned-postgres")

    def test_recovery_retains_database_owner_settings_and_executes_all_worker_tasks(
        self,
    ):
        import json
        from types import SimpleNamespace

        module = self.module()
        config = {
            "services": {
                name: {"environment": {}, "networks": {"internal": {}}}
                for name in (
                    "postgres",
                    "redis",
                    "rabbitmq",
                    "quyet",
                    "cli-proxy-api",
                    "migrate",
                    "backend",
                    "frontend",
                )
            },
            "networks": {"internal": {}},
            "volumes": {"data": {}},
        }
        images = {
            name: "sha256:" + "1" * 64 for name in ("backend", "frontend", "quyet")
        }
        tasks = []
        initialized = []

        def execute(args, **kwargs):
            if "--format" in args and "config" in args:
                return json.dumps(config).encode()
            if "ps" in args:
                return ("owned-" + args[-1]).encode()
            if "port" in args:
                return b"127.0.0.1:18080"
            if "SELECT marker" in str(args):
                return b"preserve"
            if "numeric_precision=46" in str(args):
                return b"2"
            return b""

        def initialize(root, settings, postgres):
            self.assertIn("POSTGRES_APP_USER", settings)
            self.assertIn("POSTGRES_APP_PASSWORD", settings)
            self.assertIn("POSTGRES_USER", settings)
            initialized.append(settings["POSTGRES_DB"])

        def infer(container, url, key, body):
            tasks.append(body["task"])
            return {
                "answers": {"observed": 1},
                "warnings": [],
                "task": body["task"],
                "revision": "233167bba5df61b5375a522bf8a042d8d2189379",
            }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "frontend").mkdir()
            (root / "frontend/nginx.prod.conf").write_text("test config")
            (root / ".env.example").write_text("")
            (root / "docker-compose.prod.yml").write_text("services: {}")
            settings = {
                "POSTGRES_USER": "owner",
                "POSTGRES_APP_USER": "runtime",
                "POSTGRES_APP_PASSWORD": "synthetic",
                "POSTGRES_DB": "danasea_rehearse",
            }
            with (
                patch.object(module, "execute", side_effect=execute),
                patch.object(module, "assert_owned"),
                patch.object(module, "database_fingerprint", return_value="a" * 64),
                patch.object(module, "business_probe"),
                patch.object(module, "http_json", return_value={"content": []}),
                patch.object(module.production, "prepare"),
                patch.object(module.production, "validate_quyet_model"),
                patch.object(module.production, "settings", return_value=settings),
                patch.object(
                    module.production, "initialize_database", side_effect=initialize
                ),
                patch.object(module.backup, "restore"),
                patch.object(module.backup, "verify_restore"),
                patch.object(module.acceptance, "request"),
                patch.object(
                    module.acceptance,
                    "backend_environment",
                    return_value={"AI_QUYET_API_KEY": "test-key"},
                ),
                patch.object(module.acceptance, "internal_post", side_effect=infer),
                patch.object(
                    module.subprocess, "run", return_value=SimpleNamespace(returncode=0)
                ),
            ):
                output = root / "result.json"
                module.rehearse(
                    root, root, images, {"sha": "2" * 40, "images": images}, output
                )
                evidence = json.loads(output.read_text())
            self.assertEqual(
                tasks,
                [
                    "intent",
                    "service_category",
                    "review",
                    "moderation",
                    "relevance",
                    "complaint",
                    "risk",
                ],
            )
            self.assertEqual(len(initialized), 2)
            self.assertNotEqual(initialized[0], initialized[1])
            self.assertTrue(evidence["baseline_application_started"])


if __name__ == '__main__':
    unittest.main()
