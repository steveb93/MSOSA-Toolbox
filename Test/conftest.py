import pytest


@pytest.fixture
def neo4j_env(monkeypatch):
    """Inject default Neo4j env vars — use in tests that reload the server module."""
    monkeypatch.setenv("NEO4J_URI", "bolt://localhost:7687")
    monkeypatch.setenv("NEO4J_USERNAME", "neo4j")
    monkeypatch.setenv("NEO4J_PASSWORD", "Password123")
    monkeypatch.setenv("NEO4J_DATABASE", "neo4j")
